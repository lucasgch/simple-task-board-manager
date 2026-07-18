package org.desviante.sync;

import org.desviante.config.DatabaseMigrationConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Restore de snapshots entre versões de schema diferentes (interação com
 * as migrações de {@link DatabaseMigrationConfig}).
 *
 * <p>Cobre os dois sentidos previstos no plano:</p>
 * <ul>
 *   <li><strong>snapshot antigo → app novo</strong>: o import recria o schema
 *       antigo fielmente e as migrações idempotentes do startup levam o banco
 *       ao schema atual, preservando os dados;</li>
 *   <li><strong>snapshot novo → app antigo</strong>: o {@code RUNSCRIPT}
 *       preserva tabelas/colunas que o app antigo não conhece (nada é
 *       descartado), e as migrações atuais rodam sem quebrar nada.</li>
 * </ul>
 */
@DisplayName("Sincronização - Restore entre Versões de Schema")
class SchemaMigrationRestoreIntegrationTest {

    private static final String DB_USER = "myboarduser";
    private static final String DB_PASSWORD = "myboardpassword";

    @TempDir
    Path tempDir;

    private Path cloudFolder;
    private Path dataDirA;
    private Path dbFileBaseA;
    private String jdbcUrlA;
    private Path dataDirB;
    private Path dbFileBaseB;
    private String jdbcUrlB;

    private void setupDevices() throws Exception {
        cloudFolder = Files.createDirectories(tempDir.resolve("cloud"));
        dataDirA = Files.createDirectories(tempDir.resolve("deviceA"));
        dbFileBaseA = dataDirA.resolve("board_h2_db");
        jdbcUrlA = "jdbc:h2:file:" + dbFileBaseA;
        dataDirB = Files.createDirectories(tempDir.resolve("deviceB"));
        dbFileBaseB = dataDirB.resolve("board_h2_db");
        jdbcUrlB = "jdbc:h2:file:" + dbFileBaseB;

        try (Connection connection = DriverManager.getConnection(jdbcUrlA, DB_USER, DB_PASSWORD)) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        }
    }

    private SnapshotExportService exportServiceFor(String jdbcUrl) {
        return new SnapshotExportService(
                new JdbcTemplate(new DriverManagerDataSource(jdbcUrl, DB_USER, DB_PASSWORD)), null);
    }

    private SnapshotImportService importServiceForDeviceB() {
        return new SnapshotImportService(dataDirB, cloudFolder, jdbcUrlB, DB_USER, DB_PASSWORD, dbFileBaseB);
    }

    private void runCurrentMigrations(String jdbcUrl) throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(jdbcUrl, DB_USER, DB_PASSWORD);
        new DatabaseMigrationConfig(dataSource, new JdbcTemplate(dataSource)).run();
    }

    private boolean columnExists(String jdbcUrl, String table, String column) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD)) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, null, table, column)) {
                return rs.next();
            }
        }
    }

    private boolean tableExists(String jdbcUrl, String table) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD)) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getTables(null, null, table, new String[]{"TABLE"})) {
                return rs.next();
            }
        }
    }

    private long queryLong(String jdbcUrl, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    @DisplayName("Snapshot de schema antigo: import fiel + migrações levam ao schema atual sem perder dados")
    void oldSchemaSnapshotIsImportedAndMigratedForward() throws Exception {
        setupDevices();

        // Rebaixa o schema do dispositivo A para simular um app antigo:
        // sem order_index/progress_type (adicionados depois por migração)
        // e sem a tabela checklist_items (schema.sql já não a cria).
        try (Connection connection = DriverManager.getConnection(jdbcUrlA, DB_USER, DB_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP INDEX IF EXISTS idx_cards_column_order");
            statement.execute("ALTER TABLE cards DROP COLUMN order_index");
            statement.execute("ALTER TABLE cards DROP COLUMN progress_type");
        }
        long cardsInOldDatabase = queryLong(jdbcUrlA, "SELECT COUNT(*) FROM cards");
        assertTrue(cardsInOldDatabase > 0, "O banco antigo deve ter dados de exemplo");

        SyncResult exported = exportServiceFor(jdbcUrlA).sync(dataDirA, cloudFolder, "device-A");
        assertEquals(SyncResult.Status.EXPORTED, exported.status(), exported.message());

        // Dispositivo B (app novo, sem banco): restore do snapshot antigo
        assertEquals(SnapshotImportService.StartupImportResult.IMPORTED, importServiceForDeviceB().run());
        assertFalse(columnExists(jdbcUrlB, "CARDS", "ORDER_INDEX"),
                "O import deve recriar o schema antigo fielmente, sem inventar colunas");

        // Startup do app novo: migrações idempotentes completam o schema
        runCurrentMigrations(jdbcUrlB);

        assertTrue(columnExists(jdbcUrlB, "CARDS", "ORDER_INDEX"),
                "Migração deve adicionar order_index ao banco importado");
        assertTrue(columnExists(jdbcUrlB, "CARDS", "PROGRESS_TYPE"),
                "Migração deve adicionar progress_type ao banco importado");
        assertTrue(tableExists(jdbcUrlB, "CHECKLIST_ITEMS"),
                "Migração deve criar checklist_items no banco importado");
        assertEquals(cardsInOldDatabase, queryLong(jdbcUrlB, "SELECT COUNT(*) FROM cards"),
                "Nenhum card pode ser perdido na combinação import + migração");
        assertEquals(cardsInOldDatabase,
                queryLong(jdbcUrlB, "SELECT COUNT(*) FROM cards WHERE progress_type = 'PERCENTAGE'"),
                "Cards migrados devem receber o progress_type padrão");

        // O ciclo de sync continua após a migração: o banco migrado difere do
        // snapshot importado (dirty) e publica uma nova geração normalmente
        SyncResult afterMigration = exportServiceFor(jdbcUrlB).sync(dataDirB, cloudFolder, "device-B");
        assertEquals(SyncResult.Status.EXPORTED, afterMigration.status(), afterMigration.message());
        assertEquals(2, new SyncStateRepository(dataDirB)
                .loadManifest(SyncStateRepository.resolveSyncDir(cloudFolder)).orElseThrow().getGeneration());
    }

    @Test
    @DisplayName("Snapshot de schema mais novo: artefatos desconhecidos sobrevivem ao restore e às migrações atuais")
    void newerSchemaSnapshotPreservesUnknownArtifacts() throws Exception {
        setupDevices();

        // Avança o schema do dispositivo A para simular um app mais novo:
        // uma tabela e uma coluna que a versão atual não conhece.
        try (Connection connection = DriverManager.getConnection(jdbcUrlA, DB_USER, DB_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE future_feature ("
                    + "id BIGINT PRIMARY KEY, note VARCHAR(100) NOT NULL)");
            statement.execute("INSERT INTO future_feature (id, note) VALUES (1, 'dados da versão nova')");
            statement.execute("ALTER TABLE cards ADD COLUMN future_column VARCHAR(50) DEFAULT 'novo'");
        }

        SyncResult exported = exportServiceFor(jdbcUrlA).sync(dataDirA, cloudFolder, "device-A");
        assertEquals(SyncResult.Status.EXPORTED, exported.status(), exported.message());

        // Dispositivo B (app "antigo" = versão atual, sem banco): restore
        assertEquals(SnapshotImportService.StartupImportResult.IMPORTED, importServiceForDeviceB().run());

        assertTrue(tableExists(jdbcUrlB, "FUTURE_FEATURE"),
                "Tabela desconhecida do app deve sobreviver ao restore");
        assertEquals(1, queryLong(jdbcUrlB, "SELECT COUNT(*) FROM future_feature"),
                "Dados da versão mais nova não podem ser descartados");
        assertTrue(columnExists(jdbcUrlB, "CARDS", "FUTURE_COLUMN"),
                "Coluna desconhecida do app deve sobreviver ao restore");

        // Migrações atuais rodam por cima sem quebrar nem apagar nada
        long cardsBefore = queryLong(jdbcUrlB, "SELECT COUNT(*) FROM cards");
        runCurrentMigrations(jdbcUrlB);
        assertEquals(cardsBefore, queryLong(jdbcUrlB, "SELECT COUNT(*) FROM cards"));
        assertEquals(1, queryLong(jdbcUrlB, "SELECT COUNT(*) FROM future_feature"));
        assertTrue(columnExists(jdbcUrlB, "CARDS", "FUTURE_COLUMN"),
                "Migrações idempotentes não podem remover artefatos desconhecidos");
    }
}
