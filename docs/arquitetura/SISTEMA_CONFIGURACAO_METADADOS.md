# 🚀 Sistema de Configuração Baseado em Metadados

## 📋 Visão Geral

O **Simple Task Board Manager** agora possui um sistema robusto de configuração baseado em metadados que permite ajustar o comportamento da aplicação sem necessidade de recompilação. Este sistema torna a aplicação mais dinâmica e adaptável às necessidades do usuário.

## 🏗️ Arquitetura do Sistema

### **Componentes Principais**

1. **`AppMetadata`** - Classe de dados que representa todas as configurações
2. **`AppMetadataConfig`** - Gerenciador principal de configurações
3. **`FileWatcherService`** - Monitora alterações no arquivo de configuração
4. **`DefaultConfigurationService`** - Gerencia configurações padrão para cards
5. **`ConfigurationManagementController`** - Interface para gerenciar configurações

### **Fluxo de Funcionamento**

```
Aplicação Inicia → Carrega Metadados → Monitora Arquivo → Notifica Alterações
       ↓                ↓                ↓                ↓
   Configurações    Validações      Detecta Mudanças   Solicita Reinicialização
   Aplicadas        Executadas      Automaticamente    Para Aplicar Mudanças
```

## 📁 Estrutura de Arquivos

### **Localização dos Metadados**

- **Diretório**: `{user.home}/myboards/config/`
- **Arquivo Principal**: `app-metadata.json`
- **Backup Padrão**: `default-app-metadata.json`

### **Exemplo de Arquivo de Metadados**

```json
{
  "metadataVersion": "1.0",
  "defaultCardTypeId": 1,
  "defaultProgressType": "PERCENTAGE",
  "installationDirectory": "/path/to/app",
  "userDataDirectory": "/home/user/myboards",
  "uiConfig": {
    "theme": "system",
    "language": "pt-BR",
    "fontSize": 12
  },
  "performanceConfig": {
    "maxCardsPerPage": 100,
    "enableCaching": true
  }
}
```

## ⚙️ Configurações Disponíveis

### **🔧 Configurações de Card**

| Propriedade | Descrição | Padrão |
|-------------|-----------|---------|
| `defaultCardTypeId` | ID do tipo de card padrão | `null` (primeiro disponível) |
| `defaultProgressType` | Tipo de progresso padrão | `NONE` |

### **📁 Configurações de Diretórios**

| Propriedade | Descrição | Padrão |
|-------------|-----------|---------|
| `installationDirectory` | Diretório de instalação | `{user.dir}` |
| `userDataDirectory` | Dados do usuário | `{user.home}/myboards` |
| `logDirectory` | Diretório de logs | `{user.home}/myboards/logs` |
| `autoBackupDirectory` | Diretório de backups | `{user.home}/myboards/backups` |

### **🎨 Configurações de Interface**

| Propriedade | Descrição | Padrão |
|-------------|-----------|---------|
| `theme` | Tema da interface | `"system"` |
| `language` | Idioma | `"pt-BR"` |
| `fontSize` | Tamanho da fonte | `12` |
| `showTooltips` | Mostrar dicas | `true` |
| `confirmDestructiveActions` | Confirmar ações destrutivas | `true` |
| `showProgressBars` | Mostrar barras de progresso | `true` |

### **⚡ Configurações de Performance**

| Propriedade | Descrição | Padrão |
|-------------|-----------|---------|
| `maxCardsPerPage` | Máximo de cards por página | `100` |
| `enableCaching` | Habilitar cache | `true` |
| `maxCacheSizeMB` | Tamanho máximo do cache | `50` |
| `cacheTimeToLiveMinutes` | Tempo de vida do cache | `30` |

### **🔒 Configurações de Segurança**

| Propriedade | Descrição | Padrão |
|-------------|-----------|---------|
| `validateInput` | Validar entrada de dados | `true` |
| `logSensitiveOperations` | Log de operações sensíveis | `false` |
| `maxSessionTimeMinutes` | Tempo máximo de sessão | `480` (8h) |

## 🚀 Como Usar

### **1. Configuração Automática**

A aplicação cria automaticamente o arquivo de configuração na primeira execução:

```bash
# O arquivo será criado em:
~/myboards/config/app-metadata.json
```

### **2. Modificação Manual**

Edite o arquivo `app-metadata.json` diretamente:

```json
{
  "defaultCardTypeId": 2,
  "defaultProgressType": "CHECKLIST",
  "uiConfig": {
    "theme": "dark",
    "fontSize": 14
  }
}
```

### **3. Modificação via Interface**

Use o controlador de configurações para modificar via interface gráfica:

```java
@Autowired
private DefaultConfigurationService defaultConfigService;

// Definir tipo de card padrão
defaultConfigService.setDefaultCardType(2L);

// Definir tipo de progresso padrão
defaultConfigService.setDefaultProgressType(ProgressType.CHECKLIST);
```

## 🔄 Monitoramento de Alterações

### **Como Funciona**

1. **Inicialização**: O `FileWatcherService` inicia o monitoramento
2. **Detecção**: Usa `WatchService` do Java NIO para detectar mudanças
3. **Notificação**: Executa callback quando arquivo é modificado
4. **Recarregamento**: Metadados são recarregados automaticamente
5. **Aviso**: Usuário é notificado sobre necessidade de reinicialização

### **Exemplo de Uso**

```java
@Autowired
private FileWatcherService fileWatcherService;

// Inicia monitoramento
fileWatcherService.startWatching(
    configDirectory, 
    "app-metadata.json", 
    this::handleMetadataChange
);

// Callback executado quando arquivo é alterado
private void handleMetadataChange(Path changedFile) {
    log.warn("⚠️ ALTERAÇÃO DETECTADA! Reinicialização necessária.");
    // Notificar usuário
}
```

## 🛡️ Validação e Fallbacks

### **Estratégias de Fallback**

1. **Tipo de Card Padrão**:
   - Primeiro: Usa ID dos metadados
   - Segundo: Primeiro tipo disponível no sistema
   - Terceiro: Cria tipo básico "Tarefa"

2. **Tipo de Progresso Padrão**:
   - Primeiro: Usa valor dos metadados
   - Segundo: `ProgressType.NONE`

3. **Configurações Gerais**:
   - Primeiro: Arquivo de metadados
   - Segundo: Valores padrão hardcoded
   - Terceiro: Configurações mínimas de segurança

### **Validação Automática**

```java
// Valida configurações ao inicializar
boolean isValid = defaultConfigService.validateDefaultConfigurations();

if (!isValid) {
    log.error("Configurações inválidas detectadas!");
    // Aplicar fallbacks
}
```

## 📊 Benefícios do Sistema

### **✅ Vantagens**

- **Flexibilidade**: Configurações podem ser alteradas sem recompilação
- **Manutenibilidade**: Configurações centralizadas em um local
- **Monitoramento**: Detecta alterações automaticamente
- **Fallbacks**: Sistema robusto com múltiplas estratégias de recuperação
- **Extensibilidade**: Fácil adicionar novas configurações
- **Validação**: Verificação automática de integridade

### **🔧 Casos de Uso**

1. **Desenvolvimento**: Ajustar configurações durante desenvolvimento
2. **Produção**: Personalizar comportamento sem redeploy
3. **Usuários Finais**: Permitir personalização da interface
4. **DevOps**: Configurar ambientes diferentes
5. **Testes**: Ajustar configurações para diferentes cenários

## 🚨 Considerações Importantes

### **⚠️ Limitações**

- **Reinicialização**: Algumas mudanças requerem reinicialização da aplicação
- **Validação**: Configurações inválidas podem causar problemas
- **Performance**: Monitoramento de arquivo consome recursos
- **Segurança**: Arquivo de configuração deve ser protegido

### **🔒 Recomendações de Segurança**

1. **Permissões**: Restringir acesso ao arquivo de configuração
2. **Backup**: Manter backup das configurações válidas
3. **Validação**: Sempre validar configurações antes de aplicar
4. **Logs**: Registrar todas as alterações de configuração

### **📝 Boas Práticas**

1. **Versionamento**: Sempre incluir `metadataVersion`
2. **Documentação**: Comentar configurações complexas
3. **Testes**: Testar configurações em ambiente de desenvolvimento
4. **Backup**: Fazer backup antes de alterações significativas

## 🔮 Próximos Passos

### **Funcionalidades Planejadas**

1. **Configuração por Perfil**: Diferentes configurações para diferentes usuários
2. **Validação Avançada**: Regras de validação customizáveis
3. **Sincronização**: Sincronizar configurações entre dispositivos
4. **Templates**: Templates de configuração pré-definidos
5. **Importação/Exportação**: Backup e restauração de configurações

### **Melhorias Técnicas**

1. **Cache Inteligente**: Cache com invalidação automática
2. **Validação em Tempo Real**: Validação enquanto usuário digita
3. **Histórico**: Histórico de alterações de configuração
4. **Rollback**: Reverter para configuração anterior
5. **API REST**: Endpoints para gerenciar configurações

## 📚 Referências

- **Java NIO WatchService**: [Documentação Oracle](https://docs.oracle.com/javase/8/docs/api/java/nio/file/WatchService.html)
- **Spring Boot Configuration**: [Documentação Spring](https://spring.io/projects/spring-boot)
- **JSON Schema**: [json-schema.org](https://json-schema.org/)
- **Design Patterns**: Strategy Pattern e Observer Pattern

---

**Desenvolvido por**: Aú Desviante - Lucas Godoy  
**Versão**: 1.0  
**Data**: 2024  
**Licença**: MIT
