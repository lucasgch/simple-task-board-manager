package org.desviante.service.progress;

import org.desviante.model.enums.FieldType;
import org.desviante.model.enums.ProgressType;
import org.desviante.service.FieldService;
import org.springframework.stereotype.Component;

/**
 * Estratégia de progresso baseada em campos percentuais.
 *
 * <p>Implementa o cálculo de progresso baseado na média aritmética apenas dos
 * campos do tipo PercentageField associados a um card.</p>
 *
 * <p><strong>Cálculo de Progresso:</strong></p>
 * <p>O progresso é calculado como a média aritmética simples dos percentuais
 * de todos os PercentageFields. Por exemplo:</p>
 *
 * <pre>
 * PercentageField 1: 150/300 páginas = 50%
 * PercentageField 2: 75/100 exercícios = 75%
 *
 * Progresso Total = (50 + 75) / 2 = 62.5%
 * </pre>
 *
 * <p>ChecklistFields são ignorados nesta estratégia.</p>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 2.0
 * @since 1.0
 * @see ProgressStrategy
 * @see FieldService
 */
@Component
public class PercentageProgressStrategy implements ProgressStrategy {

    private final FieldService fieldService;

    /**
     * Construtor que inicializa a estratégia com o serviço de campos.
     *
     * @param fieldService serviço para acesso aos campos do card
     */
    public PercentageProgressStrategy(FieldService fieldService) {
        this.fieldService = fieldService;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getDisplayName() {
        return "Percentual (Campos Percentuais)";
    }

    @Override
    public ProgressType getType() {
        return ProgressType.PERCENTAGE;
    }

    @Override
    public void configureUI(ProgressUIConfig config) {
        // Mostrar container de progresso
        config.getProgressContainer().setVisible(true);
        config.getProgressContainer().setManaged(true);

        // Ocultar spinners - o progresso é calculado automaticamente dos campos percentuais
        config.getTotalLabel().setVisible(false);
        config.getTotalSpinner().setVisible(false);
        config.getCurrentLabel().setVisible(false);
        config.getCurrentSpinner().setVisible(false);

        // Mostrar label de progresso
        config.getProgressLabel().setVisible(true);
        config.getProgressLabel().setManaged(true);
        config.getProgressLabel().setText("Progresso (campos percentuais):");

        // A interface de gerenciamento de campos será fornecida por
        // um componente separado (FieldViewController)
    }

    /**
     * Atualiza a exibição do progresso com os dados do card.
     *
     * <p>Para a estratégia percentual, calcula o progresso usando a média apenas dos PercentageFields.</p>
     *
     * @param data dados para atualização da exibição
     */
    @Override
    public void updateDisplay(ProgressDisplayData data) {
        // Calcular progresso apenas de PercentageFields
        Double progress = fieldService.calculateFieldsProgressByType(data.getCardId(), FieldType.PERCENTAGE);
        data.setProgressPercentage(progress != null ? progress : 0.0);
    }

    @Override
    public ProgressValidationResult validate(ProgressInputData input) {
        // Validar título
        if (input.getTitle() == null || input.getTitle().trim().isEmpty()) {
            return ProgressValidationResult.error("O título não pode estar vazio.");
        }

        // Para percentual baseado em fields, não validar valores de total/current
        // pois o progresso é calculado automaticamente dos PercentageFields
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
