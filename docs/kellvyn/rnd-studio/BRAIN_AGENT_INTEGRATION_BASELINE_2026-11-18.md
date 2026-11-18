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


## Contrato transversal implementado — 18/11/2026

Branch: kellvyn-rnd/brain-agent-contract-001.

Foi adicionada a classe KellvynBrainAgentIntegrationContractTest com dois casos:
1. protocolDelegationRunsSpecialistWithLeastPrivilegeAndUntrustedHandoff — o teste entrega mensagens KELLVYN/1 roteirizadas; o parser cria DELEGATE, o agente principal chama o orquestrador, o especialista recebe somente read_source, executa leitura, devolve observação UNTRUSTED e o agente principal conclui.
2. malformedWireDecisionFailsBeforeAnyToolExecution — protocolo CALL_TOOL com nome inválido é rejeitado antes de qualquer execução de ferramenta.

A suíte integration da R&D Studio inclui a nova classe. Os testes usam ProtocolScriptedEngine; atravessam o parser real e o loop real de agente/orquestrador, mas não usam checkpoint neural real.

CI run 44407125400 falhou antes dos steps: runner_id=0, steps=[] nos dois jobs. Estado IMPLEMENTED_NOT_RUN; não há aprovação de compilação/execução. KellvynRuntime.isReady() continua false.


## Integration phase boundary — 18/11/2026 22:25 UTC

The current cross-layer test covers parser -> KellvynAgent -> KellvynOrchestrator -> read-only tool -> UNTRUSTED specialist handoff -> coordinator final response. It does NOT invoke KellvynIndependentVerifier. Existing harness/verifier tests remain separate. A future integration phase must connect the independent verifier and require objective evidence before marking an outcome passed.

The engine in the test is ProtocolScriptedEngine. Parser, agent and orchestrator are real code, but the neural checkpoint is not loaded. KellvynRuntime.isReady() remains false.
