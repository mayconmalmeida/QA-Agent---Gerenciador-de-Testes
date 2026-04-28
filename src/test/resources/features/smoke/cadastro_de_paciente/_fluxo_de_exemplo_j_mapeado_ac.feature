Funcionalidade: Cadastro de Paciente
  # language: pt
  Background:
    Dado que o usuário está logado no sistema
  Cenário: Acolhimento de novo paciente
    Dado que o usuário está na tela de cadastro de paciente
    Quando o usuário preenche os dados obrigatórios do paciente
    E clica no botão 'Salvar'
    Então o sistema deve exibir a mensagem de sucesso
    E o paciente deve ser cadastrado corretamente