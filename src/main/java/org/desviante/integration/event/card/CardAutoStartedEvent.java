package org.desviante.integration.event.card;

import lombok.Builder;
import lombok.Data;
import org.desviante.integration.event.DomainEvent;

import java.time.LocalDateTime;

/**
 * Evento disparado quando um card é movido automaticamente para coluna PENDING
 * porque seu progresso saiu de 0%.
 *
 * <p>Este evento é publicado após o CardProgressStartedEvent ser processado e o
 * card ter sido movido automaticamente da coluna INITIAL para a primeira coluna
 * do tipo PENDING do board.</p>
 *
 * <p><strong>Propósito:</strong></p>
 * <p>Notificar a UI que o trabalho em um card começou, permitindo exibir um dialog
 * informativo ao usuário e recarregar a view do board para refletir a mudança visual.</p>
 *
 * <p><strong>Fluxo Típico:</strong></p>
 * <ol>
 *   <li>Field é atualizado e o progresso do card sai de 0%</li>
 *   <li>CardProgressStartedEvent é publicado</li>
 *   <li>CardProgressStartObserver move card para coluna PENDING</li>
 *   <li>CardAutoStartedEvent é publicado</li>
 *   <li>CardStartUIObserver exibe dialog e recarrega view</li>
 * </ol>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 1.0
 * @since 2.1
 * @see DomainEvent
 * @see CardProgressStartedEvent
 */
@Data
@Builder
public class CardAutoStartedEvent implements DomainEvent {

    /**
     * ID do card que foi auto-iniciado.
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
        return "CardAutoStarted";
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
            "O card '%s' saiu de 0%% de progresso e foi movido automaticamente para a coluna '%s'.",
            cardTitle,
            pendingColumnName
        );
    }
}
