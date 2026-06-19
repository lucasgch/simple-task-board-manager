# Script para testar a correção no ambiente de produção
Write-Host "🧪 TESTE DE CORREÇÃO - AMBIENTE DE PRODUÇÃO" -ForegroundColor Green
Write-Host "===============================================" -ForegroundColor Green
Write-Host ""

# Limpar ambiente anterior
Write-Host "🧹 Limpando ambiente anterior..." -ForegroundColor Yellow
if (Test-Path "C:\Users\Lucas\MyBoards") {
    Remove-Item -Path "C:\Users\Lucas\MyBoards" -Recurse -Force
    Write-Host "✅ Ambiente anterior removido" -ForegroundColor Green
}

# Parar processos existentes
Write-Host "🛑 Parando processos existentes..." -ForegroundColor Yellow
Get-Process -Name "SimpleTaskBoardManager" -ErrorAction SilentlyContinue | Stop-Process -Force
Get-Process -Name "java" -ErrorAction SilentlyContinue | Where-Object {$_.MainWindowTitle -like "*MyBoards*"} | Stop-Process -Force

# Verificar se o instalador existe
$installerPath = "build\dist\SimpleTaskBoardManager-1.3.8.exe"
if (-not (Test-Path $installerPath)) {
    Write-Host "❌ ERRO: Instalador não encontrado em $installerPath" -ForegroundColor Red
    exit 1
}

Write-Host "✅ Instalador encontrado: $installerPath" -ForegroundColor Green
Write-Host "📦 Tamanho: $((Get-Item $installerPath).Length / 1MB) MB" -ForegroundColor Cyan

Write-Host ""
Write-Host "🚀 INSTRUÇÕES PARA O TESTE:" -ForegroundColor Cyan
Write-Host "1. Execute o instalador: $installerPath" -ForegroundColor White
Write-Host "2. Instale o aplicativo" -ForegroundColor White
Write-Host "3. Execute o aplicativo instalado" -ForegroundColor White
Write-Host "4. Crie um board e um card" -ForegroundColor White
Write-Host "5. Tente definir uma data de agendamento" -ForegroundColor White
Write-Host "6. Verifique se NÃO aparece o erro de rollback" -ForegroundColor White
Write-Host ""
Write-Host "📊 MONITORAMENTO:" -ForegroundColor Cyan
Write-Host "Os logs serão salvos em: C:\Users\Lucas\MyBoards\card_repository_debug.log" -ForegroundColor White
Write-Host ""

# Monitorar criação do diretório MyBoards
Write-Host "👀 Aguardando criação do diretório MyBoards..." -ForegroundColor Yellow
$timeout = 300 # 5 minutos
$elapsed = 0
while (-not (Test-Path "C:\Users\Lucas\MyBoards") -and $elapsed -lt $timeout) {
    Start-Sleep -Seconds 2
    $elapsed += 2
    Write-Host "." -NoNewline -ForegroundColor Yellow
}

if (Test-Path "C:\Users\Lucas\MyBoards") {
    Write-Host ""
    Write-Host "✅ Diretório MyBoards criado!" -ForegroundColor Green
    Write-Host "📁 Conteúdo:" -ForegroundColor Cyan
    Get-ChildItem -Path "C:\Users\Lucas\MyBoards" | Format-Table Name, LastWriteTime, Length
} else {
    Write-Host ""
    Write-Host "⏰ Timeout aguardando criação do diretório" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "🎯 TESTE CONCLUÍDO!" -ForegroundColor Green
Write-Host "Verifique se o erro de rollback foi corrigido!" -ForegroundColor White
