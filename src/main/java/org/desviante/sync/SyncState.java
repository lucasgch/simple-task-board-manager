package org.desviante.sync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Estado local de sincronização deste dispositivo.
 *
 * <p>Persistido em {@code <dataDir>/sync-state.json} — fora da pasta de
 * nuvem, pois descreve a relação <em>deste</em> dispositivo com o snapshot
 * remoto. Um estado ausente equivale a "nunca sincronizou" (geração 0).</p>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lucasgch">GitHub</a>
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyncState {

    /**
     * Última geração do manifest remoto com a qual este dispositivo esteve
     * sincronizado (por export ou import). 0 = nunca sincronizou.
     */
    @Builder.Default
    private long lastSyncedGeneration = 0L;

    /**
     * SHA-256 do conteúdo SQL do banco no momento da última sincronização.
     * Comparado com o hash do conteúdo atual para detectar alterações
     * locais (dirty) sem depender de hooks nos serviços de escrita.
     */
    private String lastSyncedContentSha256;

    /**
     * Indica que a última verificação detectou conflito (local e nuvem
     * divergiram). Na Fase 1 apenas sinaliza na UI; a resolução vem na Fase 2.
     */
    @Builder.Default
    private boolean pendingConflict = false;

    /**
     * Momento da última sincronização bem-sucedida (ISO-8601, informativo).
     */
    private String lastSyncAt;
}
