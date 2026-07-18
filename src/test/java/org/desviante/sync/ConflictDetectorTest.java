package org.desviante.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Matriz completa de decisão do {@link ConflictDetector}.
 *
 * <p>A detecção usa apenas gerações monotônicas e o dirty flag local —
 * nunca timestamps — portanto é imune a clock skew entre dispositivos
 * (não há cenário de relógio que altere o resultado).</p>
 */
@DisplayName("ConflictDetector - Matriz de Decisão")
class ConflictDetectorTest {

    @Test
    @DisplayName("Sem manifest remoto e local limpo: nada a fazer")
    void noRemoteClean() {
        assertEquals(SyncStatus.UP_TO_DATE, ConflictDetector.detect(null, 0, false));
    }

    @Test
    @DisplayName("Sem manifest remoto e local com alterações: push")
    void noRemoteDirty() {
        assertEquals(SyncStatus.PUSH, ConflictDetector.detect(null, 0, true));
    }

    @Test
    @DisplayName("Mesma geração e local limpo: sincronizado")
    void sameGenerationClean() {
        assertEquals(SyncStatus.UP_TO_DATE, ConflictDetector.detect(3L, 3, false));
    }

    @Test
    @DisplayName("Mesma geração e local com alterações: push")
    void sameGenerationDirty() {
        assertEquals(SyncStatus.PUSH, ConflictDetector.detect(3L, 3, true));
    }

    @Test
    @DisplayName("Nuvem à frente e local limpo: pull")
    void remoteAheadClean() {
        assertEquals(SyncStatus.PULL, ConflictDetector.detect(5L, 3, false));
    }

    @Test
    @DisplayName("Nuvem à frente e local com alterações: conflito")
    void remoteAheadDirty() {
        assertEquals(SyncStatus.CONFLICT, ConflictDetector.detect(5L, 3, true));
    }

    @Test
    @DisplayName("Primeiro pull de um dispositivo novo (geração local 0)")
    void freshDevicePullsExisting() {
        assertEquals(SyncStatus.PULL, ConflictDetector.detect(7L, 0, false));
    }

    @Test
    @DisplayName("Dispositivo novo com dados locais e nuvem populada: conflito, nunca sobrescrever")
    void freshDeviceWithLocalDataConflicts() {
        assertEquals(SyncStatus.CONFLICT, ConflictDetector.detect(7L, 0, true));
    }

    @Test
    @DisplayName("Geração remota regrediu (manifest recriado): conflito mesmo com local limpo")
    void remoteRegressionIsConflictEvenWhenClean() {
        assertEquals(SyncStatus.CONFLICT, ConflictDetector.detect(2L, 5, false));
        assertEquals(SyncStatus.CONFLICT, ConflictDetector.detect(2L, 5, true));
    }
}
