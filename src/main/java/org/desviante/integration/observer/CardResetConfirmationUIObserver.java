package org.desviante.integration.observer;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import lombok.extern.slf4j.Slf4j;
import org.desviante.integration.event.DomainEvent;
import org.desviante.integration.event.EventObserver;
import org.desviante.integration.event.card.CardResetConfirmationRequestedEvent;
import org.desviante.service.CardService;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Observer que reage ao CardResetConfirmationRequestedEvent perguntando ao usuário,
 * via diálogo de confirmação, se o card deve ser movido de volta para a coluna
 * "Não iniciado" (INITIAL).
 *
 * <p>Diferente dos demais observers de UI de progresso, este observer executa a
 * movimentação do card diretamente (ao invés de delegar a um observer de negócio),
 * pois a decisão de mover só pode ser tomada após a resposta do usuário.</p>
 */
@Component
@Slf4j
public class CardResetConfirmationUIObserver implements EventObserver<CardResetConfirmationRequestedEvent> {

    private final CardService cardService;
    private final UIEventBridge bridge;

    public CardResetConfirmationUIObserver(CardService cardService, UIEventBridge bridge) {
        this.cardService = cardService;
        this.bridge = bridge;
    }

    @Override
    public void handle(CardResetConfirmationRequestedEvent event) {
        Platform.runLater(() -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Progresso zerado");
            confirm.setHeaderText("\"" + event.getCardTitle() + "\" voltou a 0% de progresso");
            confirm.setContentText(
                "Deseja mover o card de volta para a coluna \"" + event.getInitialColumnName() + "\"?"
            );

            ButtonType moveButton = new ButtonType("Mover", ButtonBar.ButtonData.OK_DONE);
            ButtonType keepButton = new ButtonType("Manter na coluna atual", ButtonBar.ButtonData.CANCEL_CLOSE);
            confirm.getButtonTypes().setAll(moveButton, keepButton);

            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == moveButton) {
                try {
                    cardService.moveCardToColumn(event.getCardId(), event.getInitialColumnId());
                    log.info("✅ Card {} movido para coluna INITIAL '{}' após confirmação do usuário",
                            event.getCardId(), event.getInitialColumnName());
                    bridge.triggerCardAutoRegressed();
                } catch (Exception e) {
                    log.error("❌ Erro ao mover card {} para coluna INITIAL: {}",
                            event.getCardId(), e.getMessage(), e);
                }
            } else {
                log.debug("Usuário optou por manter o card {} na coluna atual", event.getCardId());
            }
        });
    }

    @Override
    public boolean canHandle(DomainEvent event) {
        return event instanceof CardResetConfirmationRequestedEvent;
    }

    @Override
    public int getPriority() {
        return 5;
    }

    @Override
    public String getObserverName() {
        return "CardResetConfirmationUIObserver";
    }
}
