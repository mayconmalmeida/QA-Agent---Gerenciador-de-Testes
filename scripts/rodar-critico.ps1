#!/usr/bin/env pwsh
# Sinnc Saúde - Roda testes Críticos
# Script para executar testes de fluxos assistenciais críticos

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "═══════════════════════════════════════" -ForegroundColor Red
Write-Host "  Executando Testes CRÍTICOS" -ForegroundColor Red
Write-Host "═══════════════════════════════════════" -ForegroundColor Red
Write-Host ""
Write-Host "⚠️  ATENÇÃO: Estes testes cobrem fluxos assistenciais críticos!" -ForegroundColor Yellow
Write-Host ""

$env:HEADLESS = "false"

mvn test -Dtest=RunCriticoTests -q

Write-Host ""
Write-Host "═══════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Execução concluída!" -ForegroundColor Cyan
Write-Host "  Relatório: output/reports/relatorio.html" -ForegroundColor Yellow
Write-Host "═══════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
