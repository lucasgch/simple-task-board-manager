package org.desviante.integration.event.card;

import lombok.Builder;
import lombok.Data;
import org.desviante.integration.event.DomainEvent;
import org.desviante.model.Card;
import org.desviante.model.enums.ProgressType;

import java.time.LocalDateTime;

/**
 * Evento disparado quando o progresso de um card cai de 100% para um valor menor.
 *
 * <p>Este evento é publicado quando um Field é atualizado e o progresso
 * calculado do card, que estava em 100%, passa a ser menor que 100%. Isso indica
 * que o card deixou de estar concluído de acordo com seu tipo de progresso.</p>
 *
 * <p><strong>Tipos de Progresso que Disparam Este Evento:</strong></p>
 * <ul>
 *   <li><strong>TOTAL:</strong> Quando a média de todos os fields cai abaixo de 100%</li>
 *   <li><strong>PERCENTAGE:</strong> Quando a média dos PercentageFields cai abaixo de 100%</li>
 *   <li><strong>CHECKLIST:</strong> Quando a média dos ChecklistFields cai abaixo de 100%</li>
 * </ul>
 *
 * <p><strong>NONE</strong> não dispara este evento pois não tem progresso calculado.</p>
 *
 * <p><strong>Observadores Típicos:</strong></p>
 * <ul>
 *   <li>CardProgressRegressionObserver - Move card de volta para coluna PENDING automaticamente</li>
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
public class CardProgressRegressedEvent implements DomainEvent {

    /**
     * Card cujo progresso caiu abaixo de 100%.
     */
    private final Card card;

    /**
     * Tipo de progresso do card que regrediu.
     */
    private final ProgressType progressType;

    /**
     * Valor do progresso calculado (deve ser < 100.0).
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
        return "CardProgressRegressed";
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
     * Obtém o ID do card cujo progresso regrediu.
     *
     * @return ID do card, ou null se o card não estiver disponível
     */
    public Long getCardId() {
        return getEntityId();
    }

    /**
     * Verifica se o card deve ser movido automaticamente de volta para coluna PENDING.
     *
     * <p>Apenas cards com tipos de progresso TOTAL, PERCENTAGE ou CHECKLIST
     * devem ser movidos automaticamente. Cards com NONE não são movidos.</p>
     *
     * @return true se o card deve ser auto-regredido
     */
    public boolean shouldAutoRegress() {
        return progressType == ProgressType.TOTAL ||
               progressType == ProgressType.PERCENTAGE ||
               progressType == ProgressType.CHECKLIST;
    }
}
