package org.desviante.integration.event.card;

import lombok.Builder;
import lombok.Data;
import org.desviante.integration.event.DomainEvent;

import java.time.LocalDateTime;

/**
 * Evento disparado quando um card é movido automaticamente para coluna PENDING
 * porque seu progresso caiu abaixo de 100%.
 *
 * <p>Este evento é publicado após o CardProgressRegressedEvent ser processado e o
 * card ter sido movido automaticamente de volta para a primeira coluna do tipo
 * PENDING do board.</p>
 *
 * <p><strong>Propósito:</strong></p>
 * <p>Notificar a UI que um card deixou de estar concluído, permitindo exibir um dialog
 * informativo ao usuário e recarregar a view do board para refletir a mudança visual.</p>
 *
 * <p><strong>Fluxo Típico:</strong></p>
 * <ol>
 *   <li>Field é atualizado e o progresso do card cai abaixo de 100%</li>
 *   <li>CardProgressRegressedEvent é publicado</li>
 *   <li>CardProgressRegressionObserver move card para coluna PENDING</li>
 *   <li>CardAutoRegressedEvent é publicado</li>
 *   <li>CardRegressionUIObserver exibe dialog e recarrega view</li>
 * </ol>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 1.0
 * @since 2.1
 * @see DomainEvent
 * @see CardProgressRegressedEvent
 */
@Data
@Builder
public class CardAutoRegressedEvent implements DomainEvent {

    /**
     * ID do card que foi auto-regredido.
     */
    private final Long cardId;

    /**
     * Título do card (para exibição na notificação).
     */
    private final String cardTitle;

    /**
     * ID do board onde o card está localizado.
     */
    private final Long boardId;

    /**
     * ID da coluna PENDING para onde o card foi movido.
     */
    private final Long pendingColumnId;

    /**
     * Nome da coluna PENDING (para exibição na notificação).
     */
    private final String pendingColumnName;

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
        return "CardAutoRegressed";
    }

    @Override
    public Long getEntityId() {
        return cardId;
    }

    @Override
    public String getEntityType() {
        return "Card";
    }

    /**
     * Gera a mensagem de notificação para o usuário.
     *
     * @return mensagem formatada para exibição
     */
    public String getNotificationMessage() {
        return String.format(
            "O card '%s' caiu abaixo de 100%% de progresso e foi movido automaticamente para a coluna '%s'.",
            cardTitle,
            pendingColumnName
        );
    }
}
