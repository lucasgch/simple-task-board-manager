package org.desviante.integration.observer;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.desviante.integration.event.DomainEvent;
import org.desviante.integration.event.EventObserver;
import org.desviante.integration.event.card.CardAutoRegressedEvent;
import org.springframework.stereotype.Component;

/**
 * Observer que reage ao CardAutoRegressedEvent exibindo uma notificação ao
 * usuário e recarregando o kanban para refletir a movimentação do card.
 */
@Component
public class CardRegressionUIObserver implements EventObserver<CardAutoRegressedEvent> {

    private final UIEventBridge bridge;

    public CardRegressionUIObserver(UIEventBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void handle(CardAutoRegressedEvent event) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Card Reaberto");
            alert.setHeaderText("\"" + event.getCardTitle() + "\" caiu abaixo de 100%");
            alert.setContentText(
                "O card foi movido automaticamente para a coluna \"" + event.getPendingColumnName() + "\".\n" +
                "O board será atualizado ao confirmar."
            );
            alert.getButtonTypes().setAll(ButtonType.OK);
            alert.showAndWait();
            bridge.triggerCardAutoRegressed();
        });
    }

    @Override
    public boolean canHandle(DomainEvent event) {
        return event instanceof CardAutoRegressedEvent;
    }

    @Override
    public int getPriority() {
        return 5;
    }

    @Override
    public String getObserverName() {
        return "CardRegressionUIObserver";
    }
}
