package org.desviante.sync;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Optional;
import java.util.function.LongConsumer;

/**
 * Watcher NIO da pasta de sincronização durante a execução do aplicativo.
 *
 * <p>Observa o {@code sync-manifest.json} na pasta de nuvem e, quando o
 * cliente do provedor baixa uma geração mais nova que a local, avisa a UI:
 * "chegaram dados novos — importar exige reiniciar" (o import só acontece
 * no startup, com o banco fechado). Nunca importa nem escreve nada.</p>
 *
 * <p>Detalhes de robustez: escuta {@code ENTRY_CREATE} e {@code ENTRY_MODIFY}
 * (clientes de nuvem gravam via arquivo temporário + rename); aplica um
 * debounce antes de ler o manifest (gravações em andamento); notifica no
 * máximo uma vez por geração. Não usa o {@code FileWatcherService} do app,
 * que é um singleton já dedicado ao {@code app-metadata.json}.</p>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lucasgch">GitHub</a>
 * @since 1.0
 */
@Slf4j
public class SyncFolderWatcher implements AutoCloseable {

    private static final long DEFAULT_DEBOUNCE_MILLIS = 1500;

    private final Path syncDir;
    private final Path dataDir;
    private final long debounceMillis;
    private final LongConsumer onRemoteNewer;

    private volatile boolean running = false;
    private volatile long lastNotifiedGeneration = -1;
    private WatchService watchService;

    /**
     * Cria o watcher da pasta de sincronização.
     *
     * @param syncDir pasta de sincronização ({@code <nuvem>/SimpleTaskBoard})
     * @param dataDir diretório de dados local (para ler o sync-state)
     * @param onRemoteNewer callback com a geração remota detectada; invocado
     *                      na thread do watcher (a UI deve reencaminhar para
     *                      {@code Platform.runLater})
     */
    public SyncFolderWatcher(Path syncDir, Path dataDir, LongConsumer onRemoteNewer) {
        this(syncDir, dataDir, DEFAULT_DEBOUNCE_MILLIS, onRemoteNewer);
    }

    /**
     * Variante com debounce configurável (testes).
     *
     * @param syncDir pasta de sincronização
     * @param dataDir diretório de dados local
     * @param debounceMillis espera após um evento antes de ler o manifest
     * @param onRemoteNewer callback com a geração remota detectada
     */
    SyncFolderWatcher(Path syncDir, Path dataDir, long debounceMillis, LongConsumer onRemoteNewer) {
        this.syncDir = syncDir;
        this.dataDir = dataDir;
        this.debounceMillis = debounceMillis;
        this.onRemoteNewer = onRemoteNewer;
    }

    /**
     * Inicia o monitoramento em uma thread daemon.
     *
     * @throws IOException se o registro no WatchService falhar
     */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        Files.createDirectories(syncDir);
        watchService = FileSystems.getDefault().newWatchService();
        syncDir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
        running = true;
        Thread thread = new Thread(this::watchLoop, "sync-folder-watcher");
        thread.setDaemon(true);
        thread.start();
        log.info("Watcher da pasta de sincronização iniciado: {}", syncDir);
    }

    /**
     * Encerra o monitoramento (a thread daemon termina em seguida).
     */
    @Override
    public synchronized void close() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("Erro ao fechar o watcher de sincronização: {}", e.getMessage());
            }
        }
    }

    private void watchLoop() {
        while (running) {
            try {
                WatchKey key = watchService.take();
                boolean manifestTouched = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        // Eventos perdidos — verificar o manifest por precaução
                        manifestTouched = true;
                    } else if (event.context() instanceof Path changed
                            && SyncStateRepository.MANIFEST_FILENAME.equals(changed.toString())) {
                        manifestTouched = true;
                    }
                }
                if (!key.reset()) {
                    log.warn("Pasta de sincronização deixou de ser observável: {}", syncDir);
                    break;
                }
                if (manifestTouched) {
                    // Deixa o cliente de nuvem terminar a gravação e absorve
                    // a rajada de eventos do mesmo download.
                    Thread.sleep(debounceMillis);
                    drainPendingEvents();
                    checkManifest();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            } catch (Exception e) {
                log.warn("Erro no watcher da pasta de sincronização: {}", e.getMessage());
            }
        }
        log.debug("Watcher da pasta de sincronização finalizado");
    }

    private void drainPendingEvents() {
        WatchKey key;
        while ((key = watchService.poll()) != null) {
            key.pollEvents();
            if (!key.reset()) {
                break;
            }
        }
    }

    private void checkManifest() {
        SyncStateRepository repository = new SyncStateRepository(dataDir);
        Optional<SyncManifest> manifest = repository.loadManifest(syncDir);
        if (manifest.isEmpty()) {
            return;
        }
        long remoteGeneration = manifest.get().getGeneration();
        long localGeneration = repository.loadState().getLastSyncedGeneration();
        if (remoteGeneration > localGeneration && remoteGeneration != lastNotifiedGeneration) {
            lastNotifiedGeneration = remoteGeneration;
            log.info("Nova geração detectada na nuvem durante a execução: {} (local: {})",
                    remoteGeneration, localGeneration);
            onRemoteNewer.accept(remoteGeneration);
        }
    }
}
