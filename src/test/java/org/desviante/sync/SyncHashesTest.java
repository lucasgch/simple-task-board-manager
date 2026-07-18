package org.desviante.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Hashes SHA-256 da sincronização: arquivo bruto e conteúdo descomprimido.
 */
@DisplayName("SyncHashes - SHA-256 de Arquivo e de Conteúdo Gzip")
class SyncHashesTest {

    /** SHA-256 conhecido da string "abc" (vetor de teste do NIST). */
    private static final String SHA256_ABC =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Hash de arquivo confere com vetor conhecido")
    void fileHashMatchesKnownVector() throws IOException {
        Path file = tempDir.resolve("abc.txt");
        Files.writeString(file, "abc");
        assertEquals(SHA256_ABC, SyncHashes.sha256OfFile(file));
    }

    @Test
    @DisplayName("Hash do conteúdo gzip ignora a compressão (hash do SQL, não do .gz)")
    void gzipContentHashMatchesUncompressedContent() throws IOException {
        Path gzipFile = tempDir.resolve("abc.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gzipFile))) {
            out.write("abc".getBytes());
        }

        assertEquals(SHA256_ABC, SyncHashes.sha256OfGzipContent(gzipFile),
                "Hash do conteúdo deve ser o do dado descomprimido");
        assertNotEquals(SyncHashes.sha256OfFile(gzipFile), SyncHashes.sha256OfGzipContent(gzipFile),
                "Hash do arquivo .gz e do conteúdo devem ser distintos");
    }

    @Test
    @DisplayName("Arquivo que não é gzip válido falha com IOException (nunca hash errado)")
    void invalidGzipFails() throws IOException {
        Path notGzip = tempDir.resolve("fake.gz");
        Files.writeString(notGzip, "isto não é gzip");
        assertThrows(IOException.class, () -> SyncHashes.sha256OfGzipContent(notGzip));
    }
}
