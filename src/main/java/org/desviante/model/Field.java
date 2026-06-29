package org.desviante.model;

import org.desviante.model.enums.FieldType;
import java.time.LocalDateTime;

/**
 * Classe abstrata que representa um campo genérico que pode ser adicionado ao Card.
 *
 * <p>Esta classe serve como base para diferentes tipos de campos personalizados
 * que podem enriquecer a funcionalidade de um card. Utiliza o padrão Template Method
 * para definir operações comuns enquanto permite que subclasses especializem comportamentos.</p>
 *
 * <p><strong>Características Principais:</strong></p>
 * <ul>
 *   <li><strong>Identificação:</strong> ID único e referência ao card pai</li>
 *   <li><strong>Tipo:</strong> Discriminador de tipo para polimorfismo</li>
 *   <li><strong>Ordenação:</strong> Posição na lista para controle de exibição</li>
 *   <li><strong>Rastreamento:</strong> Timestamps de criação e última atualização</li>
 *   <li><strong>Polimorfismo:</strong> Métodos abstratos para comportamento específico</li>
 * </ul>
 *
 * <p><strong>Subclasses Concretas:</strong></p>
 * <ul>
 *   <li>{@code ChecklistField}: Campo tipo checklist com itens marcáveis</li>
 *   <li>{@code PercentageField}: Campo com progresso percentual (ex: páginas lidas)</li>
 * </ul>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lucasgch">GitHub</a>
 * @version 2.0
 * @since 1.0
 * @see FieldType
 * @see ChecklistField
 * @see PercentageField
 */
public abstract class Field {

    /**
     * Identificador único do campo.
     * Gerado automaticamente pelo banco de dados.
     */
    protected Long id;

    /**
     * Identificador do card ao qual este campo pertence.
     * Referência para estabelecer o relacionamento pai-filho.
     */
    protected Long cardId;

    /**
     * Tipo do campo (CHECKLIST_ITEM, PERCENTAGE, etc.).
     * Usado como discriminador para polimorfismo em banco de dados.
     */
    protected FieldType fieldType;

    /**
     * Posição do campo na lista de campos do card.
     * Utilizado para controlar a ordem de exibição dos campos.
     */
    protected Integer orderIndex;

    /**
     * Data e hora de criação do campo.
     * Definida automaticamente no momento da criação.
     */
    protected LocalDateTime createdAt;

    /**
     * Data e hora da última atualização do campo.
     * Atualizada automaticamente sempre que o campo for modificado.
     */
    protected LocalDateTime updatedAt;

    /**
     * Construtor padrão protegido.
     * Inicializa timestamps com valores atuais.
     */
    protected Field() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Retorna o texto de exibição do campo para a interface do usuário.
     * Cada tipo de campo deve formatar sua exibição de forma apropriada.
     *
     * @return texto formatado para exibição
     */
    public abstract String getDisplayText();

    /**
     * Verifica se o campo está em estado concluído/completo.
     * A definição de "concluído" varia por tipo de campo.
     *
     * @return true se o campo está concluído, false caso contrário
     */
    public abstract boolean isCompleted();

    /**
     * Calcula a contribuição percentual deste campo para o progresso total.
     * Retorna um valor entre 0.0 e 100.0 representando a conclusão do campo.
     *
     * @return percentual de progresso (0.0 a 100.0)
     */
    public abstract Double getProgressPercentage();

    // Getters e Setters

    /**
     * Retorna o identificador único do campo.
     *
     * @return identificador único do campo, ou null se não foi definido
     */
    public Long getId() {
        return id;
    }

    /**
     * Define o identificador único do campo.
     *
     * @param id identificador único a ser definido
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Retorna o identificador do card ao qual o campo pertence.
     *
     * @return identificador do card pai, ou null se não foi definido
     */
    public Long getCardId() {
        return cardId;
    }

    /**
     * Define o identificador do card ao qual o campo pertence.
     *
     * @param cardId identificador do card pai a ser definido
     */
    public void setCardId(Long cardId) {
        this.cardId = cardId;
    }

    /**
     * Retorna o tipo do campo.
     *
     * @return tipo do campo (CHECKLIST_ITEM, PERCENTAGE, etc.)
     */
    public FieldType getFieldType() {
        return fieldType;
    }

    /**
     * Define o tipo do campo.
     *
     * @param fieldType tipo do campo a ser definido
     */
    public void setFieldType(FieldType fieldType) {
        this.fieldType = fieldType;
    }

    /**
     * Retorna a posição do campo na lista de campos do card.
     *
     * @return posição do campo na lista (baseada em zero)
     */
    public Integer getOrderIndex() {
        return orderIndex;
    }

    /**
     * Define a posição do campo na lista de campos do card.
     *
     * @param orderIndex nova posição do campo na lista
     */
    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    /**
     * Retorna a data e hora de criação do campo.
     *
     * @return data e hora de criação, nunca null
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Define a data e hora de criação do campo.
     *
     * @param createdAt nova data e hora de criação
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Retorna a data e hora da última atualização do campo.
     *
     * @return data e hora da última atualização, nunca null
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Define a data e hora da última atualização do campo.
     *
     * @param updatedAt nova data e hora de atualização
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Atualiza o timestamp de atualização para o momento atual.
     * Útil para registrar modificações no campo.
     */
    protected void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}