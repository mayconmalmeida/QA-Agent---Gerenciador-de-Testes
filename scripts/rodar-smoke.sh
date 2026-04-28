#!/bin/bash
# Sinnc Saúde - Roda testes Smoke
# Script para executar suite de testes smoke (rápidos, rodam em todo deploy)

set -e

echo ""
echo "═══════════════════════════════════════"
echo "  Executando Testes SMOKE"
echo "═══════════════════════════════════════"
echo ""

export HEADLESS=false

mvn test -Dtest=RunSmokeTests -q

echo ""
echo "═══════════════════════════════════════"
echo "  Execução concluída!"
echo "  Relatório: output/reports/relatorio.html"
echo "═══════════════════════════════════════"
echo ""
