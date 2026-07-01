package org.desviante.integration.event.card;

import lombok.Builder;
import lombok.Data;
import org.desviante.integration.event.DomainEvent;
import org.desviante.model.Card;
import org.desviante.model.enums.ProgressType;

import java.time.LocalDateTime;

/**
 * Evento disparado quando o progresso de um card sai de 0% para um valor maior.
 *
 * <p>Este evento é publicado quando um Field é atualizado e o progresso
 * calculado do card, que estava em 0%, passa a ser maior que 0%. Isso indica
 * que o trabalho no card começou de acordo com seu tipo de progresso.</p>
 *
 * <p><strong>Tipos de Progresso que Disparam Este Evento:</strong></p>
 * <ul>
 *   <li><strong>TOTAL:</strong> Quando a média de todos os fields sai de 0%</li>
 *   <li><strong>PERCENTAGE:</strong> Quando a média dos PercentageFields sai de 0%</li>
 *   <li><strong>CHECKLIST:</strong> Quando a média dos ChecklistFields sai de 0%</li>
 * </ul>
 *
 * <p><strong>NONE</strong> não dispara este evento pois não tem progresso calculado.</p>
 *
 * <p><strong>Observadores Típicos:</strong></p>
 * <ul>
 *   <li>CardProgressStartObserver - Move card para coluna PENDING automaticamente</li>
 *   <li>Logging observers - Registram marcos importantes</li>
 * </ul>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 1.0
 * @since 2.1
 * @see DomainEvent
 * @see Card
 * @see ProgressType
 */
@Data
@Builder
public class CardProgressStartedEvent implements DomainEvent {

    /**
     * Card cujo progresso saiu de 0%.
     */
    private final Card card;

    /**
     * Tipo de progresso do card que iniciou.
     */
    private final ProgressType progressType;

    /**
     * Valor do progresso calculado (deve ser > 0.0).
     */
    private final Double progress;

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
        return "CardProgressStarted";
    }

    @Override
    public Long getEntityId() {
        return card != null ? card.getId() : null;
    }

    @Override
    public String getEntityType() {
        return "Card";
    }

    /**
     * Obtém o ID do card cujo progresso iniciou.
     *
     * @return ID do card, ou null se o card não estiver disponível
     */
    public Long getCardId() {
        return getEntityId();
    }

    /**
     * Verifica se o card deve ser movido automaticamente para coluna PENDING.
     *
     * <p>Apenas cards com tipos de progresso TOTAL, PERCENTAGE ou CHECKLIST
     * devem ser movidos automaticamente. Cards com NONE não são movidos.</p>
     *
     * @return true se o card deve ser auto-iniciado
     */
    public boolean shouldAutoStart() {
        return progressType == ProgressType.TOTAL ||
               progressType == ProgressType.PERCENTAGE ||
               progressType == ProgressType.CHECKLIST;
    }
}
