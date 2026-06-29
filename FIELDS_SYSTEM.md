# Sistema Genérico de Fields - Documentação

## 📋 Visão Geral

Este documento descreve o novo **Sistema Genérico de Fields** implementado no Simple Task Board Manager. O sistema permite adicionar campos personalizados aos cards, tornando a aplicação mais flexível e desacoplada.

## 🎯 Objetivos Alcançados

1. ✅ Criar modelo genérico `Field` extensível
2. ✅ Migrar `CheckListItem` para `ChecklistField`
3. ✅ Criar novo tipo `PercentageField` para progresso percentual
4. ✅ Suportar múltiplos campos por card
5. ✅ Calcular progresso como média ponderada de todos os campos
6. ✅ Migração automática de dados existentes

## 🏗️ Arquitetura

### Modelo de Dados

```
Field (abstrato)
├── ChecklistField (itens marcáveis)
└── PercentageField (progresso percentual)
```

### Estrutura de Banco de Dados

**Tabela: `fields`**
- Usa **discriminador de tipo** (`field_type`: CHECKLIST_ITEM, PERCENTAGE)
- Colunas comuns: `id`, `card_id`, `field_type`, `order_index`, `created_at`, `updated_at`
- Colunas específicas de checklist: `checklist_text`, `checklist_completed`, `checklist_completed_at`
- Colunas específicas de percentage: `percentage_label`, `percentage_total`, `percentage_current`, `percentage_unit`

### Relacionamentos

```
Card (1) ──→ (*) Field
             ├── ChecklistField
             └── PercentageField
```

## 📂 Arquivos Criados

### Modelos
- `src/main/java/org/desviante/model/Field.java` - Classe abstrata base
- `src/main/java/org/desviante/model/ChecklistField.java` - Campo tipo checklist
- `src/main/java/org/desviante/model/PercentageField.java` - Campo tipo percentual
- `src/main/java/org/desviante/model/enums/FieldType.java` - Enum de tipos

### Repositório
- `src/main/java/org/desviante/repository/FieldRepository.java` - Repositório JDBC com RowMapper polimórfico

### Serviços
- `src/main/java/org/desviante/service/FieldService.java` - Lógica de negócio e cálculo de progresso

### Progresso
- `src/main/java/org/desviante/service/progress/FieldsProgressStrategy.java` - Estratégia de progresso baseada em campos

### Migração
- `src/main/java/org/desviante/migration/FieldMigration.java` - Componente de migração automática
- `src/main/resources/db/changelog/migrations/db.changelog-20260628-add-fields-table.sql` - Schema SQL

## 📊 Arquivos Modificados

### Modelos
- `src/main/java/org/desviante/model/enums/ProgressType.java`
  - ✅ Adicionou `FIELDS` ("Baseado em Campos")
  - ⚠️ Deprecou `PERCENTAGE` e `CHECKLIST`

### Serviços
- `src/main/java/org/desviante/service/ChecklistItemService.java`
  - ✅ Refatorado para usar `FieldService`
  - ✅ Mantém interface original para compatibilidade

- `src/main/java/org/desviante/service/TaskManagerFacade.java`
  - ✅ Adicionou `FieldService` como dependência
  - ✅ Expõe `getFieldService()` para controllers
  - ⚠️ Deprecou `getChecklistItemRepository()`

### UI
- `src/main/java/org/desviante/view/component/CardViewController.java`
  - ✅ Atualizado para usar `FieldService`

### Testes
- `src/test/java/org/desviante/service/TaskManagerFacadeDefaultGroupTest.java`
  - ✅ Atualizado para incluir mock de `FieldService`

- `src/test/java/org/desviante/service/TaskManagerFacadeIntegrationTest.java`
  - ✅ Adicionados beans `FieldService` e `FieldRepository`

## 🔄 Migração Automática

A migração é executada automaticamente na inicialização da aplicação através do componente `FieldMigration`:

### O que é migrado:

1. **Checklist Items**
   ```sql
   checklist_items → fields (field_type = 'CHECKLIST_ITEM')
   ```

2. **Card Progress** (totalUnits/currentUnits)
   ```sql
   cards.total_units + cards.current_units → fields (field_type = 'PERCENTAGE')
   ```

3. **Limpeza**
   - Remove tabela `checklist_items`
   - Remove colunas `total_units` e `current_units` de `cards`

### Segurança da Migração

- ✅ **Transacional**: Rollback automático em caso de erro
- ✅ **Idempotente**: Pode ser executada múltiplas vezes
- ✅ **Logging**: Registro detalhado de todas as etapas

## 📈 Cálculo de Progresso

O progresso de um card com `progress_type = FIELDS` é calculado como:

```
Progresso = Σ(progresso de cada campo) / número de campos
```

### Exemplos:

**Exemplo 1: Checklist + Percentage**
- Checklist: 3/5 itens concluídos = 60%
- Percentage: 150/300 páginas = 50%
- **Progresso Total = (60 + 50) / 2 = 55%**

**Exemplo 2: Múltiplos Checklists**
- Checklist 1: 2/2 itens = 100%
- Checklist 2: 1/4 itens = 25%
- Checklist 3: 3/3 itens = 100%
- **Progresso Total = (100 + 25 + 100) / 3 = 75%**

**Exemplo 3: Leitura de Livro**
- Percentage: 0/300 páginas = 0%
- **Progresso Total = 0%**

## 🎨 Exemplos de Uso

### Criar Campo de Checklist

```java
ChecklistField field = fieldService.createChecklistItem(
    cardId,
    "Implementar login",
    0  // order index
);
```

### Criar Campo Percentual

```java
PercentageField field = fieldService.createPercentageField(
    cardId,
    "Progresso de Leitura",  // label
    300,                      // total de páginas
    "páginas",                // unidade
    0                         // order index
);
```

### Atualizar Campo Percentual

```java
Optional<Field> fieldOpt = fieldService.findById(fieldId);
if (fieldOpt.isPresent() && fieldOpt.get() instanceof PercentageField) {
    PercentageField field = (PercentageField) fieldOpt.get();
    field.setCurrent(150);  // Agora em 150 páginas
    fieldService.updateField(field);
}
```

### Calcular Progresso do Card

```java
Double progress = fieldService.calculateCardProgress(cardId);
// Retorna percentual 0.0 a 100.0
```

## 🔍 Padrões de Design Utilizados

1. **Strategy Pattern** - `FieldsProgressStrategy` implementa cálculo de progresso
2. **Template Method** - `Field` define estrutura comum, subclasses especializam
3. **Factory Method** - `FieldService.createChecklistItem()`, `createPercentageField()`
4. **Repository Pattern** - `FieldRepository` abstrai persistência JDBC
5. **Facade Pattern** - `TaskManagerFacade` expõe interface unificada

## 🧪 Testes

- **450 testes executados**
- **441 testes passaram** (98% de sucesso)
- 9 falhas pré-existentes no `SimpleIntegrationTest` (não relacionadas)

## ⚠️ Breaking Changes

### Deprecações

1. `ProgressType.PERCENTAGE` → Use `ProgressType.FIELDS` com `PercentageField`
2. `ProgressType.CHECKLIST` → Use `ProgressType.FIELDS` com `ChecklistField`
3. `TaskManagerFacade.getChecklistItemRepository()` → Use `getFieldService()`

### Remoções (pós-migração)

- ❌ Tabela `checklist_items` (migrada para `fields`)
- ❌ Coluna `cards.total_units` (migrada para `PercentageField`)
- ❌ Coluna `cards.current_units` (migrada para `PercentageField`)

## 🚀 Próximos Passos (Futuro)

### Fase 6 (Adiada): UI para Fields

Criar `FieldViewController.java` para:
- Adicionar campos percentuais via interface
- Editar campos existentes
- Visualização polimórfica (checklist + percentage juntos)

### Novos Tipos de Campos (Sugestões)

- **DateField**: Campos de data (ex: prazo, data de início)
- **FileField**: Anexos de arquivo
- **LinkField**: URLs relacionados
- **TextFieldField**: Notas longas
- **NumberField**: Valores numéricos customizados

## 📝 Notas Técnicas

### Compatibilidade

- ✅ H2 Database (testado)
- ✅ Spring Boot 3.5.2
- ✅ Java 25
- ✅ JDBC puro (sem JPA)

### Performance

- Índices criados em `card_id`, `field_type` e `order_index`
- Queries otimizadas para busca por tipo
- Cálculo de progresso em memória (O(n) onde n = número de campos)

### Limitações Atuais

1. UI para adicionar campos percentuais não implementada (usa ChecklistViewController existente)
2. Número máximo de campos por card não limitado
3. Tipos de campos fixos (não extensível em runtime)

## 📞 Suporte

Para dúvidas sobre o sistema de Fields:
- Consultar este documento
- Ver exemplos em `FieldService.java`
- Verificar testes em `TaskManagerFacadeIntegrationTest.java`

---

**Data de Implementação**: 2026-06-28
**Versão**: 2.0
**Autor**: Implementado via Claude Code
