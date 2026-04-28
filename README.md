# QA Agent — Gerenciador de Testes Automatizados

Framework completo de automação de testes com interface web, integrando Playwright, JUnit 5 e IA (OpenAI, Groq, Ollama, Anthropic) para geração automática de cenários de teste, código Java e integração com sistemas de gestão.

## Sobre o projeto

Este projeto nasceu de uma necessidade real do meu dia a dia como **Analista de Requisitos e QA**.

Cansado de escrever casos de teste manualmente, criar cenários Gherkin do zero e repetir o mesmo trabalho de documentação toda vez que um novo fluxo precisava ser testado, decidi automatizar o próprio processo de criação de testes.

O **QA Agent** é uma aplicação web que combina uma interface visual intuitiva com o poder de IA (Anthropic Claude) para gerar automaticamente:

- Arquivos `.feature` (Gherkin/BDD) em português
- Código Java com Playwright pronto para executar
- Casos de teste formatados para Jira/Xray
- Relatório HTML de execução com screenshots de falhas

A ideia é simples: você descreve o fluxo em linguagem natural, o agente entende, gera todos os artefatos e ainda executa o teste no navegador real — tudo isso em minutos, não horas.

> Desenvolvido por **Maycon Malicheski de Almeida** para uso próprio como Analista de Requisitos e QA.

## 📋 Pré-requisitos

- **Java 17+**
- **Maven 3.9+**
- **Navegadores Chrome/Chromium** (para Playwright)

## 🚀 Configuração Inicial

### 1. Clone e prepare o ambiente

```bash
cd qa-agent
```

### 2. Configure as credenciais

```bash
cp .env.example .env
```

Edite o arquivo `.env` e preencha (escolha um provedor):

**Opção 1 - OpenAI (você já tem acesso):**
```env
OPENAI_API_KEY=sua_chave_openai_aqui
```

**Opção 2 - Groq (GRÁTIS):**
```env
GROQ_API_KEY=sua_chave_groq_aqui
```

### Escolha seu Provedor de IA

O agente suporta múltiplos provedores (do mais barato/gratuito ao pago):

| Provedor | Custo | Modelo Recomendado | Link |
|----------|-------|-------------------|------|
| **Ollama** | **GRÁTIS** (roda local) | `codellama` ou `llama3.1` | [ollama.com](https://ollama.com) |
| **Groq** | **GRÁTIS** (tier generoso) | `llama-3.1-70b-versatile` | [groq.com](https://groq.com) |
| **OpenAI** | Pago (créditos) | `gpt-3.5-turbo` | [openai.com](https://openai.com) |
| **Anthropic** | Pago (créditos) | `claude-sonnet-4-20250514` | [anthropic.com](https://anthropic.com) |

**Recomendação para orçamento limitado:**
1. **Groq** - Cadastre-se gratuitamente e obtenha uma API key (muitas requisições por minuto no tier gratuito)
2. **Ollama** - Rode modelos totalmente gratuitos localmente (requer GPU/CPU razoável)

### Como obter API Keys

#### Groq (GRÁTIS - Recomendado)
1. Acesse [console.groq.com](https://console.groq.com)
2. Crie uma conta gratuita
3. Vá em "API Keys" e gere uma nova key
4. Copie para o `.env` como `GROQ_API_KEY`

#### OpenAI (Pago - separado do ChatGPT Plus!)
⚠️ **Atenção:** ChatGPT Plus ($20/mês) é diferente da API. A API é **pague conforme uso**.

1. Acesse [platform.openai.com](https://platform.openai.com)
2. Faça login com sua conta
3. Vá em "Billing" e adicione créditos (mínimo $5)
4. Vá em "API Keys" e crie uma nova key
5. Copie para o `.env` como `OPENAI_API_KEY`

**Custo aproximado:** GPT-3.5-turbo custa ~$0.002 por requisição. Gerar um teste completo custa centavos de dólar.

#### Ollama (GRÁTIS - Roda local)
1. Instale o Ollama: [ollama.com/download](https://ollama.com/download)
2. Rode no terminal: `ollama pull codellama` (baixa o modelo)
3. Inicie o servidor: `ollama serve`
4. Não precisa de API key! O sistema detecta automaticamente

### Para Iniciar

```powershell
# Windows
cd qa-agent
cp .env.example .env
# Edite .env com sua OPENAI_API_KEY (ou GROQ_API_KEY, ou configure Ollama)
```

### 3. Instale os browsers do Playwright

```bash
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

### 4. Compile o projeto

```bash
mvn clean install
```

## 🖥️ Iniciar Servidor GUI

O servidor GUI agora roda em Python e usa SQLite para persistência de dados.

### Windows (PowerShell)
```powershell
python gui\server.py
```

### Linux/Mac (Bash)
```bash
python3 gui/server.py
```

Ou use os scripts de inicialização:

### Windows
```powershell
.\start-server.ps1
```

### Windows (Batch)
```cmd
start-server.bat
```

Após iniciar, acesse: **http://localhost:8080**

> **Nota:** O banco de dados SQLite (`data/qa_agent.db`) é criado automaticamente na primeira execução. Todos os testes, menus e configurações são persistidos entre reinícios do servidor.

## 🧪 Gerar Novo Teste com IA

### Windows (PowerShell)
```powershell
.\scripts\gerar-teste.ps1
```

### Linux/Mac (Bash)
```bash
./scripts/gerar-teste.sh
```

O CLI interativo irá guiar você através de:
1. Seleção do módulo (Cadastro, Agendamento, Prescrição, etc.)
2. Tipo de teste (smoke/regression/crítico)
3. Prioridade (Alta/Média/Baixa)
4. Descrição do requisito
5. Passos do fluxo
6. Tags adicionais

A IA (Claude) gerará automaticamente:
- Arquivo `.feature` (Gherkin em pt-BR)
- Classe Java com Page Objects
- Caso de teste formatado para Jira/Xray

## ▶️ Executar Testes

### Windows (PowerShell)
```powershell
# Testes Smoke (rápidos, rodam em todo deploy)
.\scripts\rodar-smoke.ps1

# Testes de Regressão (suite completa)
.\scripts\rodar-regression.ps1

# Testes Críticos (fluxos assistenciais)
.\scripts\rodar-critico.ps1

# Todos os testes
.\scripts\rodar-todos.ps1
```

### Linux/Mac (Bash)
```bash
# Testes Smoke
./scripts/rodar-smoke.sh

# Testes de Regressão
./scripts/rodar-regression.sh

# Testes Críticos
./scripts/rodar-critico.sh

# Todos os testes
./scripts/rodar-todos.sh
```

### Maven diretamente
```bash
# Por tipo de teste
mvn test -Pcucumber.filter.tags="@smoke"
mvn test -Pcucumber.filter.tags="@regression"
mvn test -Pcucumber.filter.tags="@critico"

# Por runner específico
mvn test -Dtest=RunSmokeTests
mvn test -Dtest=RunRegressionTests
mvn test -Dtest=RunCriticoTests
mvn test -Dtest=RunAllTests
```

## 📊 Relatórios

Após cada execução, um relatório HTML é gerado automaticamente em:

```
output/reports/relatorio.html
```

O relatório inclui:
- **Cards de Resumo**: Total, Passou, Falhou, Pulado, Tempo
- **Tabela de Resultados** com filtros por status e tipo
- **Seção de Falhas** com screenshots e stack traces
- **Informações de versão** (Git branch e commit)

O relatório abre automaticamente no navegador padrão após a execução (configurável em `config.properties`).

## 📁 Estrutura de Pastas

```
qa-agent/
├── src/
│   ├── test/
│   │   ├── java/br/com/qasuite/
│   │   │   ├── config/          # ConfigLoader, BrowserFactory, BaseTest
│   │   │   ├── core/            # AgentIA, ReportBuilder, DTOs
│   │   │   ├── pages/           # Page Objects por módulo
│   │   │   ├── steps/           # Step Definitions Cucumber
│   │   │   └── runners/         # JUnit Runners
│   │   └── resources/
│   │       ├── features/        # Arquivos .feature (smoke/regression/critico)
│   │       ├── testdata/        # JSONs com dados de teste
│   │       └── config.properties
│   └── main/java/br/com/qasuite/cli/  # CLI GerarTeste
├── output/
│   ├── screenshots/             # Screenshots de falhas
│   ├── reports/                 # Relatórios HTML
│   └── artefatos/               # Artefatos gerados pela IA
├── scripts/                     # Scripts de execução (.sh e .ps1)
├── .github/workflows/           # GitHub Actions (smoke.yml, regression.yml)
├── pom.xml
└── README.md
```

## ⚙️ Configuração

### config.properties

```properties
# Ambiente
base.url=http://10.8.0.20/ViewLogin
ambiente=homologacao

# Browser
browser=chromium
headless=false
slow.motion.ms=200
timeout.padrao.ms=30000

# Screenshots (apenas em falha)
screenshot.em.falha=true
screenshot.pasta=output/screenshots

# Relatório
relatorio.output=output/reports/relatorio.html
relatorio.abrir.apos.execucao=true
```

### Variáveis de Ambiente (CI)

| Variável | Descrição |
|----------|-----------|
| `QA_USUARIO` | Usuário do sistema |
| `QA_SENHA` | Senha do sistema |
| `ANTHROPIC_API_KEY` | API Key da Anthropic (Claude) |
| `HEADLESS` | `true` para modo headless |

## 🔁 CI/CD (GitHub Actions)

### Smoke Tests
- **Trigger**: A cada push em qualquer branch
- **Executa**: Testes com tag `@smoke`
- **Artifacts**: Relatório HTML e screenshots

### Regression Tests
- **Trigger**: Cron `0 6 * * 1-5` (Seg-Sex às 6h UTC)
- **Executa**: Testes com tag `@regression`
- **Artifacts**: Relatório HTML e screenshots

Configure os secrets em `Settings > Secrets and variables > Actions`:
- `QA_USUARIO`
- `QA_SENHA`
- `ANTHROPIC_API_KEY`

## 🏷️ Tipos de Teste

| Tipo | Descrição | Tags |
|------|-----------|------|
| **Smoke** | Testes rápidos de sanidade | `@smoke` |
| **Regression** | Suite completa de regressão | `@regression` |
| **Crítico** | Fluxos assistenciais críticos | `@critico` |

## 🎨 Boas Práticas

### Page Objects
- Use seletores por **texto, role ou label**
- Evite XPath fixo quando possível
- Mantenha seletores em constantes no topo da classe

### Gherkin
- Escreva em **português (pt-BR)**
- Use `# language: pt` na primeira linha
- Mantenha o `Background` simples (login é feito pelo BaseTest)

### Java
- Estenda sempre `BaseTest`
- Use a anotação `@Tag` apropriada
- Comente em português explicando blocos importantes

## 🐛 Depuração

### Problemas Comuns

#### "mvn não é reconhecido" (Windows)
O Maven não está no PATH. Soluções:
1. **Instale o Maven:** Baixe em [maven.apache.org](https://maven.apache.org/download.cgi) e adicione ao PATH
2. **Ou use o wrapper:** Se o projeto tiver `mvnw`:
   ```powershell
   .\mvnw exec:java -Dexec.mainClass="br.com.qasuite.cli.GerarTeste"
   ```
3. **Ou use IDE:** Execute diretamente pela IDE (IntelliJ, VS Code, Eclipse)

#### "OPENAI_API_KEY não configurada"
Verifique se:
1. Criou o arquivo `.env` na raiz do projeto
2. Preencheu a chave corretamente (sem espaços ou aspas)
3. Reiniciou o terminal após editar o `.env`

### Executar com modo visual
```bash
mvn test -Dheadless=false
```

### Capturar screenshot manual
```java
capturarScreenshot("nome_do_passo");
```

### Logs detalhados
```bash
mvn test -X
```

## Autor

**Maycon Malheski de Almeida**  
Analista de Requisitos e QA

[![LinkedIn](https://img.shields.io/badge/LinkedIn-Maycon%20Malheski-0A66C2?style=flat&logo=linkedin)](https://www.linkedin.com/in/maycon-malheski-de-almeida/)  
[![GitHub](https://img.shields.io/badge/GitHub-mayconmalmeida-181717?style=flat&logo=github)](https://github.com/mayconmalmeida)

---

## 📝 Licença

Projeto de uso pessoal. Sinta-se livre para se inspirar, adaptar e evoluir para o seu próprio contexto.

## 🆘 Suporte

Em caso de dúvidas ou problemas:
1. Verifique os logs em `output/reports/`
2. Consulte os screenshots em `output/screenshots/`
3. Verifique se o `.env` está configurado corretamente
4. Confirme que o sistema está acessível na URL configurada
