# Kellvyn R&D Studio

Bancada externa para estudar e desenvolver o cérebro e os agentes Kellvyn, e validar os contratos entre eles. Não é tela do app nem o Agent Lab Android.

Três frentes:
- KN: tokenizer, KN-001/002/003, treinamento, pesos, inferência e KELLVYN/1.
- Agents: KellvynAgent, KellvynOrchestrator, 14 perfis, políticas de ferramentas, planner, traces e avaliação.
- Integration: inferência -> decisões -> loop do agente -> ferramentas/delegação -> verificador.

Comandos:

    python3 tools/kellvyn-rnd-studio/studio.py track-map --scope kn
    python3 tools/kellvyn-rnd-studio/studio.py track-map --scope agents
    python3 tools/kellvyn-rnd-studio/studio.py track-map --scope integration

    python3 tools/kellvyn-rnd-studio/studio.py task create --id agent-delegation-duration-001 --scope agents --title "Delegation duration" --objective "Measure specialist dispatch duration" --require-test agents
    python3 tools/kellvyn-rnd-studio/studio.py task test --id agent-delegation-duration-001 --suite agents
    python3 tools/kellvyn-rnd-studio/studio.py task check --id agent-delegation-duration-001

    python3 tools/kellvyn-rnd-studio/studio.py experiment create --id agent-routing-001 --scope agents --title "Agent routing" --hypothesis "Each role receives only permitted tools"

Suítes kn, agents e integration selecionam classes Kotlin com Gradle --tests. As suítes unit, assemble e instrumented continuam disponíveis. A suíte integration atual é de contrato unitário; não prova que o chat esteja conectado ao cérebro real. KellvynRuntime.isReady() continua false.

Cada tarefa cria branch e Git worktree fora da árvore principal. Alterar código depois do teste invalida a evidência. Worktree não é sandbox de sistema operacional; Gradle executa scripts do repositório, portanto use somente clone confiável. O Studio não faz commit/push/merge automático, não promove pesos/perfis e bloqueia alterações em arquivos visuais protegidos.

A presença de testes no mapa não significa que foram executados. Fakes testam contratos, não competência cognitiva. Gradle, inferência neural real e teste físico no Poco F6 são gates separados.


## Experimentos recentes da oficina

Os candidatos KN, Agents e Integration estão isolados nas branches kellvyn-rnd/* e não foram fundidos. A versão experimental mais recente do Studio na branch kn-agent-contract-001 também adiciona:
- associação fonte→teste por símbolos mencionados, e não apenas pelo nome do arquivo;
- teste do wrapper Gradle simulado e do gate de evidência;
- teste de saída não-zero registrada como FAILED, nunca PASSED.

O snapshot anterior da CLI teve py_compile e 13/13 testes aprovados localmente. A inspeção do arquivo atual encontrou 20 métodos `test_*`. O antigo número 16 e a lista anterior de três testes pendentes ficaram desatualizados. A contagem de 20 é inventário de código, não execução: os 20 precisam ser executados no snapshot atual. O último resultado confirmado (13/13) pertence a um snapshot anterior. O CI segue bloqueado antes de qualquer step por runner_id=0. Ver o diagnóstico em docs/kellvyn/rnd-studio/GITHUB_ACTIONS_RUNNER_DIAGNOSTIC_2026-11-18.md.
