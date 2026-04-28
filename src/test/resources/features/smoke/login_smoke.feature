# language: pt
@smoke
Funcionalidade: Login no Sistema Sinnc Saúde
  Como um usuário do sistema
  Quero realizar login
  Para acessar as funcionalidades do sistema hospitalar

  Cenário: Login com credenciais válidas
    Dado que estou na página de login
    Quando eu insiro o usuário "usuario_qa"
    E eu insiro a senha "senha_qa"
    E eu clico no botão Entrar
    Então devo ser redirecionado para a página principal

  Cenário: Login com credenciais inválidas
    Dado que estou na página de login
    Quando eu insiro o usuário "usuario_invalido"
    E eu insiro a senha "senha_invalida"
    E eu clico no botão Entrar
    Então devo ver uma mensagem de erro de autenticação
