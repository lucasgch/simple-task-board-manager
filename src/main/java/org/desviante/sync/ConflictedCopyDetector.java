package org.desviante.sync;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Detecção de "conflicted copies" criadas pelos provedores de nuvem.
 *
 * <p>Quando dois dispositivos gravam o mesmo arquivo antes de a
 * sincronização propagar, Dropbox/Google Drive/OneDrive não sobrescrevem:
 * criam uma cópia com sufixo no nome (ex.: {@code "... (conflicted copy
 * 2026-07-18)"}, {@code "... (cópia em conflito ...)"} ou {@code "... (1)"}).
 * Essas cópias ficam invisíveis para o app (os nomes não batem com
 * {@code boards-snapshot.sql.gz}/{@code sync-manifest.json}), então aqui
 * apenas as detectamos e avisamos o usuário para inspecionar a pasta.</p>
 *
 * <p>Os arquivos {@code conflito-*.sql.gz} criados intencionalmente pela
 * resolução "manter os dados deste computador" não casam com os padrões
 * abaixo e não geram aviso.</p>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lucasgch">GitHub</a>
 * @since 1.0
 */
@Slf4j
public final class ConflictedCopyDetector {

    private static final Pattern[] CONFLICTED_NAME_PATTERNS = {
            // Dropbox (inglês): "boards-snapshot (conflicted copy 2026-07-18).sql.gz"
            Pattern.compile("\\(conflicted copy", Pattern.CASE_INSENSITIVE),
            // Dropbox (português): "... (cópia em conflito de PC-X 2026-07-18)..."
            Pattern.compile("\\(c[óo]pia em conflito", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            // Google Drive / genérico: duplicata numerada "boards-snapshot (1).sql.gz"
            Pattern.compile(" \\(\\d+\\)\\.")
    };

    private ConflictedCopyDetector() {
        // Classe utilitária estática
    }

    /**
     * Procura cópias em conflito criadas pelo provedor na pasta de sync.
     *
     * @param syncDir pasta de sincronização ({@code <nuvem>/SimpleTaskBoard})
     * @return nomes de arquivo suspeitos, ordenados (vazio se nada encontrado
     *         ou se a pasta não existe)
     */
    public static List<String> findConflictedCopies(Path syncDir) {
        if (!Files.isDirectory(syncDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(syncDir)) {
            List<String> found = files
                    .map(p -> p.getFileName().toString())
                    .filter(ConflictedCopyDetector::matchesConflictedPattern)
                    .sorted()
                    .toList();
            if (!found.isEmpty()) {
                log.warn("Cópias em conflito do provedor de nuvem detectadas em {}: {}", syncDir, found);
            }
            return found;
        } catch (IOException e) {
            log.warn("Não foi possível varrer {} por cópias em conflito: {}", syncDir, e.getMessage());
            return List.of();
        }
    }

    private static boolean matchesConflictedPattern(String fileName) {
        for (Pattern pattern : CONFLICTED_NAME_PATTERNS) {
            if (pattern.matcher(fileName).find()) {
                return true;
            }
        }
        return false;
    }
}
