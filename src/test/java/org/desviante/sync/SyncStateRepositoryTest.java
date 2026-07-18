package org.desviante.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip de serialização do estado local e do manifest remoto.
 */
@DisplayName("SyncStateRepository - Persistência de Estado e Manifest")
class SyncStateRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Estado local: round-trip preserva todos os campos")
    void stateRoundTrip() throws IOException {
        SyncStateRepository repository = new SyncStateRepository(tempDir);
        SyncState state = SyncState.builder()
                .lastSyncedGeneration(42)
                .lastSyncedContentSha256("abc123")
                .pendingConflict(true)
                .resolveWithRemote(true)
                .lastSyncAt("2026-07-18T12:00:00Z")
                .build();

        repository.saveState(state);
        SyncState loaded = repository.loadState();

        assertEquals(42, loaded.getLastSyncedGeneration());
        assertEquals("abc123", loaded.getLastSyncedContentSha256());
        assertTrue(loaded.isPendingConflict());
        assertTrue(loaded.isResolveWithRemote());
        assertEquals("2026-07-18T12:00:00Z", loaded.getLastSyncAt());
    }

    @Test
    @DisplayName("Estado ausente equivale a nunca sincronizado (geração 0)")
    void missingStateIsInitial() {
        SyncState loaded = new SyncStateRepository(tempDir).loadState();
        assertEquals(0, loaded.getLastSyncedGeneration());
        assertNull(loaded.getLastSyncedContentSha256());
        assertFalse(loaded.isPendingConflict());
    }

    @Test
    @DisplayName("Estado corrompido não derruba: tratado como inicial")
    void corruptedStateFallsBackToInitial() throws IOException {
        Files.writeString(tempDir.resolve(SyncStateRepository.STATE_FILENAME), "{not-json!!");
        SyncState loaded = new SyncStateRepository(tempDir).loadState();
        assertEquals(0, loaded.getLastSyncedGeneration());
    }

    @Test
    @DisplayName("Manifest remoto: round-trip preserva todos os campos")
    void manifestRoundTrip() throws IOException {
        SyncStateRepository repository = new SyncStateRepository(tempDir);
        Path syncDir = tempDir.resolve("cloud").resolve(SyncStateRepository.SYNC_SUBDIR);
        SyncManifest manifest = SyncManifest.builder()
                .deviceId("device-1")
                .generation(7)
                .sha256("filehash")
                .contentSha256("contenthash")
                .schemaVersion("1")
                .appVersion("1.4.1")
                .exportedAt("2026-07-18T12:00:00Z")
                .build();

        repository.saveManifest(syncDir, manifest);
        Optional<SyncManifest> loaded = repository.loadManifest(syncDir);

        assertTrue(loaded.isPresent());
        assertEquals("device-1", loaded.get().getDeviceId());
        assertEquals(7, loaded.get().getGeneration());
        assertEquals("filehash", loaded.get().getSha256());
        assertEquals("contenthash", loaded.get().getContentSha256());
        assertEquals("1", loaded.get().getSchemaVersion());
        assertEquals("1.4.1", loaded.get().getAppVersion());
    }

    @Test
    @DisplayName("Manifest ausente ou ilegível retorna vazio (upload em andamento)")
    void missingOrCorruptedManifestIsEmpty() throws IOException {
        SyncStateRepository repository = new SyncStateRepository(tempDir);
        Path syncDir = tempDir.resolve("cloud");
        assertTrue(repository.loadManifest(syncDir).isEmpty());

        Files.createDirectories(syncDir);
        Files.writeString(syncDir.resolve(SyncStateRepository.MANIFEST_FILENAME), "trunca");
        assertTrue(repository.loadManifest(syncDir).isEmpty());
    }

    @Test
    @DisplayName("Escritas são atômicas: nenhum arquivo temporário sobra após salvar")
    void atomicWritesLeaveNoTempFiles() throws IOException {
        SyncStateRepository repository = new SyncStateRepository(tempDir);
        Path syncDir = tempDir.resolve("cloud").resolve(SyncStateRepository.SYNC_SUBDIR);

        repository.saveState(SyncState.builder().lastSyncedGeneration(1).build());
        repository.saveManifest(syncDir, SyncManifest.builder().generation(1).build());

        try (Stream<Path> files = Files.walk(tempDir)) {
            assertTrue(files.noneMatch(p -> p.getFileName().toString().contains(".tmp-")),
                    "Não deve sobrar arquivo temporário após escrita atômica");
        }
    }

    @Test
    @DisplayName("Campos desconhecidos (versões mais novas do app) são tolerados")
    void unknownFieldsAreTolerated() throws IOException {
        Path syncDir = tempDir.resolve("cloud");
        Files.createDirectories(syncDir);
        Files.writeString(syncDir.resolve(SyncStateRepository.MANIFEST_FILENAME),
                "{\"generation\": 3, \"campoDoFuturo\": \"x\"}");

        Optional<SyncManifest> loaded = new SyncStateRepository(tempDir).loadManifest(syncDir);
        assertTrue(loaded.isPresent());
        assertEquals(3, loaded.get().getGeneration());
    }
}
