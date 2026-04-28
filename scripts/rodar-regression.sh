#!/bin/bash
# Sinnc Saúde - Roda testes de Regressão
# Script para executar suite completa de testes de regressão

set -e

echo ""
echo "═══════════════════════════════════════"
echo "  Executando Testes de REGRESSÃO"
echo "═══════════════════════════════════════"
echo ""

export HEADLESS=false

mvn test -Dtest=RunRegressionTests -q

echo ""
echo "═══════════════════════════════════════"
echo "  Execução concluída!"
echo "  Relatório: output/reports/relatorio.html"
echo "═══════════════════════════════════════"
echo ""
