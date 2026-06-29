package org.desviante.integration.observer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.desviante.integration.event.DomainEvent;
import org.desviante.integration.event.EventObserver;
import org.desviante.integration.event.EventPublisher;
import org.desviante.integration.event.card.CardAutoCompletedEvent;
import org.desviante.integration.event.card.CardProgressCompletedEvent;
import org.desviante.model.BoardColumn;
import org.desviante.model.Card;
import org.desviante.model.enums.BoardColumnKindEnum;
import org.desviante.model.enums.ProgressType;
import org.desviante.repository.BoardColumnRepository;
import org.desviante.service.CardService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Observador responsável por mover cards automaticamente para coluna FINAL ao atingir 100%.
 *
 * <p>Este observador processa eventos CardProgressCompletedEvent e executa a ação
 * de auto-completar o card movendo-o para a primeira coluna do tipo FINAL do board.</p>
 *
 * <p><strong>Funcionalidades:</strong></p>
 * <ul>
 *   <li>Detecta quando card atingiu 100% de progresso</li>
 *   <li>Verifica se card não está já em coluna FINAL</li>
 *   <li>Move card para primeira coluna FINAL do board</li>
 *   <li>Publica CardAutoCompletedEvent para notificação da UI</li>
 *   <li>Trata erros sem quebrar o fluxo da aplicação</li>
 * </ul>
 *
 * <p><strong>Tipos de Progresso Suportados:</strong></p>
 * <ul>
 *   <li>TOTAL - Média de todos os fields</li>
 *   <li>PERCENTAGE - Média de PercentageFields</li>
 *   <li>CHECKLIST - Média de ChecklistFields</li>
 *   <li>NONE - Não processado (cards NONE não são auto-completados)</li>
 * </ul>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 1.0
 * @since 2.1
 * @see EventObserver
 * @see CardProgressCompletedEvent
 * @see CardAutoCompletedEvent
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CardProgressCompletionObserver implements EventObserver<CardProgressCompletedEvent> {

    private final CardService cardService;
    private final BoardColumnRepository boardColumnRepository;
    private final EventPublisher eventPublisher;

    @Override
    public boolean canHandle(DomainEvent event) {
        if (!(event instanceof CardProgressCompletedEvent)) {
            return false;
        }

        CardProgressCompletedEvent evt = (CardProgressCompletedEvent) event;
        ProgressType type = evt.getProgressType();

        // Apenas processar para tipos que devem auto-completar
        boolean shouldHandle = type == ProgressType.TOTAL ||
                                type == ProgressType.PERCENTAGE ||
                                type == ProgressType.CHECKLIST;

        if (!shouldHandle) {
            log.debug("CardProgressCompletionObserver - Ignorando evento para tipo {}", type);
        }

        return shouldHandle;
    }

    @Override
    public void handle(CardProgressCompletedEvent event) throws Exception {
        if (event == null || event.getCard() == null) {
            log.warn("CardProgressCompletionObserver - Evento ou card nulo recebido, ignorando");
            return;
        }

        Card card = event.getCard();
        log.info("CardProgressCompletionObserver - Processando card {} que atingiu 100% (tipo: {})",
                card.getId(), event.getProgressType());

        try {
            // 1. Buscar coluna atual
            BoardColumn currentColumn = boardColumnRepository.findById(card.getBoardColumnId())
                    .orElse(null);

            if (currentColumn == null) {
                log.warn("Coluna atual do card {} não encontrada, não é possível auto-completar", card.getId());
                return;
            }

            // 2. Se já está em coluna FINAL, não fazer nada
            if (currentColumn.getKind() == BoardColumnKindEnum.FINAL) {
                log.debug("Card {} já está em coluna FINAL '{}', skip auto-completion",
                        card.getId(), currentColumn.getName());
                return;
            }

            Long boardId = currentColumn.getBoardId();

            // 3. Buscar coluna FINAL do board
            java.util.Optional<BoardColumn> finalColumnOpt = boardColumnRepository.findByBoardIdAndKind(
                    boardId,
                    BoardColumnKindEnum.FINAL
            );

            if (finalColumnOpt.isEmpty()) {
                log.warn("Board {} não possui coluna FINAL, não é possível auto-completar card {}",
                        boardId, card.getId());
                return;
            }

            BoardColumn finalColumn = finalColumnOpt.get();

            // 4. Mover card para coluna FINAL
            try {
                cardService.moveCardToColumn(card.getId(), finalColumn.getId());
                log.info("✅ Card {} ('{}') movido automaticamente para coluna FINAL '{}' (100% progresso)",
                        card.getId(), card.getTitle(), finalColumn.getName());

                // 5. Publicar evento para notificação da UI
                CardAutoCompletedEvent uiEvent = CardAutoCompletedEvent.builder()
                        .cardId(card.getId())
                        .cardTitle(card.getTitle())
                        .boardId(boardId)
                        .finalColumnId(finalColumn.getId())
                        .finalColumnName(finalColumn.getName())
                        .occurredOn(LocalDateTime.now())
                        .build();

                eventPublisher.publish(uiEvent);

                log.debug("CardAutoCompletedEvent publicado para card {}", card.getId());

            } catch (Exception e) {
                log.error("❌ Erro ao mover card {} para coluna FINAL: {}", card.getId(), e.getMessage(), e);
                // Não re-lançar exceção para não quebrar o fluxo
            }

        } catch (Exception e) {
            log.error("❌ Erro ao processar CardProgressCompletedEvent para card {}: {}",
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
        return "CardProgressCompletionObserver";
    }
}
