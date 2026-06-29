package org.desviante.service.progress;

import org.desviante.model.enums.FieldType;
import org.desviante.model.enums.ProgressType;
import org.desviante.service.FieldService;
import org.springframework.stereotype.Component;

/**
 * Estratégia de progresso baseada em itens de checklist.
 *
 * <p>Implementa o cálculo de progresso baseado na média aritmética apenas dos
 * campos do tipo ChecklistField associados a um card.</p>
 *
 * <p><strong>Cálculo de Progresso:</strong></p>
 * <p>O progresso é calculado como a média aritmética simples dos percentuais
 * de todos os ChecklistFields. Cada item contribui com 0% (não concluído) ou 100% (concluído).
 * Por exemplo:</p>
 *
 * <pre>
 * ChecklistField 1: Concluído = 100%
 * ChecklistField 2: Não concluído = 0%
 * ChecklistField 3: Concluído = 100%
 * ChecklistField 4: Não concluído = 0%
 *
 * Progresso Total = (100 + 0 + 100 + 0) / 4 = 50%
 * </pre>
 *
 * <p>PercentageFields são ignorados nesta estratégia.</p>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 2.0
 * @since 1.0
 * @see ProgressStrategy
 * @see FieldService
 */
@Component
public class ChecklistProgressStrategy implements ProgressStrategy {

    private final FieldService fieldService;

    /**
     * Construtor que inicializa a estratégia com o serviço de campos.
     *
     * @param fieldService serviço para acesso aos campos do card
     */
    public ChecklistProgressStrategy(FieldService fieldService) {
        this.fieldService = fieldService;
    }

    @Override
    public boolean isEnabled() {
        // Para checklist, o progresso é calculado automaticamente baseado nos itens
        return true;
    }

    @Override
    public String getDisplayName() {
        return "Checklist (Itens de Checklist)";
    }

    @Override
    public ProgressType getType() {
        return ProgressType.CHECKLIST;
    }

    @Override
    public void configureUI(ProgressUIConfig config) {
        // Mostrar seção de progresso
        config.getProgressContainer().setVisible(true);
        config.getProgressContainer().setManaged(true);

        // Ocultar spinners - o progresso é calculado automaticamente dos campos de checklist
        config.getTotalLabel().setVisible(false);
        config.getTotalSpinner().setVisible(false);
        config.getCurrentLabel().setVisible(false);
        config.getCurrentSpinner().setVisible(false);

        // Mostrar label de progresso
        config.getProgressLabel().setVisible(true);
        config.getProgressLabel().setManaged(true);
        config.getProgressLabel().setText("Progresso (itens do checklist):");

        // A interface de gerenciamento de campos será fornecida por
        // um componente separado (FieldViewController)
    }

    /**
     * Atualiza a exibição do progresso com os dados do card.
     *
     * <p>Para a estratégia de checklist, calcula o progresso usando a média apenas dos ChecklistFields.</p>
     *
     * @param data dados para atualização da exibição
     */
    @Override
    public void updateDisplay(ProgressDisplayData data) {
        // Calcular progresso apenas de ChecklistFields
        Double progress = fieldService.calculateFieldsProgressByType(data.getCardId(), FieldType.CHECKLIST_ITEM);
        data.setProgressPercentage(progress != null ? progress : 0.0);
    }

    @Override
    public ProgressValidationResult validate(ProgressInputData input) {
        // Validar título
        if (input.getTitle() == null || input.getTitle().trim().isEmpty()) {
            return ProgressValidationResult.error("O título não pode estar vazio.");
        }

        // Para checklist baseado em fields, não validar valores de total/current
        // pois o progresso é calculado automaticamente dos ChecklistFields
        return ProgressValidationResult.success();
    }

    /**
     * Obtém o FieldService associado a esta estratégia.
     *
     * <p>Útil para componentes da UI que precisam gerenciar campos
     * diretamente.</p>
     *
     * @return instância do FieldService
     */
    public FieldService getFieldService() {
        return fieldService;
    }
}
