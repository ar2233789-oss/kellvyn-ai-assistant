# Especificação funcional dos 14 especialistas Kellvyn

**Data:** 19/11/2026  
**Branch:** `kellvyn-rnd/agent-trace-duration-port-20261118`  
**Objetivo:** definir missão operacional, fluxo de trabalho, ferramentas necessárias, colaboração e provas de competência de cada perfil.  
**Regra:** distinguir capacidade-alvo, permissão e ferramenta realmente executável. Não chamar um perfil de construtor de imagens, apps ou 3D quando o respectivo motor ainda não existe.

## 1. Arquitetura comum

Todos os perfis compartilham o cérebro/inferência central. Cada um recebe:
- missão e procedimento versionados;
- subtarefa e contexto estritamente necessários;
- limite próprio de passos, ferramentas, contexto, tempo e workload;
- ferramentas concretamente registradas e filtradas pela `KellvynSpecialistToolPolicy`;
- contrato de saída: conclusão, evidência/origem, incerteza/limites e próxima ação;
- trace, checkpoint e benchmark vinculados ao hash do perfil.

O especialista não é um segundo modelo nem pode alterar a própria permissão. O Orquestrador continua independente e decide a delegação. O executor do host aplica operações. Um verificador independente confere resultados. A aprovação do proprietário é separada de uma decisão do modelo.

## 2. Matriz funcional

| Papel | Missão operacional | Fluxo principal | Ferramentas necessárias no destino | Situação efetiva nesta branch |
|---|---|---|---|---|
| RESEARCHER | Investigar temas, tecnologias, APIs, licenças e documentação; comparar evidências e atualidade | Formular pergunta → buscar fontes → comparar versões/datas → validar afirmações → citar e declarar lacunas | Busca Web genérica, navegador HTTP controlado, pesquisa por documentação/repositório, leitor de PDF, extração estruturada, comparação de fontes | `wikipedia_search` restrito + inspeção JSON/CSV; ainda não é pesquisa geral da Web |
| CODE_EXPLORER | Entender qualquer projeto e encontrar a origem real de símbolos, fluxos, dependências e comportamento | Indexar → procurar símbolos/imports/referências → seguir chamadas → mapear módulos → devolver caminhos/linhas e evidências | Parser/AST por linguagem, indexação incremental, busca semântica, grafo de dependências, navegação de build scripts e recursos | Navegação lexical Kotlin/Java, lista/leitura de workspace, símbolos/referências/outline; AST completo ainda ausente |
| REVIEWER | Revisar mudanças e projetos procurando defeitos, regressões, fragilidades de arquitetura, segurança e requisitos | Inspecionar diff → cruzar requisitos → analisar chamadas/dependências → avaliar riscos → pedir testes que reproduzam cada achado | Diff semântico, static analysis, security scan, API/dependency compatibility, UI/UX review, cobertura e regressão | Navegação, scanner de segurança e inspeção de dados; análise visual/UI e revisão de execução real ainda incompletas |
| TEST_ANALYST | Projetar e executar testes, detectar cobertura ausente e interpretar resultados sem confundir estados | Critérios → casos → fixture/ambiente → execução → recolha de evidência → classificação → regressão | JUnit, integração, instrumentação Android, ADB/UIAutomator/Espresso, teste API, fuzz, screenshot, Logcat, performance e relatório | Status do build e snapshot de recursos + Agent Lab e 28 casos; execução Android real está bloqueada por runner |
| REASONING_ANALYST | Resolver problemas complexos, testar premissas e comparar estratégias | Decompor → listar hipóteses → buscar contraexemplos → comparar custos/riscos → validar conclusão | Pesquisa, calculadora/solver local, análise de dados, grafo causal/dependências, execução de experimentos | Inferência compartilhada + inspeção JSON/CSV e diagnóstico de dispositivo; ferramentas de raciocínio formal ainda limitadas |
| LEARNING_ANALYST | Melhorar o cérebro e o conhecimento técnico com dados autorizados e avaliação rigorosa | Auditar corpus/licença → separar treino/validação/teste → treinar → medir loss/generalização → benchmark holdout → aprovar ou rejeitar | Neural Training Studio, tokenizer/model inspector, corpus deduplication, dataset provenance, gradient tests, benchmark, checkpoint manager | APIs de treino KN-003 no Runtime e Agent Lab; ainda não há ferramenta de treino do especialista nem modelo conversacional aprovado |
| PROGRAMMING_ARCHITECT | Ser o especialista construtor de software/apps, inspirado nos ciclos Plan/Build/Test de Lovable e Replit e na exploração/edição do Claude Code, Copilot e Android Studio | Entender pedido → explorar projeto → desenhar arquitetura → gerar/editar arquivos no workspace isolado → compilar → executar → testar como usuário → corrigir → apresentar diff e artefato | Project Builder completo, edição multi-arquivo, terminal seguro, Gradle/SDK, dependências, templates, navegador/ADB, UI tests, screenshots, Logcat, rollback e evidence package | Perfil atual cobre arquitetura e leitura; workspace tem escrita/deleção com aprovação no agente principal; builder local Java limitado via bridge; agente especialista não recebe mutação/build |
| MEMORY_ANALYST | Recuperar e auditar conhecimento autorizado sem misturar projetos, conversas ou segredos | Resolver escopo → buscar → ordenar por relevância/data → detectar conflito → mostrar origem e confiança | Busca semântica, filtro por escopo, proveniência, expiração, deduplicação, grafo de memória e revisão de conflito | `memory_search` READ_ONLY com escopos autorizados e limites; sem escrita/exclusão |
| SECURITY_ANALYST | Prevenir abuso, vazamento, injeção de prompt, escalada de permissão e riscos em código/integrações | Modelar ameaças → revisar fronteiras → scanner → testar casos adversariais → recomendar mitigação → verificar correção | SAST, dependency/SBOM audit, secrets detector, path/permission validation, prompt-injection suite, network allowlist, sandbox audit | Scanner estático local e navegação/status Android; sem execução adversarial ampla nem SBOM completa |
| PLANNING_ANALYST | Transformar objetivo amplo em execução coordenada, com dependências, orçamento e retomada | Clarificar aceite → decompor DAG → alocar especialistas → estimar recursos → checkpoint → monitorar → reagendar/cancelar com autorização | Task graph, fila persistente, scheduler, resource broker, checkpoint/resume, estimador de RAM/CPU/tempo, calendário operacional e status de bots | Task Planner/checkpoints/broker, status de fila/recursos, Agent Creation/Lab e bot health READ_ONLY; ainda não gerencia tarefas mutáveis sem aprovação |
| DEBUGGER | Reproduzir bugs, identificar causa raiz e provar a correção | Reproduzir → capturar stack/log → reduzir caso → localizar causa → propor patch mínimo → reexecutar teste → verificar regressão | Logcat, crash/ANR, profiler, breakpoint/trace, bisect, teste de reprodução, patch/diff, build e replay | Navegação e diagnóstico build/recurso + Agent Lab; sem Logcat/ADB/profiler como ferramentas concretas do agente |
| AUDITOR | Verificar independentemente se trabalho, teste, segurança e artefatos realmente passaram | Receber evidência bruta → verificar hash/origem → reproduzir checks → checar política → comparar critério → aprovar/reprovar/review-required | Evidence verifier, hash/signature/APK inspector, test report parser, trace verifier, policy audit, visual diff e cadeia de aprovação | Audit de código/segurança/dados, Agent Creation/Lab e status Android; faltam verificadores completos de build/UI/visual e execução física |
| OPERATIONS_ANALYST | Operar e diagnosticar serviços, recursos, bots e integrações, com saúde e recuperação controladas | Ler estado → medir heartbeat/uptime → classificar falha → recomendar recovery → solicitar autorização → observar recuperação | Service supervisor, bot adapters, connection health, retry/backoff, logs, notifications, WorkManager constraints, battery/thermal monitor e alertas | Snapshot de recursos, fila, Agent Lab, Bot Operations; Telegram/Discord têm serviço foreground e reconexão; WhatsApp não implementado |
| VISUAL_ANALYST | Especialista multimodal de design, imagem, interface, vetor, 3D, CAD e animação; usar Nano Banana como benchmark de qualidade, não como dependência | Ler referência → decompor regiões/objetos → desenhar/editar → comparar → verificar geometria/texto/estados → exportar e otimizar | Visual Analysis, image generation/editing, raster layers/masks, SVG/vector editor, scene graph 3D, CAD paramétrico, timeline/keyframes, renderer, screenshot diff e acessibilidade | Capability enum declara áreas visuais, mas ainda não há ferramenta concreta de raster, geração de imagem, vetor, 3D, CAD ou animação |

## 3. Agente construtor de apps — padrão Lovable/Replit/Claude/Copilot/Android Studio

Este papel não deve ser limitado a “sugerir arquitetura”. O fluxo-alvo é uma fábrica controlada:

1. Entender o produto, requisitos, público, telas, estados, dados, rede e critérios de aceite.
2. Explorar a árvore do projeto e identificar stack, módulos, versões, dependências e restrições.
3. Criar plano com tarefas, ordem e definição de conclusão antes de alterar arquivos.
4. Criar workspace/branch isolado e checkpoint inicial.
5. Produzir alterações multi-arquivo como patch revisável; preservar arquivos protegidos.
6. Solicitar aprovação do proprietário para mutações/instalação/ações de dispositivo.
7. Compilar com toolchain real, capturar logs, resolver erros observados.
8. Instalar em AVD/dispositivo permitido, percorrer os fluxos como usuário e capturar screenshots/árvore de acessibilidade/Logcat.
9. Verificar comportamento, persistência, segurança, offline, desempenho e regressões.
10. Entregar projeto, APK quando solicitado, hashes, logs, resultados e limitações.

**Limite atual:** o builder nativo está limitado a Java simples (ECJ → D8). O chat principal não está conectado a inferência aprovada. Não declarar que o agente já constrói aplicativos complexos.

## 4. Especialista visual — padrão Nano Banana como benchmark

O Visual Analyst deverá ser dividido em ferramentas independentes e carregadas sob demanda:

- Visual understanding: segmentação, leitura de hierarquia, comparação de referências e detecção de divergências.
- Raster: layers, masks, crop, transformação, correção localizada, região protegida e export.
- Vector: SVG, paths, shapes, texto, alinhamento, stroke/fill e validação.
- 3D/CAD: scene graph, malha, materiais, transformações, parâmetros e export.
- Animation/Simulation: timeline, keyframes, interpolação, movimento, estados intermediários e replay.
- UI/UX: grid, tipografia, cores, acessibilidade, tamanho de alvos, clipping, responsividade e screenshot regression.

Benchmark visual mede preservação de regiões protegidas, consistência de referência, edição localizada, texto legível, geometria, transparência, exportação, tempo e memória. Uma ferramenta externa futura pode ser adaptador opcional e precisa identificar claramente a origem do artefato.

## 5. Regras de colaboração

- Researcher + Code Explorer + Programming Architect: pesquisar tecnologia e desenhar solução.
- Programming Architect + Test Analyst + Debugger: construir, reproduzir e corrigir.
- Reviewer + Security Analyst: revisar diff, permissões, dados e dependências.
- Planning Analyst + Orchestrator: dividir/ordenar trabalho e controlar orçamento.
- Operations Analyst + Test Analyst: bot, serviços, bateria, rede e desempenho.
- Visual Analyst + Programming Architect: criar interface/assets e integrá-los sem alterar áreas protegidas.
- Auditor: verifica evidência original de cada fluxo e não aceita apenas o resumo do executor.
- O Orquestrador pode delegar subtarefas independentes; no Poco F6, execução sequencial é padrão até medir headroom suficiente.

## 6. Condições para ampliar permissões

Nenhum especialista recebe escrita, execução shell, rede irrestrita, instalação, controle de dispositivo, publicação ou credenciais só porque a capability existe. Cada classe exige:
- ferramenta concreta e manifest/schema;
- workspace/sandbox e limite de recursos;
- aprovação exata do proprietário quando houver efeito colateral;
- trace/evidência com redaction;
- verificador independente;
- teste negativo de tentativa de escalada;
- rollback comprovado.

## 7. Critérios de aceite comuns

- Resultado do agente é evidência separada da execução da ferramenta.
- Uma ferramenta declarada não conta como executada sem log/resultado verificável.
- Build não equivale a instalação; instalação não equivale a funcionamento; screenshot isolado não prova todos os fluxos.
- Benchmark de competência usa casos fixos/holdout, pelo menos três tentativas independentes quando o cérebro estiver pronto, e não é avaliado pelo próprio agente.
- Critérios sem oráculo confiável ficam REVIEW_REQUIRED ou BLOCKED, nunca PASSED por texto plausível.
- Cada recurso pesado é lazy-loaded, mede custo e libera memória/caches após uso.
