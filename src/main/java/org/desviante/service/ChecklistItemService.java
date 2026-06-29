package org.desviante.service;

import org.desviante.model.CheckListItem;
import org.desviante.model.ChecklistField;
import org.desviante.model.Field;
import org.desviante.model.enums.FieldType;
import org.desviante.repository.CheckListItemRepository;
import org.desviante.service.dto.ChecklistItemDTO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service para gerenciar a lógica de negócio dos itens do checklist.
 *
 * <p><strong>REFATORADO:</strong> Este serviço foi refatorado para usar o novo
 * sistema genérico de Fields. Agora delega todas as operações para o {@link FieldService},
 * mantendo a interface original para compatibilidade com o código existente.</p>
 *
 * <p>A camada de compatibilidade converte entre CheckListItem (modelo antigo) e
 * ChecklistField (modelo novo), permitindo uma transição suave.</p>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 2.0
 * @since 1.0
 */
@Service
public class ChecklistItemService {

    private final FieldService fieldService;

    /**
     * Construtor que inicializa o serviço com as dependências necessárias.
     *
     * @param fieldService serviço para operações com fields genéricos
     */
    public ChecklistItemService(FieldService fieldService) {
        this.fieldService = fieldService;
    }
    
    /**
     * Adiciona um novo item ao checklist de um card.
     *
     * @param cardId identificador do card
     * @param text texto do item
     * @return DTO do item criado
     */
    public ChecklistItemDTO addItem(Long cardId, String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("O texto do item não pode estar vazio.");
        }
        int nextOrderIndex = fieldService.countFieldsByCardId(cardId);
        ChecklistField field = fieldService.createChecklistItem(cardId, text.trim(), nextOrderIndex);
        return convertFieldToDTO(field);
    }

    public ChecklistItemDTO addItemToGroup(Long cardId, Long groupId, String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("O texto do item não pode estar vazio.");
        }
        List<Field> existing = fieldService.getChecklistItemsByGroupId(groupId);
        ChecklistField field = fieldService.createChecklistItemInGroup(cardId, groupId, text.trim(), existing.size());
        return convertFieldToDTO(field);
    }

    public List<ChecklistItemDTO> getItemsByGroupId(Long groupId) {
        return fieldService.getChecklistItemsByGroupId(groupId).stream()
                .filter(f -> f instanceof ChecklistField)
                .map(f -> convertFieldToDTO((ChecklistField) f))
                .collect(Collectors.toList());
    }

    public int removeAllItemsFromGroup(Long groupId) {
        List<ChecklistItemDTO> items = getItemsByGroupId(groupId);
        items.forEach(item -> fieldService.deleteField(item.id()));
        return items.size();
    }

    public void updateGroupNameAndDescription(Long groupId, String name, String description) {
        fieldService.findById(groupId).ifPresent(f -> {
            if (f instanceof ChecklistField group) {
                group.setText(name);
                group.setDescription(description);
                fieldService.updateField(group);
            }
        });
    }

    public String getGroupDescription(Long groupId) {
        return fieldService.findById(groupId)
            .filter(f -> f instanceof ChecklistField)
            .map(f -> ((ChecklistField) f).getDescription())
            .orElse(null);
    }

    /**
     * Remove um item do checklist.
     *
     * @param itemId identificador do item
     * @return true se removido com sucesso
     */
    public boolean removeItem(Long itemId) {
        return fieldService.deleteField(itemId);
    }

    /**
     * Atualiza o texto de um item.
     *
     * @param itemId identificador do item
     * @param newText novo texto
     * @return DTO do item atualizado
     */
    public Optional<ChecklistItemDTO> updateItemText(Long itemId, String newText) {
        if (newText == null || newText.trim().isEmpty()) {
            throw new IllegalArgumentException("O texto do item não pode estar vazio.");
        }

        Optional<Field> fieldOpt = fieldService.findById(itemId);
        if (fieldOpt.isEmpty() || !(fieldOpt.get() instanceof ChecklistField)) {
            return Optional.empty();
        }

        ChecklistField field = (ChecklistField) fieldOpt.get();
        field.setText(newText.trim());

        boolean updated = fieldService.updateField(field);
        return updated ? Optional.of(convertFieldToDTO(field)) : Optional.empty();
    }
    
    /**
     * Marca um item como concluído ou não concluído.
     *
     * @param itemId identificador do item
     * @param completed estado de conclusão
     * @return true se atualizado com sucesso
     */
    public boolean toggleItemCompleted(Long itemId, boolean completed) {
        Optional<Field> fieldOpt = fieldService.findById(itemId);
        if (fieldOpt.isEmpty() || !(fieldOpt.get() instanceof ChecklistField)) {
            return false;
        }

        ChecklistField field = (ChecklistField) fieldOpt.get();
        field.setCompleted(completed);

        return fieldService.updateField(field);
    }

    /**
     * Move um item para uma nova posição.
     *
     * @param itemId identificador do item
     * @param newOrderIndex nova posição
     * @return true se movido com sucesso
     */
    public boolean moveItem(Long itemId, int newOrderIndex) {
        if (newOrderIndex < 0) {
            throw new IllegalArgumentException("A posição deve ser maior ou igual a zero.");
        }

        return fieldService.reorderField(itemId, newOrderIndex);
    }

    /**
     * Busca todos os itens de um card.
     *
     * @param cardId identificador do card
     * @return lista de DTOs dos itens
     */
    public List<ChecklistItemDTO> getItemsByCardId(Long cardId) {
        List<Field> fields = fieldService.getFieldsByCardIdAndType(cardId, FieldType.CHECKLIST_ITEM);
        return fields.stream()
                .filter(field -> field instanceof ChecklistField)
                .map(field -> convertFieldToDTO((ChecklistField) field))
                .collect(Collectors.toList());
    }

    /**
     * Busca um item por ID.
     *
     * @param itemId identificador do item
     * @return DTO do item ou empty
     */
    public Optional<ChecklistItemDTO> getItemById(Long itemId) {
        return fieldService.findById(itemId)
                .filter(field -> field instanceof ChecklistField)
                .map(field -> convertFieldToDTO((ChecklistField) field));
    }

    /**
     * Conta quantos itens um card tem.
     *
     * @param cardId identificador do card
     * @return número de itens
     */
    public int getItemCount(Long cardId) {
        return fieldService.getFieldsByCardIdAndType(cardId, FieldType.CHECKLIST_ITEM).size();
    }

    /**
     * Conta quantos itens concluídos um card tem.
     *
     * @param cardId identificador do card
     * @return número de itens concluídos
     */
    public int getCompletedItemCount(Long cardId) {
        return fieldService.countCompletedChecklistItems(cardId);
    }

    /**
     * Calcula o progresso percentual do checklist.
     *
     * @param cardId identificador do card
     * @return percentual de conclusão (0-100)
     */
    public double getProgressPercentage(Long cardId) {
        return fieldService.calculateFieldsProgressByType(cardId, FieldType.CHECKLIST_ITEM);
    }

    /**
     * Remove todos os itens de um card.
     *
     * @param cardId identificador do card
     * @return número de itens removidos
     */
    public int removeAllItemsFromCard(Long cardId) {
        return fieldService.deleteFieldsByCardId(cardId);
    }

    /**
     * Converte um ChecklistField para ChecklistItemDTO.
     *
     * @param field field a ser convertido
     * @return DTO do item
     */
    private ChecklistItemDTO convertFieldToDTO(ChecklistField field) {
        return new ChecklistItemDTO(
            field.getId(),
            field.getCardId(),
            field.getText(),
            field.isCompleted(),
            field.getOrderIndex(),
            field.getCreatedAt(),
            field.getCompletedAt()
        );
    }
}
