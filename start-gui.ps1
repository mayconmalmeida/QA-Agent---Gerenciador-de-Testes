# Script para iniciar o servidor GUI do QA Agent
$ErrorActionPreference = "Stop"

Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "  QA Agent - Iniciando GUI" -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan

# Check if Python is available
$pythonCmd = Get-Command python -ErrorAction SilentlyContinue
if (-not $pythonCmd) {
    $pythonCmd = Get-Command python3 -ErrorAction SilentlyContinue
}

if (-not $pythonCmd) {
    Write-Host "Python não encontrado. Por favor, instale o Python 3." -ForegroundColor Red
    exit 1
}

Write-Host "Python encontrado: $($pythonCmd.Source)" -ForegroundColor Green

# Change to project root (script directory)
Set-Location $PSScriptRoot

# Start Python server
Write-Host "Iniciando servidor GUI na porta 8080..." -ForegroundColor Green
Write-Host "Acesse: http://localhost:8080" -ForegroundColor Green
Write-Host "Pressione Ctrl+C para parar" -ForegroundColor Gray
Write-Host ""

& $pythonCmd.Source gui/server.py
