package org.desviante.calendar.provider;

import lombok.RequiredArgsConstructor;
import org.desviante.calendar.CalendarEvent;
import org.desviante.calendar.CalendarEventManager;
import org.desviante.calendar.CalendarEventPriority;
import org.desviante.calendar.CalendarEventType;
import org.desviante.calendar.dto.CalendarEventDTO;
import org.desviante.model.Card;
import org.desviante.service.CardService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provedor de eventos do calendário para cards (tarefas).
 * 
 * <p>Este provedor integra os cards do sistema com o calendário,
 * criando eventos baseados na data de agendamento (scheduledDate)
 * dos cards. Os eventos são gerados automaticamente e sincronizados
 * com as alterações nos cards.</p>
 * 
 * <p><strong>Características Principais:</strong></p>
 * <ul>
 *   <li>Eventos baseados na data de agendamento dos cards</li>
 *   <li>Prioridade calculada baseada na data de vencimento</li>
 *   <li>Sincronização automática com alterações nos cards</li>
 *   <li>Suporte a eventos de dia inteiro e com horário específico</li>
 * </ul>
 * 
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 1.0
 * @since 1.0
 * @see CalendarEventProvider
 * @see Card
 * @see CalendarEventDTO
 */
@Component
@RequiredArgsConstructor
public class CardCalendarEventProvider implements CalendarEventProvider {

    private final CardService cardService;
    private final CalendarEventManager calendarEventManager;

    @Override
    public List<CalendarEventDTO> getEventsForDateRange(LocalDate start, LocalDate end) {
        System.out.println("🔧 CARD CALENDAR EVENT PROVIDER - getEventsForDateRange chamado para período: " + start + " a " + end);
        
        // Buscar eventos do CalendarEventManager (agora persistido no banco)
        List<CalendarEvent> events = calendarEventManager.findAll();
        System.out.println("🔧 CARD CALENDAR EVENT PROVIDER - Total de eventos no CalendarEventManager: " + events.size());
        
        // Filtrar eventos por período e converter para DTO
        List<CalendarEventDTO> result = events.stream()
                .filter(event -> {
                    LocalDate eventDate = event.getStartDateTime().toLocalDate();
                    boolean inRange = !eventDate.isBefore(start) && !eventDate.isAfter(end);
                    System.out.println("🔧 CARD CALENDAR EVENT PROVIDER - Evento: " + event.getTitle() + " (" + eventDate + ") - Em período: " + inRange);
                    System.out.println("🔧 CARD CALENDAR EVENT PROVIDER - Período solicitado: " + start + " a " + end);
                    System.out.println("🔧 CARD CALENDAR EVENT PROVIDER - Comparação: " + eventDate + " >= " + start + " && " + eventDate + " <= " + end);
                    return inRange;
                })
                .map(this::convertEventToDTO)
                .collect(Collectors.toList());
        
        System.out.println("🔧 CARD CALENDAR EVENT PROVIDER - Eventos retornados: " + result.size());
        return result;
    }

    @Override
    public CalendarEventDTO createEvent(CalendarEventDTO event) {
        System.out.println("🔧 CARD CALENDAR EVENT PROVIDER - createEvent chamado para: " + event.getTitle());
        System.out.println("🔧 CARD CALENDAR EVENT PROVIDER - Data: " + event.getStartDateTime());
        
        // Converter DTO para entidade e salvar no CalendarEventManager
        CalendarEvent calendarEvent = convertDTOToEvent(event);
        System.out.println("🔧 CARD CALENDAR EVENT PROVIDER - Evento convertido: " + calendarEvent.getTitle());
        
        CalendarEvent savedEvent = calendarEventManager.save(calendarEvent);
        System.out.println("🔧 CARD CALENDAR EVENT PROVIDER - Evento salvo com ID: " + savedEvent.getId());
        
        // Converter de volta para DTO e retornar
        CalendarEventDTO result = convertEventToDTO(savedEvent);
        System.out.println("🔧 CARD CALENDAR EVENT PROVIDER - DTO retornado: " + result.getTitle());
        
        return result;
    }

    @Override
    public void updateEvent(CalendarEventDTO event) {
        // Para cards, atualizamos o card correspondente
        if (event.getRelatedEntityType() != null && event.getRelatedEntityType().equals("CARD")) {
            Long cardId = event.getRelatedEntityId();
            if (cardId != null && event.getStartDateTime() != null) {
                cardService.setScheduledDate(cardId, event.getStartDateTime());
            }
        }
    }

    @Override
    public void deleteEvent(Long eventId) {
        // Para cards, removemos a data de agendamento do card correspondente
        // Este método seria chamado quando um evento é removido do calendário
        // Por simplicidade, não implementamos a busca reversa aqui
        throw new UnsupportedOperationException("Remoção de eventos de card deve ser feita através do CardService");
    }

    /**
     * Converte um card em um DTO de evento do calendário.
     * 
     * @param card card a ser convertido
     * @return DTO do evento do calendário
     */
    private CalendarEventDTO convertCardToEventDTO(Card card) {
        LocalDateTime scheduledDate = card.getScheduledDate();
        LocalDateTime dueDate = card.getDueDate();
        
        // Determinar se é evento de dia inteiro ou com horário específico
        boolean allDay = isAllDayEvent(scheduledDate);
        
        // Calcular prioridade baseada na data de vencimento
        CalendarEventPriority priority = calculatePriority(card);
        
        // Determinar cor baseada na urgência
        String color = calculateColor(card);
        
        return CalendarEventDTO.builder()
                .id(card.getId())
                .title(card.getTitle())
                .description(card.getDescription())
                .startDateTime(scheduledDate)
                .endDateTime(allDay ? scheduledDate.plusHours(1) : dueDate)
                .allDay(allDay)
                .type(CalendarEventType.CARD)
                .priority(priority)
                .color(color)
                .relatedEntityId(card.getId())
                .relatedEntityType("CARD")
                .active(true)
                .build();
    }

    /**
     * Determina se o evento deve ser de dia inteiro.
     * 
     * @param scheduledDate data de agendamento
     * @return true se for evento de dia inteiro
     */
    private boolean isAllDayEvent(LocalDateTime scheduledDate) {
        // Se não há horário específico (meia-noite), é evento de dia inteiro
        return scheduledDate.getHour() == 0 && scheduledDate.getMinute() == 0;
    }

    /**
     * Calcula a prioridade do evento baseada na urgência do card.
     * 
     * @param card card para cálculo da prioridade
     * @return prioridade do evento
     */
    private CalendarEventPriority calculatePriority(Card card) {
        int urgencyLevel = card.getUrgencyLevel();
        
        return switch (urgencyLevel) {
            case 4 -> CalendarEventPriority.URGENT;      // Vencido
            case 3 -> CalendarEventPriority.HIGH;        // Vence hoje
            case 2 -> CalendarEventPriority.HIGH;        // Vence em 1 dia
            case 1 -> CalendarEventPriority.STANDARD;    // Vence em 2-3 dias
            default -> CalendarEventPriority.LOW;        // Sem urgência
        };
    }

    /**
     * Calcula a cor do evento baseada na urgência do card.
     * 
     * @param card card para cálculo da cor
     * @return cor em formato hexadecimal
     */
    private String calculateColor(Card card) {
        int urgencyLevel = card.getUrgencyLevel();
        
        return switch (urgencyLevel) {
            case 4 -> "#FF0000";  // Vermelho - vencido
            case 3 -> "#FF6600";  // Laranja - vence hoje
            case 2 -> "#FFAA00";  // Amarelo - vence em 1 dia
            case 1 -> "#00AAFF";  // Azul - vence em 2-3 dias
            default -> "#00AA00"; // Verde - sem urgência
        };
    }

    /**
     * Obtém eventos de cards para uma data específica.
     * 
     * @param date data para busca
     * @return lista de eventos de cards para a data
     */
    public List<CalendarEventDTO> getEventsForDate(LocalDate date) {
        List<Card> cards = cardService.getCardsScheduledForDate(date);
        
        return cards.stream()
                .filter(card -> card.getScheduledDate() != null)
                .map(this::convertCardToEventDTO)
                .collect(Collectors.toList());
    }

    /**
     * Obtém eventos de cards próximos do vencimento.
     * 
     * @param daysThreshold número de dias para considerar "próximo do vencimento"
     * @return lista de eventos de cards próximos do vencimento
     */
    public List<CalendarEventDTO> getEventsNearDue(int daysThreshold) {
        List<Card> cards = cardService.getCardsNearDue(daysThreshold);
        
        return cards.stream()
                .filter(card -> card.getScheduledDate() != null)
                .map(this::convertCardToEventDTO)
                .collect(Collectors.toList());
    }

    /**
     * Obtém eventos de cards vencidos.
     * 
     * @return lista de eventos de cards vencidos
     */
    public List<CalendarEventDTO> getOverdueEvents() {
        List<Card> cards = cardService.getOverdueCards();
        
        return cards.stream()
                .filter(card -> card.getScheduledDate() != null)
                .map(this::convertCardToEventDTO)
                .collect(Collectors.toList());
    }

    /**
     * Converte um DTO de evento para entidade CalendarEvent.
     * 
     * @param dto DTO do evento
     * @return entidade CalendarEvent
     */
    private CalendarEvent convertDTOToEvent(CalendarEventDTO dto) {
        CalendarEvent event = new CalendarEvent();
        event.setId(dto.getId());
        event.setTitle(dto.getTitle());
        event.setDescription(dto.getDescription());
        event.setStartDateTime(dto.getStartDateTime());
        event.setEndDateTime(dto.getEndDateTime());
        event.setAllDay(dto.isAllDay());
        event.setType(dto.getType());
        event.setPriority(dto.getPriority());
        event.setColor(dto.getColor());
        event.setRelatedEntityId(dto.getRelatedEntityId());
        event.setRelatedEntityType(dto.getRelatedEntityType());
        event.setActive(dto.isActive());
        return event;
    }

    /**
     * Converte uma entidade CalendarEvent para DTO.
     * 
     * @param event entidade CalendarEvent
     * @return DTO do evento
     */
    private CalendarEventDTO convertEventToDTO(CalendarEvent event) {
        return CalendarEventDTO.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .startDateTime(event.getStartDateTime())
                .endDateTime(event.getEndDateTime())
                .allDay(event.isAllDay())
                .type(event.getType())
                .priority(event.getPriority())
                .color(event.getColor())
                .relatedEntityId(event.getRelatedEntityId())
                .relatedEntityType(event.getRelatedEntityType())
                .active(event.isActive())
                .build();
    }
}
