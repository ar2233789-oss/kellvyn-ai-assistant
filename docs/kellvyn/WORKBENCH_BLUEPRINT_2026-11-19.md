# Kellvyn Workbench — bancada externa de engenharia e pesquisa

**Data:** 19/11/2026  
**Branch-alvo:** `kellvyn-rnd/agent-trace-duration-port-20261118`  
**HEAD-base inspecionado:** `d19ac2a653ed75873ad03d70f9c85d3519f678db`  
**Finalidade:** equipar o ambiente usado pelo assistente para construir, estudar, testar e depurar os agentes e os sistemas Kellvyn. Este documento não descreve uma tela do APK nem a implementação do cérebro.

## 1. Escopo correto

A bancada externa é o conjunto de instrumentos do engenheiro: pesquisa e registro de fontes, inventário do repositório, workspaces isolados, execução reproduzível, diagnóstico, comparação visual, produção de assets, acompanhamento e checkpoints. Ela deve reduzir perda de contexto, retrabalho e alterações destrutivas.

Não confundir:
- **Workbench:** ferramentas de pesquisa e engenharia usadas para trabalhar no código e produzir evidências.
- **R&D Studio existente:** cria tarefas/branches/worktrees e executa suítes de Brain, Agents e Integration. O Workbench o invoca; não recria sua lógica.
- **Agent Lab Android:** infraestrutura dentro do aplicativo para observar as execuções futuras dos agentes.
- **Cérebro e agentes:** software-alvo do estudo. Não são implementados por esta bancada.

## 2. Instrumentos incorporados

| Instrumento | Função operacional | Evidência gerada |
|---|---|---|
| Doctor/toolchain inventory | Detectar Git, Python, Java, Gradle, ADB, Blender, Node, FFmpeg e dependências opcionais | JSON com versões e `NOT_INSTALLED` explícito |
| Repository audit | Registrar branch, HEAD, dirty state, inventário Kotlin/testes e hashes protegidos | Snapshot JSON + Markdown datado |
| Session ledger | Registrar pesquisa, decisão, implementação, teste, incidente e handoff, com UTC/local, HEAD, branch e cadeia SHA-256 | `workbench-ledger.jsonl` verificável |
| Checkpoint | Preservar resumo, concluído, bloqueado, próximo passo e estado do ledger | Markdown + JSON |
| Isolated tasks | Usar o R&D Studio atual para worktree/branch por tarefa | Evidência do worktree e suíte associada |
| Execution runner | Executar argv sem shell, limitar timeout, gravar stdout/stderr, exit code e duração; transmitir eventos de progresso | Manifesto por run + logs brutos |
| Debugger triage | Interpretar erro Kotlin/Java, stack trace, Python, Gradle, FATAL EXCEPTION e ANR; opcionalmente obter Logcat/last ANR via ADB | Relatório JSON com arquivo/linha e hipóteses |
| Visual diff | Comparar imagens do mesmo tamanho sem redimensionar | Overlay, diferença amplificada, lado a lado e métricas |
| Web audit | Navegar em viewport mobile, capturar telas, validar controles e erros JavaScript com Playwright | Screenshots + JSON de checks |
| Android smoke | Instalar APK em AVD/dispositivo já iniciado, abrir, capturar screenshot, árvore de acessibilidade, memória, crash buffer e Logcat em falha | Screenshots, XML, logs e JSON de status |
| Creative / Blender studio | Criar cenas 2D, 3D e 3D animadas no tempo; materiais PBR, câmera, DOF, rigs de luz, sombras e exportação | `.blend`, PNG, GLB, manifest e progresso JSONL |
| Dashboard | Unificar timeline, checkpoints, status de testes, logs e previews | `artifacts/workbench/dashboard/index.html` |

## 3. Auditoria que impede perda de trabalho

Cada sessão recebe um ID e eventos sequenciais. Cada evento tem:
- sequência monotônica;
- data/hora UTC e America/Belem;
- sessão, tipo, status e resumo técnico;
- branch e HEAD observados;
- arquivos/artefatos e evidências;
- hash do evento anterior e SHA-256 do conteúdo atual.

`verify-ledger` detecta alterações, exclusões, inserções ou reordenação acidental. A cadeia é tamper-evident, não assinatura criptográfica: alguém com acesso pode reescrever todo o arquivo e recalcular os hashes. Para auditoria mais forte, espelhar periodicamente o hash final em um commit protegido ou armazenamento assinado.

A regra de continuidade é: ler o último checkpoint, conferir o HEAD real, validar o ledger, revisar o diff e somente então continuar. Não sobrescrever um checkpoint ou artefato anterior sem criar uma nova versão.

## 4. Depuração real, não apenas procurar palavras

A triagem coleta caminhos e linhas de compilador, exceções e frames da pilha. No Android autorizado, captura Logcat, crash buffer e `dumpsys activity lastanr`. A sequência de depuração é:

1. preservar log original e ambiente/versão;
2. localizar a primeira falha causal, não apenas a última mensagem;
3. identificar módulo, arquivo, linha, chamada e dependência;
4. reproduzir em worktree e salvar o caso mínimo;
5. aplicar patch pequeno;
6. executar o teste que reproduz a falha;
7. executar regressão específica;
8. anexar diff, logs e estado do teste ao ledger.

A triagem por regex é uma primeira camada. Ela não é um debugger interativo, não garante causa raiz e não substitui breakpoints/profiler. ADB só pode ler o que o Android permite; Logcat completo do sistema não está garantido a um app comum.

## 5. Estúdio visual e produção 3D

O pipeline Blender é executado headless no host ou runner compatível — não embutido no APK. Oferece:
- 2D flat layout, 3D editável e 4D como 3D animado ao longo do tempo;
- malhas procedurais, bevel, materiais Principled PBR e fundo/world;
- câmera ortográfica/perspectiva e DOF opcional;
- luz principal selecionável entre AREA, SUN e SPOT, com energia, tamanho/ângulo;
- fill e rim AREA, luz pontual de acento e plano de chão receptor de sombras;
- presets studio, dramatic, soft e sunset;
- Eevee ou Cycles, nível de amostras/resolução e qualidade de sombra;
- renders preview, frames de animação, `.blend` e export GLB;
- `progress.jsonl` e mensagens `WORKBENCH_PROGRESS` transmitidas durante as fases.

O progresso atual é por etapas (geometria, iluminação, render, exportação), não barra de progresso por pixel. Headless não fornece viewport manipulável em tempo real; o `.blend` é a cena editável e os PNGs são previews para revisão.

## 6. Testes e limites de confiança

- `PASSED` exige exit code 0 do processo real.
- `FAILED` exige código de saída diferente de zero ou exceção do processo.
- `TIMED_OUT` termina o processo e preserva logs.
- `BLOCKED` indica executável/dispositivo ausente; não é aprovação.
- `NOT_RUN` deve permanecer explícito quando não há executor.
- Um inventário não é compilação; teste escrito não é teste executado; AVD não comprova performance física do Poco F6.
- Saídas criativas devem ter manifest, tamanho e SHA-256; previews sem fonte/arquivo editável não contam como projeto 3D concluído.

## 7. Limites e bloqueios conhecidos na inspeção de 19/11/2026

- O container de trabalho tinha Git, Python 3.13.5, Java 21, Node 22 e FFmpeg; não tinha Gradle, ADB nem Blender.
- Pillow e Playwright estavam disponíveis no container.
- O Actions observado anteriormente falhou antes de iniciar steps (`runner_id=0`, `steps=[]`). A nova workflow registra a validação, mas só produzirá logs quando houver runner alocado.
- A ferramenta Android exige SDK/ADB e AVD/dispositivo já inicializado.
- Blender exige binário no host; a ferramenta pode preparar a cena, mas sem Blender não há render real.

## 8. Comandos de entrada

A única entrada operacional recomendada é `tools/kellvyn-workbench/workbench.py`. O README lista comandos para doctor, audit, sessão, diário, checkpoint, worktree, task test/check, R&D Studio, execução com timeout, debugger, web audit, Android smoke, creative render, visual diff e dashboard.

**Próxima regra de trabalho:** antes de criar ferramenta nova, consultar este inventário e o `doctor`. Antes de afirmar que uma capacidade existe, distinguir `declared`, `registered`, `available`, `executed` e `verified`.
