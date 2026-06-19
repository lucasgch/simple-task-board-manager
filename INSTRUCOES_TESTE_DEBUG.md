# 🔍 Instruções para Teste e Debug

## 🎯 **Objetivo**
Identificar exatamente por que o grupo padrão "Trabalho" não está sendo carregado corretamente nas preferências.

## 📋 **Passos para Teste**

### 1. **Abrir Preferências**
1. Na aplicação rodando, **clique em "⚙️ Preferências"**
2. **Aguarde a janela de preferências abrir**
3. **Observe os logs no terminal** - deve aparecer algo como:
   ```
   DEBUG: currentDefaultBoardGroupId = Optional[3]
   DEBUG: Grupo padrão configurado encontrado: ID = 3
   DEBUG: Grupos disponíveis carregados: X grupos
   DEBUG: - Sem Grupo (ID: null)
   DEBUG: - Trabalho (ID: 3)
   DEBUG: - Livros (ID: 2)
   DEBUG: ComboBox populado com X itens
   DEBUG: Grupo padrão selecionado no ComboBox: Trabalho (ID: 3)
   ```

### 2. **Verificar Seleção Atual**
1. **Observe o campo "Grupo de Board Padrão"**
2. **Deve estar selecionado "Trabalho"** (não "Sem Grupo")
3. **Se estiver "Sem Grupo", verifique os logs** para entender por que

### 3. **Testar Salvamento**
1. **Clique em "Salvar Preferências"**
2. **Observe os logs** - deve aparecer:
   ```
   INFO: Preferências selecionadas - Tipo de Card: Card, Progresso: NONE, Grupo: Trabalho (ID: 3)
   ```
3. **Se aparecer "Sem Grupo (ID: null)", há um problema na seleção**

### 4. **Verificar Arquivo de Configuração**
1. **Abra o arquivo**: `%USERPROFILE%\myboards\config\app-metadata.json`
2. **Verifique o campo**: `"defaultBoardGroupId" : 3`
3. **Confirme que o valor está correto**

## 🚨 **Possíveis Problemas e Soluções**

### **Problema 1: ComboBox não está sendo populado corretamente**
**Sintomas**: Logs mostram 0 grupos ou grupos incorretos
**Solução**: Verificar se `boardGroupService.getAllBoardGroups()` está retornando dados

### **Problema 2: Valor padrão não está sendo definido**
**Sintomas**: Logs mostram grupo encontrado mas ComboBox não é atualizado
**Solução**: Verificar se há problema na atualização do ComboBox

### **Problema 3: Ordem de carregamento incorreta**
**Sintomas**: ComboBox é populado depois de tentar definir valor padrão
**Solução**: Reorganizar lógica de carregamento (já implementado)

### **Problema 4: Cache de dados**
**Sintomas**: Dados antigos sendo carregados
**Solução**: Verificar se há cache sendo usado incorretamente

## 📊 **Logs Esperados vs. Logs Reais**

### **✅ Logs Esperados (Funcionando)**
```
DEBUG: currentDefaultBoardGroupId = Optional[3]
DEBUG: Grupo padrão configurado encontrado: ID = 3
DEBUG: Grupos disponíveis carregados: 4 grupos
DEBUG: - Sem Grupo (ID: null)
DEBUG: - Trabalho (ID: 3)
DEBUG: - Livros (ID: 2)
DEBUG: - Pessoal (ID: 4)
DEBUG: ComboBox populado com 4 itens
DEBUG: Grupo padrão selecionado no ComboBox: Trabalho (ID: 3)
```

### **❌ Logs Problemáticos (Não Funcionando)**
```
DEBUG: currentDefaultBoardGroupId = Optional.empty
DEBUG: Nenhum grupo padrão configurado ou é null
```
**OU**
```
DEBUG: currentDefaultBoardGroupId = Optional[3]
DEBUG: Grupo padrão configurado encontrado: ID = 3
DEBUG: Grupo configurado não foi encontrado na lista! ID esperado: 3
```

## 🔧 **Comandos para Verificar**

### **Verificar Arquivo de Configuração**
```powershell
Get-Content $env:USERPROFILE\myboards\config\app-metadata.json
```

### **Verificar Processos Java**
```powershell
Get-Process | Where-Object {$_.ProcessName -like "*java*"}
```

### **Verificar Logs em Tempo Real**
```powershell
./gradlew run
```

## 📝 **Relatório de Teste**

Após executar o teste, informe:

1. **O que aparece no campo "Grupo de Board Padrão"** quando abre preferências?
2. **Quais logs aparecem no terminal** durante o carregamento?
3. **O que acontece ao salvar** as preferências?
4. **Qual valor está no arquivo** `app-metadata.json`?

## 🎯 **Resultado Esperado**

- **Campo deve mostrar "Trabalho"** (não "Sem Grupo")
- **Logs devem mostrar grupo encontrado e selecionado**
- **Salvamento deve registrar "Trabalho (ID: 3)"**
- **Arquivo deve manter `"defaultBoardGroupId" : 3`**

Com essas informações, poderemos identificar exatamente onde está o problema! 🔍
