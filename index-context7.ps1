# Script para indexar o projeto no Context7
# Isso permite que a IA entenda melhor o código e forneça respostas mais precisas

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  Context7 - Indexação do Projeto QA Agent" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host ""

# Verifica se npx está disponível
try {
    $npxVersion = npx --version 2>$null
    Write-Host "✓ npx encontrado: $npxVersion" -ForegroundColor Green
} catch {
    Write-Host "✗ npx não encontrado. Instale o Node.js primeiro." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Iniciando indexação do projeto..." -ForegroundColor Yellow
Write-Host "Isso pode levar alguns minutos..." -ForegroundColor Gray
Write-Host ""

if (-not $env:CONTEXT7_API_KEY) {
    Write-Host "Aviso: CONTEXT7_API_KEY não está configurada. O ctx7 pode rodar com limites menores (ou exigir login)." -ForegroundColor Yellow
    Write-Host ""
}

# Executa o index
cd $PSScriptRoot
npx ctx7@latest index

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "==================================================" -ForegroundColor Green
    Write-Host "  ✓ Indexação concluída com sucesso!" -ForegroundColor Green
    Write-Host "==================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Agora a IA terá acesso a:" -ForegroundColor Cyan
    Write-Host "  • Estrutura completa do projeto" -ForegroundColor White
    Write-Host "  • Relações entre classes e métodos" -ForegroundColor White
    Write-Host "  • Contexto semântico do código" -ForegroundColor White
    Write-Host "  • Documentação e padrões do projeto" -ForegroundColor White
    Write-Host ""
    Write-Host "Para reindexar após mudanças significativas," -ForegroundColor Gray
    Write-Host "execute este script novamente." -ForegroundColor Gray
} else {
    Write-Host ""
    Write-Host "==================================================" -ForegroundColor Red
    Write-Host "  ✗ Erro na indexação" -ForegroundColor Red
    Write-Host "==================================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "Verifique:" -ForegroundColor Yellow
    Write-Host "  1. Conexão com a internet" -ForegroundColor White
    Write-Host "  2. Se a API key é válida no dashboard do Context7" -ForegroundColor White
    Write-Host "  3. Se há arquivos no projeto para indexar" -ForegroundColor White
}

Write-Host ""
Read-Host "Pressione Enter para sair"
