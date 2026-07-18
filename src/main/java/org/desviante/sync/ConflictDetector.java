package org.desviante.sync;

/**
 * Matriz de decisão da sincronização: geração remota × alterações locais.
 *
 * <p>Não usa timestamps (imune a clock skew entre dispositivos). A decisão
 * considera apenas:</p>
 * <ul>
 *   <li>a geração do manifest remoto (ou sua ausência);</li>
 *   <li>a última geração sincronizada por este dispositivo;</li>
 *   <li>se o banco local mudou desde a última sincronização (dirty).</li>
 * </ul>
 *
 * <table border="1">
 *   <caption>Matriz de decisão</caption>
 *   <tr><th>Geração remota</th><th>Local limpo</th><th>Local dirty</th></tr>
 *   <tr><td>ausente</td><td>UP_TO_DATE</td><td>PUSH</td></tr>
 *   <tr><td>== última sincronizada</td><td>UP_TO_DATE</td><td>PUSH</td></tr>
 *   <tr><td>&gt; última sincronizada</td><td>PULL</td><td>CONFLICT</td></tr>
 *   <tr><td>&lt; última sincronizada</td><td colspan="2">CONFLICT (regressão anômala)</td></tr>
 * </table>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lucasgch">GitHub</a>
 * @since 1.0
 */
public final class ConflictDetector {

    private ConflictDetector() {
        // Classe utilitária estática
    }

    /**
     * Decide a ação de sincronização segura para o estado atual.
     *
     * @param remoteGeneration geração do manifest remoto, ou {@code null} se
     *                         não existe manifest na pasta de nuvem
     * @param lastSyncedGeneration última geração sincronizada por este
     *                             dispositivo (0 se nunca sincronizou)
     * @param localDirty true se o banco local mudou desde a última sincronização
     * @return ação segura a executar
     */
    public static SyncStatus detect(Long remoteGeneration, long lastSyncedGeneration, boolean localDirty) {
        if (remoteGeneration == null || remoteGeneration == lastSyncedGeneration) {
            return localDirty ? SyncStatus.PUSH : SyncStatus.UP_TO_DATE;
        }
        if (remoteGeneration > lastSyncedGeneration) {
            return localDirty ? SyncStatus.CONFLICT : SyncStatus.PULL;
        }
        // Geração remota menor que a local: alguém apagou/restaurou o manifest
        // na nuvem. Nunca sobrescrever silenciosamente — sinalizar conflito.
        return SyncStatus.CONFLICT;
    }
}
