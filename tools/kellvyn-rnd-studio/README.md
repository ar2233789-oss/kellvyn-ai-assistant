# Kellvyn R&D Studio

Bancada externa para estudar e desenvolver o cérebro e os agentes Kellvyn, e validar os contratos entre eles. Não é tela do app nem o Agent Lab Android.

Três frentes:
- Brain: tokenizer, KN-001/002/003, treinamento, pesos, inferência e KELLVYN/1.
- Agents: KellvynAgent, KellvynOrchestrator, 14 perfis, políticas de ferramentas, planner, traces e avaliação.
- Integration: inferência -> decisões -> loop do agente -> ferramentas/delegação -> verificador.

Comandos:

    python3 tools/kellvyn-rnd-studio/studio.py track-map --scope brain
    python3 tools/kellvyn-rnd-studio/studio.py track-map --scope agents
    python3 tools/kellvyn-rnd-studio/studio.py track-map --scope integration

    python3 tools/kellvyn-rnd-studio/studio.py task create --id agent-delegation-duration-001 --scope agents --title "Delegation duration" --objective "Measure specialist dispatch duration" --require-test agents
    python3 tools/kellvyn-rnd-studio/studio.py task test --id agent-delegation-duration-001 --suite agents
    python3 tools/kellvyn-rnd-studio/studio.py task check --id agent-delegation-duration-001

    python3 tools/kellvyn-rnd-studio/studio.py experiment create --id agent-routing-001 --scope agents --title "Agent routing" --hypothesis "Each role receives only permitted tools"

Suítes brain, agents e integration selecionam classes Kotlin com Gradle --tests. As suítes unit, assemble e instrumented continuam disponíveis. A suíte integration atual é de contrato unitário; não prova que o chat esteja conectado ao cérebro real. KellvynRuntime.isReady() continua false.

Cada tarefa cria branch e Git worktree fora da árvore principal. Alterar código depois do teste invalida a evidência. Worktree não é sandbox de sistema operacional; Gradle executa scripts do repositório, portanto use somente clone confiável. O Studio não faz commit/push/merge automático, não promove pesos/perfis e bloqueia alterações em arquivos visuais protegidos.

A presença de testes no mapa não significa que foram executados. Fakes testam contratos, não competência cognitiva. Gradle, inferência neural real e teste físico no Poco F6 são gates separados.


A suite integration também inclui KellvynBrainAgentIntegrationContractTest. Ela valida o parser KELLVYN/1 real até delegação e handoff UNTRUSTED usando um ProtocolScriptedEngine determinístico. Isso é um contrato transversal, não E2E com pesos neurais ou Runtime de chat.
