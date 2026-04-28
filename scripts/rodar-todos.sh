#!/bin/bash
# Sinnc Saúde - Roda todos os testes
# Script para executar suite completa (smoke + regression + critico)

set -e

echo ""
echo "═══════════════════════════════════════"
echo "  Executando TODOS os Testes"
echo "═══════════════════════════════════════"
echo ""
echo "Inclui: Smoke + Regression + Críticos"
echo ""

export HEADLESS=false

mvn test -Dtest=RunAllTests -q

echo ""
echo "═══════════════════════════════════════"
echo "  Execução concluída!"
echo "  Relatório: output/reports/relatorio.html"
echo "═══════════════════════════════════════"
echo ""
