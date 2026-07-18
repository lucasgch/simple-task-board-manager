package org.desviante.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip completo de sincronização em banco H2 temporário:
 * export → wipe → import → contagem de linhas em TODAS as tabelas.
 *
 * <p>A contagem por tabela via {@code INFORMATION_SCHEMA} é a proteção
 * permanente contra regressão de backup incompleto (o dump manual antigo
 * não cobria {@code TASKS} nem tabelas novas). Também cobre o aborto por
 * hash divergente (download parcial/placeholder) e a sinalização de
 * conflito sem tocar no banco local.</p>
 */
@DisplayName("Sincronização - Round-trip Export/Import em H2 temporário")
class SnapshotSyncIntegrationTest {

    private static final String DB_USER = "myboarduser";
    private static final String DB_PASSWORD = "myboardpassword";

    @TempDir
    Path tempDir;

    private Path dataDir;
    private Path cloudFolder;
    private Path dbFileBase;
    private String jdbcUrl;

    private void setupDatabase() throws Exception {
        dataDir = Files.createDirectories(tempDir.resolve("data"));
        cloudFolder = Files.createDirectories(tempDir.resolve("cloud"));
        dbFileBase = dataDir.resolve("board_h2_db");
        // URL sem AUTO_SERVER/DB_CLOSE_DELAY: o engine fecha com a última conexão,
        // espelhando o cenário do import no startup (banco fechado entre operações).
        jdbcUrl = "jdbc:h2:file:" + dbFileBase;

        try (Connection connection = DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD)) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
            try (Statement statement = connection.createStatement()) {
                // Uma linha em cada tabela que o schema.sql não semeia,
                // garantindo que a contagem cubra todas as tabelas do sistema.
                statement.execute("INSERT INTO tasks (title, sent, card_id) VALUES ('Tarefa', FALSE, 1)");
                statement.execute("INSERT INTO calendar_events (title, start_date_time, end_date_time) "
                        + "VALUES ('Evento', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
                statement.execute("INSERT INTO integration_sync_status (card_id, integration_type) "
                        + "VALUES (1, 'GOOGLE_TASKS')");
            }
        }
    }

    private SnapshotExportService exportService() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(jdbcUrl, DB_USER, DB_PASSWORD);
        // AppMetadataConfig não é usado pelo núcleo sync(dataDir, cloudFolder, deviceId)
        return new SnapshotExportService(new JdbcTemplate(dataSource), null);
    }

    private SnapshotImportService importService() {
        return new SnapshotImportService(dataDir, cloudFolder, jdbcUrl, DB_USER, DB_PASSWORD, dbFileBase);
    }

    private Map<String, Long> countAllTables() throws SQLException {
        Map<String, Long> counts = new HashMap<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet tables = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables "
                             + "WHERE table_schema = 'PUBLIC' AND table_type = 'BASE TABLE'")) {
            while (tables.next()) {
                counts.put(tables.getString(1), null);
            }
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD)) {
            for (String table : counts.keySet()) {
                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM \"" + table + "\"")) {
                    rs.next();
                    counts.put(table, rs.getLong(1));
                }
            }
        }
        return counts;
    }

    @Test
    @DisplayName("Export → wipe → import preserva todas as linhas de todas as tabelas")
    void exportWipeImportPreservesAllTables() throws Exception {
        setupDatabase();
        Map<String, Long> before = countAllTables();
        assertFalse(before.isEmpty(), "O schema deve ter criado tabelas");
        assertTrue(before.containsKey("TASKS"), "TASKS deve existir no schema");
        assertTrue(before.get("TASKS") > 0, "TASKS deve ter dados de teste");

        // Export
        SyncResult exported = exportService().sync(dataDir, cloudFolder, "device-A");
        assertEquals(SyncResult.Status.EXPORTED, exported.status(), exported.message());

        Path syncDir = SyncStateRepository.resolveSyncDir(cloudFolder);
        Path snapshot = syncDir.resolve(SyncStateRepository.SNAPSHOT_FILENAME);
        assertTrue(Files.exists(snapshot), "Snapshot deve existir na pasta de nuvem");

        SyncManifest manifest = new SyncStateRepository(dataDir).loadManifest(syncDir).orElseThrow();
        assertEquals(1, manifest.getGeneration());
        assertEquals("device-A", manifest.getDeviceId());
        assertEquals(SyncHashes.sha256OfFile(snapshot), manifest.getSha256(),
                "Hash do manifest deve corresponder ao snapshot publicado");

        // Atomicidade: a nuvem nunca vê arquivos parciais/temporários
        try (Stream<Path> files = Files.list(syncDir)) {
            assertTrue(files.noneMatch(p -> p.getFileName().toString().contains(".tmp-")),
                    "Nenhum temporário deve sobrar na pasta de nuvem");
        }

        // Wipe: simula um dispositivo cujo banco foi perdido/está vazio
        Files.delete(Path.of(dbFileBase + ".mv.db"));

        // Import
        SnapshotImportService.StartupImportResult imported = importService().run();
        assertEquals(SnapshotImportService.StartupImportResult.IMPORTED, imported);

        Map<String, Long> after = countAllTables();
        assertEquals(before, after,
                "Todas as tabelas devem ter exatamente as mesmas linhas após o round-trip");

        // Após importar, novo sync não deve detectar falso dirty nem conflito
        SyncResult secondSync = exportService().sync(dataDir, cloudFolder, "device-A");
        assertEquals(SyncResult.Status.UP_TO_DATE, secondSync.status(), secondSync.message());

        // Alteração local → push avança a geração
        try (Connection connection = DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO boards (id, name, creation_date) VALUES (998, 'Novo Board', CURRENT_TIMESTAMP)");
        }
        SyncResult thirdSync = exportService().sync(dataDir, cloudFolder, "device-A");
        assertEquals(SyncResult.Status.EXPORTED, thirdSync.status(), thirdSync.message());
        assertEquals(2, new SyncStateRepository(dataDir).loadManifest(syncDir).orElseThrow().getGeneration());
    }

    @Test
    @DisplayName("Hash divergente do manifest (download parcial): aborta sem tocar no banco")
    void hashMismatchAbortsWithoutTouchingDatabase() throws Exception {
        setupDatabase();
        SyncResult exported = exportService().sync(dataDir, cloudFolder, "device-A");
        assertEquals(SyncResult.Status.EXPORTED, exported.status(), exported.message());

        // Simula download parcial/placeholder: corrompe o snapshot na nuvem
        Path snapshot = SyncStateRepository.resolveSyncDir(cloudFolder)
                .resolve(SyncStateRepository.SNAPSHOT_FILENAME);
        Files.write(snapshot, new byte[]{1, 2, 3}, StandardOpenOption.TRUNCATE_EXISTING);

        // Dispositivo novo (sem banco local) tenta importar
        Path dataDir2 = Files.createDirectories(tempDir.resolve("data2"));
        Path dbFileBase2 = dataDir2.resolve("board_h2_db");
        SnapshotImportService importer = new SnapshotImportService(
                dataDir2, cloudFolder, "jdbc:h2:file:" + dbFileBase2, DB_USER, DB_PASSWORD, dbFileBase2);

        assertEquals(SnapshotImportService.StartupImportResult.HASH_MISMATCH, importer.run());
        assertFalse(Files.exists(Path.of(dbFileBase2 + ".mv.db")),
                "Banco local não deve ser criado quando o hash diverge");
    }

    @Test
    @DisplayName("Nuvem à frente com alterações locais: conflito sinalizado, banco intacto")
    void conflictIsSignaledWithoutImporting() throws Exception {
        setupDatabase();
        SyncResult exported = exportService().sync(dataDir, cloudFolder, "device-A");
        assertEquals(SyncResult.Status.EXPORTED, exported.status(), exported.message());

        // Alteração local não sincronizada
        try (Connection connection = DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO boards (id, name, creation_date) VALUES (999, 'Local', CURRENT_TIMESTAMP)");
        }
        Map<String, Long> beforeConflict = countAllTables();

        // Outro dispositivo publicou uma geração mais nova na nuvem
        SyncStateRepository repository = new SyncStateRepository(dataDir);
        Path syncDir = SyncStateRepository.resolveSyncDir(cloudFolder);
        SyncManifest manifest = repository.loadManifest(syncDir).orElseThrow();
        manifest.setGeneration(manifest.getGeneration() + 1);
        manifest.setDeviceId("device-B");
        repository.saveManifest(syncDir, manifest);

        assertEquals(SnapshotImportService.StartupImportResult.CONFLICT, importService().run());
        assertTrue(repository.loadState().isPendingConflict(), "Conflito deve ficar sinalizado no estado");
        assertEquals(beforeConflict, countAllTables(), "Banco local deve permanecer intacto em conflito");

        // Sincronização manual também detecta o conflito e não sobrescreve a nuvem
        SyncResult manualSync = exportService().sync(dataDir, cloudFolder, "device-A");
        assertEquals(SyncResult.Status.CONFLICT, manualSync.status(), manualSync.message());
    }

    @Test
    @DisplayName("Dispositivo novo sem manifest na nuvem: nada a importar")
    void freshCloudFolderHasNothingToImport() throws Exception {
        setupDatabase();
        assertEquals(SnapshotImportService.StartupImportResult.NO_REMOTE, importService().run());
    }
}
