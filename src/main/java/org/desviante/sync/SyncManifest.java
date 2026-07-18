package org.desviante.sync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Manifest de sincronização publicado na pasta de nuvem junto ao snapshot.
 *
 * <p>Arquivo {@code sync-manifest.json} em {@code <pasta-nuvem>/SimpleTaskBoard/}.
 * Descreve o snapshot vigente ({@code boards-snapshot.sql.gz}) e permite a
 * detecção de conflitos sem depender de timestamps (imunes a clock skew):
 * contador de geração monotônico + deviceId + hashes SHA-256.</p>
 *
 * <ul>
 *   <li>{@code sha256} — hash do arquivo comprimido, valida a integridade do
 *       download (protege contra placeholders online-only e uploads parciais);</li>
 *   <li>{@code contentSha256} — hash do SQL descomprimido, usado para detectar
 *       alterações locais (dirty) comparando com o estado local.</li>
 * </ul>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lucasgch">GitHub</a>
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
// Tolerar campos gerados por versões mais novas do app em outros dispositivos.
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyncManifest {

    /**
     * Identificador (UUID) do dispositivo que exportou este snapshot.
     */
    private String deviceId;

    /**
     * Contador de geração monotônico. Cada export bem-sucedido publica
     * {@code max(geração remota, geração local) + 1}.
     */
    private long generation;

    /**
     * SHA-256 (hex) do arquivo {@code boards-snapshot.sql.gz} como está no disco.
     */
    private String sha256;

    /**
     * SHA-256 (hex) do conteúdo SQL descomprimido do snapshot.
     */
    private String contentSha256;

    /**
     * Versão do schema do snapshot (reservado para validação de migrações).
     */
    private String schemaVersion;

    /**
     * Versão da aplicação que gerou o snapshot.
     */
    private String appVersion;

    /**
     * Momento do export em formato ISO-8601 (informativo apenas — nunca
     * usado para detecção de conflito).
     */
    private String exportedAt;
}
