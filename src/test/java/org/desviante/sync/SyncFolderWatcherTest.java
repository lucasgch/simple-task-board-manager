package org.desviante.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Watcher NIO da pasta de sincronização: notificação quando outro
 * dispositivo publica uma geração mais nova durante a execução.
 */
@DisplayName("SyncFolderWatcher - Detecção de Novas Gerações em Execução")
class SyncFolderWatcherTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Notifica quando o manifest remoto avança além da geração local")
    void notifiesWhenRemoteGenerationAdvances() throws Exception {
        Path dataDir = Files.createDirectories(tempDir.resolve("data"));
        Path syncDir = tempDir.resolve("cloud").resolve(SyncStateRepository.SYNC_SUBDIR);
        SyncStateRepository repository = new SyncStateRepository(dataDir);
        repository.saveState(SyncState.builder().lastSyncedGeneration(1).build());

        CountDownLatch notified = new CountDownLatch(1);
        AtomicLong seenGeneration = new AtomicLong();
        try (SyncFolderWatcher watcher = new SyncFolderWatcher(syncDir, dataDir, 100, generation -> {
            seenGeneration.set(generation);
            notified.countDown();
        })) {
            watcher.start();
            Thread.sleep(300); // garante o registro do WatchService antes do evento

            repository.saveManifest(syncDir, SyncManifest.builder()
                    .generation(2).deviceId("device-B").build());

            assertTrue(notified.await(10, TimeUnit.SECONDS),
                    "Watcher deve notificar a chegada de uma geração mais nova");
            assertEquals(2, seenGeneration.get());
        }
    }

    @Test
    @DisplayName("Não notifica para o export do próprio dispositivo (geração igual à local)")
    void doesNotNotifyForOwnGeneration() throws Exception {
        Path dataDir = Files.createDirectories(tempDir.resolve("data"));
        Path syncDir = tempDir.resolve("cloud").resolve(SyncStateRepository.SYNC_SUBDIR);
        SyncStateRepository repository = new SyncStateRepository(dataDir);
        repository.saveState(SyncState.builder().lastSyncedGeneration(3).build());

        CountDownLatch notified = new CountDownLatch(1);
        try (SyncFolderWatcher watcher = new SyncFolderWatcher(syncDir, dataDir, 100,
                generation -> notified.countDown())) {
            watcher.start();
            Thread.sleep(300);

            repository.saveManifest(syncDir, SyncManifest.builder()
                    .generation(3).deviceId("device-A").build());

            assertFalse(notified.await(2, TimeUnit.SECONDS),
                    "Geração igual à local não deve gerar notificação");
        }
    }

    @Test
    @DisplayName("Notifica apenas uma vez por geração (sem repetição na mesma execução)")
    void notifiesOnlyOncePerGeneration() throws Exception {
        Path dataDir = Files.createDirectories(tempDir.resolve("data"));
        Path syncDir = tempDir.resolve("cloud").resolve(SyncStateRepository.SYNC_SUBDIR);
        SyncStateRepository repository = new SyncStateRepository(dataDir);
        repository.saveState(SyncState.builder().lastSyncedGeneration(1).build());

        CountDownLatch first = new CountDownLatch(1);
        CountDownLatch second = new CountDownLatch(2);
        try (SyncFolderWatcher watcher = new SyncFolderWatcher(syncDir, dataDir, 100, generation -> {
            first.countDown();
            second.countDown();
        })) {
            watcher.start();
            Thread.sleep(300);

            SyncManifest manifest = SyncManifest.builder().generation(2).deviceId("device-B").build();
            repository.saveManifest(syncDir, manifest);
            assertTrue(first.await(10, TimeUnit.SECONDS));

            // Regravação da MESMA geração (retry de upload do provedor)
            repository.saveManifest(syncDir, manifest);
            assertFalse(second.await(2, TimeUnit.SECONDS),
                    "A mesma geração não deve ser notificada duas vezes");
        }
    }
}
