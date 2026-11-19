# Arquitetura-alvo do Laboratório Kellvyn — v2 poliglota

**Data:** 19/11/2026  
**Branch:** `kellvyn-rnd/polyglot-lab-foundation-20261119`  
**Estado:** especificação de engenharia; não declarar módulos planejados como implementados.  
**Base auditada:** `kellvyn-rnd/agent-trace-duration-port-20261118`, commit `ce0d7170c576e50fcb852d27aedfd4cae3d45caa`.

## 1. Decisão central

O laboratório deixa de ser tratado como uma CLI Python que executa alguns testes. O alvo é uma **plataforma local de pesquisa, engenharia e validação poliglota**, com um núcleo coordenador pequeno e adaptadores especializados por linguagem, artefato e ambiente de execução.

Python pode continuar como uma das linguagens do plano de controle, porque é útil para automação e análise de dados. Ele não substitui nem limita o cérebro Kellvyn: o modelo, tokenizer, treinamento e inferência próprios continuam no ecossistema Kotlin/JVM/Android. Ferramentas de referência em Python podem servir como oráculos numéricos independentes, nunca como implementação oculta do cérebro de produção.

O objetivo é equipar o processo de criação: estudar, projetar, mapear dependências, produzir patches isolados, compilar, executar, depurar, medir, comparar, revisar e fazer rollback com evidência rastreável.

## 2. Limites arquiteturais que não podem ser confundidos

| Parte | Responsabilidade | Não é |
|---|---|---|
| Cérebro KN | Tokenizer, pesos, treinamento, inferência e decisões KELLVYN/1 | Orquestrador ou Agent Lab |
| KellvynAgent | Loop de controle que consome decisões de uma inferência injetada | Modelo neural |
| KellvynOrchestrator | Política, decomposição operacional, despacho de especialistas, limites e handoffs | Cérebro |
| 14 especialistas | Papéis com missões, contexto, capacidades e ferramentas filtradas | 14 modelos independentes |
| Agent Creation Studio | Definir, versionar, revisar e aprovar perfis | Executor de experimentos |
| Agent Lab Android | Capturar traces, avaliar execuções e recursos dentro do app | Oficina de engenharia externa |
| Engineering Workbench | Auditoria, execução instrumentada, depuração, evidência e relatórios | Ambiente isolado de segurança por si só |
| R&D Studio | Workspaces, hipóteses e suítes de pesquisa Brain/Agents/Integration | Uma simples suíte Python |
| Laboratório-alvo v2 | Coordenar os adaptadores e gates de todo o ciclo de engenharia | Um novo cérebro ou uma nova UI |

O chat de produção permanece desconectado enquanto não houver checkpoint treinado, avaliação comportamental independente e os gates de integração descritos na auditoria de prontidão.

## 3. Arquitetura em camadas

### Camada A — Control Plane e orquestração de trabalho

Responsável por coordenar tarefas de engenharia, não por gerar respostas do usuário.

Cada execução recebe um manifesto versionado com:
- `task_id`, `experiment_id`, tarefa-pai e IDs de subtarefas;
- objetivo, escopo, requisitos, restrições, pressupostos e critérios de aceite;
- branch, commit-base, worktree e hashes dos arquivos de entrada;
- linguagens, ferramentas e versões exigidas;
- orçamento de tempo, CPU, RAM, armazenamento, temperatura e concorrência;
- política de rede, permissões, classificação de risco e política de segredos;
- comandos exatos, timeout, artefatos esperados e verificadores;
- status, checkpoint, tentativas, causa de bloqueio e evidência vinculada.

Estados padronizados: `PROPOSED`, `READY`, `RUNNING`, `PAUSED`, `BLOCKED`, `REVIEW_REQUIRED`, `PASSED`, `FAILED`, `CANCELLED`, `NOT_RUN`. Um estado terminal não pode ser alterado por uma simples resposta textual do agente.

O plano deve ser um DAG tipado: tarefas independentes podem ser paralelizadas quando o orçamento permitir; tarefas dependentes aguardam pré-condições. Por padrão, no perfil móvel, uma tarefa pesada de cada vez. Retry só para operações idempotentes ou explicitamente marcadas como repetíveis. Build, benchmark e treinamento precisam registrar seed/configuração e não reaproveitar resultados de outra versão do código.

A execução precisa suportar checkpoint e retomada. Checkpoint guarda estado serializável mínimo, versão do schema, eventos já confirmados e hashes de artefatos; retomada nunca repete uma mutação não idempotente sem reconciliação e nova autorização.

### Camada B — Inteligências de planejamento e estudo

Papéis de laboratório propostos (não confundir com os 14 especialistas do produto):

1. **Research Lead:** pesquisa documentação técnica, padrões, alternativas, versões, licenças, APIs e limitações; entrega uma matriz fonte → afirmação → data/versão → confiança → implicação para Kellvyn.
2. **Architecture Critic:** recebe requisitos e evidências, procura falhas de premissa, dependências ocultas, riscos, alternativas e contraprovas; não aprova sozinho o próprio trabalho.
3. **Implementation Planner:** transforma uma decisão aprovada em DAG de tarefas, pré-condições, interfaces, arquivos prováveis e testes de aceitação.
4. **Independent Verifier:** verifica artefatos, logs e estados reais com oráculos determinísticos ou revisão humana; jamais confia no resumo do executor.

**Limite honesto:** hoje o projeto possui perfis especialistas que compartilham o backend de inferência; não possui um segundo modelo autônomo comprovado nem o cérebro de produção pronto. Esses papéis podem estruturar tarefas separadas e análises críticas, mas duas execuções do mesmo backend não são independência de modelo. A arquitetura deve permitir um segundo backend no futuro sem torná-lo dependência paga ou obrigatória. Até lá, a verificação independente é feita por testes, ferramentas determinísticas e revisão do proprietário.

### Camada C — Catálogo de adaptadores poliglotas

O control plane não deve entender detalhes internos de cada compilador. Cada adaptador declara um contrato comum:

- `adapter_id`, linguagem/artefato e versão suportada;
- detecção do ambiente e pré-requisitos;
- parser sintático e, quando possível, resolvedor semântico;
- formatter, linter, typechecker e compilador;
- testes unitários, funcionais, integração e E2E;
- depuração, coleta de logs e símbolos;
- métricas e artefatos produzidos;
- perfil de isolamento, rede e recursos;
- limitações conhecidas e nível de confiança da análise.

**Matriz do repositório atual:**

| Domínio | Fonte real no repositório | Ferramentas específicas a integrar | Evidência final |
|---|---|---|---|
| Kotlin / Android | `android/**/*.kt` | Gradle 8.13, JDK 17, Kotlin 2.3.20, compilação/testes; K2/PSI e Kotlin Analysis API para navegação semântica | Compilação, JUnit, instrumentados, APK e AVD |
| Java / Android | `MainActivity.java` e código Java | javac/ECJ, análise de chamadas, lint e testes JVM/Android | Compilação Java + APK + teste de comportamento |
| TypeScript / TSX | `src/**/*.ts(x)`, `package.json` | TypeScript Compiler API, tsc, ESLint, Vitest, Vite e Playwright | typecheck, lint, unit, build e auditoria visual |
| Python | `tools/**/*.py` | AST, py_compile, unittest/pytest; Ruff/mypy como ferramentas opcionais | logs de teste, cobertura e saída estruturada |
| JavaScript / MJS | scripts de ferramenta | Node, parser TS/JS, ESLint e Playwright | syntax check, testes e execução browser |
| Shell | `*.sh`, workflows | bash -n, ShellCheck e Bats quando presentes | análise estática + execução isolada |
| XML / Manifest / recursos | `AndroidManifest.xml`, `res/**` | parser XML, validação Android, aapt2 e lint | recursos/manifest válidos e pacote |
| JSON / YAML / Gradle | configs, workflows e scripts | JSON Schema, parser YAML, actionlint e Gradle model/build | schema, workflow e configuração válidos |
| Assets / visual / mídia | PNG, SVG, CSS, animações e cenas | Playwright, visual diff, Blender e FFmpeg conforme o alvo | screenshots, frames, diffs e manifestos |

A matriz deve ser descoberta no checkout real: `DETECTED` não significa executável; `EXECUTABLE` não significa que dependências estejam satisfeitas; `PASSED` exige um run real.

#### Estratégia de análise de código

- Tree-sitter é a camada rápida e multi-linguagem para árvores sintáticas incrementais e fontes parcialmente inválidas.
- A análise semântica continua específica: Kotlin Analysis API/K2 para símbolos e tipos Kotlin; TypeScript Compiler API para TS/TSX; javac/ECJ para Java; AST para Python.
- KSP pode inspecionar declarações e tipos, mas não substitui inspeção de expressões e corpos de funções.
- Regex serve apenas para busca inicial e heurísticas claramente rotuladas; nunca é fonte final para grafo de chamadas, tipos ou dependências.
- O grafo de impacto deve ligar símbolo → arquivo → import/call/reference → teste candidato → módulo → workflow, com a origem da relação identificada (semântica, AST, import ou heurística).

### Camada D — Ferramentas de design e engenharia de software

O laboratório precisa de capacidades distintas, e não apenas um comando genérico:

- **Requirements & architecture:** requisitos verificáveis, ADRs, diagramas de sequência/estado, matriz de dependências, contratos de APIs e análise de impacto.
- **Code navigation:** símbolos, chamadas, referências, herança, módulos, recursos e testes relacionados.
- **Implementation:** patches pequenos, arquivos novos, refatorações limitadas, geradores de código e migrações.
- **Build:** toolchain identificada, cache por versão, build incremental e limpo, APK/artefatos assinados e verificados.
- **Debugging:** stack traces, crashes, ANRs, logs de compilador, dumps, traces de método e reprodução mínima.
- **Security:** threat model, análise de permissões, segredos, dependências, prompt injection, sandbox e revisão de supply chain.
- **Design/visual:** inspeção de screenshots, geometria, acessibilidade, estados de tela, diffs, temas e regressões.
- **Animation:** captura em tempos/quadro determinados, análise de frame pacing, jank, continuidade, clipping, layering e combinações de efeitos.
- **Documentation/research:** fontes primárias, datas/versões, licença, changelog, comparação de alternativas e registro de decisões.
- **Patch/update:** diff, hash, testes vinculados ao HEAD, revisão independente, aprovação explícita, PR e rollback.

### Camada E — Laboratórios de execução e validação

**E1. Kotlin/Android**
- preflight JDK, Gradle, Android SDK, build-tools, platform e ADB;
- compilar por módulo e executar testes JVM;
- instrumentados em AVD/API fixada;
- validar manifesto, assinatura, package ID e conteúdo do APK;
- instalar/abrir/interagir via ADB + Espresso/UI Automator quando disponível;
- medir startup, jank, rendering, memória, CPU e temperatura;
- reservar validação física do Poco F6 como gate separado.

**E2. TS/TSX/Web de referência**
- instalar dependências a partir do lockfile verificado;
- TypeScript typecheck, ESLint, Vitest e Vite build;
- Playwright para comportamento e screenshot tests;
- manter screenshots determinísticas por ambiente e separar regressão funcional de diferença visual;
- usar a interface Web/Lovable apenas como referência, sem converter isso em WebView no APK final.

**E3. Cérebro neural**
- testes determinísticos de tokenizer, máscara causal, dimensões, softmax, atenção e atualização de parâmetros;
- gradcheck por grupo de tensor, incluindo gradientes zero e não zero, seeds e comparação central finita;
- comparar KV cache com inferência por prefixo completo em cada token/logit;
- corpus com proveniência, direitos, deduplicação, TRAIN/VALIDATION/TEST sem vazamento;
- medir loss, generalização, tarefas held-out em português, formato KELLVYN/1, recusa de saídas malformadas e comportamento por ferramenta;
- uma referência numérica Python pode verificar kernels pequenos, mas o resultado de produção deve ser reproduzido no Kotlin;
- nenhum checkpoint é promovido apenas por loss ou por benchmark sintético.

**E4. Agentes/orquestração**
- testar cada papel e o conjunto: roteamento, ferramenta correta, argumentos/schema, política, contexto mínimo, vazamento, cancelamento, retries, timeout, conflitos, handoff UNTRUSTED e verificador;
- registrar trace de ponta a ponta e spans para modelo, ferramenta, agente-filho, guardrail e verificação;
- usar datasets repetíveis, rubricas objetivas, testes adversariais, pass^k, mediana/dispersão e taxa de regressão;
- separar contrato com fake, execução de ferramenta real, competência cognitiva e aprovação humana.

**E5. Animação e fidelidade visual**
- para a web de referência: Playwright com viewport, dispositivo, escala, tema e baseline versionados;
- para Android Views/Java atuais: Espresso/UI Automator, captura de screenshots/frames e system trace; não adicionar Compose apenas para testar animações;
- se Jetpack Compose for usado em um módulo futuro, o relógio virtual do Compose permite avançar animações por frame e validar estados intermediários determinísticos;
- testes precisam verificar também efeitos simultâneos, clipping, layering, transições, orientação, densidade, fontes, estado de bateria e tema;
- screenshots não provam comportamento, e teste comportamental não prova fidelidade visual: são gates separados.

### Camada F — Observabilidade, evidência e reprodutibilidade

Todo run deve preservar:
- `run_id`, tarefa, tentativa, trace pai/filhos e horário UTC;
- commit/HEAD, diff hash, versões de runtime e toolchain;
- parâmetros, seed, configuração de benchmark e versão de dataset;
- comandos normalizados, redigidos e identificadores de ferramenta (nunca credenciais);
- eventos de início/fim, duração, exit code, timeout/cancelamento e classificação de bloqueio;
- artefatos com hash, logs stdout/stderr, relatórios JUnit, screenshots, traces e métricas;
- consumo e limites observados, com origem/unidade e intervalo de coleta;
- relação exata entre evidência e snapshot do código.

Adotar semântica compatível com spans de agente/ferramenta para exportação e inspeção, sem exigir um servidor remoto. No app Android, preferir telemetria compacta e sob demanda; no laboratório externo, exportar traces/JSON para análise rica.

### Camada G — Segurança e isolamento

**Um Git worktree não é sandbox de sistema operacional.** O executor atual inicia processos no host, herda o ambiente do processo e não prova isolamento de rede/arquivos. Até corrigir isso:
- não executar patches ou scripts não confiáveis em host persistente;
- remover segredos do ambiente do processo filho por padrão;
- não permitir acesso à rede por padrão; rede exige autorização explícita e domínio/objetivo delimitado;
- não montar diretórios pessoais, keystores, tokens ou dados privados no worker;
- usar executor descartável, sem privilégios, com limites de CPU/RAM/PIDs/tempo/disco e filesystem mínimo;
- preferir runner efêmero ou VM/contêiner descartável com imagem/toolchain fixada e artefatos exportados;
- não usar o Poco F6 pessoal como runner remoto/self-hosted;
- não ativar cobrança, publicar repositório ou contratar serviço para contornar ausência de runner sem aprovação;
- se o isolamento adequado não existir, classificar execução como `BLOCKED`, não improvisar um falso sandbox.

### Camada H — Política de recursos e execução móvel

- O APK de produção não incorpora IDE, compiladores host, Blender, Playwright ou ferramentas de pesquisa.
- O dispositivo executa apenas inferência/treino autorizados e tarefas que caibam no orçamento térmico/energético.
- O laboratório de engenharia fica fora do caminho crítico do chat.
- Concorrência inicial: 1 tarefa pesada; aumentar apenas após medir RSS/PSS, heap, CPU, tempo, bateria e temperatura.
- Carregar adaptadores sob demanda; descarregar processos e caches após a tarefa; impor limites de contexto/saída/arquivos e timeout.
- Cada benchmark registra o modo de energia, estado térmico inicial e condições do dispositivo.
- Falha de telemetria não deve travar o agente, mas invalida qualquer conclusão que dependa daquela métrica.

## 4. Pipeline obrigatório para qualquer patch

1. **Intake:** capturar objetivo, requisitos, restrições, arquivos protegidos, aceite e risco.
2. **Research:** coletar documentação primária atual, changelog e versões; separar fato, hipótese e opinião.
3. **Architecture review:** mapear dependências e propor alternativas; registrar ADR e hipótese falsificável.
4. **Plan:** gerar DAG de tarefas, adaptadores, ferramentas, recursos e critérios por etapa.
5. **Baseline:** confirmar branch/HEAD, auditoria, testes atuais, estado visual protegido e hashes.
6. **Workspace:** criar worktree; executar apenas em ambiente apropriado para o risco.
7. **Implement:** produzir patch pequeno e reversível; não misturar refatoração sem relação.
8. **Static validation:** parser/AST, tipo, lint, schemas, segredo, dependências, diff e arquivos protegidos.
9. **Build & tests:** executar camadas específicas do adaptador e preservar logs/relatórios.
10. **Behavior/performance/visual:** E2E, screenshots/frames e perfil de recursos conforme a alteração.
11. **Adversarial review:** agente crítico verifica premissas, testes ausentes, riscos e regressões.
12. **Independent verification:** oráculos determinísticos, artefatos e logs originais; não aceitar apenas resumo do executor.
13. **Approval:** proprietário decide sobre patch mutável, promoção de perfil/peso e merge.
14. **Release/rollback:** gerar pacote verificável, registrar proveniência e manter caminho de reversão.

Nenhuma etapa posterior pode transformar `NOT_RUN` em `PASSED`. Uma alteração de fonte depois do teste invalida a evidência daquele HEAD.

## 5. Auditoria de lacunas — estado depois da primeira expansão

1. A R&D Studio e a Workbench continuam como ferramentas separadas. Ainda falta um catálogo/contrato comum de adaptadores, IDs de tarefas e um pipeline único de evidências.
2. O R&D Studio ainda mapeia Kotlin por heurísticas/regex. Nesta branch foi adicionado um mapa semântico TS/TSX baseado no TypeScript Compiler API; **isso não resolve Kotlin, Java, Python ou o restante das linguagens**.
3. A Workbench agora inventaria pré-requisitos por domínio (Android/Kotlin/Java, TS/TSX, Python, shell/workflows, ADB, visual/mídia e sandbox candidato). Detecção de toolchain não equivale a execução comprovada nem garante compatibilidade completa.
4. As suites TS/TSX `web-typecheck`, `web-unit`, `web-lint` e `web-build` estão implementadas e selecionam Bun pelo `bun.lock`. O CI fixa Bun 1.4.5 e instala em modo frozen; os gates ainda aguardam execução real.
5. O mapa semântico TS/TSX consulta declarações, aliases, imports, chamadas e referências com o compilador do próprio projeto. Um defeito encontrado na primeira revisão — retorno incompatível do resolvedor de módulos — foi corrigido, e os testes agora distinguem import local, pacote externo e import ausente. A correção ainda precisa passar por Node tests reais.
6. `run_capture` agora limita o ambiente herdado, rejeita variáveis explicitamente sensíveis, redige padrões comuns de credenciais e encerra o grupo de processos POSIX no timeout. Isso reduz exposição acidental, mas não é sandbox e não garante detectar todos os formatos possíveis de segredo.
7. O executor ainda roda no host e o Git worktree não é isolamento de sistema operacional. Até existir runner efêmero/VM/contêiner com rede e filesystem limitados, executar somente código confiável e classificar código não confiável como `BLOCKED`.
8. A contagem da R&D Studio foi reconciliada: o snapshot atual contém 20 métodos `test_*`; README e manifesto já não devem reportar 16 nem manter a antiga lista de três testes pendentes. Os 20 métodos continuam NOT_RUN no snapshot atual.
9. O Workbench tem 23 métodos de teste Python e dois testes Node para o mapa semântico TS. Inventário de testes não é evidência de aprovação: nenhum deles deve ser reportado como PASS até um runner executar o SHA atual.
10. Workflows Workbench, Agent Lab, APK e núcleo neural ainda são pipelines separados. O Workbench passou a incluir o gate web/TypeScript, mas não há um DAG único relacionando pesquisa, Brain, Agents, integração, Android e evidências no mesmo HEAD.
11. O app Android atual não declara Jetpack Compose. Testes Compose não devem ser aplicados à interface existente; validar Views/Java por Espresso/UI Automator, screenshots e Macrobenchmark quando a infraestrutura estiver disponível.


## 6. Roadmap de implementação

### Fase 0 — corrigir a confiança na bancada
- **Implementado, ainda NOT_RUN:** reconciliação da contagem dos 20 testes da R&D Studio; matriz de toolchain por domínio; suites TS/TSX typecheck/Vitest/ESLint/Vite; seleção pelo Bun lockfile; ambiente filho com allowlist, redaction e cancelamento de grupos POSIX.
- **Implementado, ainda NOT_RUN:** mapa semântico TS/TSX via TypeScript Compiler API e casos para import local, pacote externo e import ausente.
- **Pendente:** conectar Workbench ↔ R&D Studio com IDs de execução, hashes de diff/evidência e invalidar automaticamente resultados quando HEAD/fingerprint mudar.
- **Pendente:** validar política de segredos com fixtures adicionais e documentar as variáveis ambientais não secretas aceitas por cada adaptador.
- **Pendente:** executor com isolamento real de OS/rede/filesystem; não promover worktree a sandbox.

### Fase 1 — compreensão de código de verdade
- **Já existe:** mapa semântico TypeScript/TSX com o checker do compilador do projeto; precisa executar os testes e validar o mapa gerado no repositório real.
- Prioridade Kotlin: comparar Kotlin Analysis API Standalone e Kotlin Language Server oficial. O LSP oferece diagnósticos, símbolos, semantic highlighting e call hierarchy; a integração Android Gradle Plugin é experimental e o servidor está em alpha. Analysis API Standalone permite consultas de símbolos/tipos em CLI, mas ainda está em desenvolvimento ativo. Criar experimento reproduzível antes de escolher dependência.
- Adicionar Java Compiler API/javac, Python AST e parsers XML/YAML/JSON/shell.
- Tree-sitter pode prover parsing incremental uniforme, mas não substitui resolução semântica específica de Kotlin/TS/Java.
- Produzir grafo de dependências e impacto com proveniência de cada aresta e distinguir relações semânticas de relações heurísticas.

### Fase 2 — executor isolado
- Definir contrato `SandboxExecutor`, preflight e política de recursos.
- Implementar runner descartável com rede negada por padrão e ambiente sem segredos.
- Verificar integridade de imagens/toolchains e coletar logs/artefatos.
- Não usar dispositivo pessoal como host de CI.

### Fase 3 — laboratórios especializados
- Brain Numerical Lab: referências numéricas, gradcheck, KV-cache, qualidade e profiling.
- Agent Behavior Lab: trace/evals/benchmarks e teste de conflitos.
- Android Visual & Performance Lab: testes de frame/estado, macrobenchmark e Perfetto.
- Software Factory Lab: build, APK, instalação, smoke, patch review e rollback.
- Research Lab: fonte → evidência → decisão, com Research Lead e Architecture Critic.

### Fase 4 — integração e melhoria contínua
- DAG único de pesquisa → patch → testes → auditoria → review → aprovação.
- dashboards e relatório de comparação de experimentos;
- regressão contínua e gates por risco;
- integração gradual ao cérebro e ao runtime só após evidência real.

## 7. Critério de laboratório adequado

O laboratório só pode ser chamado de capaz de desenvolver o ecossistema quando demonstrar, com execução real:
- descoberta e análise semântica das tecnologias efetivamente usadas;
- toolchains identificadas e reprodutíveis;
- execução isolada que proteja host, segredos e dados;
- patch end-to-end com testes adequados e rollback;
- avaliação de agentes por traces e oráculos independentes;
- validação numérica e comportamental do cérebro com dataset autorizado;
- testes visuais/animados e profiling no alvo Android;
- retomada após falha com evidências preservadas;
- orçamento de recursos e política de concorrência adequados ao Poco F6;
- relatórios que nunca confundam escrito, executado, aprovado e validado fisicamente.

**Estado de saída desta especificação:** arquitetura documentada; nenhuma das fases acima pode ser marcada como implementada apenas por existir este arquivo. A primeira entrega executável deve ser a Fase 0, seguida da análise semântica e isolamento, não a adição de mais perfis sem infraestrutura.


## 8. Referências técnicas consultadas — 19/11/2026

As referências abaixo fundamentam a arquitetura; não significam que as bibliotecas estejam instaladas ou integradas ao repositório.

- Kotlin Analysis API (símbolos, tipos e relações semânticas; modo standalone ainda sujeito a mudanças): https://analysis-api.kotlinlang.org/
- Kotlin Symbol Processing e seus limites de inspeção de expressões/corpos: https://kotlinlang.org/docs/ksp-overview.html
- Tree-sitter, parsing incremental multi-linguagem: https://tree-sitter.github.io/tree-sitter/
- Temporal, execução durável, histórico de eventos e retomada: https://docs.temporal.io/evaluate/understanding-temporal
- LangGraph checkpointing e preservação de estado/pending writes: https://langchain-ai.github.io/langgraph/reference/checkpoints/
- OpenAI agent evals e workflow de traces/datasets/eval runs: https://developers.openai.com/api/docs/guides/agent-evals
- OpenTelemetry semantic conventions para spans de agentes e ferramentas: https://github.com/open-telemetry/semantic-conventions-genai
- Android Macrobenchmark para startup, manipulação complexa e animações: https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
- Testes determinísticos de animação Compose com relógio virtual: https://developer.android.com/develop/ui/compose/animation/testing
- Android system tracing/Perfetto: https://developer.android.com/topic/performance/tracing
- Android Power Profiler e disponibilidade variável de power rails por dispositivo: https://developer.android.com/studio/profile/power-profiler
- Playwright visual comparisons e necessidade de ambiente de baseline consistente: https://playwright.dev/docs/test-snapshots
- NIST IR 8397, verificações mínimas de software: https://csrc.nist.gov/pubs/ir/8397/final
- GitHub secure use e riscos de runners self-hosted persistentes: https://docs.github.com/en/actions/reference/security/secure-use
- GitHub ephemeral self-hosted runners: https://docs.github.com/en/actions/reference/runners/self-hosted-runners
- Bun v1.4.5 release (19/11/2026), documentação de instalação: https://bun.com/ e https://bun.sh/docs/installation

### Limite de interpretação

- Parser sintático não equivale a análise semântica.
- Métrica de loss não equivale a competência conversacional.
- Trace não equivale a correção; precisa de grader/oráculo independente.
- Screenshot não equivale a teste de comportamento.
- Toolchain detectada não equivale a build aprovado.
- Worktree não equivale a sandbox seguro.
- Runner efêmero reduz riscos de persistência, mas ainda exige permissões mínimas, ambiente limpo e política de artefatos.


## 8. Estado da Fase 0 nesta branch — 19/11/2026

**Implementado no código (ainda não validado por execução):**
- `doctor` com matriz por domínio, toolchain e pré-requisitos do stack real;
- suites `web-typecheck`, `web-unit`, `web-lint`, `web-build`;
- seleção do gerenciador JS pelo lockfile e preflight para dependências locais;
- CI Workbench com Bun 1.4.5, `bun install --frozen-lockfile` e os quatro gates TS/TSX;
- ambiente mínimo para processos filhos, redação de valores sensíveis e encerramento do grupo de processos;
- testes unitários novos para matriz, suite web, segredos e timeout de descendentes;
- manifesto R&D Studio reconciliado para 20 métodos de teste. Os 20 continuam sem execução comprovada no snapshot atual.

**Não implementado ainda:** sandbox descartável com limites reais, parsers/Analysis API semânticos, grafo de impacto, tracing OTel completo, benchmark/animation lab Android, benchmark cognitivo com checkpoint treinado e DAG durável integrado. Não declarar a Fase 0 concluída antes dos testes reais e da auditoria independente do patch.


## 9. Entrega incremental — poliglot foundation, 19/11/2026

Branch: `kellvyn-rnd/polyglot-lab-foundation-20261119`  
PR: [#25 — Build out the polyglot engineering lab foundation](https://github.com/ar2233789-oss/kellvyn-ai-assistant/pull/25)  
Status: DRAFT / IMPLEMENTED_NOT_RUN.

### Código presente
- Matriz de toolchain por domínio com JDK, javac, Kotlin compiler CLI, Gradle, Android SDK, ADB, Node, npm, Bun, Blender, FFmpeg, ShellCheck, actionlint, Tree-sitter e possíveis executores Podman/Docker.
- Suites de projeto TS/TSX com Bun e lockfile frozen.
- Mapa semântico TypeScript com declarações, imports, resolução local/externa, chamadas e referências a partir do TypeScript Compiler API.
- Política de ambiente e limpeza de logs/manifests do executor; cancelamento de grupos de processos em POSIX.
- Revisão do resolvedor de módulos TS encontrou e corrigiu um contrato quebrado (retornava string/null enquanto o consumidor esperava objeto); o novo teste inclui pacote local de fixture.
- Workflow Workbench agora dispara quando package.json, bun.lock, tsconfig ou código TS/TSX muda, tanto em push quanto em pull_request.

### Testes escritos / estado
- 23 métodos Python em `tools/kellvyn-workbench/tests/test_workbench.py`.
- Dois casos Node em `tools/kellvyn-workbench/tests/typescript_semantic_map.test.mjs`.
- 20 métodos em `tools/kellvyn-rnd-studio/test_studio.py`.
- **Nenhuma das três suítes foi executada no runner para este HEAD.** Testes permanecem NOT_RUN.
- O último run de Workbench observado encerrou antes de qualquer step; não classificar como falha de código nem aprovação. O bloqueio de alocação continua sendo gate externo.

### Próximo experimento de linguagem — Kotlin
ID proposto: `kotlin-semantic-index-001`.

Hipótese: uma camada semântica Kotlin pode mapear símbolos, tipos, referências e call hierarchy sobre o projeto Android sem alterar o APK nem adicionar custo ao runtime do app.

Variantes:
1. Kotlin Analysis API Standalone, alinhada à versão de Kotlin/K2 do projeto.
2. Kotlin Language Server oficial via LSP, usando Gradle project import e requests de document symbols, definitions, references e call hierarchy.

Critérios:
- importar o projeto Android real sem substituir sua toolchain;
- resolver uma amostra fixa de símbolos entre cérebro, orquestrador, agentes, ferramentas e testes;
- distinguir símbolo resolvido, externo, ambíguo e ausente;
- medir cold start, cache hit, tempo, RSS e memória máxima;
- gerar saída JSON determinística, versionada e com proveniência dos arquivos;
- não escrever arquivos fonte nem depender de serviços pagos;
- não aceitar a rota como semanticamente válida se o projeto Android não importar.

A documentação oficial indica que o Kotlin Language Server é mantido pela JetBrains e inclui diagnostics, semantic highlighting e call hierarchy, mas está em alpha e declara suporte experimental ao Android Gradle Plugin. A documentação da Analysis API confirma modo standalone para ferramentas CLI e alerta sobre mudanças incompatíveis durante o desenvolvimento. Portanto, a decisão deve vir do experimento, não de preferência.

