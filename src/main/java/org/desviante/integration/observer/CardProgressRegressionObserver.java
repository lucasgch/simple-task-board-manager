package org.desviante.integration.observer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.desviante.integration.event.DomainEvent;
import org.desviante.integration.event.EventObserver;
import org.desviante.integration.event.EventPublisher;
import org.desviante.integration.event.card.CardAutoRegressedEvent;
import org.desviante.integration.event.card.CardProgressRegressedEvent;
import org.desviante.model.BoardColumn;
import org.desviante.model.Card;
import org.desviante.model.enums.BoardColumnKindEnum;
import org.desviante.model.enums.ProgressType;
import org.desviante.repository.BoardColumnRepository;
import org.desviante.service.CardService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Observador responsável por mover cards automaticamente de volta para coluna PENDING
 * quando seu progresso cai abaixo de 100%.
 *
 * <p>Este observador processa eventos CardProgressRegressedEvent e executa a ação
 * de auto-reabrir o card movendo-o para a primeira coluna do tipo PENDING do board,
 * caso ele esteja atualmente em uma coluna FINAL.</p>
 *
 * <p><strong>Funcionalidades:</strong></p>
 * <ul>
 *   <li>Detecta quando card deixou de ter 100% de progresso</li>
 *   <li>Verifica se card está atualmente em coluna FINAL</li>
 *   <li>Move card para primeira coluna PENDING do board</li>
 *   <li>Publica CardAutoRegressedEvent para notificação da UI</li>
 *   <li>Trata erros sem quebrar o fluxo da aplicação</li>
 * </ul>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 1.0
 * @since 2.1
 * @see EventObserver
 * @see CardProgressRegressedEvent
 * @see CardAutoRegressedEvent
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CardProgressRegressionObserver implements EventObserver<CardProgressRegressedEvent> {

    private final CardService cardService;
    private final BoardColumnRepository boardColumnRepository;
    private final EventPublisher eventPublisher;

    @Override
    public boolean canHandle(DomainEvent event) {
        if (!(event instanceof CardProgressRegressedEvent)) {
            return false;
        }

        CardProgressRegressedEvent evt = (CardProgressRegressedEvent) event;
        ProgressType type = evt.getProgressType();

        boolean shouldHandle = type == ProgressType.TOTAL ||
                                type == ProgressType.PERCENTAGE ||
                                type == ProgressType.CHECKLIST;

        if (!shouldHandle) {
            log.debug("CardProgressRegressionObserver - Ignorando evento para tipo {}", type);
        }

        return shouldHandle;
    }

    @Override
    public void handle(CardProgressRegressedEvent event) throws Exception {
        if (event == null || event.getCard() == null) {
            log.warn("CardProgressRegressionObserver - Evento ou card nulo recebido, ignorando");
            return;
        }

        Card card = event.getCard();
        log.info("CardProgressRegressionObserver - Processando card {} que caiu abaixo de 100% (tipo: {})",
                card.getId(), event.getProgressType());

        try {
            // 1. Buscar coluna atual
            BoardColumn currentColumn = boardColumnRepository.findById(card.getBoardColumnId())
                    .orElse(null);

            if (currentColumn == null) {
                log.warn("Coluna atual do card {} não encontrada, não é possível auto-reabrir", card.getId());
                return;
            }

            // 2. Se não está em coluna FINAL, não há nada a reabrir
            if (currentColumn.getKind() != BoardColumnKindEnum.FINAL) {
                log.debug("Card {} não está em coluna FINAL (está em '{}'), skip auto-regressão",
                        card.getId(), currentColumn.getName());
                return;
            }

            Long boardId = currentColumn.getBoardId();

            // 3. Buscar coluna PENDING do board
            java.util.Optional<BoardColumn> pendingColumnOpt = boardColumnRepository.findByBoardIdAndKind(
                    boardId,
                    BoardColumnKindEnum.PENDING
            );

            if (pendingColumnOpt.isEmpty()) {
                log.warn("Board {} não possui coluna PENDING, não é possível auto-reabrir card {}",
                        boardId, card.getId());
                return;
            }

            BoardColumn pendingColumn = pendingColumnOpt.get();

            // 4. Mover card para coluna PENDING
            try {
                cardService.moveCardToColumn(card.getId(), pendingColumn.getId());
                log.info("✅ Card {} ('{}') movido automaticamente para coluna PENDING '{}' (progresso abaixo de 100%)",
                        card.getId(), card.getTitle(), pendingColumn.getName());

                // 5. Publicar evento para notificação da UI
                CardAutoRegressedEvent uiEvent = CardAutoRegressedEvent.builder()
                        .cardId(card.getId())
                        .cardTitle(card.getTitle())
                        .boardId(boardId)
                        .pendingColumnId(pendingColumn.getId())
                        .pendingColumnName(pendingColumn.getName())
                        .occurredOn(LocalDateTime.now())
                        .build();

                eventPublisher.publish(uiEvent);

                log.debug("CardAutoRegressedEvent publicado para card {}", card.getId());

            } catch (Exception e) {
                log.error("❌ Erro ao mover card {} para coluna PENDING: {}", card.getId(), e.getMessage(), e);
                // Não re-lançar exceção para não quebrar o fluxo
            }

        } catch (Exception e) {
            log.error("❌ Erro ao processar CardProgressRegressedEvent para card {}: {}",
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
        return "CardProgressRegressionObserver";
    }
}
