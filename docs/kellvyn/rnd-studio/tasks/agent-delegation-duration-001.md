# Agent R&D — delegação com duração monotônica

**Estado:** IMPLEMENTED_NOT_RUN  
**Branch:** kellvyn-rnd/agent-delegation-duration-001  
**Base:** da5da9a895f229c7a63344953af8d2554bedc4cf

## Lacuna observada

KellvynOrchestrator.Session.dispatch() registrava DELEGATION_STARTED, mas o DELEGATION_FINISHED normal passava durationMillis = null. O caminho de cancelamento também não calculava duração. O Agent Lab não conseguia medir a latência por especialista.

## Candidato implementado

- KellvynOrchestrator agora recebe monotonicNanos: () -> Long, padrão System.nanoTime.
- O tempo inicial é capturado ao começar a delegação.
- Os eventos terminais normais e de cancelamento recebem elapsedMillis.
- Relógio monotônico evita distorção por correções de relógio civil.
- Cancelamento segue sendo propagado depois do evento gravado em NonCancellable.

## Testes escritos

1. recordsMonotonicDurationForCompletedSpecialistDelegation: verifica 8 ms com relógio controlado.
2. recordsMonotonicDurationWhenSpecialistIsCancelledAndPropagatesCancellation: verifica 5 ms, detailCode=CANCELLED e que o cancelamento não é engolido.

Sem sleep; relógio determinístico evita flakiness de temporização real.

## CI

O workflow .github/workflows/agent-lab-tests.yml passou a incluir kellvyn-rnd/**, para que a branch de experimento execute os testes focados.

Run 44401916646:
- Minimal runner allocation probe (ubuntu-slim): failure, runner_id=0, steps=[].
- Unit and Android instrumented tests (ubuntu-latest): failure, runner_id=0, steps=[].

Interpretação: nenhum step/Gradle foi executado; não é evidência de falha de código nem de aprovação. Estado permanece IMPLEMENTED_NOT_RUN.

## Gate seguinte

Executar, nesta branch, a suíte focada agents com Gradle/JUnit. Só aceitar o candidato se:
- build/compilação passa;
- os dois testes novos passam;
- a bateria KellvynOrchestratorTest existente passa;
- traces terminais têm duração não nula e não negativa;
- cancelamento continua propagado;
- política, perfis, orçamento e resposta do especialista não mudam.

Não fazer merge antes desse gate. O cérebro, pesos, Runtime, política/perfis, interface e MainActivity não foram tocados.
