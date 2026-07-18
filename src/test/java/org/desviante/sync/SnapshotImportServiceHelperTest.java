package org.desviante.sync;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Helpers de resolução da conexão JDBC no startup (sem contexto Spring):
 * placeholders estilo Spring e limpeza de parâmetros da URL.
 */
@DisplayName("SnapshotImportService - Resolução de URL e Placeholders")
class SnapshotImportServiceHelperTest {

    private static final String TEST_PROP = "sync.test.data.dir";

    @AfterEach
    void clearTestProperty() {
        System.clearProperty(TEST_PROP);
    }

    @Test
    @DisplayName("Placeholder com propriedade de sistema definida usa o valor da propriedade")
    void placeholderUsesSystemPropertyWhenSet() {
        System.setProperty(TEST_PROP, "/dados/custom");
        assertEquals("jdbc:h2:file:/dados/custom/board_h2_db",
                SnapshotImportService.resolvePlaceholders(
                        "jdbc:h2:file:${" + TEST_PROP + ":/padrao}/board_h2_db"));
    }

    @Test
    @DisplayName("Placeholder sem propriedade definida usa o fallback")
    void placeholderFallsBackToDefault() {
        assertEquals("jdbc:h2:file:/padrao/board_h2_db",
                SnapshotImportService.resolvePlaceholders(
                        "jdbc:h2:file:${" + TEST_PROP + ":/padrao}/board_h2_db"));
    }

    @Test
    @DisplayName("Placeholders aninhados (como na URL real do application.properties)")
    void nestedPlaceholdersResolveInnermostFirst() {
        // Espelha spring.datasource.url: ${app.data.dir:${user.home}/myboards}
        String resolved = SnapshotImportService.resolvePlaceholders(
                "jdbc:h2:file:${" + TEST_PROP + ":${user.home}/myboards}/board_h2_db");
        assertEquals("jdbc:h2:file:" + System.getProperty("user.home") + "/myboards/board_h2_db",
                resolved, "Sem a propriedade externa, o fallback aninhado deve resolver user.home");

        System.setProperty(TEST_PROP, "/dados/escolhidos");
        assertEquals("jdbc:h2:file:/dados/escolhidos/board_h2_db",
                SnapshotImportService.resolvePlaceholders(
                        "jdbc:h2:file:${" + TEST_PROP + ":${user.home}/myboards}/board_h2_db"),
                "Com a propriedade externa definida, ela vence o fallback aninhado");
    }

    @Test
    @DisplayName("Parâmetros da URL JDBC são removidos (AUTO_SERVER, DB_CLOSE_DELAY)")
    void urlParametersAreStripped() {
        assertEquals("jdbc:h2:file:/home/user/myboards/board_h2_db",
                SnapshotImportService.stripUrlParameters(
                        "jdbc:h2:file:/home/user/myboards/board_h2_db;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1"));
        assertEquals("jdbc:h2:file:/x/db",
                SnapshotImportService.stripUrlParameters("jdbc:h2:file:/x/db"),
                "URL sem parâmetros permanece intacta");
    }
}
