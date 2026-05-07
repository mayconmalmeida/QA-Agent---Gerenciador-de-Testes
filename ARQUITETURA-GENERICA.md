# QA Agent - Arquitetura Genérica 🧠

Este documento descreve a nova arquitetura do QA Agent que permite testar **qualquer sistema web** automaticamente, sem necessidade de seletores CSS específicos.

---

## 🎯 Componentes Principais

### 1. **ElementDetector** (Base de Tudo)

```java
ElementDetector detector = new ElementDetector(page);

// Detecta elementos automaticamente
List<UIElement> campos = detector.detectarCamposFormulario();
List<UIElement> botoes = detector.detectarBotoes();
Map<String, UIElement> loginFields = detector.detectarCamposLogin();
UIElement botaoLogin = detector.detectarBotaoLogin();
```

**Funcionalidades:**
- ✅ Detecta inputs, selects, textareas
- ✅ Identifica botões (padrão + DevExpress)
- ✅ Encontra menus de navegação
- ✅ Localiza campos de login (por texto/placeholder/aria-label)
- ✅ Detecta modais/popups

---

### 2. **SmartLogin** (Login Automático)

```java
SmartLogin smartLogin = new SmartLogin(page);

// Login totalmente automático - detecta campos dinamicamente
smartLogin.login("http://sistema.com/login", "usuario", "senha");

// Ou fluxo completo com tratamento de modal pós-login
smartLogin.loginCompleto(url, usuario, senha);
```

**Funcionalidades:**
- ✅ Detecta campos de usuário/senha automaticamente
- ✅ Identifica botão de login por texto
- ✅ Trata modal de seleção de unidade pós-login
- ✅ Funciona com qualquer sistema de login

---

### 3. **SmartPage** (Ações Genéricas)

```java
SmartPage smartPage = new SmartPage(page);

// Navegação
smartPage.navegarParaMenu("Atenção Primária", "Acolhimento");

// Cliques
smartPage.clicarPorTexto("Inserir");
smartPage.clicarPorTexto("Próximo Paciente");

// Preenchimento (por nome/placeholder/label)
smartPage.preencherCampo("Nome", "João Silva");
smartPage.preencherCampo("Peso", "80");
smartPage.preencherCampo("Data de Nascimento", "01/01/1990");

// Seleção
smartPage.selecionarOpcao("Tipo", "Consulta");

// Checkbox
smartPage.marcarCheckbox("Ativo", true);

// Execução por descrição textual
smartPage.executarAcao("clicar em Inserir");
smartPage.executarAcao("preencher campo Nome com João");
smartPage.executarAcao("selecionar Masculino em Sexo");
```

**Funcionalidades:**
- ✅ Navegação por texto de menu
- ✅ Cliques por texto visível
- ✅ Preenchimento por nome/placeholder/label/aria-label
- ✅ Seleção em dropdowns
- ✅ Marcação de checkboxes
- ✅ Interpretação de comandos textuais
- ✅ Validação de mensagens de sucesso

---

### 4. **TestGenerator** (Geração Inteligente)

```java
TestGenerator generator = new TestGenerator();

// Parseia descrição em passos
String descricao = """
    1. Acessar http://sistema.com/login
    2. Preencher usuário com teste
    3. Preencher senha com 123456
    4. Clicar em Entrar
    5. Navegar em Menu > Submenu
    6. Clicar em Inserir
    7. Preencher campo Nome com João
    8. Clicar em Salvar
    9. Validar mensagem de sucesso
    """;

List<TestStep> passos = generator.parseSteps(descricao);

// Gera código Java completo
String codigo = generator.generateJavaCode("MeuTeste", descricao, passos);

// Ou gera direto do JSON do GUI
String codigo = generator.generateFromJson(jsonDoFrontend);
```

**Formatos Suportados:**
- ✅ "1. Acessar URL"
- ✅ "2. Preencher campo X com Y"
- ✅ "3. Clicar em Botão"
- ✅ "4. Selecionar Opção em Campo"
- ✅ "5. Navegar em Menu > Submenu"
- ✅ "6. Validar mensagem de sucesso"
- ✅ Números: "1.", "1-", "1)"

---

## 🔄 Fluxo de Teste Genérico

```
┌─────────────────────────────────────────────────────────────┐
│  1. Usuário descreve teste no GUI                          │
│     "1. Acessar http://..."                                │
│     "2. Preencher usuário com..."                          │
│     "3. Clicar em Entrar"                                   │
└──────────────────┬────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────┐
│  2. TestGenerator.parseSteps()                            │
│     Converte texto → List<TestStep>                        │
└──────────────────┬────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────┐
│  3. TestGenerator.generateJavaCode()                      │
│     Gera código Java com SmartLogin + SmartPage            │
└──────────────────┬────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────┐
│  4. Compila e executa teste                                │
│     SmartLogin detecta campos automaticamente              │
│     SmartPage executa ações por texto                      │
└──────────────────┬────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────┐
│  5. Relatório de resultado                                  │
│     Screenshot + status (passou/falhou)                    │
└─────────────────────────────────────────────────────────────┘
```

---

## 🎨 Exemplo de Uso Completo

### Antes (Código Específico):
```java
// Só funciona com seletores específicos do seu sistema
page.locator("#usuario").fill("teste");
page.locator("#senha").fill("123");
page.locator(".btn-entrar").click();
page.locator(".menu-atencao").click();
page.locator(".submenu-acolhimento").click();
```

### Agora (Código Genérico):
```java
// Funciona com QUALQUER sistema!
SmartLogin login = new SmartLogin(page);
login.loginCompleto("http://sistema.com", "teste", "123");

SmartPage page = new SmartPage(page);
page.navegarParaMenu("Atenção Primária", "Acolhimento");
page.clicarPorTexto("Inserir");
page.preencherCampo("Nome do Paciente", "João Silva");
page.clicarPorTexto("Salvar");
```

---

## 🧪 Testando um Novo Sistema

Para testar um sistema diferente, basta:

1. **Configurar URL e credenciais** no `config.properties`
2. **Descrever o teste** no GUI usando linguagem natural
3. **Executar** - o QA Agent detecta tudo automaticamente!

**Exemplo de descrição que funciona com qualquer sistema:**
```
1. Acessar http://novo-sistema.com/login
2. Preencher usuário com admin
3. Preencher senha com senha123
4. Clicar em Entrar
5. Navegar em Cadastros > Clientes
6. Clicar em Novo
7. Preencher campo Nome com João
8. Preencher campo Email com joao@teste.com
9. Clicar em Salvar
10. Validar mensagem de sucesso
```

---

## 📋 Tabela de Comandos Suportados

| Comando | Exemplo | Ação |
|---------|---------|------|
| Acessar | "Acessar http://..." | Navega para URL |
| Preencher | "Preencher campo Nome com João" | Preenche input |
| Clicar | "Clicar em Salvar" | Clica no elemento |
| Selecionar | "Selecionar Masculino em Sexo" | Seleciona opção |
| Navegar | "Navegar em Menu > Submenu" | Navega menu |
| Marcar | "Marcar checkbox Ativo" | Marca checkbox |
| Validar | "Validar mensagem de sucesso" | Verifica mensagem |

---

## 🔧 Como Adicionar Novos Padrões

Para ensinar o QA Agent a reconhecer novos padrões, edite `TestGenerator.java`:

```java
// No método parseSingleStep(), adicione:
if (lower.matches(".*novo padrão.*")) {
    return parseNovoPadraoStep(text);
}

// Implemente o parser:
private TestStep parseNovoPadraoStep(String text) {
    // Extraia informações do texto
    return new TestStep("novo_acao", alvo, valor, text);
}

// No generateStepCode(), adicione:
case "novo_acao":
    code.append("smartPage.novaAcao(...);\n");
    break;
```

---

## 🚀 Próximos Passos

1. ✅ **Testar com sistema real** - Execute um teste e veja se os seletores funcionam
2. 🔧 **Ajustar seletores** - Se necessário, refine os padrões de detecção
3. 📊 **Adicionar mais ações** - Upload de arquivos, drag-drop, etc.
4. 🤖 **Integração com IA** - Usar GPT para gerar descrições de teste

---

## 📝 Notas Importantes

- **SmartLogin** tenta detectar por: tipo=input, placeholder, aria-label, texto próximo
- **SmartPage** usa múltiplos seletores em ordem de prioridade
- **Timeout padrão**: 10 segundos para cada operação
- **Fallback**: Se não encontrar por um critério, tenta outro
- **DevExpress**: Suporte nativo a componentes DX (`.dx-*`)

---

**Agora o QA Agent é realmente universal!** 🎉
