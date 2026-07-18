package org.desviante.sync;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

/**
 * Cálculo dos hashes SHA-256 usados pela sincronização.
 *
 * <p>Dois hashes por snapshot: o do arquivo {@code .sql.gz} como está no
 * disco (integridade de transferência) e o do SQL descomprimido (detecção
 * de alterações locais).</p>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lucasgch">GitHub</a>
 * @since 1.0
 */
public final class SyncHashes {

    private SyncHashes() {
        // Classe utilitária estática
    }

    /**
     * SHA-256 (hex) dos bytes do arquivo.
     *
     * @param file arquivo a hashear
     * @return hash em hexadecimal minúsculo
     * @throws IOException se a leitura falhar
     */
    public static String sha256OfFile(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return sha256OfStream(in);
        }
    }

    /**
     * SHA-256 (hex) do conteúdo descomprimido de um arquivo gzip.
     *
     * @param gzipFile arquivo {@code .gz}
     * @return hash do conteúdo descomprimido em hexadecimal minúsculo
     * @throws IOException se a leitura ou descompressão falhar
     */
    public static String sha256OfGzipContent(Path gzipFile) throws IOException {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(gzipFile))) {
            return sha256OfStream(in);
        }
    }

    private static String sha256OfStream(InputStream in) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM sem SHA-256", e);
        }
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
