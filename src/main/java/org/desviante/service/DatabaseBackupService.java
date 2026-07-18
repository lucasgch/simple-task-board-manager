package org.desviante.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Serviço para backup automático do banco de dados.
 *
 * <p>Este serviço cria backups automáticos antes de executar migrações,
 * garantindo que os dados do usuário sejam preservados em caso de falha.</p>
 *
 * <p>O backup utiliza o comando nativo {@code SCRIPT TO} do H2, que gera um
 * snapshot transacionalmente consistente de <strong>todo</strong> o banco
 * (schema e dados de todas as tabelas), mesmo com o banco em uso. Isso
 * substitui a antiga geração manual de INSERTs, que dependia de uma lista
 * fixa de tabelas e colunas e ficava obsoleta a cada mudança de schema.</p>
 *
 * <p>Para restaurar um backup, execute contra um banco vazio:
 * {@code RUNSCRIPT FROM 'arquivo.sql.gz' COMPRESSION GZIP}</p>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 2.0
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseBackupService {

    private final JdbcTemplate jdbcTemplate;
    private static final String BACKUP_DIR = System.getProperty("user.home") + "/myboards/backups";

    /**
     * Cria um backup completo do banco de dados via {@code SCRIPT TO}.
     *
     * @param reason motivo do backup (ex: "before_migration", "manual")
     * @return caminho do arquivo de backup criado
     */
    public String createBackup(String reason) {
        try {
            // Criar diretório de backup se não existir
            Path backupDir = Paths.get(BACKUP_DIR);
            if (!Files.exists(backupDir)) {
                Files.createDirectories(backupDir);
                log.info("Diretório de backup criado: {}", backupDir);
            }

            // Gerar nome do arquivo com timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = String.format("backup_%s_%s.sql.gz", reason, timestamp);
            String backupPath = BACKUP_DIR + "/" + fileName;

            // SCRIPT TO exige o caminho como literal SQL — escapar aspas simples
            String escapedPath = backupPath.replace("'", "''");
            jdbcTemplate.execute("SCRIPT TO '" + escapedPath + "' COMPRESSION GZIP");

            log.info("Backup criado com sucesso: {}", backupPath);
            return backupPath;

        } catch (Exception e) {
            log.error("Erro ao criar backup: {}", e.getMessage());
            throw new RuntimeException("Falha ao criar backup", e);
        }
    }

    /**
     * Lista backups disponíveis.
     *
     * <p>Inclui backups no formato atual ({@code .sql.gz}) e no formato
     * antigo ({@code .sql}), para manter visíveis backups pré-existentes.</p>
     *
     * @return lista de arquivos de backup
     */
    public List<String> listBackups() {
        try {
            Path backupDir = Paths.get(BACKUP_DIR);
            if (!Files.exists(backupDir)) {
                return List.of();
            }

            return Files.list(backupDir)
                .filter(path -> {
                    String name = path.toString();
                    return name.endsWith(".sql") || name.endsWith(".sql.gz");
                })
                .map(path -> path.getFileName().toString())
                .sorted()
                .toList();

        } catch (Exception e) {
            log.error("Erro ao listar backups: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Remove backups antigos, mantendo apenas os mais recentes.
     *
     * @param keepCount número de backups para manter
     */
    public void cleanupOldBackups(int keepCount) {
        try {
            List<String> backups = listBackups();
            if (backups.size() <= keepCount) {
                return;
            }

            int toDelete = backups.size() - keepCount;
            for (int i = 0; i < toDelete; i++) {
                String backupFile = BACKUP_DIR + "/" + backups.get(i);
                Files.deleteIfExists(Paths.get(backupFile));
                log.info("Backup antigo removido: {}", backupFile);
            }

        } catch (Exception e) {
            log.error("Erro ao limpar backups antigos: {}", e.getMessage());
        }
    }

    /**
     * Cria backup antes de uma migração.
     *
     * @return caminho do arquivo de backup
     */
    public String createPreMigrationBackup() {
        log.info("Criando backup antes da migração...");
        String backupPath = createBackup("before_migration");

        // Limpar backups antigos, mantendo apenas os 5 mais recentes
        cleanupOldBackups(5);

        return backupPath;
    }
}
