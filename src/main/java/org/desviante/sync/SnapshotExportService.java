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
import java.util.Optional;
import java.util.UUID;

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
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lucasgch">GitHub</a>
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SnapshotExportService {

    /** Versão do formato/schema do snapshot publicada no manifest. */
    static final String SCHEMA_VERSION = "1";

    private static final String EXPORT_LOCK_FILENAME = "sync-export.lock";

    private final JdbcTemplate jdbcTemplate;
    private final AppMetadataConfig appMetadataConfig;

    /**
     * Executa a sincronização manual ("Sincronizar agora").
     *
     * <p>Resolve as configurações do usuário, detecta o estado
     * (local × nuvem) e exporta um novo snapshot quando é seguro fazê-lo
     * ({@link SyncStatus#PUSH}). Nos demais estados apenas informa.</p>
     *
     * @return resultado com mensagem amigável para a UI
     */
    public SyncResult syncNow() {
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
        return sync(Paths.get(DataDirectoryPreflight.dataDir()), cloudFolder, resolveDeviceId());
    }

    /**
     * Núcleo da sincronização manual, com dependências explícitas (testável).
     *
     * @param dataDir diretório de dados local da aplicação
     * @param cloudFolder pasta de nuvem escolhida pelo usuário
     * @param deviceId identificador deste dispositivo
     * @return resultado da sincronização
     */
    SyncResult sync(Path dataDir, Path cloudFolder, String deviceId) {
        Path lockFile = dataDir.resolve(EXPORT_LOCK_FILENAME);
        try (FileChannel lockChannel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = lockChannel.tryLock()) {

            if (lock == null) {
                return new SyncResult(SyncResult.Status.ERROR,
                        "Outra sincronização já está em andamento nesta máquina.");
            }
            return doSync(dataDir, cloudFolder, deviceId);

        } catch (Exception e) {
            log.error("Falha na sincronização manual", e);
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
                                    + "Nada foi sobrescrito; a resolução de conflitos chega em uma próxima versão.");
                }
                case PUSH -> {
                    long newGeneration = Math.max(
                            remote.map(SyncManifest::getGeneration).orElse(0L),
                            state.getLastSyncedGeneration()) + 1;
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
                    state.setLastSyncAt(Instant.now().toString());
                    repository.saveState(state);

                    log.info("Snapshot exportado para {} (geração {})", syncDir, newGeneration);
                    return new SyncResult(SyncResult.Status.EXPORTED,
                            "Dados enviados para a pasta de nuvem (geração " + newGeneration + ").");
                }
                default -> throw new IllegalStateException("Status inesperado: " + status);
            }
        } finally {
            Files.deleteIfExists(localSnapshot);
        }
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
