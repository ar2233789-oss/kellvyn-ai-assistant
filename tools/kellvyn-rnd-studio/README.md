# Kellvyn R&D Studio

Esta é a bancada de engenharia para estudar e melhorar o cérebro Kellvyn. Ela serve ao trabalho de desenvolvimento do assistente sobre o repositório. Não é uma tela do app, não é o Agent Lab Android, não cria outro cérebro e não troca o orquestrador pelo cérebro.

Capacidades implementadas:
- mapa estático de fontes Kotlin, símbolos, imports, hashes e testes candidatos;
- worktree e branch Git isolados por tarefa/hipótese;
- registro persistente de pesquisa e experimentos;
- execução de três suítes Gradle permitidas: unit, assemble e instrumented;
- gate de diff, arquivos protegidos e evidência de testes.

Requisitos: clone local do repositório, Python 3.10+ (stdlib only), Git com worktree e ambiente JDK/Android SDK quando executar Gradle.

Comandos, a partir da raiz do clone:

    python3 tools/kellvyn-rnd-studio/studio.py map

    python3 tools/kellvyn-rnd-studio/studio.py task create --id brain-attention-001 --title "Study attention" --objective "Verify attention math and tests" --require-test unit

    python3 tools/kellvyn-rnd-studio/studio.py task test --id brain-attention-001 --suite unit
    python3 tools/kellvyn-rnd-studio/studio.py task check --id brain-attention-001

    python3 tools/kellvyn-rnd-studio/studio.py research --topic neural-core --title "Research finding" --source "https://example.org/paper" --finding "Finding in own words" --decision pending

    python3 tools/kellvyn-rnd-studio/studio.py experiment create --id kv-cache-001 --title "KV cache equivalence" --hypothesis "Cached decode matches full-prefix output"
    python3 tools/kellvyn-rnd-studio/studio.py experiment record --id kv-cache-001 --variant baseline --metric token_match --value 1.0 --unit ratio --evidence "path/to/log"

Cada tarefa vai para um checkout fora da árvore versionada do repositório, numa branch kellvyn-rnd/<id>. O gate exige que os testes tenham exit code 0, log íntegro, HEAD e fingerprint do workspace ainda iguais ao estado testado. Alterar o código depois do teste invalida a evidência e obriga a executar novamente.

O teste Gradle executa scripts de build do próprio repositório e não é um sandbox de sistema operacional. Usar somente em clone confiável. O Studio não faz commit, push, merge nem promoção automática. Não edita arquivos visuais protegidos: tira snapshot dos hashes definidos em .github/interface-lock.sha e bloqueia se mudarem.

A suíte unit testada nesta entrega é a do próprio Studio, em repositórios Git temporários. Isso não equivale a Gradle/JUnit/Android ter executado no Kellvyn. O mapa é estático e não prova ligação de runtime. O Studio não treina pesos automaticamente, não habilita KellvynRuntime.isReady() e não declara o cérebro pronto.
