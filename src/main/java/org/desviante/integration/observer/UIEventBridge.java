package org.desviante.integration.observer;

import org.springframework.stereotype.Component;

/**
 * Bridge entre o sistema de eventos de domínio e a camada de UI JavaFX.
 *
 * Permite que observers de back-end disparem ações na UI sem depender
 * diretamente do BoardViewController (evita acoplamento UI/serviço).
 */
@Component
public class UIEventBridge {

    private Runnable onCardAutoCompleted;
    private Runnable onCardAutoRegressed;

    public void setOnCardAutoCompleted(Runnable callback) {
        this.onCardAutoCompleted = callback;
    }

    public void triggerCardAutoCompleted() {
        if (onCardAutoCompleted != null) {
            onCardAutoCompleted.run();
        }
    }

    public void setOnCardAutoRegressed(Runnable callback) {
        this.onCardAutoRegressed = callback;
    }

    public void triggerCardAutoRegressed() {
        if (onCardAutoRegressed != null) {
            onCardAutoRegressed.run();
        }
    }

    private Runnable onCardAutoStarted;

    public void setOnCardAutoStarted(Runnable callback) {
        this.onCardAutoStarted = callback;
    }

    public void triggerCardAutoStarted() {
        if (onCardAutoStarted != null) {
            onCardAutoStarted.run();
        }
    }
}
