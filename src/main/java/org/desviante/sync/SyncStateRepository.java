package org.desviante.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência do estado local de sincronização e do manifest remoto.
 *
 * <p>Não é um bean Spring: precisa funcionar também no pre-Spring do
 * {@code main()} (import no startup, antes de o contexto subir). Todas as
 * escritas são atômicas — arquivo temporário na mesma pasta seguido de
 * {@code ATOMIC_MOVE} — para que clientes de nuvem e outras instâncias
 * nunca observem um JSON parcial.</p>
 *
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lucasgch">GitHub</a>
 * @since 1.0
 */
@Slf4j
public class SyncStateRepository {

    /** Subpasta criada dentro da pasta de nuvem escolhida pelo usuário. */
    public static final String SYNC_SUBDIR = "SimpleTaskBoard";

    /** Nome do snapshot do banco na pasta de nuvem. */
    public static final String SNAPSHOT_FILENAME = "boards-snapshot.sql.gz";

    /** Nome do manifest na pasta de nuvem. */
    public static final String MANIFEST_FILENAME = "sync-manifest.json";

    /** Nome do arquivo de estado local (em {@code <dataDir>}). */
    public static final String STATE_FILENAME = "sync-state.json";

    private final ObjectMapper objectMapper;
    private final Path stateFile;

    /**
     * Cria o repositório apontando para o arquivo de estado local.
     *
     * @param dataDir diretório de dados da aplicação (ex.: {@code ~/myboards})
     */
    public SyncStateRepository(Path dataDir) {
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.stateFile = dataDir.resolve(STATE_FILENAME);
    }

    /**
     * Resolve a pasta efetiva de sincronização dentro da pasta de nuvem.
     *
     * @param cloudFolder pasta de nuvem escolhida pelo usuário
     * @return {@code <cloudFolder>/SimpleTaskBoard}
     */
    public static Path resolveSyncDir(Path cloudFolder) {
        return cloudFolder.resolve(SYNC_SUBDIR);
    }

    /**
     * Carrega o estado local de sincronização.
     *
     * @return estado persistido, ou um estado inicial (geração 0) se o
     *         arquivo não existe ou está corrompido
     */
    public SyncState loadState() {
        if (!Files.exists(stateFile)) {
            return new SyncState();
        }
        try {
            return objectMapper.readValue(stateFile.toFile(), SyncState.class);
        } catch (IOException e) {
            log.warn("sync-state.json ilegível ({}); tratando como nunca sincronizado", e.getMessage());
            return new SyncState();
        }
    }

    /**
     * Persiste o estado local de sincronização (escrita atômica).
     *
     * @param state estado a persistir
     * @throws IOException se a escrita falhar
     */
    public void saveState(SyncState state) throws IOException {
        writeJsonAtomically(stateFile, state);
    }

    /**
     * Lê o manifest remoto da pasta de sincronização.
     *
     * @param syncDir pasta de sincronização ({@code <nuvem>/SimpleTaskBoard})
     * @return manifest, ou vazio se não existe ou está ilegível (upload em
     *         andamento / placeholder online-only)
     */
    public Optional<SyncManifest> loadManifest(Path syncDir) {
        Path manifestFile = syncDir.resolve(MANIFEST_FILENAME);
        if (!Files.exists(manifestFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(manifestFile.toFile(), SyncManifest.class));
        } catch (IOException e) {
            log.warn("Manifest remoto ilegível ({}); ignorando", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Publica o manifest na pasta de sincronização (escrita atômica).
     *
     * @param syncDir pasta de sincronização
     * @param manifest manifest a publicar
     * @throws IOException se a escrita falhar
     */
    public void saveManifest(Path syncDir, SyncManifest manifest) throws IOException {
        Files.createDirectories(syncDir);
        writeJsonAtomically(syncDir.resolve(MANIFEST_FILENAME), manifest);
    }

    private void writeJsonAtomically(Path target, Object value) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            objectMapper.writeValue(temp.toFile(), value);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // Filesystems exóticos (algumas montagens de rede): melhor um
                // move não-atômico do que falhar — o JSON é pequeno.
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
