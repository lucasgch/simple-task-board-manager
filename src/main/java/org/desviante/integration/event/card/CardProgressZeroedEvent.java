package org.desviante.integration.event.card;

import lombok.Builder;
import lombok.Data;
import org.desviante.integration.event.DomainEvent;
import org.desviante.model.Card;
import org.desviante.model.enums.ProgressType;

import java.time.LocalDateTime;

/**
 * Evento disparado quando o progresso de um card cai de um valor maior que 0% para 0%.
 *
 * <p>Este evento é publicado quando um Field é atualizado e o progresso calculado
 * do card, que estava acima de 0%, volta a ser exatamente 0%. Isso indica que o
 * trabalho no card foi totalmente desfeito de acordo com seu tipo de progresso.</p>
 *
 * <p><strong>Tipos de Progresso que Disparam Este Evento:</strong></p>
 * <ul>
 *   <li><strong>TOTAL:</strong> Quando a média de todos os fields volta a 0%</li>
 *   <li><strong>PERCENTAGE:</strong> Quando a média dos PercentageFields volta a 0%</li>
 *   <li><strong>CHECKLIST:</strong> Quando a média dos ChecklistFields volta a 0%</li>
 * </ul>
 *
 * <p><strong>NONE</strong> não dispara este evento pois não tem progresso calculado.</p>
 *
 * <p><strong>Observadores Típicos:</strong></p>
 * <ul>
 *   <li>CardProgressResetObserver - Verifica se deve solicitar confirmação para mover o
 *   card de volta à coluna INITIAL</li>
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
public class CardProgressZeroedEvent implements DomainEvent {

    /**
     * Card cujo progresso voltou a 0%.
     */
    private final Card card;

    /**
     * Tipo de progresso do card que zerou.
     */
    private final ProgressType progressType;

    /**
     * Valor do progresso calculado (deve ser &lt;= 0.0).
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
        return "CardProgressZeroed";
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
     * Obtém o ID do card cujo progresso zerou.
     *
     * @return ID do card, ou null se o card não estiver disponível
     */
    public Long getCardId() {
        return getEntityId();
    }

    /**
     * Verifica se o card deve disparar a solicitação de confirmação para voltar à
     * coluna INITIAL.
     *
     * <p>Apenas cards com tipos de progresso TOTAL, PERCENTAGE ou CHECKLIST são
     * elegíveis. Cards com NONE não são considerados.</p>
     *
     * @return true se o card é elegível para a solicitação de confirmação
     */
    public boolean shouldPromptReset() {
        return progressType == ProgressType.TOTAL ||
               progressType == ProgressType.PERCENTAGE ||
               progressType == ProgressType.CHECKLIST;
    }
}
