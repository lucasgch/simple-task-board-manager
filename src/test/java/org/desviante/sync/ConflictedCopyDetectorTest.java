package org.desviante.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Detecção de "conflicted copies" criadas pelos provedores de nuvem.
 */
@DisplayName("ConflictedCopyDetector - Padrões de Nome dos Provedores")
class ConflictedCopyDetectorTest {

    @TempDir
    Path tempDir;

    private void touch(String name) throws IOException {
        Files.createFile(tempDir.resolve(name));
    }

    @Test
    @DisplayName("Detecta os padrões de Dropbox (EN/PT) e duplicatas numeradas")
    void detectsProviderConflictPatterns() throws IOException {
        touch("boards-snapshot (conflicted copy 2026-07-18).sql.gz");
        touch("sync-manifest (Cópia em conflito de PC-Casa 2026-07-18).json");
        touch("boards-snapshot (1).sql.gz");

        List<String> found = ConflictedCopyDetector.findConflictedCopies(tempDir);

        assertEquals(3, found.size(), "Todos os padrões de conflito devem ser detectados: " + found);
    }

    @Test
    @DisplayName("Arquivos normais do sync não geram falso positivo")
    void ignoresRegularSyncFiles() throws IOException {
        touch(SyncStateRepository.SNAPSHOT_FILENAME);
        touch(SyncStateRepository.MANIFEST_FILENAME);
        // Arquivamento intencional da resolução "manter os dados deste computador"
        touch("conflito-20260718-153000-device-abc.sql.gz");

        assertTrue(ConflictedCopyDetector.findConflictedCopies(tempDir).isEmpty(),
                "Arquivos legítimos não devem ser sinalizados");
    }

    @Test
    @DisplayName("Pasta inexistente retorna lista vazia")
    void missingFolderIsEmpty() {
        assertTrue(ConflictedCopyDetector.findConflictedCopies(tempDir.resolve("nao-existe")).isEmpty());
    }
}
