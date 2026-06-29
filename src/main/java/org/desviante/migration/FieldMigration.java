package org.desviante.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.List;

/**
 * Componente responsável pela migração automática de dados para o sistema de Fields.
 *
 * <p>Esta classe executa automaticamente durante a inicialização da aplicação,
 * migrando dados existentes das estruturas antigas (checklist_items e card progress)
 * para o novo sistema genérico de fields.</p>
 *
 * <p><strong>Migração Executada:</strong></p>
 * <ol>
 *   <li><strong>Checklist Items:</strong> Migra itens de checklist_items para fields</li>
 *   <li><strong>Card Progress:</strong> Migra totalUnits/currentUnits de cards para percentage fields</li>
 *   <li><strong>Limpeza:</strong> Remove tabelas e colunas antigas (após sucesso)</li>
 * </ol>
 *
 * <p><strong>Segurança:</strong></p>
 * <ul>
 *   <li><strong>Transacional:</strong> Toda a migração ocorre em uma única transação</li>
 *   <li><strong>Idempotente:</strong> Pode ser executada múltiplas vezes sem problemas</li>
 *   <li><strong>Rollback Automático:</strong> Em caso de erro, reverte todas as alterações</li>
 *   <li><strong>Logging Detalhado:</strong> Registra todas as etapas e possíveis erros</li>
 * </ul>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 1.0
 * @since 2.0
 */
@Component
public class FieldMigration {

    private static final Logger log = LoggerFactory.getLogger(FieldMigration.class);

    private final JdbcTemplate jdbcTemplate;

    /**
     * Construtor que inicializa o componente de migração.
     *
     * @param dataSource fonte de dados H2 para execução da migração
     */
    public FieldMigration(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * Executa a migração automaticamente após a inicialização do contexto Spring.
     * Verifica se é necessário migrar antes de executar.
     */
    @PostConstruct
    public void executeMigration() {
        try {
            renamePercentageUnitColumn();
        } catch (Exception e) {
            log.error("ERRO ao renomear coluna percentage_unit: {}", e.getMessage(), e);
        }

        try {
            addParentFieldIdColumn();
        } catch (Exception e) {
            log.error("ERRO ao adicionar coluna parent_field_id: {}", e.getMessage(), e);
        }

        try {
            addChecklistDescriptionColumn();
        } catch (Exception e) {
            log.error("ERRO ao adicionar coluna checklist_description: {}", e.getMessage(), e);
        }

        try {
            migrateChecklistItemsToGroups();
        } catch (Exception e) {
            log.error("ERRO ao migrar checklist items para grupos: {}", e.getMessage(), e);
        }

        try {
            if (needsMigration()) {
                log.info("=== Iniciando migração para sistema de Fields ===");
                performMigration();
                log.info("=== Migração concluída com sucesso! ===");
            } else {
                log.info("Migração de Fields não é necessária (já foi executada)");
            }
        } catch (Exception e) {
            log.error("ERRO durante a migração de Fields: {}", e.getMessage(), e);
            log.warn("A aplicação continuará executando, mas alguns dados podem não ter sido migrados");
        }
    }

    private void addParentFieldIdColumn() {
        if (!checkColumnExists("fields", "parent_field_id")) {
            log.info("Adicionando coluna parent_field_id à tabela fields...");
            jdbcTemplate.execute("ALTER TABLE fields ADD COLUMN parent_field_id BIGINT");
            log.info("Coluna parent_field_id adicionada com sucesso");
        }
    }

    private void addChecklistDescriptionColumn() {
        if (!checkColumnExists("fields", "checklist_description")) {
            log.info("Adicionando coluna checklist_description à tabela fields...");
            jdbcTemplate.execute("ALTER TABLE fields ADD COLUMN checklist_description VARCHAR(255)");
            log.info("Coluna checklist_description adicionada com sucesso");
        }
    }

    private void migrateChecklistItemsToGroups() {
        String sql = "SELECT COUNT(*) FROM fields WHERE FIELD_TYPE = 'CHECKLIST_ITEM' AND PARENT_FIELD_ID IS NULL";
        Integer orphans = jdbcTemplate.queryForObject(sql, Integer.class);
        if (orphans == null || orphans == 0) return;

        log.info("Migrando {} checklist items órfãos para grupos...", orphans);

        List<Long> cardIds = jdbcTemplate.queryForList(
            "SELECT DISTINCT CARD_ID FROM fields WHERE FIELD_TYPE = 'CHECKLIST_ITEM' AND PARENT_FIELD_ID IS NULL",
            Long.class
        );

        for (Long cardId : cardIds) {
            jdbcTemplate.update(
                "INSERT INTO fields (card_id, field_type, order_index, created_at, updated_at, checklist_text) " +
                "VALUES (?, 'CHECKLIST_GROUP', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'Checklist')",
                cardId
            );
            Long groupId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM fields WHERE CARD_ID = ? AND FIELD_TYPE = 'CHECKLIST_GROUP'",
                Long.class, cardId
            );
            jdbcTemplate.update(
                "UPDATE fields SET parent_field_id = ? WHERE CARD_ID = ? AND FIELD_TYPE = 'CHECKLIST_ITEM' AND PARENT_FIELD_ID IS NULL",
                groupId, cardId
            );
        }
        log.info("Migração de checklist grupos concluída para {} cards", cardIds.size());
    }

    private void renamePercentageUnitColumn() {
        if (checkColumnExists("fields", "percentage_unit")) {
            log.info("Renomeando coluna percentage_unit para percentage_description...");
            jdbcTemplate.execute("ALTER TABLE fields RENAME COLUMN percentage_unit TO percentage_description");
            log.info("Coluna percentage_unit renomeada para percentage_description com sucesso");
        }
    }

    /**
     * Verifica se a migração é necessária.
     * A migração é necessária se a tabela checklist_items ainda existir.
     *
     * @return true se a migração deve ser executada
     */
    private boolean needsMigration() {
        try {
            String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'CHECKLIST_ITEMS'";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
            return count != null && count > 0;
        } catch (Exception e) {
            log.debug("Erro ao verificar necessidade de migração: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Executa a migração completa em uma única transação.
     * Se qualquer etapa falhar, toda a migração é revertida.
     */
    @Transactional
    protected void performMigration() {
        createFieldsTable();
        migrateChecklistItems();
        migrateCardProgressFields();
        cleanupOldSchema();
    }

    /**
     * Cria a tabela fields se ela não existir.
     */
    private void createFieldsTable() {
        log.info("Criando tabela fields...");
        String sql = """
            CREATE TABLE IF NOT EXISTS fields (
                -- Common fields for all field types
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                card_id BIGINT NOT NULL,
                field_type VARCHAR(50) NOT NULL,
                order_index INTEGER NOT NULL DEFAULT 0,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                -- Checklist-specific fields (NULL for other types)
                checklist_text TEXT,
                checklist_description VARCHAR(255),
                checklist_completed BOOLEAN DEFAULT FALSE,
                checklist_completed_at TIMESTAMP,

                -- Percentage-specific fields (NULL for other types)
                percentage_label VARCHAR(255),
                percentage_total INTEGER,
                percentage_current INTEGER DEFAULT 0,
                percentage_description VARCHAR(50),
                parent_field_id BIGINT,

                -- Foreign key constraint
                CONSTRAINT fk_fields_cards FOREIGN KEY (card_id)
                    REFERENCES cards(id) ON DELETE CASCADE
            )
            """;

        jdbcTemplate.execute(sql);
        log.info("Tabela fields criada com sucesso");

        // Criar índices
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_fields_card_id ON fields(card_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_fields_type ON fields(field_type)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_fields_order ON fields(card_id, order_index)");
        log.info("Índices criados com sucesso");
    }

    /**
     * Migra itens de checklist_items para a tabela fields.
     * Converte cada item de checklist para um field do tipo CHECKLIST_ITEM.
     */
    private void migrateChecklistItems() {
        log.info("Migrando itens de checklist...");

        // Verifica se há itens para migrar
        String countSql = "SELECT COUNT(*) FROM checklist_items";
        Integer itemCount = jdbcTemplate.queryForObject(countSql, Integer.class);

        if (itemCount == null || itemCount == 0) {
            log.info("Nenhum item de checklist para migrar");
            return;
        }

        log.info("Encontrados {} itens de checklist para migrar", itemCount);

        // Migra os itens
        String migrateSql = """
            INSERT INTO fields (
                card_id, field_type, order_index, created_at, updated_at,
                checklist_text, checklist_completed, checklist_completed_at
            )
            SELECT
                card_id,
                'CHECKLIST_ITEM',
                order_index,
                created_at,
                COALESCE(completed_at, created_at),
                text,
                completed,
                completed_at
            FROM checklist_items
            """;

        int migratedCount = jdbcTemplate.update(migrateSql);
        log.info("Migrados {} itens de checklist com sucesso", migratedCount);
    }

    /**
     * Migra cards com totalUnits/currentUnits para percentage fields.
     * Cria um campo percentual para cada card que tenha progresso do tipo PERCENTAGE.
     */
    private void migrateCardProgressFields() {
        log.info("Migrando progresso de cards para percentage fields...");

        // Verifica se as colunas total_units e current_units ainda existem
        if (!checkColumnExists("cards", "total_units") || !checkColumnExists("cards", "current_units")) {
            log.info("Colunas total_units/current_units já foram removidas - migração já executada");
            return;
        }

        // Verifica quantos cards têm progresso para migrar
        String countSql = """
            SELECT COUNT(*) FROM cards
            WHERE total_units IS NOT NULL
            AND progress_type = 'PERCENTAGE'
            """;

        Integer cardCount = jdbcTemplate.queryForObject(countSql, Integer.class);

        if (cardCount == null || cardCount == 0) {
            log.info("Nenhum card com progresso para migrar");
            return;
        }

        log.info("Encontrados {} cards com progresso para migrar", cardCount);

        // Migra o progresso para percentage fields
        String migrateSql = """
            INSERT INTO fields (
                card_id, field_type, order_index, created_at, updated_at,
                percentage_label, percentage_total, percentage_current, percentage_description
            )
            SELECT
                c.id,
                'PERCENTAGE',
                0,
                c.creation_date,
                c.last_update_date,
                'Progresso',
                c.total_units,
                c.current_units,
                NULL
            FROM cards c
            LEFT JOIN card_types ct ON c.card_type_id = ct.id
            WHERE c.total_units IS NOT NULL
            AND c.progress_type = 'PERCENTAGE'
            """;

        int migratedCount = jdbcTemplate.update(migrateSql);
        log.info("Migrados {} cards com progresso para percentage fields", migratedCount);
    }

    /**
     * Remove a tabela checklist_items e as colunas total_units/current_units da tabela cards.
     * Esta limpeza só é executada após a migração bem-sucedida.
     */
    private void cleanupOldSchema() {
        log.info("Limpando estruturas antigas do banco de dados...");

        try {
            // Remove a tabela checklist_items
            log.info("Removendo tabela checklist_items...");
            jdbcTemplate.execute("DROP TABLE IF EXISTS checklist_items");
            log.info("Tabela checklist_items removida com sucesso");

            // Remove as colunas total_units e current_units da tabela cards
            log.info("Removendo colunas total_units e current_units da tabela cards...");

            // H2 suporta ALTER TABLE DROP COLUMN
            try {
                jdbcTemplate.execute("ALTER TABLE cards DROP COLUMN IF EXISTS total_units");
                log.info("Coluna total_units removida");
            } catch (Exception e) {
                log.warn("Não foi possível remover coluna total_units: {}", e.getMessage());
            }

            try {
                jdbcTemplate.execute("ALTER TABLE cards DROP COLUMN IF EXISTS current_units");
                log.info("Coluna current_units removida");
            } catch (Exception e) {
                log.warn("Não foi possível remover coluna current_units: {}", e.getMessage());
            }

            log.info("Limpeza concluída com sucesso");

        } catch (Exception e) {
            log.error("Erro durante a limpeza do schema: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Verifica se uma coluna existe em uma tabela.
     *
     * @param tableName nome da tabela
     * @param columnName nome da coluna
     * @return true se a coluna existir
     */
    private boolean checkColumnExists(String tableName, String columnName) {
        try {
            String sql = """
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = ? AND COLUMN_NAME = ?
                """;
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class,
                    tableName.toUpperCase(), columnName.toUpperCase());
            return count != null && count > 0;
        } catch (Exception e) {
            log.debug("Erro ao verificar existência da coluna {}.{}: {}", tableName, columnName, e.getMessage());
            return false;
        }
    }

    /**
     * Reverte a migração em caso de erro crítico (método de emergência).
     * NÃO deve ser chamado em condições normais.
     *
     * <p><strong>Atenção:</strong> Este método deve ser usado apenas manualmente
     * para reverter uma migração com problemas graves.</p>
     */
    @Transactional
    public void rollbackMigration() {
        log.warn("=== INICIANDO ROLLBACK DA MIGRAÇÃO ===");
        log.warn("Esta operação irá REMOVER todos os dados migrados");

        try {
            // Remove todos os fields migrados
            jdbcTemplate.execute("DELETE FROM fields WHERE field_type IN ('CHECKLIST_ITEM', 'PERCENTAGE')");
            log.info("Dados migrados removidos da tabela fields");

            log.warn("=== ROLLBACK CONCLUÍDO ===");
            log.warn("IMPORTANTE: Você precisa restaurar as tabelas e colunas antigas manualmente!");
            log.warn("Execute as migrações SQL anteriores novamente para restaurar checklist_items");

        } catch (Exception e) {
            log.error("ERRO durante rollback: {}", e.getMessage(), e);
            throw e;
        }
    }
}
