package org.desviante.calendar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.desviante.repository.CalendarEventRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Gerenciador de eventos do calendário.
 * 
 * <p>Esta classe é responsável por manter o estado dos eventos do calendário
 * e coordenar operações de CRUD. Utiliza persistência em banco de dados
 * para garantir que os eventos sejam mantidos entre sessões da aplicação.</p>
 * 
 * <p><strong>Responsabilidades:</strong></p>
 * <ul>
 *   <li>Persistência permanente de eventos</li>
 *   <li>Operações CRUD básicas</li>
 *   <li>Validação de integridade</li>
 *   <li>Carregamento automático na inicialização</li>
 * </ul>
 * 
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 1.0
 * @since 1.0
 * @see CalendarEvent
 * @see CalendarEventRepository
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CalendarEventManager {

    private final CalendarEventRepository calendarEventRepository;

    /**
     * Salva um evento no gerenciador.
     * 
     * @param event evento a ser salvo
     * @return evento salvo com ID gerado
     */
    public CalendarEvent save(CalendarEvent event) {
        System.out.println("🔧 CALENDAR EVENT MANAGER - save chamado para: " + event.getTitle());
        System.out.println("🔧 CALENDAR EVENT MANAGER - Data: " + event.getStartDateTime());
        
        if (event.getId() == null) {
            event.setCreatedAt(LocalDateTime.now());
            System.out.println("🔧 CALENDAR EVENT MANAGER - Novo evento sendo criado");
        } else {
            System.out.println("🔧 CALENDAR EVENT MANAGER - Atualizando evento existente com ID: " + event.getId());
        }
        event.setUpdatedAt(LocalDateTime.now());
        
        CalendarEvent savedEvent = calendarEventRepository.save(event);
        System.out.println("🔧 CALENDAR EVENT MANAGER - Evento salvo no banco com ID: " + savedEvent.getId());
        log.debug("Evento salvo: {}", savedEvent);
        
        return savedEvent;
    }

    /**
     * Busca um evento por ID.
     * 
     * @param id ID do evento
     * @return evento encontrado ou null
     */
    public CalendarEvent findById(Long id) {
        return calendarEventRepository.findById(id).orElse(null);
    }

    /**
     * Lista todos os eventos.
     * 
     * @return lista de todos os eventos
     */
    public List<CalendarEvent> findAll() {
        return calendarEventRepository.findAll();
    }

    /**
     * Remove um evento por ID.
     * 
     * @param id ID do evento a ser removido
     * @return true se o evento foi removido
     */
    public boolean deleteById(Long id) {
        boolean removed = calendarEventRepository.deleteById(id);
        if (removed) {
            log.debug("Evento removido: {}", id);
        }
        return removed;
    }

    /**
     * Verifica se existe um evento com o ID especificado.
     * 
     * @param id ID do evento
     * @return true se o evento existe
     */
    public boolean existsById(Long id) {
        return calendarEventRepository.existsById(id);
    }

    /**
     * Conta o número total de eventos.
     * 
     * @return número total de eventos
     */
    public long count() {
        return calendarEventRepository.count();
    }

    /**
     * Busca eventos por entidade relacionada.
     * 
     * @param relatedEntityId ID da entidade relacionada
     * @param relatedEntityType tipo da entidade relacionada
     * @return lista de eventos relacionados à entidade
     */
    public List<CalendarEvent> findByRelatedEntity(Long relatedEntityId, String relatedEntityType) {
        return calendarEventRepository.findByRelatedEntity(relatedEntityId, relatedEntityType);
    }

    /**
     * Remove eventos por entidade relacionada.
     * 
     * @param relatedEntityId ID da entidade relacionada
     * @param relatedEntityType tipo da entidade relacionada
     * @return número de eventos removidos
     */
    public int deleteByRelatedEntity(Long relatedEntityId, String relatedEntityType) {
        int deleted = calendarEventRepository.deleteByRelatedEntity(relatedEntityId, relatedEntityType);
        if (deleted > 0) {
            log.debug("Removidos {} eventos para entidade {}:{}", deleted, relatedEntityType, relatedEntityId);
        }
        return deleted;
    }

    /**
     * Remove eventos de calendário relacionados a um card específico.
     * 
     * @param cardId ID do card
     * @return número de eventos removidos
     */
    public int removeCalendarEventForCard(Long cardId) {
        log.debug("Removendo eventos de calendário para card: {}", cardId);
        return deleteByRelatedEntity(cardId, "CARD");
    }
}