package org.desviante.service.progress;

/**
 * Dados para exibição do progresso.
 * Encapsula os valores necessários para atualizar a interface.
 */
public class ProgressDisplayData {

    private final Long cardId;
    private final Integer totalUnits;
    private final Integer currentUnits;
    private Double progressPercentage;
    private final String statusText;
    private final String statusCssClass;

    public ProgressDisplayData(
            Long cardId,
            Integer totalUnits,
            Integer currentUnits,
            Double progressPercentage,
            String statusText,
            String statusCssClass) {

        this.cardId = cardId;
        this.totalUnits = totalUnits;
        this.currentUnits = currentUnits;
        this.progressPercentage = progressPercentage;
        this.statusText = statusText;
        this.statusCssClass = statusCssClass;
    }

    public Long getCardId() { return cardId; }
    public Integer getTotalUnits() { return totalUnits; }
    public Integer getCurrentUnits() { return currentUnits; }
    public Double getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(Double progressPercentage) { this.progressPercentage = progressPercentage; }
    public String getStatusText() { return statusText; }
    public String getStatusCssClass() { return statusCssClass; }
}
