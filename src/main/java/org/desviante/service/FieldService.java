package org.desviante.service;

import org.desviante.integration.event.card.CardProgressCompletedEvent;
import org.desviante.integration.event.EventPublisher;
import org.desviante.model.Card;
import org.desviante.model.Field;
import org.desviante.model.ChecklistField;
import org.desviante.model.PercentageField;
import org.desviante.model.enums.FieldType;
import org.desviante.model.enums.ProgressType;
import org.desviante.repository.FieldRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Serviço para gerenciamento de campos (fields) genéricos associados a cards.
 *
 * <p>Este serviço fornece operações de negócio para criar, atualizar, deletar e
 * consultar campos de diferentes tipos (checklist, percentual, etc.), incluindo
 * cálculo de progresso baseado nos campos.</p>
 *
 * <p><strong>Funcionalidades Principais:</strong></p>
 * <ul>
 *   <li><strong>CRUD:</strong> Operações completas para gerenciar campos</li>
 *   <li><strong>Factory Methods:</strong> Criação simplificada de ChecklistField e PercentageField</li>
 *   <li><strong>Cálculo de Progresso:</strong> Média ponderada de todos os campos de um card</li>
 *   <li><strong>Consultas Especializadas:</strong> Busca por tipo, card, etc.</li>
 *   <li><strong>Validações:</strong> Garante consistência dos dados</li>
 * </ul>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 1.0
 * @since 2.0
 * @see Field
 * @see ChecklistField
 * @see PercentageField
 * @see FieldRepository
 */
@Service
public class FieldService {

    private static final Logger log = LoggerFactory.getLogger(FieldService.class);

    private final FieldRepository fieldRepository;
    private final EventPublisher eventPublisher;
    private final CardService cardService;

    /**
     * Construtor com injeção de dependências.
     *
     * @param fieldRepository repository para persistência de campos
     * @param eventPublisher publicador de eventos de domínio
     * @param cardService serviço para acesso aos cards (@Lazy para evitar dependência circular)
     */
    public FieldService(
            FieldRepository fieldRepository,
            EventPublisher eventPublisher,
            @Lazy CardService cardService) {
        this.fieldRepository = fieldRepository;
        this.eventPublisher = eventPublisher;
        this.cardService = cardService;
    }

    /**
     * Cria um novo campo genérico no sistema.
     *
     * @param field campo a ser criado
     * @return campo criado com ID gerado
     * @throws IllegalArgumentException se o campo for nulo ou inválido
     */
    @Transactional
    public Field createField(Field field) {
        if (field == null) {
            throw new IllegalArgumentException("Campo não pode ser nulo");
        }
        if (field.getCardId() == null) {
            throw new IllegalArgumentException("Campo deve estar associado a um card");
        }

        return fieldRepository.save(field);
    }

    /**
     * Atualiza um campo existente.
     *
     * <p>Este método também detecta quando a atualização causa o progresso do card
     * a atingir 100%, publicando um CardProgressCompletedEvent nesse caso.</p>
     *
     * @param field campo com os novos valores
     * @return true se o campo foi atualizado com sucesso
     * @throws IllegalArgumentException se o campo for nulo ou não tiver ID
     */
    @Transactional
    public boolean updateField(Field field) {
        if (field == null) {
            throw new IllegalArgumentException("Campo não pode ser nulo");
        }
        if (field.getId() == null) {
            throw new IllegalArgumentException("Campo deve ter um ID para ser atualizado");
        }

        // 1. Calcular progresso ANTES da atualização
        Double oldProgress = calculateProgressForCard(field.getCardId());

        // 2. Executar update no repositório
        boolean updated = fieldRepository.update(field);

        // 3. Se update bem-sucedido, calcular progresso DEPOIS
        if (updated) {
            Double newProgress = calculateProgressForCard(field.getCardId());

            // 4. Verificar e publicar evento se necessário
            checkAndPublishProgressCompletion(field.getCardId(), oldProgress, newProgress);
        }

        return updated;
    }

    /**
     * Remove um campo pelo seu identificador.
     *
     * @param fieldId identificador do campo a ser removido
     * @return true se o campo foi removido
     */
    @Transactional
    public boolean deleteField(Long fieldId) {
        if (fieldId == null) {
            throw new IllegalArgumentException("ID do campo não pode ser nulo");
        }

        return fieldRepository.deleteById(fieldId);
    }

    /**
     * Remove todos os campos associados a um card.
     *
     * @param cardId identificador do card
     * @return número de campos removidos
     */
    @Transactional
    public int deleteFieldsByCardId(Long cardId) {
        if (cardId == null) {
            throw new IllegalArgumentException("ID do card não pode ser nulo");
        }

        return fieldRepository.deleteByCardId(cardId);
    }

    /**
     * Busca um campo pelo seu identificador.
     *
     * @param fieldId identificador do campo
     * @return Optional contendo o campo encontrado ou vazio
     */
    public Optional<Field> findById(Long fieldId) {
        if (fieldId == null) {
            return Optional.empty();
        }

        return fieldRepository.findById(fieldId);
    }

    /**
     * Busca todos os campos de um card específico.
     *
     * @param cardId identificador do card
     * @return lista de campos ordenados por order_index
     */
    public List<Field> getFieldsByCardId(Long cardId) {
        if (cardId == null) {
            throw new IllegalArgumentException("ID do card não pode ser nulo");
        }

        return fieldRepository.findByCardId(cardId);
    }

    /**
     * Busca campos de um tipo específico associados a um card.
     *
     * @param cardId identificador do card
     * @param type tipo de campo (CHECKLIST_ITEM ou PERCENTAGE)
     * @return lista de campos do tipo especificado
     */
    public List<Field> getFieldsByCardIdAndType(Long cardId, FieldType type) {
        if (cardId == null) {
            throw new IllegalArgumentException("ID do card não pode ser nulo");
        }
        if (type == null) {
            throw new IllegalArgumentException("Tipo de campo não pode ser nulo");
        }

        return fieldRepository.findByCardIdAndType(cardId, type);
    }

    /**
     * Conta o número de campos de um card.
     *
     * @param cardId identificador do card
     * @return número total de campos
     */
    public int countFieldsByCardId(Long cardId) {
        if (cardId == null) {
            return 0;
        }

        return fieldRepository.countByCardId(cardId);
    }

    @Transactional
    public ChecklistField createChecklistGroup(Long cardId, String name, int orderIndex) {
        ChecklistField group = new ChecklistField(name);
        group.setFieldType(FieldType.CHECKLIST_GROUP);
        group.setCardId(cardId);
        group.setOrderIndex(orderIndex);
        return (ChecklistField) fieldRepository.save(group);
    }

    @Transactional
    public ChecklistField createChecklistItemInGroup(Long cardId, Long groupId, String text, int orderIndex) {
        ChecklistField item = new ChecklistField(text);
        item.setCardId(cardId);
        item.setParentFieldId(groupId);
        item.setOrderIndex(orderIndex);
        return (ChecklistField) fieldRepository.save(item);
    }

    public List<Field> getChecklistGroupsByCardId(Long cardId) {
        return fieldRepository.findGroupsByCardId(cardId);
    }

    public List<Field> getChecklistItemsByGroupId(Long groupId) {
        return fieldRepository.findByParentFieldId(groupId);
    }

    public void deleteChecklistGroup(Long groupId) {
        fieldRepository.deleteByParentFieldId(groupId);
        fieldRepository.deleteById(groupId);
    }

    /**
     * Factory method: Cria um novo campo de checklist associado a um card.
     *
     * @param cardId identificador do card
     * @param text texto do item de checklist
     * @param orderIndex posição do campo na lista
     * @return ChecklistField criado e persistido
     */
    @Transactional
    public ChecklistField createChecklistItem(Long cardId, String text, int orderIndex) {
        if (cardId == null) {
            throw new IllegalArgumentException("ID do card não pode ser nulo");
        }
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Texto do checklist não pode ser vazio");
        }

        ChecklistField field = new ChecklistField(text);
        field.setCardId(cardId);
        field.setOrderIndex(orderIndex);

        return (ChecklistField) fieldRepository.save(field);
    }

    /**
     * Factory method: Cria um novo campo percentual associado a um card.
     *
     * @param cardId identificador do card
     * @param label label descritivo do campo (ex: "Progresso de Leitura")
     * @param total quantidade total
     * @param description descrição opcional exibida abaixo do título
     * @param orderIndex posição do campo na lista
     * @return PercentageField criado e persistido
     */
    @Transactional
    public PercentageField createPercentageField(Long cardId, String label, Integer total, String description, int orderIndex) {
        if (cardId == null) {
            throw new IllegalArgumentException("ID do card não pode ser nulo");
        }
        if (label == null || label.trim().isEmpty()) {
            throw new IllegalArgumentException("Label do campo percentual não pode ser vazio");
        }
        if (total == null || total < 0) {
            throw new IllegalArgumentException("Total deve ser um número positivo");
        }

        PercentageField field = new PercentageField(label, total, 0, description);
        field.setCardId(cardId);
        field.setOrderIndex(orderIndex);

        return (PercentageField) fieldRepository.save(field);
    }

    /**
     * Calcula o progresso total de um card baseado na média ponderada de todos os seus campos.
     *
     * <p>Este método implementa o cálculo de progresso conforme especificado pelo usuário:
     * calcula a média aritmética simples do percentual de progresso de cada campo.</p>
     *
     * <p><strong>Exemplo:</strong></p>
     * <ul>
     *   <li>Checklist com 3 itens, 2 concluídos: 66.67%</li>
     *   <li>Campo percentual com 150/300 páginas: 50%</li>
     *   <li>Progresso total do card: (66.67 + 50) / 2 = 58.33%</li>
     * </ul>
     *
     * @param cardId identificador do card
     * @return percentual de progresso (0.0 a 100.0), ou 0.0 se não houver campos
     */
    public Double calculateCardProgress(Long cardId) {
        if (cardId == null) {
            return 0.0;
        }

        List<Field> fields = fieldRepository.findByCardId(cardId);

        if (fields.isEmpty()) {
            return 0.0;
        }

        // Calcula a média aritmética dos percentuais de progresso
        double totalProgress = fields.stream()
                .mapToDouble(Field::getProgressPercentage)
                .sum();

        return totalProgress / fields.size();
    }

    /**
     * Calcula o progresso baseado apenas em campos de um tipo específico.
     *
     * @param cardId identificador do card
     * @param type tipo de campo a considerar no cálculo
     * @return percentual de progresso (0.0 a 100.0), ou 0.0 se não houver campos do tipo
     */
    public Double calculateFieldsProgressByType(Long cardId, FieldType type) {
        if (cardId == null || type == null) {
            return 0.0;
        }

        List<Field> fields = fieldRepository.findByCardIdAndType(cardId, type);

        if (fields.isEmpty()) {
            return 0.0;
        }

        double totalProgress = fields.stream()
                .mapToDouble(Field::getProgressPercentage)
                .sum();

        return totalProgress / fields.size();
    }

    /**
     * Conta quantos campos de checklist estão concluídos em um card.
     *
     * @param cardId identificador do card
     * @return número de checklist fields concluídos
     */
    public int countCompletedChecklistItems(Long cardId) {
        if (cardId == null) {
            return 0;
        }

        List<Field> checklistFields = fieldRepository.findByCardIdAndType(cardId, FieldType.CHECKLIST_ITEM);

        return (int) checklistFields.stream()
                .filter(Field::isCompleted)
                .count();
    }

    /**
     * Verifica se todos os campos de um card estão concluídos.
     *
     * @param cardId identificador do card
     * @return true se todos os campos estão concluídos (ou se não houver campos)
     */
    public boolean areAllFieldsCompleted(Long cardId) {
        if (cardId == null) {
            return false;
        }

        List<Field> fields = fieldRepository.findByCardId(cardId);

        if (fields.isEmpty()) {
            return true; // Sem campos = considerado completo
        }

        return fields.stream().allMatch(Field::isCompleted);
    }

    /**
     * Reordena um campo alterando sua posição na lista.
     *
     * @param fieldId identificador do campo
     * @param newOrderIndex nova posição
     * @return true se a reordenação foi bem-sucedida
     */
    @Transactional
    public boolean reorderField(Long fieldId, int newOrderIndex) {
        if (fieldId == null) {
            throw new IllegalArgumentException("ID do campo não pode ser nulo");
        }
        if (newOrderIndex < 0) {
            throw new IllegalArgumentException("Índice de ordenação deve ser não-negativo");
        }

        return fieldRepository.updateOrderIndex(fieldId, newOrderIndex);
    }

    /**
     * Verifica se houve transição para 100% de progresso e publica evento se necessário.
     *
     * <p>Detecta quando o progresso muda de menos de 100% para 100% ou mais,
     * indicando que o card foi concluído. Publica CardProgressCompletedEvent
     * apenas para cards com ProgressType habilitado (não NONE).</p>
     *
     * @param cardId identificador do card
     * @param oldProgress progresso antes da atualização
     * @param newProgress progresso depois da atualização
     */
    private void checkAndPublishProgressCompletion(Long cardId, Double oldProgress, Double newProgress) {
        // Verificar transição < 100% → >= 100%
        if (oldProgress != null && oldProgress < 100.0 &&
            newProgress != null && newProgress >= 100.0) {

            try {
                // Buscar card para obter ProgressType
                Optional<Card> cardOpt = cardService.getCardById(cardId);
                if (cardOpt.isEmpty()) {
                    log.warn("Card {} não encontrado ao tentar publicar CardProgressCompletedEvent", cardId);
                    return;
                }

                Card card = cardOpt.get();

                // Apenas publicar se card é progressable e não é NONE
                if (card.isProgressable() && card.getProgressType() != ProgressType.NONE) {
                    log.info("Card {} atingiu 100% de progresso (tipo: {}). Publicando evento...",
                            cardId, card.getProgressType());

                    CardProgressCompletedEvent event = CardProgressCompletedEvent.builder()
                            .card(card)
                            .progressType(card.getProgressType())
                            .progress(newProgress)
                            .occurredOn(LocalDateTime.now())
                            .build();

                    eventPublisher.publish(event);

                    log.debug("CardProgressCompletedEvent publicado para card {}", cardId);
                } else {
                    log.debug("Card {} atingiu 100% mas não é progressable ou é NONE. Evento não publicado.", cardId);
                }
            } catch (Exception e) {
                log.error("Erro ao publicar CardProgressCompletedEvent para card {}: {}",
                        cardId, e.getMessage(), e);
            }
        }
    }

    /**
     * Calcula o progresso de um card baseado no seu ProgressType.
     *
     * <p>Delega o cálculo para o método apropriado de acordo com o tipo:</p>
     * <ul>
     *   <li>TOTAL: média de TODOS os fields</li>
     *   <li>PERCENTAGE: média apenas de PercentageFields</li>
     *   <li>CHECKLIST: média apenas de ChecklistFields</li>
     *   <li>NONE ou null: retorna 0.0</li>
     * </ul>
     *
     * @param cardId identificador do card
     * @return progresso calculado (0.0 a 100.0)
     */
    private Double calculateProgressForCard(Long cardId) {
        if (cardId == null) {
            return 0.0;
        }

        try {
            // Buscar card para pegar ProgressType
            Optional<Card> cardOpt = cardService.getCardById(cardId);
            if (cardOpt.isEmpty()) {
                return 0.0;
            }

            Card card = cardOpt.get();
            ProgressType progressType = card.getProgressType();

            if (progressType == null || progressType == ProgressType.NONE) {
                return 0.0;
            }

            return switch (progressType) {
                case TOTAL -> calculateCardProgress(cardId);
                case PERCENTAGE -> calculateFieldsProgressByType(cardId, FieldType.PERCENTAGE);
                case CHECKLIST -> calculateFieldsProgressByType(cardId, FieldType.CHECKLIST_ITEM);
                default -> 0.0;
            };
        } catch (Exception e) {
            log.error("Erro ao calcular progresso do card {}: {}", cardId, e.getMessage());
            return 0.0;
        }
    }
}
