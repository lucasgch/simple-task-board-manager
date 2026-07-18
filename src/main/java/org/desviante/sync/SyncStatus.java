package org.desviante.sync;

/**
 * Estado da sincronização entre o banco local e o snapshot na pasta de nuvem.
 *
 * <p>Resultado da matriz de decisão do {@link ConflictDetector}: geração
 * remota (manifest) × alterações locais desde a última sincronização
 * (dirty). Ver {@code docs/arquitetura/PLANO_SINCRONIZACAO_NUVEM.md}.</p>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lucasgch">GitHub</a>
 * @since 1.0
 */
public enum SyncStatus {

    /**
     * Local e nuvem estão na mesma geração e não há alterações locais.
     * Nenhuma ação necessária.
     */
    UP_TO_DATE,

    /**
     * Há alterações locais e a nuvem não avançou desde a última
     * sincronização: é seguro exportar (push) um novo snapshot.
     */
    PUSH,

    /**
     * A nuvem tem uma geração mais nova e não há alterações locais:
     * é seguro importar (pull) o snapshot remoto.
     */
    PULL,

    /**
     * A nuvem avançou E há alterações locais (ou a geração remota
     * regrediu): ambos os lados divergiram. Na Fase 1 nada é importado
     * ou exportado — o conflito é apenas sinalizado ao usuário.
     */
    CONFLICT
}
