#!/usr/bin/env pwsh
# Sinnc Saúde - Roda testes de Regressão
# Script para executar suite completa de testes de regressão

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "═══════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Executando Testes de REGRESSÃO" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

$env:HEADLESS = "false"

mvn test -Dtest=RunRegressionTests -q

Write-Host ""
Write-Host "═══════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Execução concluída!" -ForegroundColor Cyan
Write-Host "  Relatório: output/reports/relatorio.html" -ForegroundColor Yellow
Write-Host "═══════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
