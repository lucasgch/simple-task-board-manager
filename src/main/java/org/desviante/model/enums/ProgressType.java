package org.desviante.model.enums;

/**
 * Enum que define os tipos de progresso disponíveis para os cards.
 *
 * <p>Este enum permite que os cards tenham diferentes tipos de acompanhamento
 * de progresso, desde nenhum progresso até progresso baseado em campos genéricos.</p>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 2.0
 * @since 1.0
 */
public enum ProgressType {

    /**
     * Card sem progresso.
     * <p>Cards deste tipo não mostram nenhuma informação de progresso
     * na interface do usuário, mantendo a interface limpa para tarefas
     * simples que não precisam de acompanhamento.</p>
     */
    NONE("Sem Controle"),

    /**
     * Progresso baseado em campos percentuais.
     * <p>Cards deste tipo calculam progresso como média aritmética apenas dos
     * campos do tipo PercentageField associados ao card.</p>
     * <p>Exemplo: Card com 2 PercentageFields (50% e 100%) = 75% total</p>
     */
    PERCENTAGE("Percentual (Campos Percentuais)"),

    /**
     * Progresso baseado em itens de checklist.
     * <p>Cards deste tipo calculam progresso como média aritmética apenas dos
     * campos do tipo ChecklistField associados ao card.</p>
     * <p>Cada ChecklistField contribui com 0% (não concluído) ou 100% (concluído).</p>
     */
    CHECKLIST("Checklist (Itens de Checklist)"),

    /**
     * Progresso total baseado em todos os campos.
     * <p>Cards deste tipo calculam progresso baseado na média aritmética de TODOS
     * os campos (fields) associados, independente do tipo. Suporta múltiplos tipos de campos:</p>
     * <ul>
     *   <li><strong>ChecklistField:</strong> Itens marcáveis (0% ou 100% cada)</li>
     *   <li><strong>PercentageField:</strong> Campos com progresso percentual (ex: páginas lidas)</li>
     * </ul>
     * <p>O progresso final é calculado como a média aritmética dos percentuais de todos os campos.</p>
     */
    TOTAL("Total (Todos os Campos)"),

    /**
     * Progresso baseado em todos os campos genéricos (checklist + percentual).
     * <p>Cards deste tipo calculam progresso como média aritmética de TODOS os
     * campos (Field) associados, independente do tipo.</p>
     */
    FIELDS("Baseado em Campos"),

    /**
     * Progresso customizado (reservado para futuras implementações).
     * <p>Permite implementar tipos de progresso específicos customizados.</p>
     */
    CUSTOM("Customizado");
    
    private final String displayName;
    
    ProgressType(String displayName) {
        this.displayName = displayName;
    }
    
    /**
     * Retorna o nome de exibição do tipo de progresso.
     * 
     * @return nome para exibição na interface
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Verifica se o tipo de progresso é habilitado.
     * 
     * @return true se o progresso deve ser mostrado, false caso contrário
     */
    public boolean isEnabled() {
        return this != NONE;
    }
}
