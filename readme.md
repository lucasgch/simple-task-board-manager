# Simple Task Board Manager

<p align="center">
  <a href="https://github.com/lucasgch/simple-task-board-manager" target="_blank">
    <img src=".github/preview.jpg" width="100%" alt="Simple Task Board Manager">
  </a>
</p>

<p align="center">
Aplicação desktop simples e eficiente para gerenciamento de tarefas<br/>
Desenvolvido para manter você focado no que realmente importa: **resolver suas tarefas**
</p>

<p align="center">
  <a href="#diferencial">Diferencial</a>&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;
  <a href="#tecnologias">Tecnologias</a>&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;
  <a href="#funcionalidades">Funcionalidades</a>&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;
  <a href="#sincronizacao">Sincronização</a>&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;
  <a href="#arquitetura">Arquitetura</a>&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;
  <a href="#testes">Testes</a>&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;
  <a href="#changelog">Changelog</a>
</p>

## <div id="diferencial">🎯 Nosso Diferencial: Simplicidade que Foca na Produtividade</div>

Enquanto outras ferramentas de gerenciamento de tarefas oferecem inúmeras funcionalidades e configurações complexas, o **Simple Task Board Manager** foi desenvolvido com um propósito claro: **manter o foco na resolução de tarefas, não no uso da ferramenta**.

### Por que escolher nossa solução?

- **⚡ Zero Configuração**: Instale e use imediatamente - sem setups complexos
- **🎯 Interface Minimalista**: Apenas o essencial para gerenciar tarefas eficientemente
- **⏱️ Economia de Tempo**: Menos tempo configurando, mais tempo produzindo
- **🧠 Foco na Tarefa**: Interface limpa que não distrai do objetivo principal
- **💾 Persistência Local**: Seus dados ficam seguros no seu computador

> *"A melhor ferramenta é aquela que você esquece que está usando"*

## <div id="tecnologias">🚀 Tecnologias</div>

Este projeto foi desenvolvido com tecnologias modernas e estáveis:

- **☕ Java 25** - Linguagem principal com recursos modernos
- **🍃 Spring Boot 3.5.2** - Framework para injeção de dependências e configuração
- **💾 H2 Database 2.3.232** - Banco de dados local em arquivo (`~/myboards`)
- **🖥️ JavaFX 25.0.3 + CalendarFX** - Interface gráfica moderna, com calendário integrado
- **🔧 Gradle 9.6.0** - Sistema de build e gerenciamento de dependências
- **📊 Micrometer 1.15.2** - Observabilidade e métricas
- **🧪 JUnit 5 + Mockito** - Testes unitários e de integração
- **🔌 Google Tasks API** - Integração com Google Tasks

## <div id="funcionalidades">✨ Funcionalidades</div>

### Interface Intuitiva
- **📋 Boards Visuais**: Organize suas tarefas em colunas visuais (A Fazer, Em Andamento, Concluído)
- **🎴 Cards Interativos**: Crie e edite cards com duplo clique (edição in-place)
- **🔄 Drag & Drop**: Mova tarefas entre colunas com arrastar e soltar
- **📊 Progresso Visual**: Acompanhe o progresso com percentuais automáticos

### Produtividade
- **⚡ Início Rápido**: Sem configurações complexas - abra e use
- **💾 Persistência Automática**: Dados salvos automaticamente no banco local
- **🎨 Interface Limpa**: Design minimalista que não distrai
- **📱 Responsivo**: Interface adaptável a diferentes tamanhos de tela

### Recursos Avançados
- **🔍 Busca e Filtros**: Filtre boards por grupo e por status
- **📅 Datas e Prazos**: Agendamento, vencimento e calendário integrado
- **🗂️ Grupos e Tipos de Card**: Organize boards em grupos e personalize tipos de card com unidades de progresso próprias
- **🔌 Google Tasks**: Envie tarefas para o Google Tasks
- **☁️ Sincronização entre Dispositivos**: Use seus boards em vários computadores via pasta de nuvem ([detalhes](#sincronizacao))

## <div id="sincronizacao">☁️ Sincronização entre Dispositivos</div>

Use o mesmo board em vários computadores através de uma pasta sincronizada por
**Dropbox, Google Drive ou OneDrive** — sem depender das APIs dos provedores.

### Como ativar

1. Abra **Preferências** e habilite a sincronização;
2. Escolha uma pasta sincronizada pelo seu provedor de nuvem (em modo espelho /
   "sempre manter neste dispositivo");
3. Escolha o modo: **Manual** (botão ☁ Sincronizar na barra de ferramentas) ou
   **Automático ao abrir e fechar**.

Os dados são publicados como snapshot na subpasta `SimpleTaskBoard/` da pasta
escolhida. Ao abrir o aplicativo, dados mais novos da nuvem são importados
automaticamente — com backup local prévio.

### Segurança dos seus dados

- **O banco em uso nunca fica na pasta de nuvem**: o que é sincronizado é um
  snapshot consistente, validado por hash SHA-256 contra downloads parciais;
- **Conflitos nunca são resolvidos silenciosamente**: se dois computadores
  alteraram os dados ao mesmo tempo, um diálogo oferece três opções seguras —
  manter os dados do computador (a versão da nuvem é arquivada), usar os dados
  da nuvem (com backup local) ou decidir depois;
- **Backups automáticos**: backup físico do banco antes de cada importação e
  histórico das últimas gerações de snapshot na nuvem;
- **Aviso em tempo real**: se outro computador enviar dados enquanto o app está
  aberto, você é avisado de que a importação ocorre na próxima abertura.

**📖 [Arquitetura da sincronização](docs/arquitetura/PLANO_SINCRONIZACAO_NUVEM.md)**

## <div id="arquitetura">🏗️ Arquitetura</div>

O projeto evoluiu de uma arquitetura simples para uma solução robusta e escalável, mantendo sempre o foco na simplicidade de uso.

### Evolução da Arquitetura

#### **Fase 1: Fundação Sólida**
- **HibernateJDBC → JPA**: Estrutura de banco de dados simplificada

#### **Fase 2: Spring Boot Integration**
- **Injeção de Dependência**: Spring gerencia o ciclo de vida dos componentes
- **Arquitetura Modular**: Componentes desacoplados e reutilizáveis
- **Configuração Automática**: Spring Boot configura automaticamente o ambiente

#### **Fase 3: Interface Moderna**
- **JavaFX Components**: Interface baseada em componentes FXML reutilizáveis
- **Padrão Observer**: Comunicação desacoplada entre componentes via callbacks
- **UX Aprimorada**: Edição in-place, drag & drop, feedback visual

#### **Fase 4: Atualização para o Java 25**
- A atualização do Java 21 para o Java 25 gerou um desafio técnico interessante, além de atualizar versão da JDK utilizada no projeto, foi necessário atualizar a versão das bibliotecas utilizadas, tais como:
- JavaFx atualizada para versão 25.0.3.
- Gradle atualizado para versão 9.6.0.
- Atualizada a versão do shadow e do lombok.

#### **Fase 5: Sincronização entre Dispositivos**
- **Snapshots consistentes**: export online-safe via `SCRIPT TO` do H2 — o banco em uso nunca é copiado
- **Detecção de conflitos sem timestamps**: contador de geração monotônico + hashes SHA-256, imune a diferenças de relógio entre máquinas
- **Escrita atômica na nuvem**: arquivo temporário + `ATOMIC_MOVE`, o cliente de nuvem nunca vê dados parciais
- **Import seguro no startup**: único momento com o banco fechado, com validação de hash e backup físico prévio

### Benefícios da Arquitetura Atual

- **🔧 Manutenibilidade**: Código organizado e fácil de manter
- **🧪 Testabilidade**: Arquitetura que facilita testes unitários e de integração
- **📈 Escalabilidade**: Fácil adição de novas funcionalidades
- **🛡️ Robustez**: Tratamento de erros e validações adequadas

## <div id="testes">🧪 Testes</div>

Para garantir qualidade e estabilidade, o projeto conta com uma suíte abrangente de testes:

### **Testes Unitários**
- **Services**: Validação da lógica de negócio isolada
- **Repositories**: Testes de acesso a dados
- **Validators**: Verificação de regras de validação

### **Testes de Integração**
- **Spring Context**: Testes que carregam o contexto completo
- **Database Integration**: Validação da persistência com H2
- **API Integration**: Testes de integração com Google Tasks API
- **Sincronização**: Round-trip export → wipe → import com contagem de linhas em todas as tabelas, conflitos, resoluções e restore entre versões de schema

### **Cobertura de Testes**
- **JUnit 5**: Framework moderno de testes
- **Mockito**: Mocking de dependências
- **Spring Boot Test**: Utilitários para testes de integração

## 🚀 Como Usar

1. **Download**: Baixe o instalador na [página de releases](https://github.com/lucasgch/simple-task-board-manager/releases)
2. **Instale**: Execute o instalador e siga as instruções
3. **Execute**: Abra o aplicativo e comece a usar imediatamente
4. **Produza**: Foque nas suas tarefas, não na ferramenta

## 💾 Preservação de Dados

O sistema garante que seus dados sejam preservados durante atualizações:

### **Backup Automático**
- Backup completo e transacionalmente consistente via `SCRIPT TO` do H2 (`.sql.gz`)
- Backup físico automático do banco antes de cada importação de sincronização
- Backups salvos em `~/myboards/backups/`, com política de retenção
- Scripts manuais de backup para Linux/Mac e Windows

### **Migrações Automáticas**
- Migrações idempotentes executadas no início da aplicação preservam os dados existentes durante atualizações
- Verificação de integridade automática na inicialização

### **Processo de Atualização Segura**
```bash
# 1. Backup (antes da atualização)
./scripts/backup-database.sh

# 2. Instalar nova versão
# O instalador preserva ~/myboards/

# 3. Verificar integridade
./scripts/check-database.sh
```

**📖 [Guia Completo de Atualização](docs/instalacao/ATUALIZACAO_BANCO_DADOS.md)**

## 📦 Instalação

### Gerar instaladores
```bash
# Windows
./gradlew jpackage

# Linux
./gradlew jpackageLinux      # AppImage
./gradlew jpackageLinuxDeb   # pacote .deb
```

### Desenvolvimento
```bash
# Clone o repositório
git clone https://github.com/lucasgch/simple-task-board-manager.git

# Compile e execute
./gradlew bootRun
```

## <div id="changelog">📋 Changelog</div>

### [Não lançado]

#### ☁️ Sincronização entre Dispositivos via Pasta de Nuvem
- **Snapshots na nuvem**: export/import do banco via pasta sincronizada por Dropbox, Google Drive ou OneDrive, sem APIs dos provedores
- **Modo manual ou automático**: botão ☁ Sincronizar na barra de ferramentas, ou export ao fechar e import ao abrir
- **Resolução de conflitos**: diálogo com três opções seguras — nada é apagado (versão preterida arquivada na nuvem ou em backup local)
- **Proteções**: validação por hash SHA-256 contra downloads parciais, backup físico antes de cada import, histórico de gerações na nuvem e detecção de cópias em conflito criadas pelos provedores
- **Aviso em tempo real**: notificação quando outro computador envia dados enquanto o app está aberto

### [v1.1.0] - 2025-01-08

#### ✨ Melhorias na Interface
- **Controles de Movimentação**: Adicionados botões ↑/↓ para mover cards dentro da mesma coluna
- **Posicionamento Otimizado**: Controles de movimentação posicionados no canto superior direito do card
- **Feedback Visual**: Remoção de alertas redundantes - interface atualizada automaticamente após movimentação
- **Tooltips Informativos**: Dicas visuais nos botões de movimentação

#### 🔧 Correções Técnicas
- **Validação de Dados**: Corrigido erro de validação que impedia atualização da interface
- **Atualização em Tempo Real**: Interface agora atualiza automaticamente após movimentação de cards
- **Lógica de Movimentação**: Corrigida query SQL para encontrar cards adjacentes corretamente
- **Comunicação entre Controllers**: Melhorada notificação entre CardViewController e BoardViewController

#### 🎨 Melhorias de UX
- **Interface Mais Limpa**: Removidos alertas desnecessários para movimentação bem-sucedida
- **Feedback Imediato**: Usuário vê mudanças instantaneamente na interface
- **Experiência Fluida**: Movimentação de cards sem interrupções na interface

### [v1.0.9] - 2025-01-07

#### ✨ Novas Funcionalidades
- **Sistema de Progresso**: Cards agora exibem progresso visual com percentuais
- **Controles de Edição**: Interface de edição in-place com spinners para progresso
- **Validação em Tempo Real**: Validação automática de valores de progresso
- **Status Dinâmico**: Status do card baseado na coluna atual

#### 🔧 Melhorias Técnicas
- **Arquitetura Modular**: Componentes reutilizáveis e desacoplados
- **Persistência Robusta**: Sistema de backup e migração de dados
- **Interface Responsiva**: Adaptação a diferentes tamanhos de tela

### [v1.0.8] - 2025-01-06

#### 🎉 Lançamento Inicial
- **Interface Kanban**: Boards visuais com colunas organizacionais
- **Drag & Drop**: Movimentação intuitiva de cards entre colunas
- **Persistência Local**: Banco de dados H2 para armazenamento local
- **Arquitetura Spring Boot**: Base sólida para desenvolvimento futuro

## 🤝 Contribuindo

Contribuições são bem-vindas! Mantenha sempre o foco na **simplicidade e produtividade do usuário**.

## 📄 Licença

Este projeto está sob a licença GNU General Public License v3.0. Veja o arquivo [LICENSE](LICENSE) para mais detalhes.

---

**Desenvolvido com ❤️ para manter você focado no que realmente importa: suas tarefas.**
