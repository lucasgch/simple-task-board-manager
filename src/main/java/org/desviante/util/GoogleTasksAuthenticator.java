package org.desviante.util;

import java.io.File;
import java.io.InputStream;

/**
 * Guia para configuração manual do Google Tasks.
 * 
 * <p>Esta classe exibe instruções detalhadas sobre como configurar
 * manualmente a integração com Google Tasks.</p>
 * 
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 1.0
 * @since 1.0
 */
public class GoogleTasksAuthenticator {

    private static final String CREDENTIALS_FILE_PATH = "/auth/credentials.json";
    private static final String TOKENS_DIRECTORY_PATH = System.getProperty("user.home") + File.separator + ".credentials" + File.separator + "simple-task-board-manager";

    /**
     * Método principal que exibe instruções de configuração.
     * 
     * @param args argumentos da linha de comando (não utilizados)
     */
    public static void main(String[] args) {
        System.out.println("=== Guia de Configuração Google Tasks ===");
        System.out.println();
        
        // Verificar se o arquivo de credenciais existe
        final InputStream in = GoogleTasksAuthenticator.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            showCredentialsSetupInstructions();
        } else {
            System.out.println("✅ Arquivo credentials.json encontrado!");
            showAuthenticationInstructions();
        }
        
        // Verificar se já existem credenciais salvas
        File credentialsDir = new File(TOKENS_DIRECTORY_PATH);
        File storedCredentialFile = new File(credentialsDir, "StoredCredential");
        
        if (storedCredentialFile.exists()) {
            System.out.println("✅ Credenciais encontradas em: " + storedCredentialFile.getAbsolutePath());
            System.out.println("🚀 A integração com Google Tasks deve estar funcionando!");
        } else {
            System.out.println("❌ Credenciais não encontradas em: " + TOKENS_DIRECTORY_PATH);
            System.out.println("⚠️ É necessário fazer a autenticação inicial.");
        }
        
        System.out.println();
        System.out.println("=== Status da Configuração ===");
        System.out.println("📁 Arquivo credentials.json: " + (in != null ? "✅ Encontrado" : "❌ Não encontrado"));
        System.out.println("🔑 Credenciais do usuário: " + (storedCredentialFile.exists() ? "✅ Encontradas" : "❌ Não encontradas"));
        System.out.println("🔧 Próximo passo: " + getNextStep(in != null, storedCredentialFile.exists()));
        
        try {
            if (in != null) {
                in.close();
            }
        } catch (Exception e) {
            // Ignorar erro de fechamento
        }
    }
    
    private static void showCredentialsSetupInstructions() {
        System.out.println("❌ Arquivo credentials.json não encontrado!");
        System.out.println();
        System.out.println("📋 PASSO 1: Configurar Credenciais do Google");
        System.out.println("1. Acesse: https://console.cloud.google.com/");
        System.out.println("2. Crie um novo projeto ou selecione um existente");
        System.out.println("3. Ative a API do Google Tasks:");
        System.out.println("   - Vá para 'APIs & Services' > 'Library'");
        System.out.println("   - Procure por 'Tasks API' e ative");
        System.out.println("4. Crie credenciais OAuth 2.0:");
        System.out.println("   - Vá para 'APIs & Services' > 'Credentials'");
        System.out.println("   - Clique em 'Create Credentials' > 'OAuth 2.0 Client IDs'");
        System.out.println("   - Selecione 'Desktop application'");
        System.out.println("   - Baixe o arquivo JSON das credenciais");
        System.out.println("5. Renomeie o arquivo para 'credentials.json'");
        System.out.println("6. Coloque em: src/main/resources/auth/credentials.json");
        System.out.println("7. Execute este programa novamente");
        System.out.println();
    }
    
    private static void showAuthenticationInstructions() {
        System.out.println("📋 PASSO 2: Fazer Autenticação Inicial");
        System.out.println("1. Use um dos aplicativos de exemplo do Google:");
        System.out.println("   - https://developers.google.com/tasks/quickstart/java");
        System.out.println("   - Baixe o código de exemplo");
        System.out.println("   - Copie seu credentials.json para o exemplo");
        System.out.println("   - Execute o exemplo uma vez");
        System.out.println("2. Ou use a Google Apps Script:");
        System.out.println("   - https://script.google.com/");
        System.out.println("   - Autorize o acesso ao Google Tasks");
        System.out.println("3. As credenciais serão salvas em:");
        System.out.println("   " + TOKENS_DIRECTORY_PATH);
        System.out.println();
    }
    
    private static String getNextStep(boolean hasCredentialsFile, boolean hasUserCredentials) {
        if (!hasCredentialsFile) {
            return "Configure o arquivo credentials.json";
        } else if (!hasUserCredentials) {
            return "Execute autenticação inicial";
        } else {
            return "Inicie a aplicação principal";
        }
    }
}
