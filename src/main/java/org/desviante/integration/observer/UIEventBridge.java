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

    public void setOnCardAutoCompleted(Runnable callback) {
        this.onCardAutoCompleted = callback;
    }

    public void triggerCardAutoCompleted() {
        if (onCardAutoCompleted != null) {
            onCardAutoCompleted.run();
        }
    }
}
