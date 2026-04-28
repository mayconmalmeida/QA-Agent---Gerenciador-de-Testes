#!/bin/bash
# Sinnc Saúde - Gerador de Testes (Bash)
# Script para executar o CLI interativo de geração de testes

set -e

echo ""
echo "╔══════════════════════════════════════╗"
echo "║   Sinnc Saúde — Gerador de Testes   ║"
echo "╚══════════════════════════════════════╝"
echo ""

# Verificar se Maven está instalado
if ! command -v mvn &> /dev/null; then
    echo "✗ Maven não encontrado. Por favor, instale o Maven 3.9+"
    exit 1
fi

echo "✓ Maven encontrado: $(mvn --version | head -1)"

# Verificar Java 17+
if ! command -v java &> /dev/null; then
    echo "✗ Java não encontrado. Por favor, instale o Java 17+"
    exit 1
fi

echo "✓ Java encontrado: $(java --version | head -1)"

echo ""
echo "Iniciando CLI interativo..."
echo ""

# Executar o CLI
mvn exec:java -Dexec.mainClass="br.com.sinncosaude.cli.GerarTeste" -q
