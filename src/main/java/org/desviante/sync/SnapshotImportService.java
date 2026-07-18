package org.desviante.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.desviante.config.AppMetadata;
import org.desviante.util.DataDirectoryPreflight;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Import do snapshot da nuvem para o banco local ("pull") no startup.
 *
 * <p>Executa no {@code main()}, <strong>depois</strong> do pre-flight do
 * diretório de dados e <strong>antes</strong> de o Spring subir — o único
 * momento em que o banco está garantidamente fechado neste processo
 * ({@code DB_CLOSE_DELAY=-1} mantém o engine vivo até a JVM morrer, então
 * não existe "banco parado" após o boot). Usa JDBC puro, sem o contexto.</p>
 *
 * <p>Sequência segura do import: valida o hash SHA-256 do snapshot contra o
 * manifest (protege contra downloads parciais e placeholders online-only),
 * faz backup físico do {@code .mv.db}, recria o banco via {@code RUNSCRIPT}
 * e valida as tabelas obrigatórias. Qualquer falha restaura o backup.
 * Em conflito (local e nuvem divergentes), nada é importado — o estado é
 * apenas sinalizado ({@link SyncState#isPendingConflict()}).</p>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lucasgch">GitHub</a>
 * @since 1.0
 */
@Slf4j
public class SnapshotImportService {

    /** Desfecho da verificação/import de startup. */
    public enum StartupImportResult {
        /** Sincronização desabilitada ou não configurada. */
        DISABLED,
        /** Não há manifest na pasta de nuvem (nada a importar). */
        NO_REMOTE,
        /** Local já está na geração remota, sem alterações locais. */
        UP_TO_DATE,
        /** Há alterações locais aguardando export manual. */
        LOCAL_CHANGES_PENDING,
        /** Snapshot remoto importado com sucesso. */
        IMPORTED,
        /** Local e nuvem divergiram; nada foi importado (sinalizado na UI). */
        CONFLICT,
        /** Banco em uso por outro processo; import pulado por segurança. */
        SKIPPED_DB_IN_USE,
        /** Hash do snapshot não confere com o manifest (download parcial?); abortado. */
        HASH_MISMATCH,
        /** Falha inesperada (backup restaurado se o import chegou a começar). */
        ERROR
    }

    private static final String[] REQUIRED_TABLES = {
            "BOARDS", "BOARD_COLUMNS", "CARDS", "TASKS", "BOARD_GROUPS", "CARD_TYPES"
    };

    private static volatile StartupImportResult lastStartupResult = StartupImportResult.DISABLED;
    private static volatile java.util.List<String> lastConflictedCopies = java.util.List.of();

    private final Path dataDir;
    private final Path cloudFolder;
    private final String jdbcUrl;
    private final String dbUser;
    private final String dbPassword;
    private final Path dbFileBase;

    /**
     * Cria o serviço de import com dependências explícitas (testável).
     *
     * @param dataDir diretório de dados local
     * @param cloudFolder pasta de nuvem escolhida pelo usuário
     * @param jdbcUrl URL JDBC <em>sem</em> {@code AUTO_SERVER}/{@code DB_CLOSE_DELAY}
     *                (a conexão deve fechar o engine ao terminar)
     * @param dbUser usuário do banco
     * @param dbPassword senha do banco
     * @param dbFileBase caminho base do arquivo do banco, sem a extensão {@code .mv.db}
     */
    public SnapshotImportService(Path dataDir, Path cloudFolder, String jdbcUrl,
                                 String dbUser, String dbPassword, Path dbFileBase) {
        this.dataDir = dataDir;
        this.cloudFolder = cloudFolder;
        this.jdbcUrl = jdbcUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.dbFileBase = dbFileBase;
    }

    /**
     * Hook de startup: verifica a pasta de nuvem e importa se for seguro.
     *
     * <p>Resolve as configurações do usuário ({@code app-metadata.json}) e a
     * conexão ({@code application.properties}) sem o contexto Spring. Nunca
     * lança exceção — qualquer falha é registrada e a aplicação continua
     * abrindo com o banco local intacto.</p>
     *
     * @return desfecho da verificação/import
     */
    public static StartupImportResult runStartupImportIfEnabled() {
        try {
            Path dataDir = Paths.get(DataDirectoryPreflight.dataDir());
            AppMetadata metadata = readMetadata(dataDir);
            String folder = metadata != null ? metadata.getSyncFolderPath() : null;
            if (metadata == null || !Boolean.TRUE.equals(metadata.getSyncEnabled())
                    || folder == null || folder.isBlank()) {
                return recordResult(StartupImportResult.DISABLED);
            }

            Properties props = loadApplicationProperties();
            String rawUrl = props.getProperty("spring.datasource.url",
                    "jdbc:h2:file:" + dataDir.resolve("board_h2_db"));
            String fileBase = stripUrlParameters(resolvePlaceholders(rawUrl))
                    .replaceFirst("^jdbc:h2:file:", "");

            SnapshotImportService service = new SnapshotImportService(
                    dataDir,
                    Paths.get(folder),
                    "jdbc:h2:file:" + fileBase,
                    props.getProperty("spring.datasource.username", "myboarduser"),
                    props.getProperty("spring.datasource.password", "myboardpassword"),
                    Paths.get(fileBase));
            return recordResult(service.run());

        } catch (Exception e) {
            log.error("Falha inesperada na verificação de sincronização no startup", e);
            return recordResult(StartupImportResult.ERROR);
        }
    }

    /**
     * Desfecho da última verificação de startup, para exibição na UI.
     *
     * @return último resultado registrado ({@code DISABLED} se nunca rodou)
     */
    public static StartupImportResult getLastStartupResult() {
        return lastStartupResult;
    }

    /**
     * Cópias em conflito criadas pelo provedor de nuvem, detectadas na
     * última verificação de startup — para aviso na UI.
     *
     * @return nomes de arquivo suspeitos (vazio se nada detectado)
     */
    public static java.util.List<String> getLastConflictedCopies() {
        return lastConflictedCopies;
    }

    /**
     * Executa a verificação e, se o estado for {@link SyncStatus#PULL}, o import.
     *
     * @return desfecho da operação
     */
    public StartupImportResult run() {
        SyncStateRepository repository = new SyncStateRepository(dataDir);
        Path syncDir = SyncStateRepository.resolveSyncDir(cloudFolder);
        lastConflictedCopies = ConflictedCopyDetector.findConflictedCopies(syncDir);
        Optional<SyncManifest> remoteOpt = repository.loadManifest(syncDir);
        if (remoteOpt.isEmpty()) {
            log.info("Sem manifest em {} — nada a importar", syncDir);
            return StartupImportResult.NO_REMOTE;
        }
        SyncManifest remote = remoteOpt.get();
        SyncState state = repository.loadState();

        // Banco local inexistente (dispositivo novo ou dados perdidos) com
        // snapshot disponível na nuvem: restaurar direto — não há nada local
        // a perder, e a matriz geração×dirty não cobre este caso (o estado
        // local pode dizer "sincronizado" mesmo com o arquivo apagado).
        if (!Files.exists(Paths.get(dbFileBase + ".mv.db"))) {
            log.info("Banco local inexistente — restaurando snapshot da nuvem (geração {})",
                    remote.getGeneration());
            try {
                return importSnapshot(repository, syncDir, remote, state);
            } catch (IOException e) {
                log.error("Falha ao restaurar snapshot da nuvem", e);
                return StartupImportResult.ERROR;
            }
        }

        // Decisão do diálogo de conflito: "usar os dados da nuvem". O pull é
        // executado mesmo com alterações locais (o backup físico prévio dentro
        // de importSnapshot preserva o banco atual). Antes, uma conexão de
        // sondagem garante que o banco não está em uso por outro processo.
        if (state.isResolveWithRemote()) {
            log.info("Resolução de conflito registrada: usar os dados da nuvem (geração {})",
                    remote.getGeneration());
            try (Connection probe = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword)) {
                // Sondagem apenas — fecha antes do import
            } catch (SQLException e) {
                log.warn("Banco local em uso por outro processo — import pulado ({})", e.getMessage());
                return StartupImportResult.SKIPPED_DB_IN_USE;
            }
            try {
                return importSnapshot(repository, syncDir, remote, state);
            } catch (IOException e) {
                log.error("Falha ao importar snapshot da nuvem (resolução de conflito)", e);
                return StartupImportResult.ERROR;
            }
        }

        boolean dirty;
        try {
            dirty = isLocalDirty(state);
        } catch (SQLException e) {
            log.warn("Banco local em uso por outro processo — import pulado ({})", e.getMessage());
            return StartupImportResult.SKIPPED_DB_IN_USE;
        } catch (IOException e) {
            log.error("Falha ao verificar alterações locais — import pulado", e);
            return StartupImportResult.ERROR;
        }

        SyncStatus status = ConflictDetector.detect(
                remote.getGeneration(), state.getLastSyncedGeneration(), dirty);
        log.info("Verificação de sync no startup: geração remota={}, local={}, dirty={} → {}",
                remote.getGeneration(), state.getLastSyncedGeneration(), dirty, status);

        try {
            switch (status) {
                case UP_TO_DATE -> {
                    if (state.isPendingConflict()) {
                        state.setPendingConflict(false);
                        repository.saveState(state);
                    }
                    return StartupImportResult.UP_TO_DATE;
                }
                case PUSH -> {
                    return StartupImportResult.LOCAL_CHANGES_PENDING;
                }
                case CONFLICT -> {
                    state.setPendingConflict(true);
                    repository.saveState(state);
                    return StartupImportResult.CONFLICT;
                }
                case PULL -> {
                    return importSnapshot(repository, syncDir, remote, state);
                }
                default -> throw new IllegalStateException("Status inesperado: " + status);
            }
        } catch (IOException e) {
            log.error("Falha ao atualizar o estado de sincronização", e);
            return StartupImportResult.ERROR;
        }
    }

    /**
     * Importa o snapshot remoto: valida hash → backup físico → recria o
     * banco via {@code RUNSCRIPT} → valida tabelas obrigatórias.
     */
    private StartupImportResult importSnapshot(SyncStateRepository repository, Path syncDir,
                                               SyncManifest remote, SyncState state) throws IOException {
        Path snapshot = syncDir.resolve(SyncStateRepository.SNAPSHOT_FILENAME);
        if (!Files.exists(snapshot)) {
            log.warn("Manifest presente mas snapshot ausente em {} — propagação da nuvem incompleta", syncDir);
            return StartupImportResult.HASH_MISMATCH;
        }
        String fileSha = SyncHashes.sha256OfFile(snapshot);
        if (!fileSha.equals(remote.getSha256())) {
            log.warn("Hash do snapshot não confere com o manifest (download parcial ou placeholder "
                    + "online-only?). Esperado {}, obtido {}. Import abortado.", remote.getSha256(), fileSha);
            return StartupImportResult.HASH_MISMATCH;
        }

        Path dbFile = Paths.get(dbFileBase + ".mv.db");
        Path traceFile = Paths.get(dbFileBase + ".trace.db");
        BackupManager backupManager = new BackupManager(dataDir.resolve("backups"), 5);
        Path backup = backupManager.backupDatabaseFile(dbFile);

        try {
            Files.deleteIfExists(dbFile);
            Files.deleteIfExists(traceFile);

            String newContentSha;
            try (Connection connection = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
                 Statement statement = connection.createStatement()) {
                String escapedPath = snapshot.toAbsolutePath().toString().replace("'", "''");
                statement.execute("RUNSCRIPT FROM '" + escapedPath + "' COMPRESSION GZIP");
                validateRequiredTables(connection);
                // Re-exporta o banco recém-importado para registrar o hash de
                // conteúdo na MESMA forma usada nas verificações de dirty
                // (RUNSCRIPT+SCRIPT não é byte-idêntico ao script original).
                newContentSha = scriptContentSha256(connection);
            }

            state.setLastSyncedGeneration(remote.getGeneration());
            state.setLastSyncedContentSha256(newContentSha);
            state.setPendingConflict(false);
            state.setResolveWithRemote(false);
            state.setLastSyncAt(Instant.now().toString());
            repository.saveState(state);

            log.info("Snapshot da nuvem importado com sucesso (geração {})", remote.getGeneration());
            return StartupImportResult.IMPORTED;

        } catch (Exception e) {
            log.error("Falha no import do snapshot — restaurando backup do banco local", e);
            restoreBackup(backup, dbFile);
            return StartupImportResult.ERROR;
        }
    }

    /**
     * Detecta alterações locais desde a última sincronização.
     *
     * <p>Compara o hash do conteúdo SQL atual com o registrado no estado
     * local. Sem estado prévio (primeira sincronização deste dispositivo),
     * um banco vazio é considerado limpo (permite o primeiro pull) e um
     * banco com dados é considerado dirty (força a sinalização de conflito
     * em vez de sobrescrever dados silenciosamente).</p>
     *
     * @throws SQLException se o banco estiver em uso por outro processo
     */
    private boolean isLocalDirty(SyncState state) throws SQLException, IOException {
        Path dbFile = Paths.get(dbFileBase + ".mv.db");
        if (!Files.exists(dbFile)) {
            return false;
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword)) {
            if (state.getLastSyncedContentSha256() == null) {
                return hasUserData(connection);
            }
            return !scriptContentSha256(connection).equals(state.getLastSyncedContentSha256());
        }
    }

    /**
     * Gera um script temporário do banco e retorna o SHA-256 do conteúdo.
     */
    private String scriptContentSha256(Connection connection) throws SQLException, IOException {
        Path temp = dataDir.resolve("sync-dirty-check-" + UUID.randomUUID() + ".sql.gz");
        try (Statement statement = connection.createStatement()) {
            String escapedPath = temp.toAbsolutePath().toString().replace("'", "''");
            statement.execute("SCRIPT TO '" + escapedPath + "' COMPRESSION GZIP");
            return SyncHashes.sha256OfGzipContent(temp);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private boolean hasUserData(Connection connection) {
        for (String table : new String[]{"BOARDS", "CARDS"}) {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                if (rs.next() && rs.getLong(1) > 0) {
                    return true;
                }
            } catch (SQLException e) {
                // Tabela ainda não existe — banco sem dados de usuário
            }
        }
        return false;
    }

    private void validateRequiredTables(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        for (String table : REQUIRED_TABLES) {
            try (ResultSet tables = metaData.getTables(null, null, table, new String[]{"TABLE"})) {
                if (!tables.next()) {
                    throw new SQLException("Snapshot importado não contém a tabela obrigatória: " + table);
                }
            }
        }
    }

    private void restoreBackup(Path backup, Path dbFile) {
        try {
            Files.deleteIfExists(dbFile);
            if (backup != null) {
                Files.copy(backup, dbFile, StandardCopyOption.REPLACE_EXISTING);
                log.info("Banco local restaurado do backup {}", backup);
            }
        } catch (IOException e) {
            log.error("FALHA AO RESTAURAR O BACKUP {} — restaure manualmente para {}", backup, dbFile, e);
        }
    }

    private static StartupImportResult recordResult(StartupImportResult result) {
        lastStartupResult = result;
        return result;
    }

    private static AppMetadata readMetadata(Path dataDir) {
        Path metadataFile = dataDir.resolve("config").resolve("app-metadata.json");
        if (!Files.exists(metadataFile)) {
            return null;
        }
        try {
            return new ObjectMapper().readValue(metadataFile.toFile(), AppMetadata.class);
        } catch (IOException e) {
            log.warn("app-metadata.json ilegível no startup ({}); sync ignorado", e.getMessage());
            return null;
        }
    }

    private static Properties loadApplicationProperties() throws IOException {
        Properties props = new Properties();
        try (InputStream in = SnapshotImportService.class.getResourceAsStream("/application.properties")) {
            if (in != null) {
                props.load(in);
            }
        }
        return props;
    }

    /**
     * Resolve placeholders {@code ${propriedade:default}} (inclusive
     * aninhados) usando propriedades de sistema, como o Spring faria.
     */
    static String resolvePlaceholders(String value) {
        Pattern innermost = Pattern.compile("\\$\\{([^${}]+)}");
        String result = value;
        Matcher matcher;
        while ((matcher = innermost.matcher(result)).find()) {
            String expression = matcher.group(1);
            int separator = expression.indexOf(':');
            String name = separator >= 0 ? expression.substring(0, separator) : expression;
            String fallback = separator >= 0 ? expression.substring(separator + 1) : "";
            String replacement = System.getProperty(name, fallback);
            result = result.substring(0, matcher.start()) + replacement + result.substring(matcher.end());
        }
        return result;
    }

    /**
     * Remove os parâmetros da URL JDBC ({@code ;AUTO_SERVER=TRUE;...}).
     */
    static String stripUrlParameters(String url) {
        int index = url.indexOf(';');
        return index >= 0 ? url.substring(0, index) : url;
    }
}
