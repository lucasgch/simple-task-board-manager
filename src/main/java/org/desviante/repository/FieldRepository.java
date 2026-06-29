package org.desviante.repository;

import org.desviante.model.Field;
import org.desviante.model.ChecklistField;
import org.desviante.model.PercentageField;
import org.desviante.model.enums.FieldType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * Repository para gerenciar campos genéricos (fields) no banco de dados H2.
 *
 * <p>Esta classe implementa operações de persistência para diferentes tipos de campos
 * que podem ser adicionados aos cards, utilizando um padrão polimórfico com
 * discriminador de tipo.</p>
 *
 * <p><strong>Funcionalidades Principais:</strong></p>
 * <ul>
 *   <li><strong>CRUD Polimórfico:</strong> Criar, ler, atualizar e deletar campos de diferentes tipos</li>
 *   <li><strong>Discriminador de Tipo:</strong> Usa field_type para distinguir ChecklistField e PercentageField</li>
 *   <li><strong>RowMapper Dinâmico:</strong> Mapeia resultados para a subclasse apropriada</li>
 *   <li><strong>Gerenciamento de Ordem:</strong> Controle de posicionamento dos campos</li>
 *   <li><strong>Consultas Especializadas:</strong> Busca por tipo, card, etc.</li>
 * </ul>
 *
 * <p><strong>Compatibilidade H2:</strong> Este repository é otimizado para H2 Database
 * com persistência local, utilizando sintaxe SQL compatível e tipos de dados apropriados.</p>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 1.0
 * @since 2.0
 * @see Field
 * @see ChecklistField
 * @see PercentageField
 * @see FieldType
 */
@Repository
public class FieldRepository {

    /**
     * Template JDBC para execução de operações no banco de dados.
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * Construtor que inicializa o repository.
     *
     * @param dataSource fonte de dados para conexão com o banco H2
     */
    public FieldRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * RowMapper polimórfico que converte resultados do banco para a subclasse apropriada.
     * Usa o campo field_type como discriminador para determinar qual tipo de Field instanciar.
     *
     * <p><strong>Compatibilidade H2:</strong> H2 retorna nomes de colunas em maiúsculas,
     * então o mapeamento usa uppercase para os nomes das colunas.</p>
     */
    private final RowMapper<Field> fieldRowMapper = (ResultSet rs, int rowNum) -> {
        String typeStr = rs.getString("FIELD_TYPE");
        FieldType type = FieldType.valueOf(typeStr);

        return switch (type) {
            case CHECKLIST_ITEM -> mapChecklistField(rs);
            case PERCENTAGE -> mapPercentageField(rs);
        };
    };

    /**
     * Mapeia um ResultSet para um ChecklistField.
     *
     * @param rs ResultSet posicionado no registro atual
     * @return ChecklistField mapeado
     * @throws SQLException se houver erro ao ler os dados
     */
    private ChecklistField mapChecklistField(ResultSet rs) throws SQLException {
        ChecklistField field = new ChecklistField();
        mapCommonFields(field, rs);

        field.setText(rs.getString("CHECKLIST_TEXT"));
        field.setCompleted(rs.getBoolean("CHECKLIST_COMPLETED"));

        Timestamp completedAt = rs.getTimestamp("CHECKLIST_COMPLETED_AT");
        if (completedAt != null) {
            field.setCompletedAt(completedAt.toLocalDateTime());
        }

        return field;
    }

    /**
     * Mapeia um ResultSet para um PercentageField.
     *
     * @param rs ResultSet posicionado no registro atual
     * @return PercentageField mapeado
     * @throws SQLException se houver erro ao ler os dados
     */
    private PercentageField mapPercentageField(ResultSet rs) throws SQLException {
        PercentageField field = new PercentageField();
        mapCommonFields(field, rs);

        field.setLabel(rs.getString("PERCENTAGE_LABEL"));

        // H2 pode retornar 0 para colunas NULL INTEGER
        int total = rs.getInt("PERCENTAGE_TOTAL");
        field.setTotal(rs.wasNull() ? 0 : total);

        int current = rs.getInt("PERCENTAGE_CURRENT");
        field.setCurrent(rs.wasNull() ? 0 : current);

        field.setUnit(rs.getString("PERCENTAGE_UNIT"));

        return field;
    }

    /**
     * Mapeia campos comuns para qualquer tipo de Field.
     *
     * @param field Field a ser preenchido
     * @param rs ResultSet posicionado no registro atual
     * @throws SQLException se houver erro ao ler os dados
     */
    private void mapCommonFields(Field field, ResultSet rs) throws SQLException {
        field.setId(rs.getLong("ID"));
        field.setCardId(rs.getLong("CARD_ID"));
        field.setOrderIndex(rs.getInt("ORDER_INDEX"));

        Timestamp createdAt = rs.getTimestamp("CREATED_AT");
        if (createdAt != null) {
            field.setCreatedAt(createdAt.toLocalDateTime());
        }

        Timestamp updatedAt = rs.getTimestamp("UPDATED_AT");
        if (updatedAt != null) {
            field.setUpdatedAt(updatedAt.toLocalDateTime());
        }
    }

    /**
     * Salva um novo campo no banco de dados H2.
     * O tipo de campo é determinado pela instância concreta passada.
     *
     * @param field campo a ser salvo (ChecklistField ou PercentageField)
     * @return campo salvo com ID gerado automaticamente
     * @throws IllegalArgumentException se o tipo de campo não for suportado
     */
    public Field save(Field field) {
        if (field instanceof ChecklistField) {
            return saveChecklistField((ChecklistField) field);
        } else if (field instanceof PercentageField) {
            return savePercentageField((PercentageField) field);
        } else {
            throw new IllegalArgumentException("Tipo de campo não suportado: " + field.getClass().getName());
        }
    }

    /**
     * Salva um ChecklistField no banco de dados H2.
     */
    private ChecklistField saveChecklistField(ChecklistField field) {
        final String sql = """
            INSERT INTO fields (card_id, field_type, order_index, created_at, updated_at,
                               checklist_text, checklist_completed, checklist_completed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        var keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setLong(1, field.getCardId());
            ps.setString(2, FieldType.CHECKLIST_ITEM.name());
            ps.setInt(3, field.getOrderIndex() != null ? field.getOrderIndex() : 0);
            ps.setTimestamp(4, Timestamp.valueOf(field.getCreatedAt()));
            ps.setTimestamp(5, Timestamp.valueOf(field.getUpdatedAt()));
            ps.setString(6, field.getText());
            ps.setBoolean(7, field.getCompleted() != null ? field.getCompleted() : false);
            ps.setTimestamp(8, field.getCompletedAt() != null ? Timestamp.valueOf(field.getCompletedAt()) : null);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) {
            field.setId(key.longValue());
        }
        return field;
    }

    /**
     * Salva um PercentageField no banco de dados H2.
     */
    private PercentageField savePercentageField(PercentageField field) {
        final String sql = """
            INSERT INTO fields (card_id, field_type, order_index, created_at, updated_at,
                               percentage_label, percentage_total, percentage_current, percentage_unit)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        var keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setLong(1, field.getCardId());
            ps.setString(2, FieldType.PERCENTAGE.name());
            ps.setInt(3, field.getOrderIndex() != null ? field.getOrderIndex() : 0);
            ps.setTimestamp(4, Timestamp.valueOf(field.getCreatedAt()));
            ps.setTimestamp(5, Timestamp.valueOf(field.getUpdatedAt()));
            ps.setString(6, field.getLabel());
            ps.setInt(7, field.getTotal() != null ? field.getTotal() : 0);
            ps.setInt(8, field.getCurrent() != null ? field.getCurrent() : 0);
            ps.setString(9, field.getUnit());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) {
            field.setId(key.longValue());
        }
        return field;
    }

    /**
     * Atualiza um campo existente no banco de dados H2.
     *
     * @param field campo com os novos valores
     * @return true se o campo foi atualizado com sucesso
     */
    public boolean update(Field field) {
        if (field instanceof ChecklistField) {
            return updateChecklistField((ChecklistField) field);
        } else if (field instanceof PercentageField) {
            return updatePercentageField((PercentageField) field);
        } else {
            throw new IllegalArgumentException("Tipo de campo não suportado: " + field.getClass().getName());
        }
    }

    /**
     * Atualiza um ChecklistField no banco de dados.
     */
    private boolean updateChecklistField(ChecklistField field) {
        String sql = """
            UPDATE fields
            SET checklist_text = ?, checklist_completed = ?, checklist_completed_at = ?,
                order_index = ?, updated_at = ?
            WHERE id = ?
            """;

        int rowsAffected = jdbcTemplate.update(sql,
                field.getText(),
                field.getCompleted(),
                field.getCompletedAt() != null ? Timestamp.valueOf(field.getCompletedAt()) : null,
                field.getOrderIndex(),
                Timestamp.valueOf(field.getUpdatedAt()),
                field.getId()
        );

        return rowsAffected > 0;
    }

    /**
     * Atualiza um PercentageField no banco de dados.
     */
    private boolean updatePercentageField(PercentageField field) {
        String sql = """
            UPDATE fields
            SET percentage_label = ?, percentage_total = ?, percentage_current = ?,
                percentage_unit = ?, order_index = ?, updated_at = ?
            WHERE id = ?
            """;

        int rowsAffected = jdbcTemplate.update(sql,
                field.getLabel(),
                field.getTotal(),
                field.getCurrent(),
                field.getUnit(),
                field.getOrderIndex(),
                Timestamp.valueOf(field.getUpdatedAt()),
                field.getId()
        );

        return rowsAffected > 0;
    }

    /**
     * Remove um campo pelo seu identificador único.
     *
     * @param id identificador do campo
     * @return true se o campo foi removido
     */
    public boolean deleteById(Long id) {
        String sql = "DELETE FROM fields WHERE ID = ?";
        int rowsAffected = jdbcTemplate.update(sql, id);
        return rowsAffected > 0;
    }

    /**
     * Remove todos os campos associados a um card.
     *
     * @param cardId identificador do card
     * @return número de campos removidos
     */
    public int deleteByCardId(Long cardId) {
        String sql = "DELETE FROM fields WHERE CARD_ID = ?";
        return jdbcTemplate.update(sql, cardId);
    }

    /**
     * Busca um campo pelo seu identificador único.
     *
     * @param id identificador do campo
     * @return Optional contendo o campo encontrado ou vazio
     */
    public Optional<Field> findById(Long id) {
        String sql = "SELECT * FROM fields WHERE ID = ?";
        List<Field> fields = jdbcTemplate.query(sql, fieldRowMapper, id);
        return fields.isEmpty() ? Optional.empty() : Optional.of(fields.get(0));
    }

    /**
     * Busca todos os campos de um card específico, ordenados por posição.
     *
     * @param cardId identificador do card
     * @return lista de campos ordenados por order_index
     */
    public List<Field> findByCardId(Long cardId) {
        String sql = "SELECT * FROM fields WHERE CARD_ID = ? ORDER BY ORDER_INDEX";
        return jdbcTemplate.query(sql, fieldRowMapper, cardId);
    }

    /**
     * Busca campos de um card filtrados por tipo específico.
     *
     * @param cardId identificador do card
     * @param type tipo de campo (CHECKLIST_ITEM ou PERCENTAGE)
     * @return lista de campos do tipo especificado
     */
    public List<Field> findByCardIdAndType(Long cardId, FieldType type) {
        String sql = "SELECT * FROM fields WHERE CARD_ID = ? AND FIELD_TYPE = ? ORDER BY ORDER_INDEX";
        return jdbcTemplate.query(sql, fieldRowMapper, cardId, type.name());
    }

    /**
     * Conta o número total de campos associados a um card.
     *
     * @param cardId identificador do card
     * @return número total de campos
     */
    public int countByCardId(Long cardId) {
        String sql = "SELECT COUNT(*) FROM fields WHERE CARD_ID = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, cardId);
        return count != null ? count : 0;
    }

    /**
     * Conta campos de um tipo específico associados a um card.
     *
     * @param cardId identificador do card
     * @param type tipo de campo
     * @return número de campos do tipo especificado
     */
    public int countByCardIdAndType(Long cardId, FieldType type) {
        String sql = "SELECT COUNT(*) FROM fields WHERE CARD_ID = ? AND FIELD_TYPE = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, cardId, type.name());
        return count != null ? count : 0;
    }

    /**
     * Atualiza a posição de um campo na lista.
     *
     * @param id identificador do campo
     * @param newOrderIndex nova posição
     * @return true se a posição foi atualizada
     */
    public boolean updateOrderIndex(Long id, int newOrderIndex) {
        String sql = "UPDATE fields SET ORDER_INDEX = ? WHERE ID = ?";
        int rowsAffected = jdbcTemplate.update(sql, newOrderIndex, id);
        return rowsAffected > 0;
    }
}
