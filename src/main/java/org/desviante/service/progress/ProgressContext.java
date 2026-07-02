package org.desviante.service.progress;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import org.desviante.model.enums.ProgressType;
import org.desviante.model.enums.BoardColumnKindEnum;
import org.desviante.service.FieldService;

import java.util.HashMap;
import java.util.Map;

/**
 * Contexto que gerencia as estratégias de progresso.
 * Coordena a interface do usuário com as estratégias específicas.
 */
public class ProgressContext {

    private ProgressStrategy currentStrategy;
    private final Map<ProgressType, ProgressStrategy> strategies;
    private ProgressUIConfig uiConfig;
    private final DoubleProperty progressFraction = new SimpleDoubleProperty(0);

    /**
     * Construtor da classe ProgressContext.
     *
     * @param fieldService serviço de campos para cálculo de progresso dinâmico
     */
    public ProgressContext(FieldService fieldService) {
        this.strategies = new HashMap<>();
        initializeStrategies(fieldService);
    }

    /**
     * Inicializa as estratégias disponíveis.
     */
    private void initializeStrategies(FieldService fieldService) {
        strategies.put(ProgressType.PERCENTAGE, new PercentageProgressStrategy(fieldService));
        strategies.put(ProgressType.NONE, new NoProgressStrategy());
        strategies.put(ProgressType.CHECKLIST, new ChecklistProgressStrategy(fieldService));
        strategies.put(ProgressType.TOTAL, new TotalProgressStrategy(fieldService));
    }
    
    /**
     * Define a configuração da UI.
     * 
     * @param uiConfig configuração da UI
     */
    public void setUIConfig(ProgressUIConfig uiConfig) {
        this.uiConfig = uiConfig;
        if (uiConfig.getProgressBarTrack() != null && uiConfig.getProgressBarFill() != null) {
            uiConfig.getProgressBarFill().prefWidthProperty().bind(
                uiConfig.getProgressBarTrack().widthProperty().multiply(progressFraction)
            );
        }
    }
    
    /**
     * Define a estratégia atual baseada no tipo de progresso.
     * 
     * @param progressType tipo de progresso
     */
    public void setStrategy(ProgressType progressType) {
        this.currentStrategy = strategies.get(progressType);
        if (this.currentStrategy == null) {
            // Fallback para NONE se a estratégia não existir
            this.currentStrategy = strategies.get(ProgressType.NONE);
        }
    }
    
    /**
     * Configura a interface do usuário para o tipo de progresso atual.
     */
    public void configureUI() {
        if (currentStrategy != null && uiConfig != null) {
            currentStrategy.configureUI(uiConfig);
        }
    }
    
    /**
     * Atualiza a exibição do progresso com os dados fornecidos.
     *
     * @param totalUnits unidades totais
     * @param currentUnits unidades atuais
     * @param columnKind tipo da coluna
     * @param cardId identificador do card (necessário para estratégias baseadas em campos)
     */
    public void updateDisplay(Integer totalUnits, Integer currentUnits, BoardColumnKindEnum columnKind, Long cardId) {
        if (currentStrategy == null || uiConfig == null) {
            return;
        }

        // Calcular progresso percentual para qualquer tipo que tenha unidades válidas
        Double progressPercentage = null;
        if (currentStrategy.isEnabled()
                && totalUnits != null && totalUnits > 0) {
            int current = currentUnits != null ? currentUnits : 0;
            progressPercentage = Math.min(100.0, (double) current / totalUnits * 100);
        }

        // Determinar status baseado na coluna
        String statusText = getStatusText(columnKind);
        String statusCssClass = getStatusCssClass(columnKind);

        // Criar dados de exibição (com cardId para estratégias baseadas em fields)
        ProgressDisplayData displayData = new ProgressDisplayData(
            cardId, totalUnits, currentUnits, progressPercentage, statusText, statusCssClass
        );

        // Permitir que a estratégia atualize o progresso (ex: buscar do banco)
        currentStrategy.updateDisplay(displayData);

        // Atualizar a UI com os dados (possivelmente modificados pela estratégia)
        updateUIFromData(displayData);
    }
    
    /**
     * Valida os dados de entrada.
     * 
     * @param input dados de entrada
     * @return resultado da validação
     */
    public ProgressValidationResult validate(ProgressInputData input) {
        if (currentStrategy == null) {
            return ProgressValidationResult.error("Estratégia de progresso não definida.");
        }
        
        return currentStrategy.validate(input);
    }
    
    /**
     * Verifica se o progresso atual está habilitado.
     * 
     * @return true se habilitado, false caso contrário
     */
    public boolean isProgressEnabled() {
        return currentStrategy != null && currentStrategy.isEnabled();
    }
    
    /**
     * Obtém o nome de exibição da estratégia atual.
     * 
     * @return nome de exibição
     */
    public String getCurrentDisplayName() {
        return currentStrategy != null ? currentStrategy.getDisplayName() : "Desconhecido";
    }
    
    /**
     * Atualiza a UI com os dados fornecidos.
     * 
     * @param data dados para atualização
     */
    private void updateUIFromData(ProgressDisplayData data) {
        // Atualizar label de progresso
        if (data.getProgressPercentage() != null) {
            uiConfig.getProgressValueLabel().setText(String.format("%.1f%%", data.getProgressPercentage()));
        } else {
            uiConfig.getProgressValueLabel().setText("");
        }
        
        // Atualizar status
        if (data.getStatusText() != null) {
            uiConfig.getStatusValueLabel().setText(data.getStatusText());
        }
        
        // Aplicar classe CSS do status
        if (data.getStatusCssClass() != null) {
            uiConfig.getStatusValueLabel().getStyleClass().removeAll(
                "status-not-started", "status-in-progress", "status-completed", "status-unknown"
            );
            uiConfig.getStatusValueLabel().getStyleClass().add(data.getStatusCssClass());
        }

        // Atualizar a barra de progresso visual (trilho + preenchimento)
        if (uiConfig.getProgressBarFill() != null) {
            double fraction = data.getProgressPercentage() != null
                ? Math.max(0, Math.min(1, data.getProgressPercentage() / 100.0))
                : 0.0;
            progressFraction.set(fraction);

            uiConfig.getProgressBarFill().getStyleClass().removeAll(
                "bar-fill-todo", "bar-fill-doing", "bar-fill-done"
            );
            uiConfig.getProgressBarFill().getStyleClass().add(barFillCssClass(data.getStatusCssClass()));
        }
    }

    /**
     * Mapeia a classe CSS do status (já calculada a partir da coluna) para a
     * classe de preenchimento da barra de progresso visual correspondente.
     */
    private String barFillCssClass(String statusCssClass) {
        if ("status-in-progress".equals(statusCssClass)) {
            return "bar-fill-doing";
        }
        if ("status-completed".equals(statusCssClass)) {
            return "bar-fill-done";
        }
        return "bar-fill-todo";
    }
    
    /**
     * Obtém o texto do status baseado no tipo da coluna.
     * 
     * @param columnKind tipo da coluna
     * @return texto do status
     */
    private String getStatusText(BoardColumnKindEnum columnKind) {
        if (columnKind == null) {
            return "Desconhecido";
        }
        
        switch (columnKind) {
            case INITIAL: return "Não iniciado";
            case PENDING: return "Em andamento";
            case FINAL: return "Concluído";
            default: return "Desconhecido";
        }
    }
    
    /**
     * Obtém a classe CSS do status baseado no tipo da coluna.
     * 
     * @param columnKind tipo da coluna
     * @return classe CSS do status
     */
    private String getStatusCssClass(BoardColumnKindEnum columnKind) {
        if (columnKind == null) {
            return "status-unknown";
        }
        
        switch (columnKind) {
            case INITIAL: return "status-not-started";
            case PENDING: return "status-in-progress";
            case FINAL: return "status-completed";
            default: return "status-unknown";
        }
    }
}
