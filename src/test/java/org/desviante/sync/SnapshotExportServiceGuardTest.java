package org.desviante.sync;

import org.desviante.config.AppMetadataConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guardas de configuração e de concorrência do export: sincronização
 * desabilitada, pasta ausente, lock local e geração do deviceId.
 */
@DisplayName("SnapshotExportService - Guardas de Configuração e Lock")
class SnapshotExportServiceGuardTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void restoreDataDirProperty() {
        System.clearProperty("app.data.dir");
    }

    private AppMetadataConfig configMock() {
        return mock(AppMetadataConfig.class);
    }

    @Test
    @DisplayName("Sincronização desabilitada: retorna DISABLED sem tocar em nada")
    void disabledSyncShortCircuits() {
        AppMetadataConfig config = configMock();
        when(config.isSyncEnabled()).thenReturn(false);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        SyncResult result = new SnapshotExportService(jdbc, config).syncNow();

        assertEquals(SyncResult.Status.DISABLED, result.status());
        Mockito.verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("Pasta de sincronização não configurada: retorna DISABLED")
    void missingFolderConfigurationShortCircuits() {
        AppMetadataConfig config = configMock();
        when(config.isSyncEnabled()).thenReturn(true);
        when(config.getSyncFolderPath()).thenReturn(Optional.empty());

        SyncResult result = new SnapshotExportService(mock(JdbcTemplate.class), config).syncNow();

        assertEquals(SyncResult.Status.DISABLED, result.status());
    }

    @Test
    @DisplayName("Pasta configurada mas inexistente no disco: retorna ERROR com o caminho")
    void nonexistentFolderIsError() {
        AppMetadataConfig config = configMock();
        when(config.isSyncEnabled()).thenReturn(true);
        when(config.getSyncFolderPath()).thenReturn(Optional.of(tempDir.resolve("sumiu").toString()));

        SyncResult result = new SnapshotExportService(mock(JdbcTemplate.class), config).syncNow();

        assertEquals(SyncResult.Status.ERROR, result.status());
        assertTrue(result.message().contains("sumiu"),
                "Mensagem deve apontar a pasta ausente: " + result.message());
    }

    @Test
    @DisplayName("Lock local ocupado: segunda sincronização na mesma máquina falha sem executar")
    void concurrentSyncOnSameMachineIsRejected() throws Exception {
        Path dataDir = Files.createDirectories(tempDir.resolve("data"));
        Path cloudFolder = Files.createDirectories(tempDir.resolve("cloud"));
        Path lockFile = dataDir.resolve("sync-export.lock");

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SnapshotExportService service = new SnapshotExportService(jdbc, configMock());

        try (FileChannel channel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock held = channel.lock()) {

            SyncResult result = service.sync(dataDir, cloudFolder, "device-A");

            assertEquals(SyncResult.Status.ERROR, result.status(),
                    "Com o lock ocupado, o export não pode prosseguir");
            Mockito.verifyNoInteractions(jdbc);
        }
    }

    @Test
    @DisplayName("Primeira sincronização gera e persiste o deviceId (UUID)")
    void deviceIdIsGeneratedAndPersistedOnFirstSync() throws Exception {
        // dataDir isolado para o lock e temporários desta execução
        Path dataDir = Files.createDirectories(tempDir.resolve("data"));
        System.setProperty("app.data.dir", dataDir.toString());
        Path cloudFolder = Files.createDirectories(tempDir.resolve("cloud"));

        AppMetadataConfig config = configMock();
        when(config.isSyncEnabled()).thenReturn(true);
        when(config.getSyncFolderPath()).thenReturn(Optional.of(cloudFolder.toString()));
        when(config.getSyncDeviceId()).thenReturn(Optional.empty());

        // JdbcTemplate mock: SCRIPT TO não gera arquivo, então o sync termina
        // em ERROR — mas o deviceId já deve ter sido gerado e persistido antes.
        SyncResult result = new SnapshotExportService(mock(JdbcTemplate.class), config).syncNow();

        assertEquals(SyncResult.Status.ERROR, result.status());
        verify(config).updateMetadata(Mockito.any());
    }
}
