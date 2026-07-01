package org.desviante.service.progress;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Configuração da interface do usuário para progresso.
 * Encapsula os componentes da UI relacionados ao progresso.
 */
public class ProgressUIConfig {

    private final VBox progressContainer;
    private final VBox progressSection;
    private final Label progressLabel;
    private final Label progressValueLabel;
    private final Label statusValueLabel;
    private final HBox progressTypeContainer;

    /**
     * Construtor para configuração da interface de progresso.
     *
     * @param progressContainer container principal do progresso
     * @param progressSection seção específica do progresso
     * @param progressLabel label principal do progresso
     * @param progressValueLabel label para valor do progresso
     * @param statusValueLabel label para status do progresso
     * @param progressTypeContainer container para tipo de progresso
     */
    public ProgressUIConfig(
            VBox progressContainer,
            VBox progressSection,
            Label progressLabel,
            Label progressValueLabel,
            Label statusValueLabel,
            HBox progressTypeContainer) {

        this.progressContainer = progressContainer;
        this.progressSection = progressSection;
        this.progressLabel = progressLabel;
        this.progressValueLabel = progressValueLabel;
        this.statusValueLabel = statusValueLabel;
        this.progressTypeContainer = progressTypeContainer;
    }
    
    /**
     * Obtém o container principal do progresso.
     * 
     * @return container principal do progresso
     */
    public VBox getProgressContainer() { return progressContainer; }
    
    /**
     * Obtém a seção específica do progresso.
     * 
     * @return seção específica do progresso
     */
    public VBox getProgressSection() { return progressSection; }
    
    /**
     * Obtém o label principal do progresso.
     * 
     * @return label principal do progresso
     */
    public Label getProgressLabel() { return progressLabel; }
    
    /**
     * Obtém o label para valor do progresso.
     * 
     * @return label para valor do progresso
     */
    public Label getProgressValueLabel() { return progressValueLabel; }
    
    /**
     * Obtém o label para status do progresso.
     * 
     * @return label para status do progresso
     */
    public Label getStatusValueLabel() { return statusValueLabel; }
    
    /**
     * Obtém o container para tipo de progresso.
     * 
     * @return container para tipo de progresso
     */
    public HBox getProgressTypeContainer() { return progressTypeContainer; }
}
