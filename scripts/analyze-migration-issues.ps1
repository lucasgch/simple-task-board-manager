# Script para analisar problemas de migração
# Verifica inconsistências entre schemas e identifica possíveis problemas

Write-Host "🔍 Analisando problemas de migração..." -ForegroundColor Green
Write-Host ""

# Verificar se há problemas de sintaxe nos scripts de migração
Write-Host "📋 Verificando scripts de migração..." -ForegroundColor Yellow

$migrationFiles = Get-ChildItem "src\main\resources\db\changelog\migrations\*.sql" | Sort-Object Name

Write-Host "Scripts de migração encontrados:" -ForegroundColor Cyan
$migrationFiles | ForEach-Object { Write-Host "  - $($_.Name)" -ForegroundColor White }

Write-Host ""

# Verificar problemas específicos nos scripts
Write-Host "🔍 Verificando problemas de sintaxe H2..." -ForegroundColor Yellow

$problematicPatterns = @(
    @{ Pattern = "ON UPDATE CURRENT_TIMESTAMP"; Description = "Sintaxe não suportada pelo H2" },
    @{ Pattern = "CREATE INDEX IF NOT EXISTS"; Description = "Sintaxe problemática no H2" },
    @{ Pattern = "DROP TABLE IF EXISTS"; Description = "Sintaxe problemática no H2" },
    @{ Pattern = "CONSTRAINT.*FOREIGN KEY"; Description = "Constraints nomeadas podem causar problemas" },
    @{ Pattern = "COMMENT ON"; Description = "Comentários não suportados pelo H2" }
)

foreach ($file in $migrationFiles) {
    $content = Get-Content $file.FullName -Raw
    $hasProblems = $false
    
    Write-Host "Verificando $($file.Name)..." -ForegroundColor Cyan
    
    foreach ($pattern in $problematicPatterns) {
        if ($content -match $pattern.Pattern) {
            Write-Host "  ❌ $($pattern.Description): $($pattern.Pattern)" -ForegroundColor Red
            $hasProblems = $true
        }
    }
    
    if (-not $hasProblems) {
        Write-Host "  ✅ Nenhum problema encontrado" -ForegroundColor Green
    }
    
    Write-Host ""
}

# Verificar inconsistências entre schema.sql e test-schema.sql
Write-Host "🔍 Verificando inconsistências entre schemas..." -ForegroundColor Yellow

$productionSchema = Get-Content "src\main\resources\schema.sql" -Raw
$testSchema = Get-Content "src\test\resources\test-schema.sql" -Raw

# Verificar se as colunas scheduled_date e due_date existem em ambos
if ($productionSchema -match "scheduled_date" -and $testSchema -match "scheduled_date") {
    Write-Host "✅ Coluna scheduled_date presente em ambos os schemas" -ForegroundColor Green
} else {
    Write-Host "❌ Coluna scheduled_date inconsistente entre schemas" -ForegroundColor Red
}

if ($productionSchema -match "due_date" -and $testSchema -match "due_date") {
    Write-Host "✅ Coluna due_date presente em ambos os schemas" -ForegroundColor Green
} else {
    Write-Host "❌ Coluna due_date inconsistente entre schemas" -ForegroundColor Red
}

# Verificar se a tabela integration_sync_status está definida
if ($productionSchema -match "integration_sync_status") {
    Write-Host "✅ Tabela integration_sync_status presente no schema de produção" -ForegroundColor Green
} else {
    Write-Host "❌ Tabela integration_sync_status AUSENTE no schema de produção" -ForegroundColor Red
    Write-Host "  Esta tabela é necessária para o funcionamento das integrações!" -ForegroundColor Yellow
}

if ($testSchema -match "integration_sync_status") {
    Write-Host "✅ Tabela integration_sync_status presente no schema de teste" -ForegroundColor Green
} else {
    Write-Host "❌ Tabela integration_sync_status AUSENTE no schema de teste" -ForegroundColor Red
}

Write-Host ""

# Verificar se há migrações duplicadas ou conflitantes
Write-Host "🔍 Verificando migrações duplicadas..." -ForegroundColor Yellow

$migrationNames = $migrationFiles | ForEach-Object { $_.BaseName }
$duplicates = $migrationNames | Group-Object | Where-Object { $_.Count -gt 1 }

if ($duplicates) {
    Write-Host "❌ Migrações duplicadas encontradas:" -ForegroundColor Red
    $duplicates | ForEach-Object { Write-Host "  - $($_.Name)" -ForegroundColor Red }
} else {
    Write-Host "✅ Nenhuma migração duplicada encontrada" -ForegroundColor Green
}

Write-Host ""

# Verificar se há migrações com datas inconsistentes
Write-Host "🔍 Verificando datas das migrações..." -ForegroundColor Yellow

$migrationDates = $migrationFiles | ForEach-Object { 
    if ($_.Name -match "(\d{8})") {
        [PSCustomObject]@{
            Name = $_.Name
            Date = [DateTime]::ParseExact($matches[1], "yyyyMMdd", $null)
        }
    }
} | Sort-Object Date

$outOfOrder = @()
for ($i = 1; $i -lt $migrationDates.Count; $i++) {
    if ($migrationDates[$i].Date -lt $migrationDates[$i-1].Date) {
        $outOfOrder += "$($migrationDates[$i].Name) (antes de $($migrationDates[$i-1].Name))"
    }
}

if ($outOfOrder) {
    Write-Host "❌ Migrações fora de ordem cronológica:" -ForegroundColor Red
    $outOfOrder | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
} else {
    Write-Host "✅ Migrações em ordem cronológica correta" -ForegroundColor Green
}

Write-Host ""

# Resumo e recomendações
Write-Host "📋 RESUMO E RECOMENDAÇÕES:" -ForegroundColor Green
Write-Host ""

Write-Host "1. Verifique se a tabela integration_sync_status está sendo criada corretamente" -ForegroundColor Yellow
Write-Host "2. Execute as migrações em ordem cronológica" -ForegroundColor Yellow
Write-Host "3. Teste as migrações em ambiente de desenvolvimento antes de produção" -ForegroundColor Yellow
Write-Host "4. Considere usar Liquibase ou Flyway para gerenciar migrações" -ForegroundColor Yellow

Write-Host ""
Write-Host "🎯 Análise concluída!" -ForegroundColor Green
