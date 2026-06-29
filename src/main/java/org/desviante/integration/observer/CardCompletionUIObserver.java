package org.desviante.integration.observer;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.desviante.integration.event.DomainEvent;
import org.desviante.integration.event.EventObserver;
import org.desviante.integration.event.card.CardAutoCompletedEvent;
import org.springframework.stereotype.Component;

/**
 * Observer que reage ao CardAutoCompletedEvent exibindo uma notificação ao
 * usuário e recarregando o kanban para refletir a movimentação do card.
 */
@Component
public class CardCompletionUIObserver implements EventObserver<CardAutoCompletedEvent> {

    private final UIEventBridge bridge;

    public CardCompletionUIObserver(UIEventBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void handle(CardAutoCompletedEvent event) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Card Concluído!");
            alert.setHeaderText("\"" + event.getCardTitle() + "\" atingiu 100%");
            alert.setContentText(
                "O card foi movido automaticamente para a coluna \"" + event.getFinalColumnName() + "\".\n" +
                "O board será atualizado ao confirmar."
            );
            alert.getButtonTypes().setAll(ButtonType.OK);
            alert.showAndWait();
            bridge.triggerCardAutoCompleted();
        });
    }

    @Override
    public boolean canHandle(DomainEvent event) {
        return event instanceof CardAutoCompletedEvent;
    }

    @Override
    public int getPriority() {
        return 5;
    }

    @Override
    public String getObserverName() {
        return "CardCompletionUIObserver";
    }
}
