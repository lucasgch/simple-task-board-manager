package org.desviante.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Backups físicos pré-import do arquivo do banco e política de retenção.
 */
@DisplayName("BackupManager - Backups Físicos Pré-Import")
class BackupManagerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Banco inexistente: nada a fazer backup (retorna null)")
    void missingDatabaseReturnsNull() throws IOException {
        BackupManager manager = new BackupManager(tempDir.resolve("backups"), 5);
        assertNull(manager.backupDatabaseFile(tempDir.resolve("nao-existe.mv.db")));
    }

    @Test
    @DisplayName("Backup é cópia fiel com prefixo pre_import_ e nome do arquivo original")
    void backupIsFaithfulCopy() throws IOException {
        Path dbFile = tempDir.resolve("board_h2_db.mv.db");
        Files.writeString(dbFile, "conteudo-do-banco");
        BackupManager manager = new BackupManager(tempDir.resolve("backups"), 5);

        Path backup = manager.backupDatabaseFile(dbFile);

        assertNotNull(backup);
        assertTrue(Files.exists(backup));
        assertTrue(backup.getFileName().toString().startsWith("pre_import_"));
        assertTrue(backup.getFileName().toString().endsWith("_board_h2_db.mv.db"));
        assertEquals("conteudo-do-banco", Files.readString(backup),
                "Backup deve ter o mesmo conteúdo do banco");
        assertTrue(Files.exists(dbFile), "O arquivo original não deve ser movido, apenas copiado");
    }

    @Test
    @DisplayName("Retenção: mantém apenas os N backups pre_import_ mais recentes")
    void retentionKeepsOnlyNewestBackups() throws IOException {
        Path backupDir = Files.createDirectories(tempDir.resolve("backups"));
        // Nomes com timestamp crescente (a ordenação da retenção é lexicográfica)
        for (int i = 1; i <= 4; i++) {
            Files.createFile(backupDir.resolve("pre_import_20260101_00000" + i + "_db.mv.db"));
        }
        // Arquivos de outros mecanismos de backup não são tocados
        Files.createFile(backupDir.resolve("backup_manual_20260101.sql.gz"));

        new BackupManager(backupDir, 2).cleanupOldBackups();

        try (Stream<Path> files = Files.list(backupDir)) {
            List<String> remaining = files.map(p -> p.getFileName().toString()).sorted().toList();
            assertEquals(List.of(
                    "backup_manual_20260101.sql.gz",
                    "pre_import_20260101_000003_db.mv.db",
                    "pre_import_20260101_000004_db.mv.db"), remaining,
                    "Devem restar os 2 pre_import_ mais recentes e o arquivo alheio intacto");
        }
    }
}
