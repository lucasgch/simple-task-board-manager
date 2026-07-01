package org.desviante.integration.observer;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.desviante.integration.event.DomainEvent;
import org.desviante.integration.event.EventObserver;
import org.desviante.integration.event.card.CardAutoStartedEvent;
import org.springframework.stereotype.Component;

/**
 * Observer que reage ao CardAutoStartedEvent exibindo uma notificação ao
 * usuário e recarregando o kanban para refletir a movimentação do card.
 */
@Component
public class CardStartUIObserver implements EventObserver<CardAutoStartedEvent> {

    private final UIEventBridge bridge;

    public CardStartUIObserver(UIEventBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void handle(CardAutoStartedEvent event) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Card Iniciado");
            alert.setHeaderText("\"" + event.getCardTitle() + "\" saiu de 0%");
            alert.setContentText(
                "O card foi movido automaticamente para a coluna \"" + event.getPendingColumnName() + "\".\n" +
                "O board será atualizado ao confirmar."
            );
            alert.getButtonTypes().setAll(ButtonType.OK);
            alert.showAndWait();
            bridge.triggerCardAutoStarted();
        });
    }

    @Override
    public boolean canHandle(DomainEvent event) {
        return event instanceof CardAutoStartedEvent;
    }

    @Override
    public int getPriority() {
        return 5;
    }

    @Override
    public String getObserverName() {
        return "CardStartUIObserver";
    }
}
