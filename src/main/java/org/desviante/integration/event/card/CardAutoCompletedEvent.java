package org.desviante.integration.event.card;

import lombok.Builder;
import lombok.Data;
import org.desviante.integration.event.DomainEvent;

import java.time.LocalDateTime;

/**
 * Evento disparado quando um card é movido automaticamente para coluna FINAL ao atingir 100%.
 *
 * <p>Este evento é publicado após o CardProgressCompletedEvent ser processado e o
 * card ter sido movido automaticamente para a primeira coluna do tipo FINAL do board.</p>
 *
 * <p><strong>Propósito:</strong></p>
 * <p>Notificar a UI que um card foi auto-completado, permitindo exibir um dialog
 * informativo ao usuário e recarregar a view do board para refletir a mudança visual.</p>
 *
 * <p><strong>Fluxo Típico:</strong></p>
 * <ol>
 *   <li>Field é atualizado atingindo 100% de progresso</li>
 *   <li>CardProgressCompletedEvent é publicado</li>
 *   <li>CardProgressCompletionObserver move card para coluna FINAL</li>
 *   <li>CardAutoCompletedEvent é publicado</li>
 *   <li>UINotificationObserver exibe dialog e recarrega view</li>
 * </ol>
 *
 * <p><strong>Observadores Típicos:</strong></p>
 * <ul>
 *   <li>UINotificationObserver - Notifica usuário via dialog e recarrega board</li>
 *   <li>Logging observers - Registram ações automáticas</li>
 * </ul>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 1.0
 * @since 2.1
 * @see DomainEvent
 * @see CardProgressCompletedEvent
 */
@Data
@Builder
public class CardAutoCompletedEvent implements DomainEvent {

    /**
     * ID do card que foi auto-completado.
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
     * ID da coluna FINAL para onde o card foi movido.
     */
    private final Long finalColumnId;

    /**
     * Nome da coluna FINAL (para exibição na notificação).
     */
    private final String finalColumnName;

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
        return "CardAutoCompleted";
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
            "O card '%s' atingiu 100%% de progresso e foi movido automaticamente para a coluna '%s'.",
            cardTitle,
            finalColumnName
        );
    }
}
