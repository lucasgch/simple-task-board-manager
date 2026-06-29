package org.desviante.model;

import org.desviante.model.enums.FieldType;
import java.time.LocalDateTime;

/**
 * Representa um campo do tipo checklist item associado a um card.
 *
 * <p>Esta classe modela um item de checklist que pode ser marcado como concluído
 * ou pendente, mantendo informações sobre sua criação, conclusão e estado.</p>
 *
 * <p><strong>Características Principais:</strong></p>
 * <ul>
 *   <li><strong>Texto:</strong> Descrição textual do item</li>
 *   <li><strong>Estado de Conclusão:</strong> Flag booleano indicando se está concluído</li>
 *   <li><strong>Timestamp de Conclusão:</strong> Registra quando foi concluído</li>
 *   <li><strong>Gerenciamento Automático:</strong> Atualiza timestamps automaticamente</li>
 * </ul>
 *
 * <p><strong>Comportamento Automático:</strong> O setter do campo {@code completed}
 * automaticamente atualiza o campo {@code completedAt} quando o status muda,
 * facilitando o rastreamento de quando o item foi concluído.</p>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lucasgch">GitHub</a>
 * @version 1.0
 * @since 2.0
 * @see Field
 * @see FieldType
 */
public class ChecklistField extends Field {

    /**
     * Texto descritivo do item de checklist.
     * Representa a descrição da tarefa ou verificação a ser realizada.
     */
    private String text;

    /**
     * Status de conclusão do item.
     * true se o item foi concluído, false se está pendente.
     */
    private Boolean completed;

    /**
     * Data e hora de conclusão do item.
     * Definida automaticamente quando o item é marcado como concluído.
     * Pode ser null se o item ainda não foi concluído.
     */
    private LocalDateTime completedAt;

    /**
     * Construtor padrão que inicializa o campo como tipo CHECKLIST_ITEM.
     * Define o item como não concluído e inicializa timestamps.
     */
    public ChecklistField() {
        super();
        this.fieldType = FieldType.CHECKLIST_ITEM;
        this.completed = false;
    }

    /**
     * Construtor que inicializa o item com texto específico.
     *
     * @param text texto descritivo do item de checklist
     */
    public ChecklistField(String text) {
        this();
        this.text = text;
    }

    /**
     * Retorna o texto do checklist item para exibição.
     *
     * @return texto do item
     */
    @Override
    public String getDisplayText() {
        return text;
    }

    /**
     * Verifica se o item de checklist está concluído.
     *
     * @return true se o item foi concluído, false caso contrário
     */
    @Override
    public boolean isCompleted() {
        return completed != null && completed;
    }

    /**
     * Retorna a contribuição percentual deste item para o progresso.
     * Um item de checklist contribui 100% se concluído, 0% caso contrário.
     *
     * @return 100.0 se concluído, 0.0 se pendente
     */
    @Override
    public Double getProgressPercentage() {
        return isCompleted() ? 100.0 : 0.0;
    }

    // Getters e Setters

    /**
     * Retorna o texto descritivo do item.
     *
     * @return texto do item, ou null se não foi definido
     */
    public String getText() {
        return text;
    }

    /**
     * Define o texto descritivo do item.
     *
     * @param text texto a ser definido para o item
     */
    public void setText(String text) {
        this.text = text;
        touch();
    }

    /**
     * Retorna o status de conclusão do item.
     *
     * @return true se o item foi concluído, false caso contrário
     */
    public Boolean getCompleted() {
        return completed;
    }

    /**
     * Define o status de conclusão do item.
     *
     * <p><strong>Comportamento Automático:</strong> Este setter automaticamente
     * gerencia o campo {@code completedAt}:</p>
     * <ul>
     *   <li>Se marcado como concluído (true) e completedAt for null,
     *       define automaticamente a data/hora atual</li>
     *   <li>Se marcado como não concluído (false), limpa o campo completedAt</li>
     *   <li>Atualiza o timestamp updatedAt em ambos os casos</li>
     * </ul>
     *
     * @param completed novo status de conclusão do item
     */
    public void setCompleted(Boolean completed) {
        this.completed = completed;
        if (completed != null && completed && this.completedAt == null) {
            this.completedAt = LocalDateTime.now();
        } else if (completed != null && !completed) {
            this.completedAt = null;
        }
        touch();
    }

    /**
     * Retorna a data e hora de conclusão do item.
     *
     * @return data e hora de conclusão, ou null se o item não foi concluído
     */
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    /**
     * Define a data e hora de conclusão do item.
     *
     * <p><strong>Nota:</strong> Este setter permite sobrescrever o comportamento
     * automático do setter setCompleted(Boolean). Use com cuidado para
     * manter a consistência dos dados.</p>
     *
     * @param completedAt nova data e hora de conclusão, ou null para limpar
     */
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
        touch();
    }

    /**
     * Retorna uma representação em string do checklist field.
     *
     * @return string representando o checklist field
     */
    @Override
    public String toString() {
        return "ChecklistField{" +
                "id=" + id +
                ", cardId=" + cardId +
                ", text='" + text + '\'' +
                ", completed=" + completed +
                ", orderIndex=" + orderIndex +
                '}';
    }
}
