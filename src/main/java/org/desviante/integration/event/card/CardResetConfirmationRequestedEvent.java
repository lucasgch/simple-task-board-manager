package org.desviante.integration.event.card;

import lombok.Builder;
import lombok.Data;
import org.desviante.integration.event.DomainEvent;

import java.time.LocalDateTime;

/**
 * Evento disparado quando um card, atualmente na coluna PENDING, teve seu progresso
 * zerado e é elegível para ser movido de volta à coluna INITIAL ("Não iniciado")
 * mediante confirmação do usuário.
 *
 * <p>Diferente de CardAutoCompletedEvent/CardAutoRegressedEvent/CardAutoStartedEvent,
 * este evento NÃO indica que o card já foi movido: a movimentação é opcional e depende
 * da escolha do usuário em um diálogo de confirmação exibido pela camada de UI.</p>
 *
 * <p><strong>Fluxo Típico:</strong></p>
 * <ol>
 *   <li>Field é atualizado e o progresso do card volta a 0%</li>
 *   <li>CardProgressZeroedEvent é publicado</li>
 *   <li>CardProgressResetObserver verifica se o card está na coluna PENDING e se o
 *   board possui uma coluna INITIAL</li>
 *   <li>CardResetConfirmationRequestedEvent é publicado</li>
 *   <li>CardResetConfirmationUIObserver exibe um diálogo de confirmação; se o usuário
 *   confirmar, o card é movido para a coluna INITIAL</li>
 * </ol>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 1.0
 * @since 2.1
 * @see DomainEvent
 * @see CardProgressZeroedEvent
 */
@Data
@Builder
public class CardResetConfirmationRequestedEvent implements DomainEvent {

    /**
     * ID do card elegível para ser movido de volta à coluna INITIAL.
     */
    private final Long cardId;

    /**
     * Título do card (para exibição na confirmação).
     */
    private final String cardTitle;

    /**
     * ID do board onde o card está localizado.
     */
    private final Long boardId;

    /**
     * ID da coluna INITIAL para onde o card seria movido, caso confirmado.
     */
    private final Long initialColumnId;

    /**
     * Nome da coluna INITIAL (para exibição na confirmação).
     */
    private final String initialColumnName;

    /**
     * Momento exato em que o evento ocorreu.
     */
    @Builder.Default
    private final LocalDateTime occurredOn = LocalDateTime.now();

    @Override
    public LocalDateTime getOccurredOn() {
        return occurredOn;
    }

    @Override
    public String getEventType() {
        return "CardResetConfirmationRequested";
    }

    @Override
    public Long getEntityId() {
        return cardId;
    }

    @Override
    public String getEntityType() {
        return "Card";
    }
}
