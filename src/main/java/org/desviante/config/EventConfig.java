package org.desviante.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.desviante.integration.event.EventPublisher;
import org.desviante.integration.observer.CalendarSyncObserver;
import org.desviante.integration.observer.CardCompletionUIObserver;
import org.desviante.integration.observer.CardProgressCompletionObserver;
import org.desviante.integration.observer.CardProgressRegressionObserver;
import org.desviante.integration.observer.CardProgressResetObserver;
import org.desviante.integration.observer.CardProgressStartObserver;
import org.desviante.integration.observer.CardRegressionUIObserver;
import org.desviante.integration.observer.CardResetConfirmationUIObserver;
import org.desviante.integration.observer.CardStartUIObserver;
import org.desviante.integration.observer.GoogleTasksSyncObserver;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

/**
 * Configuração de eventos e observers para o sistema de integração.
 * 
 * <p>Esta classe configura automaticamente o registro de observers
 * no EventPublisher quando a aplicação é inicializada.</p>
 * 
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 1.0
 * @since 1.0
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class EventConfig {

    private final EventPublisher eventPublisher;
    private final GoogleTasksSyncObserver googleTasksSyncObserver;
    private final CalendarSyncObserver calendarSyncObserver;
    private final CardProgressCompletionObserver cardProgressCompletionObserver;
    private final CardCompletionUIObserver cardCompletionUIObserver;
    private final CardProgressRegressionObserver cardProgressRegressionObserver;
    private final CardRegressionUIObserver cardRegressionUIObserver;
    private final CardProgressStartObserver cardProgressStartObserver;
    private final CardStartUIObserver cardStartUIObserver;
    private final CardProgressResetObserver cardProgressResetObserver;
    private final CardResetConfirmationUIObserver cardResetConfirmationUIObserver;
    
    /**
     * Registra automaticamente os observers no EventPublisher quando o contexto Spring é inicializado.
     * 
     * <p>Este método é executado após todos os beans serem criados e inicializados,
     * garantindo que os observers estejam prontos para receber eventos.</p>
     */
    @EventListener(ContextRefreshedEvent.class)
    public void registerObservers() {
        log.info("🔧 EVENT CONFIG - Registrando observers no EventPublisher...");
        
        try {
            // Registrar GoogleTasksSyncObserver
            eventPublisher.subscribe(googleTasksSyncObserver);
            log.info("✅ GoogleTasksSyncObserver registrado com sucesso");

            // Registrar CalendarSyncObserver
            eventPublisher.subscribe(calendarSyncObserver);
            log.info("✅ CalendarSyncObserver registrado com sucesso");

            // Registrar CardProgressCompletionObserver
            eventPublisher.subscribe(cardProgressCompletionObserver);
            log.info("✅ CardProgressCompletionObserver registrado com sucesso");

            // Registrar CardCompletionUIObserver
            eventPublisher.subscribe(cardCompletionUIObserver);
            log.info("✅ CardCompletionUIObserver registrado com sucesso");

            // Registrar CardProgressRegressionObserver
            eventPublisher.subscribe(cardProgressRegressionObserver);
            log.info("✅ CardProgressRegressionObserver registrado com sucesso");

            // Registrar CardRegressionUIObserver
            eventPublisher.subscribe(cardRegressionUIObserver);
            log.info("✅ CardRegressionUIObserver registrado com sucesso");

            // Registrar CardProgressStartObserver
            eventPublisher.subscribe(cardProgressStartObserver);
            log.info("✅ CardProgressStartObserver registrado com sucesso");

            // Registrar CardStartUIObserver
            eventPublisher.subscribe(cardStartUIObserver);
            log.info("✅ CardStartUIObserver registrado com sucesso");

            // Registrar CardProgressResetObserver
            eventPublisher.subscribe(cardProgressResetObserver);
            log.info("✅ CardProgressResetObserver registrado com sucesso");

            // Registrar CardResetConfirmationUIObserver
            eventPublisher.subscribe(cardResetConfirmationUIObserver);
            log.info("✅ CardResetConfirmationUIObserver registrado com sucesso");

            log.info("🎉 Todos os observers foram registrados com sucesso! Total: {}", eventPublisher.getObserverCount());

        } catch (Exception e) {
            log.error("❌ Erro ao registrar observers: {}", e.getMessage(), e);
        }
    }
}
