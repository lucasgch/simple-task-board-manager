package org.desviante.service.progress;

import org.desviante.model.enums.ProgressType;
import org.desviante.service.FieldService;
import org.springframework.stereotype.Component;

/**
 * Estratégia de progresso total baseada em todos os campos.
 *
 * <p>Implementa o cálculo de progresso baseado na média aritmética de TODOS os
 * campos (fields) associados a um card, independente do tipo. Esta estratégia suporta múltiplos tipos
 * de campos simultaneamente:</p>
 *
 * <ul>
 *   <li><strong>ChecklistField:</strong> Itens de checklist marcáveis (0% ou 100%)</li>
 *   <li><strong>PercentageField:</strong> Campos com progresso percentual customizado</li>
 * </ul>
 *
 * <p><strong>Cálculo de Progresso:</strong></p>
 * <p>O progresso total é calculado como a média aritmética simples dos percentuais
 * de progresso de cada campo individual. Por exemplo:</p>
 *
 * <pre>
 * Campo 1 (Checklist): 3/5 itens concluídos = 60%
 * Campo 2 (Percentage): 150/300 páginas = 50%
 * Campo 3 (Checklist): 2/2 itens concluídos = 100%
 *
 * Progresso Total = (60 + 50 + 100) / 3 = 70%
 * </pre>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 1.0
 * @since 2.1
 * @see ProgressStrategy
 * @see FieldService
 */
@Component
public class TotalProgressStrategy implements ProgressStrategy {

    private final FieldService fieldService;

    /**
     * Construtor que inicializa a estratégia com o serviço de campos.
     *
     * @param fieldService serviço para acesso aos campos do card
     */
    public TotalProgressStrategy(FieldService fieldService) {
        this.fieldService = fieldService;
    }

    /**
     * Verifica se o progresso está habilitado.
     * Para estratégia total, sempre está habilitado se houver campos.
     *
     * @return true sempre (o progresso é calculado automaticamente dos campos)
     */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * Retorna o nome de exibição da estratégia.
     *
     * @return "Total (Todos os Campos)"
     */
    @Override
    public String getDisplayName() {
        return "Total (Todos os Campos)";
    }

    /**
     * Retorna o tipo de progresso associado a esta estratégia.
     *
     * @return ProgressType.TOTAL
     */
    @Override
    public ProgressType getType() {
        return ProgressType.TOTAL;
    }

    /**
     * Configura a interface do usuário para exibir progresso total baseado em campos.
     *
     * <p>Para a estratégia total:</p>
     * <ul>
     *   <li>Mostra o container de progresso</li>
     *   <li>Oculta spinners de total/atual (não aplicável para fields)</li>
     *   <li>O progresso é mostrado como barra de progresso calculada automaticamente</li>
     * </ul>
     *
     * @param config configuração da UI a ser aplicada
     */
    @Override
    public void configureUI(ProgressUIConfig config) {
        // Mostrar container de progresso
        config.getProgressContainer().setVisible(true);
        config.getProgressContainer().setManaged(true);

        // Ocultar spinners - o progresso é calculado automaticamente dos campos
        config.getTotalLabel().setVisible(false);
        config.getTotalSpinner().setVisible(false);
        config.getCurrentLabel().setVisible(false);
        config.getCurrentSpinner().setVisible(false);

        // Mostrar label de progresso
        config.getProgressLabel().setVisible(true);
        config.getProgressLabel().setManaged(true);
        config.getProgressLabel().setText("Progresso:");

        // A interface de gerenciamento de campos será fornecida por
        // um componente separado (FieldViewController)
    }

    /**
     * Atualiza a exibição do progresso com os dados do card.
     *
     * <p>Para a estratégia total, calcula o progresso usando a média de todos os campos.</p>
     *
     * @param data dados para atualização da exibição
     */
    @Override
    public void updateDisplay(ProgressDisplayData data) {
        // Calcular progresso de TODOS os campos
        Double progress = fieldService.calculateCardProgress(data.getCardId());
        data.setProgressPercentage(progress != null ? progress : 0.0);
    }

    /**
     * Valida os dados de entrada para cards com progresso total.
     *
     * <p>Para a estratégia total, a validação é mínima pois o progresso
     * é calculado automaticamente dos campos associados.</p>
     *
     * @param input dados de entrada para validação
     * @return resultado da validação
     */
    @Override
    public ProgressValidationResult validate(ProgressInputData input) {
        // Validar título
        if (input.getTitle() == null || input.getTitle().trim().isEmpty()) {
            return ProgressValidationResult.error("O título não pode estar vazio.");
        }

        // Para total, não validar valores de total/current pois o progresso
        // é calculado automaticamente dos campos
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
