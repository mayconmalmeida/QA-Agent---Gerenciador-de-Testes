#!/usr/bin/env pwsh
# Sinnc Saúde - Gerador de Testes (PowerShell)
# Script para executar o CLI interativo de geração de testes

$ErrorActionPreference = "Stop"

# Cores para output
$Green = "`e[32m"
$Red = "`e[31m"
$Yellow = "`e[33m"
$Reset = "`e[0m"

Write-Host ""
Write-Host "╔══════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║   Sinnc Saúde — Gerador de Testes   ║" -ForegroundColor Cyan
Write-Host "╚══════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# Verificar se Maven está instalado
try {
    $mvnVersion = mvn --version 2>&1 | Select-Object -First 1
    Write-Host "✓ Maven encontrado: $mvnVersion" -ForegroundColor Green
} catch {
    Write-Host "✗ Maven não encontrado. Por favor, instale o Maven 3.9+" -ForegroundColor Red
    exit 1
}

# Verificar Java 17+
try {
    $javaVersion = java --version 2>&1 | Select-Object -First 1
    Write-Host "✓ Java encontrado: $javaVersion" -ForegroundColor Green
} catch {
    Write-Host "✗ Java não encontrado. Por favor, instale o Java 17+" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Iniciando CLI interativo..." -ForegroundColor Yellow
Write-Host ""

# Executar o CLI
try {
    mvn exec:java -Dexec.mainClass="br.com.qasuite.cli.GerarTeste" -q
} catch {
    Write-Host "✗ Erro ao executar o gerador: $_" -ForegroundColor Red
    exit 1
}
