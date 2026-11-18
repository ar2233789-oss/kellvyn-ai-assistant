# Kellvyn brain — source baseline

**Inspected:** 2026-11-18, 20:03–20:12 UTC
**Repository:** ar2233789-oss/kellvyn-ai-assistant
**Branch:** lab/kellvyn-intelligence-core-20261116
**Commit inspected:** 18c93f9a71fa4a1c8d1b862a7922ef2b8ef6b338

This baseline comes from source inspection. It is not a runtime test.

## Components and boundaries

| Component | Source | Actual responsibility / state |
|---|---|---|
| App brain boundary | android/app/src/main/java/com/kellvyn/assistant/KellvynRuntime.kt | Owns memory, workspace, traces, neural stores and factories. isReady() currently returns false; reply() reports that the original neural brain is not connected to chat. |
| Agent control loop | KellvynAgent.kt | Control loop, not a neural model. Receives an injected KellvynInferenceEngine. |
| Specialist coordinator | KellvynOrchestrator.kt | Separate coordinator for typed DELEGATE decisions: checks policy, compiles profiles, builds least-privilege tool subsets, constructs child sessions, bounds recursion/time/context and returns untrusted observations. |
| Earlier local neural adapter | KellvynLocalNeuralInferenceEngine.kt | Adapts the local attention model to strict KELLVYN/1 decisions; source comments say this does not enable chat or prove instruction following. |
| Own Transformer training/evaluation | neural/KellvynTransformerTrainingPipeline.kt and neural/KellvynTransformerTrainingQualityGate.kt | Own-model training. Passing the current gate only makes a checkpoint eligible for protocol benchmarking; it does not prove general conversation, reasoning or production readiness. |
| Agent execution lab | KellvynAgentLabTrace.kt, KellvynAgentLabTraceStore.kt, KellvynAgentLabStudio.kt | Captures execution traces and resource snapshots. This is the app's agent execution lab, not this external R&D Studio. |

## Architectural conclusion

The brain, agent loop and orchestrator are distinct. Do not merge the orchestrator into the brain or replace the brain with it. The brain/inference engine supplies decisions; the agent loop handles the permitted control flow; the orchestrator distributes specialist work.

The specialist roles share the injected inference backend. They are not fourteen separately trained neural models.

## Not proven yet

- Production neural inference is not connected to the main conversation.
- Passing the training gate is not proof of Portuguese conversation, reasoning or coding skill.
- No physical Poco F6 latency, memory, thermal or battery result is established by this inspection.
- GitHub Actions jobs inspected for this branch did not receive a runner (runner_id=0, no steps). Gradle/JUnit/Android are NOT RUN, not failed and not approved.

## First study sequence

1. Reproduce tokenizer vocabulary, merge rules, encode/decode properties and current test coverage.
2. Check attention scaling, masks, tensor dimensions, stable softmax, gradients and optimizer updates on tiny deterministic fixtures.
3. Compare incremental KV-cache decoding with full-prefix inference token by token and logit by logit.
4. Validate KELLVYN/1 termination, malformed output, argument schema and untrusted context handling.
5. Audit dataset provenance, rights, deduplication, split leakage, seeds, training configuration and held-out generalization.
6. Build a Portuguese task suite with independent oracles; never use loss alone as a measure of intelligence.
7. Measure cold/warm latency, tokens per second, PSS/heap, storage, temperature and battery on the actual Poco F6.
8. Only after correctness and behavioral gates, create a reversible runtime integration candidate.

Read these source files and their matching tests before each brain task: KellvynRuntime.kt, KellvynAgent.kt, KellvynOrchestrator.kt, KellvynLocalNeuralInferenceEngine.kt, KellvynTransformerInferenceEngine.kt, neural/KellvynBpeTokenizer.kt, neural/KellvynTransformerLanguageModel.kt, neural/KellvynTransformerTrainingPipeline.kt, neural/KellvynTransformerTrainingQualityGate.kt.

A source file or class existing does not prove that the main chat calls it. Trace construction, call sites and tests.


## Primeiro achado de auditoria de gradientes — 18/11/2026

**Arquivos inspecionados:** `KellvynTransformerLanguageModel.kt` e `KellvynTransformerLanguageModelTest.kt`.

### O que o código faz hoje

`checkGradients()` calcula gradientes analíticos e compara diferenças centrais finitas, mas forma a lista de coordenadas amostráveis somente com índices onde `abs(gradient[index]) > 1e-8`. Depois amostra no máximo `maxCoordinatesPerGroup` dessas coordenadas por array de parâmetros. O teste existente usa um seed/configuração e `maxCoordinatesPerGroup = 2`.

### Lacuna encontrada

Se um grupo inteiro tiver gradiente analítico acidentalmente zerado por uma regressão na retropropagação, ele terá zero coordenadas candidatas e não será auditado. O requisito final `checked > 0` pode continuar satisfeito por outros grupos. Portanto, o relatório pode passar sem verificar aquela família de parâmetros.

**Classificação:** lacuna de cobertura do verificador — não é prova de que a implementação atual tenha um gradiente errado.

### Experimento aberto

`brain-gradient-coverage-001` irá testar uma seleção determinística que inclua coordenadas de gradiente zero e não zero em todos os grupos, múltiplos números de cabeças, seeds, comprimentos de contexto e alvo EOS. Critérios: todo grupo deve produzir amostras numéricas; os erros precisam respeitar limites absolutos e relativos pré-definidos; execução com baseline e candidato em ambiente idêntico.

**Estado:** PLANNED / NOT RUN. Nenhum arquivo neural ou peso foi modificado. Não alterar a implementação até que o Gradle execute e a hipótese possa ser falsificada.


## Candidato de cobertura implementado — 18/11/2026

Branch isolada: `kellvyn-rnd/brain-gradient-coverage-001`.

A versão candidata:
- nomeia os 14 arrays de parâmetros e mantém o método antigo `arrays()` direto para não acrescentar alocações ao caminho quente de treino;
- amostra coordenadas em todos os grupos, incluindo gradiente analítico zero;
- seleciona representante não-zero e zero quando ambas as categorias existem;
- restaura sempre o parâmetro perturbado em `finally`;
- inclui mapa `checkedParametersByGroup` no relatório;
- aplica floor de 1e-4 ao denominador do erro relativo para comparação float32 perto de zero, mantendo o erro absoluto independente.

Testes escritos:
- `multiHeadTransformerGradientsMatchFiniteDifferences` verifica os nomes e contagens de todos os 14 grupos;
- `gradientCheckerCoversEveryParameterGroupAcrossHeadCountsAndSeeds` cobre headCount 1/2/4 com seeds distintos.

**Estado:** IMPLEMENTED_NOT_RUN. Run GitHub Actions `44405313920`: ambos jobs falharam antes de steps, `runner_id=0`, `steps=[]`. A baseline/candidata Gradle não foi executada. A tolerância float32 não está validada por execução.

Ainda pendente: checagem direcional seeded por grupo e negative control que zera um grupo de gradientes e prova detecção. Não houve alteração de pesos, treinamento, tokenizer, runtime ou interface.


## Latest branch and CI checkpoint — 18/11/2026 22:25 UTC

- Candidate branch HEAD: c4d26bc099881b3d842b40735f465ab76b23761b.
- Latest run: 44408098843. Hosted runner allocation failed before steps (runner_id=0, steps=[]).
- Candidate remains IMPLEMENTED_NOT_RUN. Neither epsilon/tolerances nor numerical error thresholds have been confirmed by execution.
- Keep this patch isolated until baseline and candidate Kotlin tests execute.
