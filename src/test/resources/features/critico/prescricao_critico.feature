# language: pt
@critico
Funcionalidade: Prescrição Médica - Fluxos Críticos
  Como um médico
  Quero prescrever medicamentos e procedimentos
  Para garantir o tratamento correto dos pacientes

  Contexto:
    Dado que estou logado como médico
    E seleciono um paciente ativo na lista de atendimento

  @critico @seguranca
  Cenário: Prescrição de medicamento controlado com validação dupla
    Dado que estou na tela de prescrição médica
    Quando adiciono um medicamento da lista ANVISA Tarja Preta
    E informo a quantidade prescrita
    Então o sistema deve solicitar senha de validação do farmacêutico
    E o medicamento deve ser marcado como "Aguardando validação"

  @critico @alergia
  Cenário: Alerta de alergia cruzada na prescrição
    Dado que o paciente tem alergia à "Penicilina" registrada no prontuário
    Quando tento prescrever "Amoxicilina"
    Então o sistema deve exibir alerta de contraindicação
    E deve solicitar confirmação com senha e justificativa

  @critico @interacao
  Cenário: Alerta de interação medicamentosa
    Dado que o paciente já tem prescrição ativa de "Warfarina"
    Quando tento prescrever "AAS"
    Então o sistema deve exibir alerta de interação medicamentosa grave
    E deve bloquear a prescrição até confirmação do médico responsável
