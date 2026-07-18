package org.desviante.sync;

/**
 * Resultado de uma sincronização manual ("Sincronizar agora").
 *
 * @param status desfecho da operação
 * @param message mensagem amigável para exibição ao usuário
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lucasgch">GitHub</a>
 * @since 1.0
 */
public record SyncResult(Status status, String message) {

    /**
     * Desfecho possível de uma sincronização manual.
     */
    public enum Status {
        /** Snapshot exportado para a pasta de nuvem com sucesso. */
        EXPORTED,
        /** Nada a fazer: local e nuvem já estão sincronizados. */
        UP_TO_DATE,
        /** A nuvem tem dados mais novos; o import ocorre na próxima abertura do app. */
        REMOTE_NEWER,
        /** Local e nuvem divergiram; nada foi importado ou exportado. */
        CONFLICT,
        /** Sincronização desabilitada ou pasta não configurada. */
        DISABLED,
        /** Falha na operação (detalhes na mensagem/log). */
        ERROR
    }
}
