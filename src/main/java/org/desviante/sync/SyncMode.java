package org.desviante.sync;

/**
 * Modo de sincronização de dados entre dispositivos via pasta de nuvem.
 *
 * <p>Define quando a sincronização (export/import de snapshots do banco)
 * é executada. Ver {@code docs/PLANO_SINCRONIZACAO_NUVEM.md}.</p>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @since 1.0
 */
public enum SyncMode {

    /**
     * Sincronização apenas manual, acionada pelo usuário (botão "Sincronizar agora").
     */
    MANUAL,

    /**
     * Sincronização automática: verificação/import ao abrir a aplicação
     * e export ao fechar, além do botão manual.
     */
    ON_OPEN_CLOSE
}
