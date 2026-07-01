package org.desviante.integration.observer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.desviante.integration.event.DomainEvent;
import org.desviante.integration.event.EventObserver;
import org.desviante.integration.event.EventPublisher;
import org.desviante.integration.event.card.CardProgressZeroedEvent;
import org.desviante.integration.event.card.CardResetConfirmationRequestedEvent;
import org.desviante.model.BoardColumn;
import org.desviante.model.Card;
import org.desviante.model.enums.BoardColumnKindEnum;
import org.desviante.model.enums.ProgressType;
import org.desviante.repository.BoardColumnRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Observador responsável por decidir se um card, cujo progresso voltou a 0%, é
 * elegível para ser movido de volta à coluna INITIAL ("Não iniciado").
 *
 * <p>Diferente dos observadores de conclusão/regressão/início automáticos, este
 * observador NÃO move o card diretamente: ele apenas verifica as pré-condições
 * (card atualmente na coluna PENDING e existência de uma coluna INITIAL no board)
 * e, se satisfeitas, publica um CardResetConfirmationRequestedEvent para que a
 * camada de UI pergunte ao usuário antes de mover.</p>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 1.0
 * @since 2.1
 * @see EventObserver
 * @see CardProgressZeroedEvent
 * @see CardResetConfirmationRequestedEvent
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CardProgressResetObserver implements EventObserver<CardProgressZeroedEvent> {

    private final BoardColumnRepository boardColumnRepository;
    private final EventPublisher eventPublisher;

    @Override
    public boolean canHandle(DomainEvent event) {
        if (!(event instanceof CardProgressZeroedEvent)) {
            return false;
        }

        CardProgressZeroedEvent evt = (CardProgressZeroedEvent) event;
        ProgressType type = evt.getProgressType();

        boolean shouldHandle = type == ProgressType.TOTAL ||
                                type == ProgressType.PERCENTAGE ||
                                type == ProgressType.CHECKLIST;

        if (!shouldHandle) {
            log.debug("CardProgressResetObserver - Ignorando evento para tipo {}", type);
        }

        return shouldHandle;
    }

    @Override
    public void handle(CardProgressZeroedEvent event) throws Exception {
        if (event == null || event.getCard() == null) {
            log.warn("CardProgressResetObserver - Evento ou card nulo recebido, ignorando");
            return;
        }

        Card card = event.getCard();
        log.info("CardProgressResetObserver - Processando card {} que voltou a 0% (tipo: {})",
                card.getId(), event.getProgressType());

        try {
            // 1. Buscar coluna atual
            BoardColumn currentColumn = boardColumnRepository.findById(card.getBoardColumnId())
                    .orElse(null);

            if (currentColumn == null) {
                log.warn("Coluna atual do card {} não encontrada, não é possível solicitar confirmação", card.getId());
                return;
            }

            // 2. Só faz sentido perguntar se o card está "em andamento" (PENDING)
            if (currentColumn.getKind() != BoardColumnKindEnum.PENDING) {
                log.debug("Card {} não está em coluna PENDING (está em '{}'), skip solicitação de reset",
                        card.getId(), currentColumn.getName());
                return;
            }

            Long boardId = currentColumn.getBoardId();

            // 3. Buscar coluna INITIAL do board
            java.util.Optional<BoardColumn> initialColumnOpt = boardColumnRepository.findByBoardIdAndKind(
                    boardId,
                    BoardColumnKindEnum.INITIAL
            );

            if (initialColumnOpt.isEmpty()) {
                log.warn("Board {} não possui coluna INITIAL, não é possível solicitar confirmação para o card {}",
                        boardId, card.getId());
                return;
            }

            BoardColumn initialColumn = initialColumnOpt.get();

            // 4. Publicar evento para que a UI pergunte ao usuário
            CardResetConfirmationRequestedEvent uiEvent = CardResetConfirmationRequestedEvent.builder()
                    .cardId(card.getId())
                    .cardTitle(card.getTitle())
                    .boardId(boardId)
                    .initialColumnId(initialColumn.getId())
                    .initialColumnName(initialColumn.getName())
                    .occurredOn(LocalDateTime.now())
                    .build();

            eventPublisher.publish(uiEvent);

            log.debug("CardResetConfirmationRequestedEvent publicado para card {}", card.getId());

        } catch (Exception e) {
            log.error("❌ Erro ao processar CardProgressZeroedEvent para card {}: {}",
                    card.getId(), e.getMessage(), e);
            // Não re-lançar exceção para não quebrar o fluxo
        }
    }

    @Override
    public int getPriority() {
        return 10; // Prioridade normal
    }

    @Override
    public String getObserverName() {
        return "CardProgressResetObserver";
    }
}
