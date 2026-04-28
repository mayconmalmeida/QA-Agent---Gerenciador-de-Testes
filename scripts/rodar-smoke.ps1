#!/usr/bin/env pwsh
# Sinnc Saúde - Roda testes Smoke
# Script para executar suite de testes smoke (rápidos, rodam em todo deploy)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "═══════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Executando Testes SMOKE" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

$env:HEADLESS = "false"

mvn test -Dtest=RunSmokeTests -q

Write-Host ""
Write-Host "═══════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Execução concluída!" -ForegroundColor Cyan
Write-Host "  Relatório: output/reports/relatorio.html" -ForegroundColor Yellow
Write-Host "═══════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
