#!/usr/bin/env pwsh
# Sinnc Saúde - Roda todos os testes
# Script para executar suite completa (smoke + regression + critico)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "═══════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Executando TODOS os Testes" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
Write-Host "Inclui: Smoke + Regression + Críticos" -ForegroundColor Yellow
Write-Host ""

$env:HEADLESS = "false"

mvn test -Dtest=RunAllTests -q

Write-Host ""
Write-Host "═══════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Execução concluída!" -ForegroundColor Cyan
Write-Host "  Relatório: output/reports/relatorio.html" -ForegroundColor Yellow
Write-Host "═══════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
