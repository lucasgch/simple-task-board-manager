package org.desviante.sync;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Backups físicos do arquivo do banco H2 antes de um import de sincronização.
 *
 * <p>Diferente do {@code DatabaseBackupService} (dump SQL online via
 * {@code SCRIPT TO}), este gerencia cópias do arquivo {@code .mv.db} feitas
 * com o banco <strong>fechado</strong> — o import roda no startup, antes de
 * o Spring abrir o datasource, então a cópia direta do arquivo é segura e o
 * restore em caso de falha é um simples copy-back.</p>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lucasgch">GitHub</a>
 * @since 1.0
 */
@Slf4j
public class BackupManager {

    private static final String BACKUP_PREFIX = "pre_import_";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Path backupDir;
    private final int retention;

    /**
     * Cria o gerenciador de backups pré-import.
     *
     * @param backupDir diretório de backups (ex.: {@code <dataDir>/backups})
     * @param retention quantos backups pré-import manter (os mais recentes)
     */
    public BackupManager(Path backupDir, int retention) {
        this.backupDir = backupDir;
        this.retention = retention;
    }

    /**
     * Copia o arquivo do banco para o diretório de backups.
     *
     * <p>Deve ser chamado apenas com o banco fechado (startup pré-Spring).</p>
     *
     * @param databaseFile arquivo {@code .mv.db} do banco
     * @return caminho do backup criado, ou {@code null} se o banco ainda não existe
     * @throws IOException se a cópia falhar
     */
    public Path backupDatabaseFile(Path databaseFile) throws IOException {
        if (!Files.exists(databaseFile)) {
            log.info("Banco ainda não existe ({}); nada a fazer backup", databaseFile);
            return null;
        }
        Files.createDirectories(backupDir);
        String name = BACKUP_PREFIX + LocalDateTime.now().format(TS) + "_" + databaseFile.getFileName();
        Path target = backupDir.resolve(name);
        Files.copy(databaseFile, target, StandardCopyOption.REPLACE_EXISTING);
        log.info("Backup físico pré-import criado: {}", target);
        cleanupOldBackups();
        return target;
    }

    /**
     * Remove backups pré-import antigos, mantendo os {@code retention} mais recentes.
     */
    public void cleanupOldBackups() {
        try (Stream<Path> files = Files.list(backupDir)) {
            List<Path> backups = files
                    .filter(p -> p.getFileName().toString().startsWith(BACKUP_PREFIX))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (int i = 0; i < backups.size() - retention; i++) {
                Files.deleteIfExists(backups.get(i));
                log.info("Backup pré-import antigo removido: {}", backups.get(i));
            }
        } catch (IOException e) {
            log.warn("Erro ao limpar backups pré-import antigos: {}", e.getMessage());
        }
    }
}
