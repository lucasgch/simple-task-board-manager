package org.desviante.integration.event.card;

import lombok.Builder;
import lombok.Data;
import org.desviante.integration.event.DomainEvent;
import org.desviante.model.Card;
import org.desviante.model.enums.ProgressType;

import java.time.LocalDateTime;

/**
 * Evento disparado quando o progresso de um card atinge 100%.
 *
 * <p>Este evento é publicado quando um Field é atualizado e o progresso
 * calculado do card atinge ou ultrapassa 100%. Isso indica que o card
 * está concluído de acordo com seu tipo de progresso.</p>
 *
 * <p><strong>Tipos de Progresso que Disparam Este Evento:</strong></p>
 * <ul>
 *   <li><strong>TOTAL:</strong> Quando a média de todos os fields atinge 100%</li>
 *   <li><strong>PERCENTAGE:</strong> Quando a média dos PercentageFields atinge 100%</li>
 *   <li><strong>CHECKLIST:</strong> Quando a média dos ChecklistFields atinge 100%</li>
 * </ul>
 *
 * <p><strong>NONE</strong> não dispara este evento pois não tem progresso calculado.</p>
 *
 * <p><strong>Observadores Típicos:</strong></p>
 * <ul>
 *   <li>CardProgressCompletionObserver - Move card para coluna FINAL automaticamente</li>
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
public class CardProgressCompletedEvent implements DomainEvent {

    /**
     * Card que atingiu 100% de progresso.
     */
    private final Card card;

    /**
     * Tipo de progresso do card que atingiu 100%.
     */
    private final ProgressType progressType;

    /**
     * Valor do progresso calculado (deve ser >= 100.0).
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
        return "CardProgressCompleted";
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
     * Obtém o ID do card que atingiu 100%.
     *
     * @return ID do card, ou null se o card não estiver disponível
     */
    public Long getCardId() {
        return getEntityId();
    }

    /**
     * Verifica se o card deve ser movido automaticamente para coluna FINAL.
     *
     * <p>Apenas cards com tipos de progresso TOTAL, PERCENTAGE ou CHECKLIST
     * devem ser movidos automaticamente. Cards com NONE não são movidos.</p>
     *
     * @return true se o card deve ser auto-completado
     */
    public boolean shouldAutoComplete() {
        return progressType == ProgressType.TOTAL ||
               progressType == ProgressType.PERCENTAGE ||
               progressType == ProgressType.CHECKLIST;
    }
}
