#!/bin/bash
# Sinnc Saúde - Roda testes Críticos
# Script para executar testes de fluxos assistenciais críticos

set -e

echo ""
echo "═══════════════════════════════════════"
echo "  Executando Testes CRÍTICOS"
echo "═══════════════════════════════════════"
echo ""
echo "⚠️  ATENÇÃO: Estes testes cobrem fluxos assistenciais críticos!"
echo ""

export HEADLESS=false

mvn test -Dtest=RunCriticoTests -q

echo ""
echo "═══════════════════════════════════════"
echo "  Execução concluída!"
echo "  Relatório: output/reports/relatorio.html"
echo "═══════════════════════════════════════"
echo ""
