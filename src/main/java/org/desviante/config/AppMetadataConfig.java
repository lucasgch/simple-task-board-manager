package org.desviante.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

// Importações JavaFX para notificações
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;

/**
 * Configuração principal dos metadados da aplicação.
 * 
 * <p>Esta classe gerencia o carregamento, salvamento e monitoramento dos
 * metadados de configuração da aplicação. Os metadados são armazenados
 * em um arquivo JSON que pode ser modificado pelo usuário sem necessidade
 * de recompilação.</p>
 * 
 * <p>Principais funcionalidades:</p>
 * <ul>
 *   <li>Carregamento automático de metadados ao inicializar</li>
 *   <li>Monitoramento de alterações no arquivo de configuração</li>
 *   <li>Notificação quando reinicialização é necessária</li>
 *   <li>Validação de configurações carregadas</li>
 *   <li>Fallback para configurações padrão</li>
 * </ul>
 * 
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@Component
public class AppMetadataConfig {
    
    private static final String METADATA_FILENAME = "app-metadata.json";
    private static final String DEFAULT_METADATA_FILENAME = "default-app-metadata.json";
    
    @Value("${app.metadata.directory:${user.home}/myboards/config}")
    private String metadataDirectoryPath;
    
    @Autowired
    private FileWatcherService fileWatcherService;
    
    private AppMetadata currentMetadata;
    private Path metadataFilePath;
    private Path defaultMetadataFilePath;
    private final ObjectMapper objectMapper;
    
    /**
     * Construtor que inicializa a configuração de metadados.
     */
    public AppMetadataConfig() {
        // ⭐ NOVO: Logs para verificar diretório de trabalho da aplicação reiniciada
        log.info("🔄 CONSTRUTOR AppMetadataConfig() CHAMADO!");
        log.info("🔄 Diretório de trabalho atual: {}", System.getProperty("user.dir"));
        log.info("🔄 Diretório home do usuário: {}", System.getProperty("user.home"));
        log.info("🔄 Diretório temporário: {}", System.getProperty("java.io.tmpdir"));
        
        // Inicializar ObjectMapper
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        log.info("✅ Construtor AppMetadataConfig() concluído com sucesso");
    }
    
    /**
     * Inicializa a configuração de metadados.
     */
    @PostConstruct
    public void init() {
        try {
            log.info("🔄 MÉTODO init() CHAMADO!");
            log.info("🔄 metadataDirectoryPath: {}", metadataDirectoryPath);
            
            // ⭐ CORREÇÃO: Verificar se metadataDirectoryPath foi injetado
            if (metadataDirectoryPath == null || metadataDirectoryPath.isEmpty()) {
                log.warn("⚠️ metadataDirectoryPath não foi injetado, usando valor padrão");
                metadataDirectoryPath = System.getProperty("user.home") + "/myboards/config";
            }
            
            log.info("🔄 Diretório de configuração final: {}", metadataDirectoryPath);
            
            initializeMetadataFiles();
            loadMetadata();
            startFileMonitoring();
            log.info("✅ Configuração de metadados inicializada com sucesso");
        } catch (Exception e) {
            log.error("❌ Erro ao inicializar configuração de metadados", e);
            // Usar configurações padrão em caso de erro
            useDefaultMetadata();
        }
    }
    
    /**
     * Inicializa os arquivos de metadados.
     */
    private void initializeMetadataFiles() throws IOException {
        // Cria o diretório de configuração se não existir
        Path configDir = Paths.get(metadataDirectoryPath);
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir);
            log.info("Diretório de configuração criado: {}", configDir);
        }
        
        this.metadataFilePath = configDir.resolve(METADATA_FILENAME);
        this.defaultMetadataFilePath = configDir.resolve(DEFAULT_METADATA_FILENAME);
        
        // Cria arquivo de metadados padrão se não existir
        if (!Files.exists(metadataFilePath)) {
            createDefaultMetadataFile();
        }
        
        // Cria arquivo de backup dos metadados padrão se não existir
        if (!Files.exists(defaultMetadataFilePath)) {
            createBackupDefaultMetadata();
        }
    }
    
    /**
     * Cria o arquivo de metadados padrão.
     */
    private void createDefaultMetadataFile() throws IOException {
        AppMetadata defaultMetadata = createDefaultMetadata();
        objectMapper.writeValue(metadataFilePath.toFile(), defaultMetadata);
        log.info("Arquivo de metadados padrão criado: {}", metadataFilePath);
    }
    
    /**
     * Cria backup dos metadados padrão.
     */
    private void createBackupDefaultMetadata() throws IOException {
        AppMetadata defaultMetadata = createDefaultMetadata();
        objectMapper.writeValue(defaultMetadataFilePath.toFile(), defaultMetadata);
        log.info("Backup de metadados padrão criado: {}", defaultMetadataFilePath);
    }
    
    /**
     * Cria metadados padrão com valores sensatos.
     */
    private AppMetadata createDefaultMetadata() {
        return AppMetadata.builder()
                .metadataVersion("1.0")
                .defaultCardTypeId(1L) // Tipo "Card" como padrão
                .defaultProgressType(org.desviante.model.enums.ProgressType.PERCENTAGE) // Progresso percentual como padrão
                .defaultBoardGroupId(null) // Sem grupo padrão - usuário deve configurar explicitamente
                .defaultStatusFilter("Não concluídos") // Filtro padrão: mostrar apenas boards não concluídos
                .installationDirectory(System.getProperty("user.dir"))
                .userDataDirectory(System.getProperty("user.home") + "/myboards")
                .logDirectory(System.getProperty("user.home") + "/myboards/logs")
                .defaultLogLevel("INFO")
                .maxLogFileSizeMB(10)
                .maxLogFiles(5)
                .updateCheckIntervalHours(24)
                .autoCheckUpdates(true)
                .showSystemNotifications(true)
                .databaseTimeoutSeconds(30)
                .autoBackupDatabase(true)
                .autoBackupIntervalHours(24)
                .autoBackupDirectory(System.getProperty("user.home") + "/myboards/backups")
                .uiConfig(AppMetadata.UIConfig.builder()
                        .theme("system")
                        .language("pt-BR")
                        .fontSize(12)
                        .showTooltips(true)
                        .confirmDestructiveActions(true)
                        .showProgressBars(true)
                        .build())
                .performanceConfig(AppMetadata.PerformanceConfig.builder()
                        .maxCardsPerPage(100)
                        .enableCaching(true)
                        .maxCacheSizeMB(50)
                        .cacheTimeToLiveMinutes(30)
                        .build())
                .securityConfig(AppMetadata.SecurityConfig.builder()
                        .validateInput(true)
                        .logSensitiveOperations(false)
                        .maxSessionTimeMinutes(480)
                        .build())
                .build();
    }
    
    /**
     * Carrega os metadados da aplicação do arquivo JSON.
     */
    private void loadMetadata() {
        log.info("🔄 Iniciando carregamento de metadados...");
        log.info("📁 Caminho do arquivo: {}", metadataFilePath);
        log.info("📁 Arquivo existe: {}", Files.exists(metadataFilePath));
        
        if (!Files.exists(metadataFilePath)) {
            log.warn("❌ Arquivo de metadados não encontrado, usando configurações padrão");
            useDefaultMetadata();
            return;
        }
        
        try {
            long fileSize = Files.size(metadataFilePath);
            log.info("📏 Tamanho do arquivo: {} bytes", fileSize);
            
            if (fileSize == 0) {
                log.warn("❌ Arquivo de metadados está vazio, usando configurações padrão");
                useDefaultMetadata();
                return;
            }
            
            log.info("📖 Tentando ler arquivo de metadados...");
            String content = Files.readString(metadataFilePath);
            log.info("📄 Conteúdo do arquivo lido: {} caracteres", content.length());
            log.info("📄 Primeiros 200 caracteres: {}", content.substring(0, Math.min(200, content.length())));
            
            AppMetadata loadedMetadata = objectMapper.readValue(content, AppMetadata.class);
            log.info("✅ Metadados carregados com sucesso de: {}", metadataFilePath);
            
            if (loadedMetadata != null) {
                this.currentMetadata = loadedMetadata;
                log.info("📊 Dados carregados:");
                log.info("   - Versão: {}", this.currentMetadata.getMetadataVersion());
                log.info("   - defaultCardTypeId: {}", this.currentMetadata.getDefaultCardTypeId());
                log.info("   - defaultProgressType: {}", this.currentMetadata.getDefaultProgressType());
                log.info("   - defaultBoardGroupId: {}", this.currentMetadata.getDefaultBoardGroupId());
                
                // ⭐ NOVO: Validação adicional dos dados carregados
                if (this.currentMetadata.getDefaultBoardGroupId() == null) {
                    log.warn("⚠️ ATENÇÃO: defaultBoardGroupId é null após carregamento!");
                    log.warn("⚠️ Isso pode indicar um problema no arquivo ou na deserialização");
                } else {
                    log.info("✅ defaultBoardGroupId carregado corretamente: {}", this.currentMetadata.getDefaultBoardGroupId());
                }
            } else {
                log.warn("⚠️ Metadados carregados são null, usando configurações padrão");
                useDefaultMetadata();
                return;
            }
            
            // Validação dos metadados carregados
            if (this.currentMetadata.getMetadataVersion() == null || this.currentMetadata.getMetadataVersion().isEmpty()) {
                log.warn("❌ Versão dos metadados inválida, usando configurações padrão");
                useDefaultMetadata();
                return;
            }
            
            log.info("✅ Validação de metadados concluída com sucesso");
            
        } catch (IOException e) {
            log.error("❌ Erro ao ler arquivo de metadados: {}", e.getMessage());
            log.error("❌ Stack trace completo:", e);
            
            // Tentar restaurar do backup
            Path backupPath = metadataFilePath.resolveSibling(metadataFilePath.getFileName() + ".backup");
            if (Files.exists(backupPath)) {
                log.info("🔄 Tentando restaurar do backup: {}", backupPath);
                try {
                    log.info("📖 Tentando restaurar do backup...");
                    String backupContent = Files.readString(backupPath);
                    AppMetadata backupMetadata = objectMapper.readValue(backupContent, AppMetadata.class);
                    this.currentMetadata = backupMetadata;
                    log.info("✅ Backup restaurado com sucesso");
                    return;
                } catch (Exception backupException) {
                    log.error("❌ Erro ao restaurar backup: {}", backupException.getMessage());
                }
            } else {
                log.warn("⚠️ Arquivo de backup não encontrado: {}", backupPath);
            }
            
            log.warn("🔄 Usando metadados padrão devido a falha no carregamento");
            useDefaultMetadata();
            
        } catch (Exception e) {
            log.error("❌ Erro inesperado ao carregar metadados: {}", e.getMessage());
            log.error("❌ Stack trace completo:", e);
            log.warn("🔄 Usando metadados padrão devido a erro inesperado");
            useDefaultMetadata();
        }
    }
    
    /**
     * Cria e aplica metadados padrão quando não é possível carregar do arquivo.
     */
    private void useDefaultMetadata() {
        log.warn("🔄 MÉTODO useDefaultMetadata() CHAMADO!");
        log.warn("🔄 Stack trace da chamada:");
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().contains("org.desviante")) {
                log.warn("   - {}:{}({})", element.getClassName(), element.getMethodName(), element.getLineNumber());
            }
        }
        
        // ⭐ NOVO: Log adicional para identificar o contexto
        log.warn("🔄 Contexto da chamada:");
        log.warn("   - Thread: {}", Thread.currentThread().getName());
        log.warn("   - Stack trace completo:");
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            log.warn("     {}:{}({})", element.getClassName(), element.getMethodName(), element.getLineNumber());
        }
        
        this.currentMetadata = createDefaultMetadata();
        log.info("✅ Metadados padrão criados e aplicados");
        log.info("📊 Dados padrão aplicados:");
        log.info("   - defaultCardTypeId: {}", this.currentMetadata.getDefaultCardTypeId());
        log.info("   - defaultProgressType: {}", this.currentMetadata.getDefaultProgressType());
        log.info("   - defaultBoardGroupId: {}", this.currentMetadata.getDefaultBoardGroupId());
        
        // ⭐ NOVO: Log adicional para confirmar que defaultBoardGroupId é null
        if (this.currentMetadata.getDefaultBoardGroupId() == null) {
            log.warn("⚠️ CONFIRMADO: defaultBoardGroupId definido como null nos metadados padrão");
            log.warn("⚠️ Este é o motivo pelo qual o sistema sugere 'Sem Grupo'");
        }
    }
    
    /**
     * Inicia o monitoramento do arquivo de metadados.
     */
    private void startFileMonitoring() {
        Path configDir = Paths.get(metadataDirectoryPath);
        fileWatcherService.startWatching(configDir, METADATA_FILENAME, this::handleMetadataFileChange);
        log.info("Monitoramento de metadados iniciado para: {}", metadataFilePath);
    }
    
    /**
     * Manipula alterações no arquivo de metadados.
     */
    private void handleMetadataFileChange(Path changedFile) {
        log.warn("Arquivo de metadados alterado: {}", changedFile);
        
        // Aguardar um pouco para evitar conflitos com operações de salvamento
        try {
            Thread.sleep(100); // Aguardar 100ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        
        // Verificar se o arquivo ainda existe e não está vazio
        try {
            if (!Files.exists(metadataFilePath) || Files.size(metadataFilePath) == 0) {
                log.warn("Arquivo de metadados não existe ou está vazio após alteração, aguardando...");
                return;
            }
        } catch (IOException e) {
            log.warn("Erro ao verificar tamanho do arquivo de metadados: {}, aguardando...", e.getMessage());
            return;
        }
        
        log.info("🔄 ALTERAÇÃO DETECTADA! Recarregando configurações em tempo real...");
        
        // ⭐ NOVA ESTRATÉGIA: Recarregar configurações sem reiniciar
        try {
            // Recarregar metadados do arquivo
            loadMetadata();
            
            log.info("✅ Configurações atualizadas com sucesso em tempo real!");
            log.info("📊 Novos valores carregados:");
            log.info("   - defaultCardTypeId: {}", this.currentMetadata.getDefaultCardTypeId());
            log.info("   - defaultProgressType: {}", this.currentMetadata.getDefaultProgressType());
            log.info("   - defaultBoardGroupId: {}", this.currentMetadata.getDefaultBoardGroupId());
            
            // Mostrar notificação de sucesso para o usuário
            showSuccessNotification();
            
        } catch (Exception e) {
            log.error("❌ Erro ao recarregar configurações: {}", e.getMessage());
            
            // Mostrar notificação de erro para o usuário
            showErrorNotification();
        }
    }
    
    /**
     * Mostra notificação de sucesso para o usuário.
     */
    private void showSuccessNotification() {
        Platform.runLater(() -> {
            try {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("✅ Configurações Atualizadas");
                alert.setHeaderText("Configurações Atualizadas com Sucesso!");
                alert.setContentText(
                    "As preferências foram atualizadas em tempo real!\n\n" +
                    "• Novos cards e boards usarão as novas configurações padrão\n" +
                    "• Não é necessário reiniciar a aplicação\n" +
                    "• Todas as mudanças estão ativas agora"
                );
                
                ButtonType okButton = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
                alert.getButtonTypes().setAll(okButton);
                
                alert.showAndWait();
                
            } catch (Exception e) {
                log.error("❌ Erro ao mostrar notificação de sucesso: {}", e.getMessage());
            }
        });
    }
    
    /**
     * Mostra notificação de erro para o usuário.
     */
    private void showErrorNotification() {
        Platform.runLater(() -> {
            try {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("❌ Erro na Atualização");
                alert.setHeaderText("Erro ao Atualizar Configurações");
                alert.setContentText(
                    "Ocorreu um erro ao atualizar as configurações.\n\n" +
                    "• As configurações antigas continuam ativas\n" +
                    "• Tente salvar novamente ou reiniciar a aplicação\n" +
                    "• Verifique os logs para mais detalhes"
                );
                
                ButtonType okButton = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
                alert.getButtonTypes().setAll(okButton);
                
                alert.showAndWait();
                
            } catch (Exception e) {
                log.error("❌ Erro ao mostrar notificação de erro: {}", e.getMessage());
            }
        });
    }
    
    
    
    
    
    
    
    /**
     * Obtém os metadados atuais.
     * 
     * @return metadados atuais da aplicação
     */
    public AppMetadata getCurrentMetadata() {
        return currentMetadata;
    }
    
    /**
     * Obtém o ID do tipo de card padrão.
     * 
     * @return ID do tipo de card padrão ou null se não definido
     */
    public Optional<Long> getDefaultCardTypeId() {
        return Optional.ofNullable(currentMetadata.getDefaultCardTypeId());
    }
    
    /**
     * Obtém o tipo de progresso padrão.
     * 
     * @return tipo de progresso padrão ou null se não definido
     */
    public Optional<org.desviante.model.enums.ProgressType> getDefaultProgressType() {
        return Optional.ofNullable(currentMetadata.getDefaultProgressType());
    }
    
    /**
     * Obtém o ID do grupo de quadro padrão.
     * 
     * @return ID do grupo de quadro padrão ou null se não definido
     */
    public Optional<Long> getDefaultBoardGroupId() {
        return Optional.ofNullable(currentMetadata.getDefaultBoardGroupId());
    }
    
    /**
     * Obtém o filtro de status padrão.
     * 
     * @return filtro de status padrão ou null se não definido
     */
    public Optional<String> getDefaultStatusFilter() {
        return Optional.ofNullable(currentMetadata.getDefaultStatusFilter());
    }
    
    /**
     * Obtém o diretório de instalação.
     * 
     * @return diretório de instalação
     */
    public String getInstallationDirectory() {
        return currentMetadata.getInstallationDirectory();
    }
    
    /**
     * Obtém o diretório de dados do usuário.
     * 
     * @return diretório de dados do usuário
     */
    public String getUserDataDirectory() {
        return currentMetadata.getUserDataDirectory();
    }
    
    /**
     * Obtém o diretório de logs.
     * 
     * @return diretório de logs
     */
    public String getLogDirectory() {
        return currentMetadata.getLogDirectory();
    }
    
    /**
     * Obtém o nível de logging padrão.
     * 
     * @return nível de logging padrão
     */
    public String getDefaultLogLevel() {
        return currentMetadata.getDefaultLogLevel();
    }
    
    /**
     * Obtém as configurações de interface.
     * 
     * @return configurações de interface
     */
    public AppMetadata.UIConfig getUIConfig() {
        return currentMetadata.getUiConfig();
    }
    
    /**
     * Obtém as configurações de performance.
     * 
     * @return configurações de performance
     */
    public AppMetadata.PerformanceConfig getPerformanceConfig() {
        return currentMetadata.getPerformanceConfig();
    }
    
    /**
     * Obtém as configurações de segurança.
     * 
     * @return configurações de segurança
     */
    public AppMetadata.SecurityConfig getSecurityConfig() {
        return currentMetadata.getSecurityConfig();
    }
    
    /**
     * Salva os metadados atuais no arquivo.
     * 
     * @throws IOException se houver erro ao salvar
     */
    public void saveMetadata() throws IOException {
        // Criar backup do arquivo atual se existir
        if (Files.exists(metadataFilePath)) {
            Path backupPath = metadataFilePath.resolveSibling(metadataFilePath.getFileName() + ".backup");
            try {
                Files.copy(metadataFilePath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                log.debug("Backup criado: {}", backupPath);
            } catch (IOException e) {
                log.warn("Não foi possível criar backup: {}", e.getMessage());
            }
        }
        
        // Salvar em arquivo temporário primeiro
        Path tempFile = metadataFilePath.resolveSibling(metadataFilePath.getFileName() + ".tmp");
        try {
            // Salvar no arquivo temporário
            objectMapper.writeValue(tempFile.toFile(), currentMetadata);
            log.debug("Metadados salvos em arquivo temporário: {}", tempFile);
            
            // Mover arquivo temporário para localização final
            Files.move(tempFile, metadataFilePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("Metadados salvos com sucesso em: {}", metadataFilePath);
            
            // Verificar se o arquivo foi salvo corretamente
            try {
                if (!Files.exists(metadataFilePath) || Files.size(metadataFilePath) == 0) {
                    throw new IOException("Arquivo salvo está vazio ou não existe");
                }
            } catch (IOException e) {
                throw new IOException("Erro ao verificar arquivo salvo: " + e.getMessage(), e);
            }
            
            // Verificar se o arquivo pode ser lido novamente
            try {
                objectMapper.readValue(metadataFilePath.toFile(), AppMetadata.class);
                log.debug("Verificação de leitura bem-sucedida - arquivo válido");
            } catch (Exception e) {
                throw new IOException("Arquivo salvo não pode ser lido: " + e.getMessage());
            }
            
        } catch (Exception e) {
            // Se algo deu errado, tentar restaurar do backup
            if (Files.exists(metadataFilePath.resolveSibling(metadataFilePath.getFileName() + ".backup"))) {
                try {
                    Path backupPath = metadataFilePath.resolveSibling(metadataFilePath.getFileName() + ".backup");
                    Files.copy(backupPath, metadataFilePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    log.warn("Arquivo restaurado do backup devido a erro no salvamento");
                } catch (IOException restoreError) {
                    log.error("Falha ao restaurar arquivo do backup: {}", restoreError.getMessage());
                }
            }
            
            // Limpar arquivo temporário se existir
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException deleteError) {
                log.warn("Não foi possível deletar arquivo temporário: {}", deleteError.getMessage());
            }
            
            throw new IOException("Erro ao salvar metadados: " + e.getMessage(), e);
        }
    }
    
    /**
     * Atualiza um valor específico nos metadados.
     * 
     * @param updater função para atualizar os metadados
     * @throws IOException se houver erro ao salvar
     */
    public void updateMetadata(MetadataUpdater updater) throws IOException {
        updater.update(currentMetadata);
        saveMetadata();
        log.info("Metadados atualizados e salvos");
    }
    
    /**
     * Interface funcional para atualizar metadados.
     */
    @FunctionalInterface
    public interface MetadataUpdater {
        void update(AppMetadata metadata);
    }
    
    /**
     * Obtém o caminho do arquivo de metadados.
     * 
     * @return caminho do arquivo de metadados
     */
    public Path getMetadataFilePath() {
        return metadataFilePath;
    }
    
    /**
     * Verifica se o arquivo de metadados existe.
     * 
     * @return true se existir, false caso contrário
     */
    public boolean metadataFileExists() {
        return Files.exists(metadataFilePath);
    }
}
