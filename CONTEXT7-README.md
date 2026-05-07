# Context7 - Configuração para QA Agent

## 🎯 O que é o Context7?

Context7 é uma ferramenta que permite que IAs (como eu!) entendam melhor o seu código através de indexação semântica. Isso significa:

- ✅ Respostas mais precisas sobre o código
- ✅ Melhor compreensão de relacionamentos entre classes
- ✅ Contexto automático do projeto
- ✅ Navegação inteligente no código

---

## 🚀 Como usar

### Opção 1: Indexar pelo Dashboard (Recomendado)

1. Acesse: https://context7.com/personal/dashboard
2. Clique no projeto "QA Agent"
3. Clique em "Add Docs" ou "Reindex"
4. Aguarde a conclusão

### Opção 2: Indexar pelo Script

```powershell
cd D:\Dev\AutomationQA\qa-agent
.\index-context7.ps1
```

Ou manualmente:

```powershell
setx CONTEXT7_API_KEY "sua_chave_context7_aqui"
npx ctx7@latest index
```

---

## 📊 Monitoramento

Verifique o uso no dashboard:
- URL: https://context7.com/personal/dashboard
- Requests: 0/1.000 (gratuito)
- Última indexação: verificar coluna "Last Used"

---

## 🔄 Quando Reindexar?

Reindexe após:
- ✅ Mudanças significativas na estrutura do projeto
- ✅ Novos módulos ou arquiteturas
- ✅ Refatorações importantes
- ✅ Problemas de contexto da IA

**Não precisa reindexar** para:
- ❌ Pequenas correções de bugs
- ❌ Ajustes de testes
- ❌ Mudanças menores de configuração

---

## 💡 Benefícios

Após a indexação, a IA será capaz de:

1. **Entender a arquitetura completa** do projeto
2. **Navegar entre arquivos** relacionados automaticamente
3. **Sugerir correções** baseadas em padrões do código
4. **Explicar código complexo** com contexto completo
5. **Identificar dependências** entre componentes

---

## 🔧 Configuração Atual

Arquivo: `.context7/config.json`

```json
{
  "mode": "mcp",
  "indexing": {
    "include": ["src/**/*.java", "gui/**/*.js", "gui/**/*.html", "pom.xml"]
  }
}
```

---

## ⚠️ Limites do Plano Gratuito

- **1.000 requests/mês**
- Indexação ilimitada
- Perfeito para projetos pessoais

Se atingir o limite, aguarde o próximo mês ou considere o plano pago.

---

## 📞 Suporte

- **Docs**: https://context7.com/docs
- **GitHub**: https://github.com/upstash/context7
- **Dashboard**: https://context7.com/personal/dashboard
