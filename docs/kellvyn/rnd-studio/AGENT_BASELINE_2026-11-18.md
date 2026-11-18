# Agent R&D — baseline de código e validação

**Inspecionado:** 18/11/2026 UTC. Inspeção de fonte; não executou os agentes.

## Arquitetura

- KellvynAgent.kt: loop operacional que recebe KellvynInferenceEngine por injeção; não é o modelo neural.
- KellvynOrchestrator.kt: recebe DELEGATE, valida limites/política, compila perfil, filtra ferramentas e executa especialista.
- KellvynSpecialistToolPolicy.kt: matriz fail-closed de capabilities; especialistas recebem ferramentas de leitura, e o Researcher só recebe leitura de rede com a chave do proprietário habilitada.
- Profile Registry/Compiler/Store: 14 papéis, validação, versionamento, hash e promoção confiável.
- Specialist Evaluation Harness/Benchmark Registry: aprova somente evidência reconhecida por verificador independente; estados PASSED, REVIEW_REQUIRED, FAILED e BLOCKED.
- Specialist Agent Benchmark Executor: executa casos através do loop do agente, sem se autoavaliar.
- AgentLabTrace/TraceStore: eventos de modelo, delegação, ferramentas, resultados e recursos, sem prompts/respostas/argumentos brutos.

Os 14 especialistas são perfis que compartilham o mesmo backend de inferência; não são 14 modelos separados.

## Testes já presentes

12 classes focadas:
KellvynAgentDelegationTest, KellvynAgentLabTraceStoreTest, KellvynAgentLimitsTest, KellvynAgentPlanTest, KellvynAgentSpecialistEvaluationTest, KellvynAgentTest, KellvynOrchestratorTest, KellvynSpecialistAgentBenchmarkExecutorTest, KellvynSpecialistEvaluationHarnessTest, KellvynSpecialistProfileRegistryTest, KellvynSpecialistProfileStoreTest e KellvynSpecialistToolPolicyTest.

Cobrem isolamento de contexto, não delegação aninhada, limites por sessão, ferramentas permitidas, bloqueio de mutações, schemas, limites de passos e verificação independente. Presença de testes não significa execução Gradle. O registry contém 28 casos (dois por papel), mas casos e fakes não provam competência cognitiva.

## Lacuna concreta

Em KellvynOrchestrator.Session.dispatch(), DELEGATION_STARTED é registrado, mas o evento terminal normal fornece durationMillis=null. No caminho de cancelamento também não há duração. O trace não consegue medir a latência de cada especialista.

Hipótese agent-delegation-duration-001: usar relógio monotônico, registrar duração em caminhos normal e cancelado e testar com relógio determinístico. É lacuna de telemetria, não prova de falha de raciocínio ou roteamento.

## Métricas

Routing por papel; cobertura de evidência; ferramentas pedidas/permitidas/recusadas/executadas; violações de política; vazamento de contexto; timeout/cancelamento/orçamento; duração de inferência, ferramenta e delegação; sucesso em tentativas repetidas; RAM/PSS/temperatura/bateria quando medidos.

**Estado:** fontes e testes identificados; Gradle não executado nesta rodada; competência com cérebro real não comprovada.
