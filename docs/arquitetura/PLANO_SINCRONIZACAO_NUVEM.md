# Plano de Implementação — Sincronização entre Dispositivos via Pasta de Nuvem

> Sincronização de dados entre múltiplos dispositivos do mesmo usuário usando pastas locais
> sincronizadas por Dropbox, Google Drive ou OneDrive — **sem API dos provedores**, apenas
> manipulação de arquivos. Documento gerado em 2026-07-18 a partir da análise da base de código.

## 1. Contexto e diagnóstico do código atual

| Aspecto | Situação atual |
|---|---|
| Banco | H2 2.3.232, arquivo `~/myboards/board_h2_db.mv.db` |
| Datasource | HikariCP em `DataConfig.java:65`, URL `jdbc:h2:file:...;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1` |
| Lifecycle | Spring sobe em `SimpleTaskBoardManagerApplication.main()` **antes** do JavaFX; `MainApp.stop()` fecha o contexto (e o pool) |
| Preferências | `AppMetadataConfig` persiste JSON em `~/myboards/config/app-metadata.json`; UI em `PreferencesController` + `preferences.fxml`; eventos via `PreferencesUpdatedEvent` |
| Reutilizáveis | `DatabaseIntegrityChecker` (validação pós-import), `FileWatcherService` (WatchService NIO) |

### Riscos identificados (motivam as decisões abaixo)

1. **`DB_CLOSE_DELAY=-1`**: o engine H2 mantém o arquivo aberto até a JVM morrer, mesmo após o
   pool fechar. Copiar o `.mv.db` em `MainApp.stop()` copiaria um arquivo em uso → corrupção.
2. **`AUTO_SERVER=TRUE`**: outro processo pode conectar no mesmo arquivo; nenhuma suposição de
   "banco parado" é válida. O `.lock.db` contém IP/porta e jamais pode ir para a pasta de nuvem.
3. **MVStore escreve em background** (~500ms de write delay + compactação): snapshot por cópia
   de filesystem com engine viva pode capturar chunks parciais.
4. **Config de datasource duplicada e divergente**: `application.properties` usa
   `MYBOARDUSER/MYBOARDPASS`; `DataConfig` usa `myboarduser/myboardpassword`. Hoje o bean vence,
   mas é frágil — unificar antes de mexer em sync.
5. **`DatabaseBackupService` é incompleto**: dump manual de INSERTs que **não cobre `TASKS`**
   (tabela obrigatória em `DataConfig`) **nem as tabelas do sistema de Fields**. Um restore
   perderia dados. Corrigir/aposentar independentemente do sync.

### Princípios de design (derivados da pesquisa 2025–2026)

- **O banco vivo nunca fica na pasta sincronizada.** O que vai para a nuvem é um snapshot
  exportado atomicamente + manifest (padrão consolidado; comunidade H2 e modelo KeePass).
- **Export online-safe**: `SCRIPT TO` / `BACKUP TO` são transacionalmente consistentes com o
  banco aberto — elimina o "shutdown forçado" da proposta original.
- **Sem timestamp como detector de conflito** (clock skew): usar contador de geração monotônico
  + deviceId + hash SHA-256 num manifest JSON.
- **Sem lock distribuído via pasta de nuvem** (propagação lenta torna o lock inútil): detectar e
  resolver conflitos, não tentar preveni-los.
- **Escrita atômica**: temp file na mesma pasta + `Files.move(..., ATOMIC_MOVE)`.
- **Placeholders online-only** (OneDrive Files On-Demand, Dropbox online-only, Drive "stream"):
  validar hash do snapshot contra o manifest antes de importar; documentar que a pasta deve estar
  em modo espelho / "sempre manter neste dispositivo". No macOS, Dropbox agora vive em
  `~/Library/CloudStorage/...` (File Provider) — nunca hardcodar caminhos.

## 2. Arquitetura

```
~/myboards/board_h2_db.mv.db          ← banco vivo, NUNCA na pasta de nuvem
<pasta-nuvem>/SimpleTaskBoard/
  ├── boards-snapshot.sql.gz          ← export (SCRIPT TO comprimido), escrito via temp + ATOMIC_MOVE
  └── sync-manifest.json              ← { deviceId, generation, sha256, schemaVersion, appVersion, exportedAt }
~/myboards/sync-state.json            ← estado local: última geração exportada/importada, dirty flag
~/myboards/backups/                   ← backups físicos pré-import (retenção N)
```

### Classes novas (pacote sugerido: `org.desviante.sync`)

| Classe | Responsabilidade |
|---|---|
| `SyncManifest` (record/DTO) | Serialização do manifest JSON da nuvem |
| `SyncStateRepository` | Lê/escreve `sync-state.json` local e o manifest remoto |
| `SnapshotExportService` | `SCRIPT TO` em arquivo temp → hash SHA-256 → `ATOMIC_MOVE` para a pasta de nuvem → atualiza manifest |
| `SnapshotImportService` | Valida hash vs manifest → backup físico do `.mv.db` → restore via `RUNSCRIPT` em banco recriado → `DatabaseIntegrityChecker` |
| `ConflictDetector` | Matriz: geração remota × dirty local → `UP_TO_DATE` / `PULL` / `PUSH` / `CONFLICT` |
| `BackupManager` | Backups físicos pré-import + política de retenção |

### Pontos de integração no código existente

- **Import no startup**: no `main()` de `SimpleTaskBoardManagerApplication`, **antes** de
  `SpringApplicationBuilder.run()` — único momento em que o banco está garantidamente fechado.
  Se `CONFLICT`, guardar a decisão e perguntar via diálogo após o start da UI.
- **Export no fechamento**: em `MainApp.stop()`, **antes** de `springContext.close()`
  (export é online-safe, não precisa derrubar o pool).
- **Preferências**: novos campos em `AppMetadata` — `syncEnabled`, `syncFolderPath`,
  `syncDeviceId` (UUID gerado na primeira ativação), `syncMode` (`MANUAL` | `ON_OPEN_CLOSE`).
- **UI**: seção nova em `preferences.fxml` (toggle + `DirectoryChooser` + modo); botão
  "Sincronizar agora" na toolbar do `BoardViewController` com indicador de status
  (✓ sincronizado / pendente / ⚠ conflito).
- **Dirty flag**: marcar `sync-state.json` como dirty em qualquer escrita no banco (hook nos
  services de escrita, ou comparação de hash do último export no fechamento).

### Dependências

Nenhuma nova: `MessageDigest` (SHA-256), NIO `WatchService`/`Files`, `java.util.zip`,
`SCRIPT TO ... COMPRESSION GZIP` do próprio H2.

## 3. Fases de entrega

### Fase 0 — Hardening (pré-requisito) ✅ concluída em 2026-07-18

1. ✅ Unificar configuração de datasource: `application.properties` é a fonte única
   (`spring.datasource.*`, credenciais `myboarduser` preservando bancos existentes);
   `DataConfig` injeta via `@Value` e deriva o caminho do arquivo da URL;
   `liquibase.properties` alinhado.
2. ✅ `DatabaseBackupService` reescrito com `SCRIPT TO ... COMPRESSION GZIP`
   (backup completo e transacionalmente consistente; o dump manual antigo não cobria
   `TASKS` nem as tabelas de Fields). Restore: `RUNSCRIPT FROM '...' COMPRESSION GZIP`.
3. ✅ Campos de sync no `AppMetadata` (`syncEnabled`, `syncFolderPath`, `syncDeviceId`,
   `syncMode` + enum `org.desviante.sync.SyncMode`), com migração automática de JSONs
   antigos em `AppMetadataConfig` e `@JsonIgnoreProperties(ignoreUnknown = true)` para
   tolerar campos de versões mais novas.

### Fase 1 — Export/import manual (MVP) ✅ concluída em 2026-07-18

1. ✅ `SyncManifest`, `SyncState`, `SyncStateRepository` (escritas atômicas via
   temp + `ATOMIC_MOVE`), `BackupManager` (backups físicos pré-import, retenção 5).
2. ✅ `SnapshotExportService` com lock local (`FileChannel.tryLock`), publicação
   atômica na subpasta `SimpleTaskBoard/` e manifest com dois hashes:
   `sha256` (arquivo .gz, integridade de transferência) e `contentSha256`
   (SQL descomprimido, detecção de dirty sem hooks nos services de escrita —
   o hash do conteúdo atual é comparado ao da última sincronização).
3. ✅ `SnapshotImportService` com hook no `main()` (após o pre-flight, antes do
   Spring), JDBC puro com URL sem `AUTO_SERVER`/`DB_CLOSE_DELAY`. Sequência:
   valida hash → backup físico → `RUNSCRIPT` em banco recriado → valida tabelas
   obrigatórias → re-script para registrar o `contentSha256` canônico (round-trip
   `RUNSCRIPT`+`SCRIPT` não é byte-idêntico). Falha restaura o backup; banco em
   uso por outro processo pula o import. Banco local inexistente + snapshot na
   nuvem = restore direto (caso fora da matriz geração×dirty).
4. ✅ `ConflictDetector` (matriz por geração monotônica, sem timestamps; geração
   remota regredida também é conflito). Em conflito nada é importado/exportado —
   apenas sinalizado (`pendingConflict` no sync-state + indicador ⚠ na UI).
5. ✅ UI: seção de sincronização em `preferences.fxml` (toggle + `DirectoryChooser`
   + modo, com deviceId UUID gerado na primeira ativação); botão "☁ Sincronizar"
   e label de status na toolbar do `BoardViewController` (sync roda em thread de
   background; status do import de startup refletido ao abrir).

   Testes: matriz completa do `ConflictDetector`; round-trip de serialização de
   manifest/estado (incl. corrompidos e campos desconhecidos); integração em H2
   temporário — export → wipe → import com contagem de linhas em **todas** as
   tabelas via `INFORMATION_SCHEMA`, hash divergente aborta sem tocar no banco,
   conflito sinalizado com banco intacto.

### Fase 2 — Automação e resolução de conflito ✅ concluída em 2026-07-18

1. ✅ Export automático em `MainApp.stop()` (antes de `springContext.close()`,
   pois `SCRIPT TO` é online-safe), condicionado a `syncMode == ON_OPEN_CLOSE`;
   a verificação/import automático ao abrir já existia desde a Fase 1 (roda
   para qualquer modo — é o único momento com o banco fechado).
2. ✅ Diálogo de conflito com 3 opções (aparece após o start da UI quando o
   startup detecta conflito, e também quando o botão manual retorna conflito):
   - **Manter os dados deste computador** — `resolveConflictKeepLocal()`:
     renomeia o snapshot remoto para `conflito-<data>-<device>.sql.gz` e
     publica o banco local como nova geração (nada é perdido);
   - **Usar os dados da nuvem** — `resolveConflictUseRemote()`: como import
     exige banco fechado, a decisão é persistida (`resolveWithRemote` no
     sync-state) e executada no próximo startup, com backup físico prévio e
     sondagem de banco-em-uso; o flag é limpo após o import bem-sucedido;
   - **Decidir depois** — nada muda, sync pausado, indicador ⚠.
3. ✅ `ConflictedCopyDetector`: varre a pasta de sync no startup por padrões
   dos provedores (`(conflicted copy`, `(cópia em conflito`, duplicatas
   ` (N).`), excluindo os arquivamentos intencionais `conflito-*.sql.gz`;
   a UI avisa e lista os arquivos para inspeção manual (o app nunca os apaga).

   Testes: resoluções keep-local (arquiva + avança geração) e use-remote
   (pull no startup seguinte apesar de dirty, com backup e limpeza do flag)
   em H2 temporário; padrões do detector de cópias em conflito; round-trip
   do novo campo de estado.

### Fase 3 — Polimento / v2

1. Watcher NIO na pasta de sync durante a execução: avisar "chegaram dados novos — importar
   exige reiniciar".
2. Histórico de N gerações de snapshots na nuvem.
3. Merge granular por entidade (estilo KeePass Synchronize) — somente se o conflito binário
   se mostrar doloroso na prática.

## 4. Testes

### Unitários
- Matriz completa do `ConflictDetector` (4 estados × cenários de clock skew).
- Round-trip de serialização do manifest e do sync-state.
- Atomicidade do export: simular falha entre escrita do temp e o `ATOMIC_MOVE`
  (nuvem nunca vê arquivo parcial).

### Integração (H2 temporário)
- Export → wipe → import → `DatabaseIntegrityChecker` + contagem de linhas em **todas** as
  tabelas (proteção permanente contra backup incompleto).
- Import com hash divergente do manifest (simula download parcial/placeholder) → aborta sem
  tocar no banco local.
- Restore sobre banco com schema mais antigo/mais novo (interação com migrações).

### Manuais (cenários de borda)
- Duas instâncias do app na mesma máquina (`AUTO_SERVER=TRUE`).
- Pasta OneDrive com arquivo online-only (placeholder não hidratado).
- Matar o app no meio do export.
- Cliente do provedor pausado no meio do upload.
- Dois PCs editando offline e reconectando → deve gerar diálogo de conflito,
  nunca perda silenciosa de dados.

## 5. Suposições assumidas

1. **Single-user, um app por vez por dispositivo** — objetivo é o mesmo usuário em várias
   máquinas, não colaboração multiusuário.
2. **Sem restart do datasource em runtime** — import só no startup; reiniciar o contexto Spring
   em execução seria outra frente de trabalho.
3. **Volume de dados pequeno** (< dezenas de MB) — snapshot completo por sync é viável.
4. `~/myboards/auth` (tokens Google) e logs **não** sincronizam — apenas o banco.

## 6. Perguntas em aberto (decisões pendentes)

1. **Formato do snapshot**: `SCRIPT TO` (SQL portável entre versões do H2 — recomendado) ou
   `BACKUP TO` (zip binário fiel, restore mais simples)?
2. **Conflito**: resolução binária com diálogo basta na v1, ou merge granular é requisito
   desde o início?
3. As **preferências** (`app-metadata.json`) também sincronizam entre dispositivos, ou só o banco?
4. **Auto-sync periódico** durante a execução (aumenta janela de conflito) ou somente
   abrir/fechar + botão manual?
5. Corrigir o `DatabaseBackupService` incompleto como item independente, antes do projeto de sync?

## 7. Referências

- [Baeldung — Export e backup de bancos H2](https://www.baeldung.com/java-h2-export-backup)
- [javathinking — Backup de H2 embarcado em execução](https://www.javathinking.com/blog/how-to-back-up-the-embedded-h2-database-engine-while-it-is-running/)
- [KeePass — Modelo de sincronização](https://keepass.info/help/v2/sync.html)
- [Ctrl blog — Conflitos de sync do KeePass](https://www.ctrl.blog/entry/keepass-file-conflicts-android.html)
- [Google Drive for desktop — stream vs mirror](https://support.google.com/drive/answer/13401938?hl=en)
- [Google Drive for desktop — release notes (differential upload)](https://knowledge.workspace.google.com/admin/drive/google-drive-for-desktop-release-notes)
- [Dropbox — File Provider no macOS](https://help.dropbox.com/installs/macos-support-for-expected-changes)
- [OneDrive Files On-Demand](https://www.multcloud.com/tutorials/onedrive-files-on-demand-2223-gc.html)
- [gmethvin/directory-watcher](https://github.com/gmethvin/directory-watcher) (opcional, v2)
- [Baeldung — WatchService vs Apache Commons IO Monitor](https://www.baeldung.com/java-watchservice-vs-apache-commons-io-monitor-library)
- [HowToGeek — riscos de sincronizar arquivos abertos](https://www.howtogeek.com/this-common-file-syncing-mistake-can-cost-you-your-data/)
- [Comunidade H2 — corrupção recorrente com arquivos sincronizados](https://h2-database.narkive.com/r8k8uuIv/h2-recurring-data-corruption-issues)
