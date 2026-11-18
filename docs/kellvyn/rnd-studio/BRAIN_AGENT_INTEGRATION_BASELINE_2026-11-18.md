# Brain + Agent Integration R&D — baseline

**Inspecionado:** 18/11/2026 UTC. Não houve inferência em produção.

## Fluxo pretendido

1. Backend neural gera decisão estruturada KELLVYN/1.
2. KellvynDecisionProtocol valida e cria FINAL, CALL_TOOL, PLAN ou DELEGATE.
3. KellvynAgent aplica limites e autorização e chama ferramenta ou delegação.
4. KellvynOrchestrator compila perfil, limita contexto/tempo e fornece ferramentas autorizadas.
5. Especialista usa o mesmo backend e devolve observação UNTRUSTED.
6. Agente principal continua e o verificador independente comprova critérios.

O cérebro fornece decisões; o agente executa o loop; o orquestrador distribui especialistas.

## Estado real

- KellvynLocalNeuralInferenceEngine adapta KN-002.
- KellvynTransformerInferenceEngine adapta KN-003.
- KellvynAgent e KellvynOrchestrator recebem KellvynInferenceEngine por injeção.
- KellvynRuntime.isReady() retorna false e reply() informa que o cérebro ainda não está conectado ao chat.
- Portanto, adapters e agentes existem, mas o chat de produção não está integrado ao cérebro.

## Suite integration

Seleciona testes de parser e adapter KN-003, loop do agente, ferramentas, delegação, isolamento, política, executor e verificador. É teste unitário de contrato; alguns casos usam engines determinísticas. Não é E2E com checkpoint real e KellvynRuntime.

## Gate antes da integração de produção

Checkpoint/tokenizer íntegros; qualidade neural elegível para benchmark; decisões KELLVYN/1 válidas; inferência real passando pelo loop; schema/política de ferramentas; delegação restrita e resultado UNTRUSTED; verificação independente; traces completos; testes unitários/instrumentados/físicos; integração reversível. Runtime só pode ficar pronto depois de evidência real.

Hipótese brain-agent-contract-001 congela casos para os quatro tipos de decisão e sequencia adapter -> parser -> agent loop -> tool/delegation -> verifier. A primeira rodada usa engine determinística; não comprova competência do modelo.

**Estado:** Runtime de produção desconectado; suíte de contrato preparada, mas não executada.


## Candidato atual

A branch kellvyn-rnd/brain-agent-contract-001 adicionou KellvynBrainAgentIntegrationContractTest, com um caso de delegação de especialista e outro de protocolo malformado. Usa ProtocolScriptedEngine, portanto não prova inferência neural real. CI run 44407125400: runner_id=0, steps=[]; NOT RUN. Ver SESSION_CONTROL seção 16.
