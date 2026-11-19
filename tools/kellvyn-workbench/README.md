# Kellvyn Workbench — bancada externa de engenharia

Esta é a bancada usada para pesquisar, auditar, criar workspaces isolados, executar verificações, depurar logs, comparar imagens e produzir cenas 2D/3D/animadas. Ela não é parte do APK, não implementa o cérebro, não altera agentes do aplicativo e não toca na interface aprovada.

## O que a bancada entrega

- **Auditoria e continuidade:** inventário do repositório, branch/HEAD, estado sujo, arquivos protegidos, ferramentas disponíveis e snapshots datados.
- **Diário encadeado:** cada evento é JSONL com sequência, horário UTC e America/Belem, HEAD, branch, evidências, hash anterior e SHA-256 do próprio evento. `verify-ledger` detecta edição, remoção e reordenação. Hash chain é tamper-evident, não assinatura digital nem garantia contra alguém reescrever todo o arquivo e recalcular a cadeia.
- **Checkpoints:** resumo do trabalho, concluído, bloqueado, próximo passo, HEAD e estado da integridade, em Markdown e JSON.
- **Workspaces isolados:** encaminha a criação para o R&D Studio já existente, que cria branch e Git worktree; não replica seu código nem faz merge automático.
- **Execução de testes:** cada execução recebe run ID, timeout, stdout/stderr separados, exit code, duração e manifest. `PASSED`, `FAILED`, `TIMED_OUT` e `BLOCKED` não são confundidos.
- **Debugger:** triagem de erros Kotlin/Java, stack traces, Python, Gradle, FATAL EXCEPTION e ANR a partir de logs. Não substitui reprodução nem debugger interativo.
- **Visual diff:** compara imagens sem redimensionar; produz overlay, diferença amplificada, lado a lado e métricas. Pillow é opcional.
- **Estúdio criativo Blender:** cria arquivos `.blend` editáveis, previews PNG, GLB em 3D/4D, materiais PBR, câmera, DOF opcional, chão receptor de sombras, rigs Key/Fill/Rim, luz de preenchimento pontual, configurações de render e keyframes. O arquivo `progress.jsonl` acompanha as fases e os artefatos gerados.
- **Web e Android smoke:** podem ser conectados a Playwright/ADB quando as ferramentas e um AVD/dispositivo estiverem disponíveis. A presença dos scripts não comprova que o host possua essas dependências.

## Instalação opcional

Base: Python 3.10+ e Git. Para comparações de imagem: `python -m pip install -r tools/kellvyn-workbench/requirements-visual.txt`. Para auditoria web: Node.js e Playwright/Chromium. Para o estúdio 3D: Blender com o exportador glTF habilitado. Para smoke Android: Android SDK/ADB e AVD compatível.

## Comandos principais

```bash
python3 tools/kellvyn-workbench/workbench.py doctor
python3 tools/kellvyn-workbench/workbench.py audit
python3 tools/kellvyn-workbench/workbench.py verify-ledger
python3 tools/kellvyn-workbench/workbench.py session start --summary "Estudo de agentes e laboratório"
python3 tools/kellvyn-workbench/workbench.py log --kind research --status implemented --summary "Mapeada a arquitetura do orquestrador" --evidence "docs/kellvyn/SESSION_CONTROL.md"
python3 tools/kellvyn-workbench/workbench.py checkpoint --summary "..." --next "..." --done "..." --blocked "..."
python3 tools/kellvyn-workbench/workbench.py worktree --id debugger-tools-001 --title "Debugger tools" --objective "Add log triage and replay evidence" --scope agents
python3 tools/kellvyn-workbench/workbench.py test --suite workbench
python3 tools/kellvyn-workbench/workbench.py debug --log artifacts/workbench/runs/<id>/stderr.log
python3 tools/kellvyn-workbench/workbench.py creative --mode 3d --lighting dramatic --engine cycles --samples 64 --shadow-quality high
```

`run` accepts an explicit argv and never invokes a shell:

```bash
python3 tools/kellvyn-workbench/workbench.py run --name "gradle-unit" --timeout 2400 -- ./android/gradlew testDebugUnitTest --no-daemon
```

## Regras da bancada

1. Confirmar branch, HEAD e diário antes de continuar trabalho antigo.
2. Criar worktree para experiências; não editar a branch estável por conveniência.
3. Nunca sobrescrever arquivo de estado ou evidência sem verificar a versão anterior.
4. Guardar logs brutos e resultados estruturados; não colocar segredos em traces.
5. Marcar teste não executado como `NOT_RUN`/`BLOCKED`, nunca como aprovado.
6. Não executar 3D/Android pesado sem checar ferramenta, memória, espaço e tempo.
7. Preservar `MainActivity.java`, `.github/interface-lock.sha` e a interface aprovada.
8. Uma renderização headless produz artefatos e progresso por fase; não é viewport interativa ao vivo.

## Comandos adicionais

```bash
# Criar e depois validar uma tarefa isolada pelo R&D Studio existente
python3 tools/kellvyn-workbench/workbench.py worktree --id lab-debug-001 --title "Debug tools" --objective "Reproduce and diagnose a build defect" --scope agents
python3 tools/kellvyn-workbench/workbench.py task test --id lab-debug-001 --suite agents --timeout 1800
python3 tools/kellvyn-workbench/workbench.py task check --id lab-debug-001 --require-test agents

# Encaminhar diretamente operações de pesquisa/experimento para a CLI R&D já existente
python3 tools/kellvyn-workbench/workbench.py rnd -- map
python3 tools/kellvyn-workbench/workbench.py rnd -- research --title "..." --source "https://..." --finding "..." --decision "..."

# Depurar log de build ou capturar Logcat de um Android/AVD autorizado
python3 tools/kellvyn-workbench/workbench.py debug --log artifacts/workbench/runs/<id>/stderr.log
python3 tools/kellvyn-workbench/workbench.py debug --adb --serial emulator-5554

# Auditar uma aplicação web já iniciada; não inicia o servidor do projeto
python3 tools/kellvyn-workbench/workbench.py web-audit --url http://127.0.0.1:4173/

# Smoke test Android; requer AVD/dispositivo já iniciado e APK dentro do repositório
python3 tools/kellvyn-workbench/workbench.py android-smoke --apk android/app/build/outputs/apk/debug/app-debug.apk

# Gerar painel local com timeline, runs, logs e previews
python3 tools/kellvyn-workbench/workbench.py report
```

O painel é `artifacts/workbench/dashboard/index.html`. Ele agrega evidências já geradas; não é um serviço hospedado.


## Matriz poliglota e testes do projeto

O comando `doctor` agora apresenta disponibilidade por domínio: Kotlin/Java/Android SDK, TypeScript/TSX, Python, shell/workflows, ADB/dispositivo, visual/mídia e ferramentas candidatas de sandbox. Os estados descrevem detecção e pré-requisitos; não certificam compilação, execução segura ou aprovação.

O gerenciador JS é escolhido pelo lockfile do checkout. Neste repositório, `bun.lock` seleciona Bun; o laboratório não deve substituir silenciosamente esse lockfile por npm/yarn.

Suites para a aplicação Web/TSX de referência:

```bash
python3 tools/kellvyn-workbench/workbench.py test --suite web-typecheck
python3 tools/kellvyn-workbench/workbench.py test --suite web-unit
python3 tools/kellvyn-workbench/workbench.py test --suite web-lint
python3 tools/kellvyn-workbench/workbench.py test --suite web-build
```

Pré-condições ausentes são classificadas como `BLOCKED`, não como falha do código. A Web continua sendo referência de interface; estas suites não autorizam portar WebView para o APK nativo.

**Limite de segurança:** Git worktree não é isolamento de sistema operacional. A detecção de Docker/Podman é apenas inventário; não significa que um sandbox seguro esteja configurado. Não executar código não confiável em host persistente.


### Segurança do executor (alteração em revisão)

- Os processos filhos recebem apenas variáveis ambientais essenciais; variáveis com nomes de credenciais não são repassadas por padrão.
- Valores comuns de tokens/senhas/chaves são redigidos antes de persistir logs e manifests.
- Timeout em POSIX encerra o grupo de processos para evitar subprocessos de Gradle/testes sobrevivendo.
- Isto reduz exposição acidental; não fornece isolamento de filesystem/rede nem substitui sandbox. Código não confiável continua bloqueado até existir executor descartável apropriado.


## Navegação semântica TS/TSX

Com as dependências instaladas pelo `bun.lock`, o mapa usa o TypeScript Compiler API do próprio projeto para relacionar declarações, tipos, imports, chamadas, referências e arquivos de teste. Ele marca relações de projeto e externas separadamente e reporta imports não resolvidos.

```bash
python3 tools/kellvyn-workbench/workbench.py typescript-map --fail-on-diagnostics
python3 tools/kellvyn-workbench/workbench.py test --suite web-map-tests
```

O mapa é estático: não prova que os testes referenciados foram executados nem que uma função funciona em runtime. A próxima frente semântica é Kotlin; o experimento `kotlin-semantic-index-001` compara Analysis API Standalone com o Kotlin Language Server oficial sem alterar o APK.

**Inventário atual:** 31 métodos Python na suíte principal da Workbench, 14 testes do parser de animação Android, 20 métodos da R&D Studio e dois casos Node para o mapa TypeScript. São testes escritos; o snapshot do GitHub Actions continua sem steps executados.


## Analisador semântico Kotlin (pré-requisitos)

A Workbench reconhece o Kotlin Language Server opcionalmente, mas não instala nem inicia o servidor sozinha. O release standalone revisado exige JDK 25; o build Android da Kellvyn permanece em JDK 17. Não altere `JAVA_HOME` global para satisfazer o LSP.

Configure o caminho do JDK 25 e, opcionalmente, o caminho explícito do launcher quando ele não estiver no `PATH`:

```bash
export KOTLIN_LSP_JAVA_HOME=/opt/jdk-25
# Opcional: quando bin/intellij-server não estiver no PATH
export KOTLIN_LSP_EXECUTABLE=/opt/kotlin-lsp/bin/intellij-server
python3 tools/kellvyn-workbench/workbench.py doctor
```

Estados:
- `OPTIONAL_NOT_INSTALLED`: servidor não detectado e sem override de caminho;
- `BLOCKED_LSP_EXECUTABLE_MISSING`: caminho explícito configurado, mas arquivo não executável;
- `BLOCKED_SEPARATE_JDK25_NOT_CONFIGURED`: servidor detectado, variável do JDK separado ausente;
- `BLOCKED_JDK25_REQUIRED`: JDK configurado não é 25;
- `READY_UNVALIDATED`: binário + JDK 25 detectados, mas importação Android e queries semânticas ainda não validadas.

A presença do executável e JDK só libera o próximo experimento; não significa que o LSP consiga importar AGP 8.13.2 ou resolver os símbolos do projeto. O experimento `kotlin-semantic-index-001` permanece PLANNED. O LSP tem importação Android experimental e continua em alpha; mantenha a decisão dependente de medições de precisão, cold start e RSS.


## Laboratório de animação e frame timing Android

O comando captura um cenário que **já está aberto** no app e exige um serial ADB exato. Não instala APK, não inicia o aplicativo, não toca na tela e não altera configurações. Ele grava vídeo temporário, coleta `dumpsys gfxinfo ... framestats`, memória, bateria, temperatura e display, puxa o vídeo para um diretório local e extrai quadros com FFmpeg quando disponível.

```bash
# Exemplo AVD 60 Hz: limite de referência ~16,67 ms por frame
python3 tools/kellvyn-workbench/workbench.py android-animation-audit \
  --serial emulator-5554 --duration 15 --frame-sample-fps 5 \
  --frame-budget-ms 16.67

# Exemplo painel 120 Hz: limite de referência ~8,33 ms por frame
python3 tools/kellvyn-workbench/workbench.py android-animation-audit \
  --serial <serial-autorizado> --duration 15 --frame-sample-fps 5 \
  --frame-budget-ms 8.33
```

O app precisa permanecer em primeiro plano durante a captura. O vídeo grava a tela inteira; feche notificações e conteúdo privado. O orçamento de frame é explícito porque o laboratório não deve presumir 60 ou 120 Hz. Os estados `CAPTURED`, `PARTIAL` e `BLOCKED` são distintos: `CAPTURED` confirma apenas que os artefatos foram coletados; `quality_assessment` permanece `NOT_EVALUATED`. Ausência do FFmpeg ou de métricas válidas gera parcial, nunca aprovação. O vídeo e os quadros recebem SHA-256 no manifesto. O relatório de `gfxinfo` não substitui Perfetto FrameTimeline nem Macrobenchmark; esta é a primeira etapa de diagnóstico externo, não um teste visual completo dos efeitos simultâneos.
