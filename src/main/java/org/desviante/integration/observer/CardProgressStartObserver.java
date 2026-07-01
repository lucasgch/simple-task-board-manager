package org.desviante.integration.observer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.desviante.integration.event.DomainEvent;
import org.desviante.integration.event.EventObserver;
import org.desviante.integration.event.EventPublisher;
import org.desviante.integration.event.card.CardAutoStartedEvent;
import org.desviante.integration.event.card.CardProgressStartedEvent;
import org.desviante.model.BoardColumn;
import org.desviante.model.Card;
import org.desviante.model.enums.BoardColumnKindEnum;
import org.desviante.model.enums.ProgressType;
import org.desviante.repository.BoardColumnRepository;
import org.desviante.service.CardService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Observador responsável por mover cards automaticamente para coluna PENDING
 * quando seu progresso sai de 0%.
 *
 * <p>Este observador processa eventos CardProgressStartedEvent e executa a ação
 * de auto-iniciar o card movendo-o para a primeira coluna do tipo PENDING do board,
 * caso ele esteja atualmente em uma coluna INITIAL.</p>
 *
 * <p><strong>Funcionalidades:</strong></p>
 * <ul>
 *   <li>Detecta quando card saiu de 0% de progresso</li>
 *   <li>Verifica se card está atualmente em coluna INITIAL</li>
 *   <li>Move card para primeira coluna PENDING do board</li>
 *   <li>Publica CardAutoStartedEvent para notificação da UI</li>
 *   <li>Trata erros sem quebrar o fluxo da aplicação</li>
 * </ul>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 1.0
 * @since 2.1
 * @see EventObserver
 * @see CardProgressStartedEvent
 * @see CardAutoStartedEvent
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CardProgressStartObserver implements EventObserver<CardProgressStartedEvent> {

    private final CardService cardService;
    private final BoardColumnRepository boardColumnRepository;
    private final EventPublisher eventPublisher;

    @Override
    public boolean canHandle(DomainEvent event) {
        if (!(event instanceof CardProgressStartedEvent)) {
            return false;
        }

        CardProgressStartedEvent evt = (CardProgressStartedEvent) event;
        ProgressType type = evt.getProgressType();

        boolean shouldHandle = type == ProgressType.TOTAL ||
                                type == ProgressType.PERCENTAGE ||
                                type == ProgressType.CHECKLIST;

        if (!shouldHandle) {
            log.debug("CardProgressStartObserver - Ignorando evento para tipo {}", type);
        }

        return shouldHandle;
    }

    @Override
    public void handle(CardProgressStartedEvent event) throws Exception {
        if (event == null || event.getCard() == null) {
            log.warn("CardProgressStartObserver - Evento ou card nulo recebido, ignorando");
            return;
        }

        Card card = event.getCard();
        log.info("CardProgressStartObserver - Processando card {} que saiu de 0% (tipo: {})",
                card.getId(), event.getProgressType());

        try {
            // 1. Buscar coluna atual
            BoardColumn currentColumn = boardColumnRepository.findById(card.getBoardColumnId())
                    .orElse(null);

            if (currentColumn == null) {
                log.warn("Coluna atual do card {} não encontrada, não é possível auto-iniciar", card.getId());
                return;
            }

            // 2. Se não está em coluna INITIAL, não há nada a iniciar
            if (currentColumn.getKind() != BoardColumnKindEnum.INITIAL) {
                log.debug("Card {} não está em coluna INITIAL (está em '{}'), skip auto-início",
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
                log.warn("Board {} não possui coluna PENDING, não é possível auto-iniciar card {}",
                        boardId, card.getId());
                return;
            }

            BoardColumn pendingColumn = pendingColumnOpt.get();

            // 4. Mover card para coluna PENDING
            try {
                cardService.moveCardToColumn(card.getId(), pendingColumn.getId());
                log.info("✅ Card {} ('{}') movido automaticamente para coluna PENDING '{}' (progresso acima de 0%)",
                        card.getId(), card.getTitle(), pendingColumn.getName());

                // 5. Publicar evento para notificação da UI
                CardAutoStartedEvent uiEvent = CardAutoStartedEvent.builder()
                        .cardId(card.getId())
                        .cardTitle(card.getTitle())
                        .boardId(boardId)
                        .pendingColumnId(pendingColumn.getId())
                        .pendingColumnName(pendingColumn.getName())
                        .occurredOn(LocalDateTime.now())
                        .build();

                eventPublisher.publish(uiEvent);

                log.debug("CardAutoStartedEvent publicado para card {}", card.getId());

            } catch (Exception e) {
                log.error("❌ Erro ao mover card {} para coluna PENDING: {}", card.getId(), e.getMessage(), e);
                // Não re-lançar exceção para não quebrar o fluxo
            }

        } catch (Exception e) {
            log.error("❌ Erro ao processar CardProgressStartedEvent para card {}: {}",
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
        return "CardProgressStartObserver";
    }
}
