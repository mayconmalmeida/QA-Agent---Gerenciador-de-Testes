# language: pt
@critico
Funcionalidade: Internação - Fluxos Críticos
  Como um médico ou enfermeiro
  Quero gerenciar internações de pacientes
  Para garantir a segurança e continuidade do cuidado

  Contexto:
    Dado que estou logado no sistema
    E tenho permissão de acesso à unidade de internação

  @critico @admissao
  Cenário: Admissão de paciente com dados obrigatórios LGPD
    Dado que estou na tela de admissão de internação
    Quando informo os dados do paciente sem consentimento LGPD assinado
    E tento confirmar a admissão
    Então o sistema deve bloquear a ação
    E exibir mensagem: "Consentimento LGPD é obrigatório para internação"

  @critico @identificacao
  Cenário: Verificação de pulseira de identificação
    Dado que o paciente foi admitido na enfermaria
    Quando acesso o prontuário do paciente internado
    Então o sistema deve exibir o número da pulseira
    E deve alertar se a pulseira não foi impressa

  @critico @alta
  Cenário: Alta hospitalar com prescrições pendentes
    Dado que estou na tela de alta do paciente
    E o paciente tem medicamentos pendentes de administração
    Quando tento confirmar a alta
    Então o sistema deve exibir alerta de medicação pendente
    E deve listar os medicamentos não administrados
