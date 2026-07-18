package org.desviante.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.desviante.config.AppMetadataConfig;
import org.desviante.util.DataDirectoryPreflight;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Export do snapshot do banco para a pasta de nuvem ("push").
 *
 * <p>O export é online-safe: usa {@code SCRIPT TO ... COMPRESSION GZIP} do H2,
 * que é transacionalmente consistente com o banco aberto — o banco vivo nunca
 * é copiado nem colocado na pasta sincronizada. A publicação é atômica
 * (temp na mesma pasta + {@code ATOMIC_MOVE}) e protegida por um lock local
 * ({@link FileChannel#tryLock()}) contra dois exports simultâneos na mesma
 * máquina. Conflitos com outros dispositivos são detectados pelo
 * {@link ConflictDetector} — nunca resolvidos por sobrescrita silenciosa.</p>
 *
 * <p>Também executa as decisões do diálogo de conflito: "manter os dados
 * deste computador" ({@link #resolveConflictKeepLocal()}, que arquiva o
 * snapshot remoto antes do push — nada é perdido) e "usar os dados da
 * nuvem" ({@link #resolveConflictUseRemote()}, que agenda o import para a
 * próxima abertura, único momento com o banco fechado).</p>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lucasgch">GitHub</a>
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SnapshotExportService {

    /** Versão do formato/schema do snapshot publicada no manifest. */
    static final String SCHEMA_VERSION = "1";

    /** Subpasta da nuvem com o histórico de gerações anteriores do snapshot. */
    static final String HISTORY_SUBDIR = "history";

    /** Quantas gerações anteriores manter em {@code history/}. */
    static final int SNAPSHOT_HISTORY_RETENTION = 5;

    private static final String EXPORT_LOCK_FILENAME = "sync-export.lock";
    private static final DateTimeFormatter ARCHIVE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final java.util.regex.Pattern HISTORY_GENERATION_PATTERN =
            java.util.regex.Pattern.compile("boards-snapshot-g(\\d+)");

    private final JdbcTemplate jdbcTemplate;
    private final AppMetadataConfig appMetadataConfig;

    /**
     * Executa a sincronização manual ou de fechamento ("Sincronizar agora").
     *
     * <p>Resolve as configurações do usuário, detecta o estado
     * (local × nuvem) e exporta um novo snapshot quando é seguro fazê-lo
     * ({@link SyncStatus#PUSH}). Nos demais estados apenas informa.</p>
     *
     * @return resultado com mensagem amigável para a UI
     */
    public SyncResult syncNow() {
        return withValidSettings(this::sync);
    }

    /**
     * Resolução de conflito: manter os dados deste computador.
     *
     * <p>O snapshot remoto é renomeado para {@code conflito-<data>-<device>.sql.gz}
     * (nada é perdido) e o banco local é publicado como a nova geração.</p>
     *
     * @return resultado com mensagem amigável para a UI
     */
    public SyncResult resolveConflictKeepLocal() {
        return withValidSettings(this::resolveKeepLocal);
    }

    /**
     * Resolução de conflito: usar os dados da nuvem.
     *
     * <p>O import só pode acontecer com o banco fechado, então a decisão é
     * persistida no estado local e executada na próxima abertura do app
     * (com backup físico prévio do banco atual).</p>
     *
     * @return resultado com mensagem amigável para a UI
     */
    public SyncResult resolveConflictUseRemote() {
        return withValidSettings(this::resolveUseRemote);
    }

    /**
     * Valida configurações do usuário e delega para a ação de sincronização.
     */
    private SyncResult withValidSettings(SyncAction action) {
        if (!appMetadataConfig.isSyncEnabled()) {
            return new SyncResult(SyncResult.Status.DISABLED,
                    "Sincronização desabilitada. Habilite em Preferências.");
        }
        Optional<String> folder = appMetadataConfig.getSyncFolderPath();
        if (folder.isEmpty() || folder.get().isBlank()) {
            return new SyncResult(SyncResult.Status.DISABLED,
                    "Pasta de sincronização não configurada. Defina em Preferências.");
        }
        Path cloudFolder = Paths.get(folder.get());
        if (!Files.isDirectory(cloudFolder)) {
            return new SyncResult(SyncResult.Status.ERROR,
                    "Pasta de sincronização não encontrada: " + cloudFolder);
        }
        return action.run(Paths.get(DataDirectoryPreflight.dataDir()), cloudFolder, resolveDeviceId());
    }

    @FunctionalInterface
    private interface SyncAction {
        SyncResult run(Path dataDir, Path cloudFolder, String deviceId);
    }

    /**
     * Núcleo da sincronização, com dependências explícitas (testável).
     *
     * @param dataDir diretório de dados local da aplicação
     * @param cloudFolder pasta de nuvem escolhida pelo usuário
     * @param deviceId identificador deste dispositivo
     * @return resultado da sincronização
     */
    SyncResult sync(Path dataDir, Path cloudFolder, String deviceId) {
        return locked(dataDir, () -> doSync(dataDir, cloudFolder, deviceId));
    }

    /**
     * Núcleo da resolução "manter os dados deste computador" (testável).
     *
     * @param dataDir diretório de dados local da aplicação
     * @param cloudFolder pasta de nuvem escolhida pelo usuário
     * @param deviceId identificador deste dispositivo
     * @return resultado da resolução
     */
    SyncResult resolveKeepLocal(Path dataDir, Path cloudFolder, String deviceId) {
        return locked(dataDir, () -> doResolveKeepLocal(dataDir, cloudFolder, deviceId));
    }

    /**
     * Núcleo da resolução "usar os dados da nuvem" (testável).
     *
     * @param dataDir diretório de dados local da aplicação
     * @param cloudFolder pasta de nuvem (não usada; presente pela simetria da validação)
     * @param deviceId identificador deste dispositivo (idem)
     * @return resultado da resolução
     */
    SyncResult resolveUseRemote(Path dataDir, Path cloudFolder, String deviceId) {
        try {
            SyncStateRepository repository = new SyncStateRepository(dataDir);
            SyncState state = repository.loadState();
            state.setResolveWithRemote(true);
            state.setPendingConflict(true);
            repository.saveState(state);
            return new SyncResult(SyncResult.Status.REMOTE_NEWER,
                    "Decisão registrada. Feche e reabra o aplicativo para importar os dados da nuvem "
                            + "(um backup do banco atual será feito antes).");
        } catch (IOException e) {
            log.error("Falha ao registrar a resolução de conflito", e);
            return new SyncResult(SyncResult.Status.ERROR,
                    "Falha ao registrar a decisão: " + e.getMessage());
        }
    }

    /**
     * Executa a ação sob o lock local de export ({@code FileChannel.tryLock}).
     */
    private SyncResult locked(Path dataDir, Callable<SyncResult> action) {
        Path lockFile = dataDir.resolve(EXPORT_LOCK_FILENAME);
        try (FileChannel lockChannel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = lockChannel.tryLock()) {

            if (lock == null) {
                return new SyncResult(SyncResult.Status.ERROR,
                        "Outra sincronização já está em andamento nesta máquina.");
            }
            return action.call();

        } catch (Exception e) {
            log.error("Falha na sincronização", e);
            return new SyncResult(SyncResult.Status.ERROR,
                    "Falha na sincronização: " + e.getMessage());
        }
    }

    private SyncResult doSync(Path dataDir, Path cloudFolder, String deviceId) throws IOException {
        SyncStateRepository repository = new SyncStateRepository(dataDir);
        Path syncDir = SyncStateRepository.resolveSyncDir(cloudFolder);
        SyncState state = repository.loadState();
        Optional<SyncManifest> remote = repository.loadManifest(syncDir);

        // Snapshot local em arquivo temporário FORA da pasta de nuvem: serve
        // tanto para detectar alterações locais (hash do conteúdo) quanto,
        // se for o caso, como o próprio artefato a publicar.
        Path localSnapshot = dataDir.resolve("sync-snapshot-" + UUID.randomUUID() + ".sql.gz");
        try {
            scriptDatabaseTo(localSnapshot);
            String contentSha256 = SyncHashes.sha256OfGzipContent(localSnapshot);
            boolean dirty = !contentSha256.equals(state.getLastSyncedContentSha256());

            SyncStatus status = ConflictDetector.detect(
                    remote.map(SyncManifest::getGeneration).orElse(null),
                    state.getLastSyncedGeneration(),
                    dirty);

            switch (status) {
                case UP_TO_DATE -> {
                    clearPendingConflict(repository, state);
                    return new SyncResult(SyncResult.Status.UP_TO_DATE,
                            "Tudo sincronizado — nenhuma alteração local ou remota.");
                }
                case PULL -> {
                    return new SyncResult(SyncResult.Status.REMOTE_NEWER,
                            "Há dados mais novos na nuvem. Feche e reabra o aplicativo para importá-los.");
                }
                case CONFLICT -> {
                    state.setPendingConflict(true);
                    repository.saveState(state);
                    return new SyncResult(SyncResult.Status.CONFLICT,
                            "Este computador e a nuvem têm alterações diferentes. "
                                    + "Nada foi sobrescrito — escolha qual versão manter.");
                }
                case PUSH -> {
                    long newGeneration = Math.max(
                            remote.map(SyncManifest::getGeneration).orElse(0L),
                            state.getLastSyncedGeneration()) + 1;
                    return publish(repository, syncDir, state, localSnapshot, contentSha256,
                            deviceId, newGeneration, remote,
                            "Dados enviados para a pasta de nuvem (geração " + newGeneration + ").");
                }
                default -> throw new IllegalStateException("Status inesperado: " + status);
            }
        } finally {
            Files.deleteIfExists(localSnapshot);
        }
    }

    /**
     * Resolução "manter os dados deste computador": arquiva o snapshot
     * remoto como {@code conflito-<data>-<device>.sql.gz} e publica o banco
     * local como a nova geração, ignorando a matriz de conflito.
     */
    private SyncResult doResolveKeepLocal(Path dataDir, Path cloudFolder, String deviceId) throws IOException {
        SyncStateRepository repository = new SyncStateRepository(dataDir);
        Path syncDir = SyncStateRepository.resolveSyncDir(cloudFolder);
        SyncState state = repository.loadState();
        Optional<SyncManifest> remote = repository.loadManifest(syncDir);

        Path localSnapshot = dataDir.resolve("sync-snapshot-" + UUID.randomUUID() + ".sql.gz");
        try {
            scriptDatabaseTo(localSnapshot);
            String contentSha256 = SyncHashes.sha256OfGzipContent(localSnapshot);

            String archivedAs = archiveRemoteSnapshot(syncDir, remote);
            long newGeneration = Math.max(
                    remote.map(SyncManifest::getGeneration).orElse(0L),
                    state.getLastSyncedGeneration()) + 1;

            String message = archivedAs != null
                    ? "Dados deste computador mantidos. A versão anterior da nuvem foi arquivada como "
                            + archivedAs + " (nada foi apagado)."
                    : "Dados deste computador enviados para a nuvem.";
            return publish(repository, syncDir, state, localSnapshot, contentSha256,
                    deviceId, newGeneration, remote, message);
        } finally {
            Files.deleteIfExists(localSnapshot);
        }
    }

    /**
     * Renomeia o snapshot remoto atual para {@code conflito-<data>-<device>.sql.gz}.
     *
     * @return nome do arquivo de arquivamento, ou {@code null} se não havia snapshot
     */
    private String archiveRemoteSnapshot(Path syncDir, Optional<SyncManifest> remote) throws IOException {
        Path snapshot = syncDir.resolve(SyncStateRepository.SNAPSHOT_FILENAME);
        if (!Files.exists(snapshot)) {
            return null;
        }
        String remoteDevice = remote.map(SyncManifest::getDeviceId)
                .filter(d -> d != null && !d.isBlank())
                .orElse("desconhecido");
        String name = "conflito-" + LocalDateTime.now().format(ARCHIVE_TS) + "-" + remoteDevice + ".sql.gz";
        Path target = syncDir.resolve(name);
        if (Files.exists(target)) {
            name = "conflito-" + LocalDateTime.now().format(ARCHIVE_TS) + "-" + remoteDevice
                    + "-" + UUID.randomUUID().toString().substring(0, 8) + ".sql.gz";
            target = syncDir.resolve(name);
        }
        Files.move(snapshot, target);
        log.info("Snapshot remoto arquivado como {}", target);
        return name;
    }

    /**
     * Publica o snapshot + manifest na nuvem e atualiza o estado local.
     *
     * <p>Antes de sobrescrever, a geração anterior do snapshot é movida para
     * {@code history/} (retenção de {@value SNAPSHOT_HISTORY_RETENTION}
     * gerações) — proteção extra contra um push equivocado.</p>
     */
    private SyncResult publish(SyncStateRepository repository, Path syncDir, SyncState state,
                               Path localSnapshot, String contentSha256, String deviceId,
                               long newGeneration, Optional<SyncManifest> previousManifest,
                               String successMessage) throws IOException {
        archivePreviousSnapshotToHistory(syncDir, previousManifest);
        publishSnapshot(localSnapshot, syncDir);
        SyncManifest manifest = SyncManifest.builder()
                .deviceId(deviceId)
                .generation(newGeneration)
                .sha256(SyncHashes.sha256OfFile(syncDir.resolve(SyncStateRepository.SNAPSHOT_FILENAME)))
                .contentSha256(contentSha256)
                .schemaVersion(SCHEMA_VERSION)
                .appVersion(appVersion())
                .exportedAt(Instant.now().toString())
                .build();
        repository.saveManifest(syncDir, manifest);

        state.setLastSyncedGeneration(newGeneration);
        state.setLastSyncedContentSha256(contentSha256);
        state.setPendingConflict(false);
        state.setResolveWithRemote(false);
        state.setLastSyncAt(Instant.now().toString());
        repository.saveState(state);

        log.info("Snapshot exportado para {} (geração {})", syncDir, newGeneration);
        return new SyncResult(SyncResult.Status.EXPORTED, successMessage);
    }

    /**
     * Move a geração anterior do snapshot para {@code history/} antes de
     * publicar a nova, mantendo as {@value SNAPSHOT_HISTORY_RETENTION}
     * mais recentes.
     *
     * <p>Se o snapshot atual já foi movido (ex.: arquivado como
     * {@code conflito-*.sql.gz} pela resolução de conflito), não faz nada.
     * A janela entre este move e o {@code ATOMIC_MOVE} da nova geração é
     * segura para importadores: manifest sem snapshot correspondente
     * resulta em abort por hash, nunca em import parcial.</p>
     */
    private void archivePreviousSnapshotToHistory(Path syncDir, Optional<SyncManifest> previousManifest)
            throws IOException {
        Path current = syncDir.resolve(SyncStateRepository.SNAPSHOT_FILENAME);
        if (!Files.exists(current)) {
            return;
        }
        Path historyDir = syncDir.resolve(HISTORY_SUBDIR);
        Files.createDirectories(historyDir);
        String label = "g" + previousManifest.map(SyncManifest::getGeneration).orElse(0L);
        Path target = historyDir.resolve("boards-snapshot-" + label + ".sql.gz");
        if (Files.exists(target)) {
            target = historyDir.resolve("boards-snapshot-" + label + "-"
                    + UUID.randomUUID().toString().substring(0, 8) + ".sql.gz");
        }
        Files.move(current, target);
        log.info("Geração anterior do snapshot movida para o histórico: {}", target);
        cleanupHistory(historyDir);
    }

    /**
     * Remove entradas antigas do histórico, mantendo as
     * {@value SNAPSHOT_HISTORY_RETENTION} gerações mais recentes.
     */
    private void cleanupHistory(Path historyDir) {
        try (java.util.stream.Stream<Path> files = Files.list(historyDir)) {
            java.util.List<Path> entries = files
                    .filter(p -> p.getFileName().toString().startsWith("boards-snapshot-g"))
                    .sorted(java.util.Comparator
                            .comparingLong(SnapshotExportService::historyGeneration)
                            .thenComparing(p -> p.getFileName().toString()))
                    .toList();
            for (int i = 0; i < entries.size() - SNAPSHOT_HISTORY_RETENTION; i++) {
                Files.deleteIfExists(entries.get(i));
                log.info("Entrada antiga do histórico removida: {}", entries.get(i));
            }
        } catch (IOException e) {
            log.warn("Erro ao limpar o histórico de snapshots: {}", e.getMessage());
        }
    }

    private static long historyGeneration(Path entry) {
        java.util.regex.Matcher matcher =
                HISTORY_GENERATION_PATTERN.matcher(entry.getFileName().toString());
        return matcher.find() ? Long.parseLong(matcher.group(1)) : 0L;
    }

    /**
     * Gera o snapshot consistente do banco aberto via {@code SCRIPT TO}.
     */
    private void scriptDatabaseTo(Path target) {
        // SCRIPT TO exige o caminho como literal SQL — escapar aspas simples
        String escapedPath = target.toAbsolutePath().toString().replace("'", "''");
        jdbcTemplate.execute("SCRIPT TO '" + escapedPath + "' COMPRESSION GZIP");
    }

    /**
     * Publica o snapshot na pasta de nuvem com escrita atômica: cópia para
     * um temp na MESMA pasta e {@code ATOMIC_MOVE} para o nome final — o
     * cliente de nuvem nunca observa um snapshot parcial.
     */
    private void publishSnapshot(Path localSnapshot, Path syncDir) throws IOException {
        Files.createDirectories(syncDir);
        Path remoteTemp = syncDir.resolve(SyncStateRepository.SNAPSHOT_FILENAME + ".tmp-" + UUID.randomUUID());
        try {
            Files.copy(localSnapshot, remoteTemp, StandardCopyOption.REPLACE_EXISTING);
            Path target = syncDir.resolve(SyncStateRepository.SNAPSHOT_FILENAME);
            try {
                Files.move(remoteTemp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(remoteTemp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(remoteTemp);
        }
    }

    private void clearPendingConflict(SyncStateRepository repository, SyncState state) throws IOException {
        if (state.isPendingConflict()) {
            state.setPendingConflict(false);
            state.setResolveWithRemote(false);
            repository.saveState(state);
        }
    }

    /**
     * Garante um deviceId persistido, gerando-o na primeira sincronização.
     */
    private String resolveDeviceId() {
        Optional<String> existing = appMetadataConfig.getSyncDeviceId();
        if (existing.isPresent() && !existing.get().isBlank()) {
            return existing.get();
        }
        String generated = UUID.randomUUID().toString();
        try {
            appMetadataConfig.updateMetadata(metadata -> metadata.setSyncDeviceId(generated));
        } catch (IOException e) {
            log.warn("Não foi possível persistir o deviceId gerado: {}", e.getMessage());
        }
        return generated;
    }

    private String appVersion() {
        String version = getClass().getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }
}
