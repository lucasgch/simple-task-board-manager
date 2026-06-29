package org.desviante.model.enums;

/**
 * Enum que define os tipos de campos que podem ser adicionados aos cards.
 *
 * <p>Este enum permite que os cards tenham diferentes tipos de campos personalizados
 * que podem ser adicionados dinamicamente para enriquecer a funcionalidade do card.</p>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 1.0
 * @since 1.0
 */
public enum FieldType {

    /**
     * Item de checklist.
     * <p>Representa um item que pode ser marcado como concluído/pendente.
     * Útil para listas de tarefas e sub-tarefas dentro de um card.</p>
     */
    CHECKLIST_GROUP("Grupo de Checklist"),

    CHECKLIST_ITEM("Item de Checklist"),

    /**
     * Campo percentual.
     * <p>Representa progresso baseado em unidades (ex: páginas lidas, minutos assistidos).
     * Permite rastrear progresso com total/current e exibe percentual automaticamente.</p>
     */
    PERCENTAGE("Campo Percentual");

    private final String displayName;

    FieldType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Retorna o nome de exibição do tipo de campo.
     *
     * @return nome para exibição na interface
     */
    public String getDisplayName() {
        return displayName;
    }
}
