package org.desviante.model;

import org.desviante.model.enums.FieldType;

/**
 * Representa um campo do tipo percentual associado a um card.
 *
 * <p>Esta classe modela um campo que rastreia progresso baseado em unidades
 * (ex: páginas lidas de um livro, minutos assistidos de um vídeo). O progresso
 * é calculado automaticamente como (current/total) * 100.</p>
 *
 * <p><strong>Características Principais:</strong></p>
 * <ul>
 *   <li><strong>Label:</strong> Nome descritivo do campo (ex: "Progresso de Leitura")</li>
 *   <li><strong>Total:</strong> Quantidade total de unidades (ex: 300 páginas)</li>
 *   <li><strong>Current:</strong> Quantidade atual completada (ex: 150 páginas)</li>
 *   <li><strong>Unit:</strong> Unidade de medida (ex: "páginas", "minutos", "capítulos")</li>
 *   <li><strong>Cálculo Automático:</strong> Percentual calculado dinamicamente</li>
 * </ul>
 *
 * <p><strong>Exemplo de Uso:</strong></p>
 * <pre>
 * PercentageField readingProgress = new PercentageField();
 * readingProgress.setLabel("Progresso de Leitura");
 * readingProgress.setTotal(300);
 * readingProgress.setCurrent(150);
 * readingProgress.setUnit("páginas");
 * // getProgressPercentage() retorna 50.0
 * // getDisplayText() retorna "Progresso de Leitura: 150/300 páginas (50.0%)"
 * </pre>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lucasgch">GitHub</a>
 * @version 1.0
 * @since 2.0
 * @see Field
 * @see FieldType
 */
public class PercentageField extends Field {

    /**
     * Label descritivo do campo.
     * Exemplo: "Progresso de Leitura", "Minutos Assistidos", etc.
     */
    private String label;

    /**
     * Quantidade total de unidades.
     * Exemplo: 300 páginas, 120 minutos, 12 capítulos.
     */
    private Integer total;

    /**
     * Quantidade atual completada.
     * Exemplo: 150 páginas, 60 minutos, 6 capítulos.
     */
    private Integer current;

    /**
     * Unidade de medida.
     * Exemplo: "páginas", "minutos", "capítulos", "exercícios".
     */
    private String unit;

    /**
     * Construtor padrão que inicializa o campo como tipo PERCENTAGE.
     * Define valores padrão para total e current como 0.
     */
    public PercentageField() {
        super();
        this.fieldType = FieldType.PERCENTAGE;
        this.total = 0;
        this.current = 0;
    }

    /**
     * Construtor que inicializa o campo com todos os dados principais.
     *
     * @param label label descritivo do campo
     * @param total quantidade total de unidades
     * @param current quantidade atual completada
     * @param unit unidade de medida
     */
    public PercentageField(String label, Integer total, Integer current, String unit) {
        this();
        this.label = label;
        this.total = total;
        this.current = current;
        this.unit = unit;
    }

    /**
     * Retorna o texto formatado para exibição do campo.
     * Formato: "Label: current/total unit (percentage%)"
     *
     * @return texto formatado (ex: "Progresso de Leitura: 150/300 páginas (50.0%)")
     */
    @Override
    public String getDisplayText() {
        return String.format("%s: %d/%d %s (%.1f%%)",
                label != null ? label : "Progresso",
                current != null ? current : 0,
                total != null ? total : 0,
                unit != null ? unit : "unidades",
                getProgressPercentage());
    }

    /**
     * Verifica se o campo está completo (current >= total).
     *
     * @return true se current >= total, false caso contrário
     */
    @Override
    public boolean isCompleted() {
        return current != null && total != null && current >= total;
    }

    /**
     * Calcula o percentual de progresso baseado em current/total.
     * O valor é limitado a 100% mesmo se current exceder total.
     *
     * @return percentual de progresso (0.0 a 100.0)
     */
    @Override
    public Double getProgressPercentage() {
        if (total == null || total == 0 || current == null) {
            return 0.0;
        }
        return Math.min(100.0, (double) current / total * 100.0);
    }

    // Getters e Setters

    /**
     * Retorna o label descritivo do campo.
     *
     * @return label do campo, ou null se não foi definido
     */
    public String getLabel() {
        return label;
    }

    /**
     * Define o label descritivo do campo.
     *
     * @param label label a ser definido
     */
    public void setLabel(String label) {
        this.label = label;
        touch();
    }

    /**
     * Retorna a quantidade total de unidades.
     *
     * @return total de unidades, ou null se não foi definido
     */
    public Integer getTotal() {
        return total;
    }

    /**
     * Define a quantidade total de unidades.
     *
     * @param total total de unidades a ser definido
     */
    public void setTotal(Integer total) {
        this.total = total;
        touch();
    }

    /**
     * Retorna a quantidade atual completada.
     *
     * @return quantidade atual, ou null se não foi definido
     */
    public Integer getCurrent() {
        return current;
    }

    /**
     * Define a quantidade atual completada.
     *
     * @param current quantidade atual a ser definida
     */
    public void setCurrent(Integer current) {
        this.current = current;
        touch();
    }

    /**
     * Retorna a unidade de medida.
     *
     * @return unidade de medida, ou null se não foi definido
     */
    public String getUnit() {
        return unit;
    }

    /**
     * Define a unidade de medida.
     *
     * @param unit unidade de medida a ser definida
     */
    public void setUnit(String unit) {
        this.unit = unit;
        touch();
    }

    /**
     * Incrementa a quantidade atual em uma unidade.
     * Útil para atualizar progresso facilmente.
     */
    public void incrementCurrent() {
        if (current == null) {
            current = 1;
        } else {
            current++;
        }
        touch();
    }

    /**
     * Decrementa a quantidade atual em uma unidade.
     * Não permite valores negativos.
     */
    public void decrementCurrent() {
        if (current != null && current > 0) {
            current--;
            touch();
        }
    }

    /**
     * Retorna uma representação em string do percentage field.
     *
     * @return string representando o percentage field
     */
    @Override
    public String toString() {
        return "PercentageField{" +
                "id=" + id +
                ", cardId=" + cardId +
                ", label='" + label + '\'' +
                ", total=" + total +
                ", current=" + current +
                ", unit='" + unit + '\'' +
                ", progress=" + getProgressPercentage() + "%" +
                '}';
    }
}
