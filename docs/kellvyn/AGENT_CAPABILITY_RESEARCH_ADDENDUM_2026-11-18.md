# Pesquisa complementar e plano técnico de evolução — Kellvyn

**Data da revisão:** 18/11/2026  
**Branch:** `lab/kellvyn-intelligence-core-20261116`  
**Escopo:** Agent Creation Studio, orquestração, avaliação, desenvolvimento Android e inferência neural local.  
**Princípio:** estudar capacidades observáveis em documentação pública e reimplementar somente os padrões necessários. Não copiar código proprietário, prompts internos, pesos ou identidade de terceiros.

Este documento complementa `AGENT_CAPABILITY_RESEARCH_2026-11-18.md`, `AGENT_TOOL_AVAILABILITY_2026-11-18.md` e `AGENT_CREATION_STUDIO_SPEC_2026-11-18.md`. Ele não declara que os itens de plano já foram implementados.

---

## 1. Descobertas que mudam decisões de arquitetura

### 1.1 Orquestração não é apenas delegar para 14 nomes

A documentação atual do OpenAI Agents organiza um agente em modelo, instruções e ferramentas, com um ciclo explícito: inferir, interpretar a decisão, executar ferramentas ou handoffs e continuar até um ponto real de parada. Também distingue guardrails automáticos de aprovações humanas e recomenda observar traces antes de ajustar comportamento. A documentação de avaliação usa datasets, graders, traces e avaliações repetíveis para identificar erros de roteamento, handoff e política.

Aplicação à Kellvyn:
- manter um único backend neural e os 14 perfis como contratos de especialidade;
- registrar o ciclo do agente como eventos estruturados, em vez de depender somente do texto final;
- separar uma decisão do modelo, autorização, execução da ferramenta e verificação independente;
- não permitir que o resumo do agente seja o próprio verificador;
- guardar trace IDs e hashes de perfil, tarefa, checkpoint e evidência, sem gravar segredos ou conteúdo privado desnecessário.

Fontes oficiais:
- https://developers.openai.com/api/docs/guides/agents/running-agents
- https://developers.openai.com/api/docs/guides/agents/guardrails-approvals
- https://developers.openai.com/api/docs/guides/agents/integrations-observability
- https://developers.openai.com/api/docs/guides/agent-evals
- https://developers.openai.com/tracks/building-agents

### 1.2 Isolamento de contexto precisa virar isolamento de alterações

O Cursor documenta subagentes com contexto próprio, instruções e ferramentas especializadas, além de projetos maiores coordenados por um agente principal. Para mudanças paralelas em arquivos, a documentação descreve cópias isoladas do projeto e branches separadas; isso evita sobrescrita quando vários agentes editam o mesmo checkout. O modo Plan cria dependências e tarefas revisáveis antes da implementação.

Aplicação à Kellvyn:
- o isolamento de contexto que já existe para subtarefas é necessário, mas não será suficiente quando os especialistas puderem editar;
- no futuro, cada tarefa mutável deverá produzir um patch/diff num workspace isolado, não escrever no workspace principal;
- integração será uma operação do coordenador após revisão do diff, aprovação do proprietário, testes e verificação;
- no Poco F6, o padrão inicial continua sequencial. Paralelismo só depois de medir margem de memória e CPU.

Fontes oficiais:
- https://cursor.com/docs/subagents
- https://cursor.com/docs/agent/overview
- https://cursor.com/docs/agent/planning
- https://cursor.com/docs/agent/plan-mode

### 1.3 Checkpoint precisa representar um marco revisável, não apenas um arquivo salvo

O Replit documenta estados de tarefa separados — rascunho, ativo, na fila, pronto, aplicando e concluído — e mantém o trabalho numa cópia separada até ser aplicado à versão principal. Também documenta checkpoints em momentos de conclusão, marcos significativos, estados estáveis e antes de recuperação de erros.

Aplicação à Kellvyn:
- manter a fila persistente/checkpoints já existentes;
- associar ao checkpoint o diff, hash, resultado do teste, evidência, perfil usado e estado de aprovação;
- um estado `SUCCEEDED` exige verificador independente; “ferramenta respondeu sucesso” não basta;
- rollback restaura o workspace, mas preserva o histórico de auditoria.

**Limite importante do benchmark:** o App Testing documentado pelo Replit automatiza navegação em navegador real para aplicações web JavaScript full-stack e Python Streamlit. Ele é uma boa referência para replay e experiência de teste, mas não comprova teste de APK Android nativo.

Fontes oficiais:
- https://docs.replit.com/pt/references/agent/task-lifecycle
- https://docs.replit.com/pt/references/version-control/checkpoints-and-rollbacks
- https://docs.replit.com/pt/references/agent/app-testing

### 1.4 O teste Android precisa agir sobre o aplicativo instalado

O Agent Mode do Android Studio pode implantar em emulador/aparelho, inspecionar o que está na tela, capturar screenshots, consultar Logcat e interagir por ADB. Isso estabelece o padrão de prova do Android Build & Test Studio: não basta analisar gradle files ou descobrir que existe um APK.

Aplicação à Kellvyn:
- separar diagnóstico do SDK, build, assinatura, instalação, abertura, interação, screenshot, Logcat e verificação do resultado;
- cada evidência precisa identificar o hash do APK, versão do app, dispositivo/AVD, versão Android, ação executada e horário;
- teste de navegador ou fixture determinístico não substitui teste do fluxo nativo;
- não instalar, executar ou controlar o dispositivo sem política e autorização específicas.

Fontes oficiais:
- https://developer.android.com/studio/gemini/features
- https://developer.android.com/studio/run
- https://developer.android.com/studio/run/emulator-commandline

### 1.5 Lovable é referência de construção e comparação web, não certificação Android

A pesquisa anterior já documenta Plan Mode, Agent Mode, Knowledge, Skills, Browser Testing, Code Mode, GitHub Sync e a Lovable Academy. O aprendizado que deve permanecer explícito é que browser testing verifica fluxos web; a interface Lovable serve como referência visual aprovada, mas não prova que o APK nativo está funcional. Não portar o runtime web nem alterar a interface aprovada para resolver problemas do cérebro/agentes.

Fontes oficiais já registradas:
- https://docs.lovable.dev/features/agent-mode
- https://docs.lovable.dev/features/plan-mode
- https://docs.lovable.dev/features/browser-testing
- https://docs.lovable.dev/features/knowledge
- https://docs.lovable.dev/integrations/github
- https://academy.lovable.app/academy/video-catalog

### 1.6 Nano Banana é referência multimodal, não dependência do núcleo local

A documentação consultada apresenta Nano Banana como uma família de modelos de geração/edição de imagem, com variantes orientadas a velocidade, uso geral e trabalho visual complexo. As APIs são serviços Gemini; portanto, são uma referência útil para os cenários de avaliação do futuro Image Studio, mas não podem ser uma dependência obrigatória do runtime offline da Kellvyn nem fornecer os pesos do cérebro.

Aplicação à Kellvyn:
- registrar casos de consistência entre várias referências, edição localizada, preservação de regiões, renderização de texto, dimensões e transparência;
- manter criação local/offline como requisito e não alegar que uma chamada externa seja geração nativa;
- qualquer adaptador externo futuro deve ser explicitamente opcional, com rede/consentimento/custos e origem do artefato indicados.

Fontes oficiais:
- https://ai.google.dev/gemini-api/docs/image-generation?hl=pt-BR
- https://ai.google.dev/gemini-api/docs/pricing

---

## 2. Nova oportunidade técnica: acelerar inferência própria no Android

Em 28/10/2026, Google anunciou o LiteRT em Google Play services com a API CompiledModel, aceleração GPU via ML Drift e fallback CPU. O anúncio informa cerca de 1,4x em relação ao antigo GPU delegate e redução aproximada de 8 MB no APK em testes da própria Google. Esses números não significam 1,4x em relação ao código Kotlin manual atual da Kellvyn; a comparação só pode ser feita com benchmark equivalente no Poco F6.

O caminho relevante:
1. manter a arquitetura e os pesos treinados pela Kellvyn como fonte de verdade;
2. avaliar exportação do modelo autoral para um grafo `.tflite`/LiteRT;
3. testar compatibilidade de operadores e equivalência numérica entre Kotlin e LiteRT;
4. medir CPU, GPU, latência, RAM, temperatura e bateria com o mesmo prompt/conjunto;
5. selecionar GPU apenas quando a disponibilidade for confirmada; manter CPU fallback;
6. não adicionar LiteRT-LM com pesos pré-treinados de terceiros como substituto do cérebro autoral.

Limitações e privacidade:
- LiteRT em Google Play services executa a inferência no dispositivo, mas a documentação informa que o serviço pode contatar servidores Google para atualizações e enviar métricas/diagnósticos. Isso precisa ser avaliado contra a exigência offline-first e informado corretamente.
- Para dispositivos sem Google Play services, a documentação recomenda o runtime LiteRT standalone, que aumenta o tamanho do pacote e exige validação própria.
- O suporte de GPU varia por dispositivo/driver. Não presumir compatibilidade apenas pelo SoC.
- O transformer atual da Kellvyn contém RoPE, atenção causal multi-head, Pre-LayerNorm e SwiGLU em um bloco; a exportação do grafo ainda é trabalho de engenharia, não uma troca de dependência trivial.

Fontes oficiais:
- https://developers.googleblog.com/en/smaller-apps-and-faster-gpu-inference-with-litert-in-google-play-services/
- https://developers.google.com/edge/litert/android/play_services
- https://developers.google.com/edge/litert/android/java
- https://developers.google.com/edge/litert/overview

## 3. Recursos móveis: não otimizar por palpites

O Android documenta que referências a objetos mantidas pela aplicação impedem o recolhimento da memória e que o sistema pode encerrar processos em cache sob pressão. O caminho atual de lazy loading, Resource Governor, Worker limitado, descarte de módulos/cache e `onTrimMemory` está alinhado a esse princípio; a validação precisa ser feita em cenários reais, não apenas por estimativa.

A documentação também apresenta App Memory Budgets a partir do Android 17 QPR2 (API 37.2). O app está em compileSdk 35 e o alvo Poco F6/HyperOS não foi qualificado para esse recurso. Portanto:
- não introduzir o novo elemento de manifesto como solução imediata;
- primeiro medir PSS, heap Java/Kotlin, memória nativa/GPU, cold start, pico de alocação e retomada;
- escolher limites de cache/working set com base no fluxo ativo e não no pico isolado;
- manter uma inferência e um especialista de cada vez até que medições provem margem segura;
- treinamento HEAVY não deve ocorrer no Worker/background.

Fontes oficiais:
- https://developer.android.com/topic/performance/memory-overview
- https://developer.android.com/topic/performance/memory/manage-app-memory
- https://developer.android.com/topic/performance/memory/app-memory-budgets
- https://developer.android.com/blog/posts/prioritizing-memory-efficiency-essential-steps-for-android-17

---

## 4. Auditoria de cobertura da branch — o que o código realmente permite

A inspeção de `KellvynRuntime.kt`, `KellvynSpecialistToolPolicy.kt`, `KellvynAgent.kt`, `KellvynWebResearchStudio.kt` e `KellvynAgentToolAvailabilityInstrumentedTest.kt` confirma:

- Os 14 especialistas compartilham a mesma instância de inferência. Eles têm perfis/contextos e permissões diferentes, não modelos neurais independentes.
- Há seis módulos registrados: navegação de código, auditoria de segurança, arquitetura de dados, status do build Android, pesquisa Wikipédia em português e Agent Creation.
- A pesquisa externa real disponível é `wikipedia_search`, com NETWORK_READ. Somente o Pesquisador recebe essa ferramenta, e somente quando Acesso à Web está habilitado. Não é um mecanismo geral de pesquisa na Web.
- O Android Build Status é diagnóstico READ_ONLY. O backend ECJ/D8 da aplicação só dá suporte a Java Android limitado; ainda não é Gradle/Android Studio completo, não instala APK nem executa UI tests.
- O Agent Creation Studio oferece leitura de catálogo, perfil, benchmark, histórico e diff. A aprovação/promoção de perfil não deve ser exposta como ferramenta do modelo.
- O papel Analista Visual tem capabilities declaradas, mas nenhuma ferramenta visual concreta. Analista de Aprendizado possui APIs de treinamento/inferência no runtime, mas ainda não ferramentas executáveis de agente.
- Os perfis e a matriz são bons contratos de segurança/roteamento; não são evidência de competência cognitiva.

A matriz detalhada foi corrigida em `AGENT_TOOL_AVAILABILITY_2026-11-18.md`.

## 5. Plano técnico consolidado — ordem sem atalhos

### Gate 0 — recuperar execução real de build/test
- Obter diagnóstico explícito do job GitHub Actions que falha antes dos steps.
- Verificar utilização/limite de compute da conta do repositório privado e disponibilidade de runner; não presumir causa por ausência de logs.
- Conseguir uma execução com steps, logs e SHA exato.
- Rodar unit tests antes de instrumented tests.
- Corrigir somente erros reproduzidos.

### Gate 1 — provar módulos existentes
- Matriz papel → política → manifest → ToolSpec → ferramenta real.
- Testar acesso à Web ligado/desligado, acesso exclusivo do Pesquisador, limite de respostas, timeout, cancelamento e falhas HTTP através de provider falso.
- Testar fila, handler READ_ONLY, checkpoint, reinício do processo, retry, cancelamento e semântica de execução.
- Testar Profile Store no Android Keystore real; confirmar autenticação, corrupção, CAS, staging, aprovação e rollback.

### Gate 2 — observabilidade e avaliação verdadeira dos agentes
Implementar depois de Gate 0:
- trace local por execução: runId, taskId, stepId, role, profileHash, modelo/checkpoint, toolCallId, risco, autorização, status, tempo, orçamento de contexto, snapshot de recurso e evidenceId;
- eventos distintos para decisão do modelo, ferramenta solicitada, ferramenta autorizada/negada, ferramenta executada, resultado, verificação e aprovação;
- redaction de credenciais/dados sensíveis antes de persistir; limites rígidos de bytes e retenção;
- replay sem reexecutar ferramentas mutáveis;
- execução sequencial dos 28 casos contra a inferência real;
- verificadores determinísticos externos para critérios observáveis e revisão humana para critérios sem juiz automático confiável;
- relatórios de regressão por papel, perfil-hash e checkpoint do modelo.

### Gate 3 — Android Build & Test operacional
- Validar projeto Gradle/SDK/dependências sem executar mudanças.
- Rodar build real, validar assinatura e hash do APK.
- Instalar no AVD, abrir a aplicação, executar cenário, capturar screenshot e Logcat.
- Para cada fluxo, guardar estado esperado/observado e prova.
- Repetir os fluxos críticos no Poco F6 real.
- Não chamar o diagnóstico atual de “Android Build & Test Studio completo”.

### Gate 4 — cérebro autoral com qualidade mensurável
- Obter corpus de origem e licença documentadas, separado em treino/validação/teste.
- Evoluir KN-003 de um único bloco para profundidade com parâmetros independentes e retropropagação através de todos os blocos; os testes precisam incluir diferenças finitas, overfit controlado, loss e checkpoint.
- Avaliar português, instruções, multi-turno, recusa, factualidade, ferramenta/protocolo e robustez contra prompt injection.
- Só ativar chat quando um checkpoint validado passar um conjunto mínimo de tarefas e estabilidade.
- Comparar inferência Kotlin e eventual LiteRT no mesmo conjunto e dispositivo.

### Gate 5 — estúdios visuais e de criação
- UI/UX: análise de screenshots, geometria, hierarquia, comparação com referência e estados.
- Imagem: pipeline de leitura/edição local, com regiões protegidas e exportação verificável.
- Vetor/3D/CAD: módulos lazy independentes, formatos explícitos e limites de memória.
- Não carregar esses motores no chat quando não forem necessários.

---

## 6. Definição de conclusão

Uma funcionalidade só pode ser declarada concluída quando tiver:
1. contrato e implementação;
2. teste escrito;
3. teste executado no ambiente correto;
4. evidência anexada ao commit/artefato;
5. autorização/política testada;
6. custo medido ou orçado no Poco F6 quando consumir recursos relevantes;
7. registro de continuidade atualizado.

Um modelo só pode ser chamado de IA conversacional funcional quando um checkpoint treinado e verificável responde através do runtime principal, passa benchmarks fixos de português/ferramentas e funciona em modo offline no dispositivo. A existência do Transformer, do treino ou dos perfis não basta.


---

## 7. Atualização de pesquisa — verificação em 18/11/2026, 15:55 UTC

### 7.1 GitHub Actions: sinal exato do job sem runner

A consulta REST detalhada ao run `44338054696` revelou para os dois jobs:
- `runner_id = 0`;
- `runner_name = ""`;
- `steps = []`;
- `status = completed`, `conclusion = failure`;
- início e conclusão em aproximadamente dois segundos.

O job mínimo e o job Android falharam antes de qualquer runner ser atribuído. Isso é evidência mais forte que apenas ausência de logs: **não houve runner hospedado alocado**. Não demonstra, por si só, qual regra impediu a atribuição.

A documentação oficial atual diz que, em repositórios privados, o compute incluído depende do plano e o uso elegível de runners hospedados consome o saldo de compute; esse saldo também é compartilhado com Cloud Sandbox compute/memory. Uso acima do incluso pode ser faturado. A documentação também lista limites e políticas de uso. Como não temos acesso aos dados privados de uso, plano e orçamento da conta, compute esgotado é uma hipótese concreta a verificar, não uma causa confirmada.

A página pública de status consultada registra “No incidents reported today” para 18/11/2026 e Actions como Operational no momento da consulta. O incidente com Actions de 16/11/2026 foi resolvido em 16:26 UTC; os jobs da Kellvyn são de 18/11, então esse incidente anterior não explica as falhas observadas.

A próxima ação tem de ser no resumo do run e em Billing and licensing → Usage: obter a mensagem literal e verificar Actions included compute / spending limit. Não fazer novas alterações Kotlin ou repetidos reruns antes disso.

Fontes:
- https://docs.github.com/en/actions/concepts/billing-and-usage
- https://docs.github.com/en/actions/how-tos/monitor-workflows/monitoring-workflow-runs
- https://www.githubstatus.com/

### 7.2 LiteRT: separar aceleração de serviços em nuvem

A página atual da Google confirma:
- LiteRT CompiledModel executa modelos próprios em CPU/GPU através de uma API unificada;
- o runtime em Google Play services pode reduzir até cerca de 8 MB do pacote e a Google relata GPU até ~1,4x mais rápida que o antigo TensorFlow Lite GPU delegate; esse benchmark da Google não pode ser extrapolado para o KN-003;
- o runtime de Google Play services pode contactar servidores Google para atualizações/compatibilidade e envia métricas de dispositivo, app, acelerador, eventos, erros e desempenho;
- standalone LiteRT é a rota indicada quando o app precisa de custom ops, NPU disponível no standalone ou execução sem Google Play services;
- a documentação de conversão aceita modelos próprios treinados em PyTorch, TensorFlow ou JAX, com conversores específicos, e recomenda validar a saída exportada contra a implementação original.

Impacto para a Kellvyn:
1. Não adicionar `play-services-litert` automaticamente ao núcleo offline-first. O envio de métricas e as chamadas periódicas do serviço precisam ser aceitos explicitamente; caso contrário, avaliar o runtime standalone.
2. A classe KN-003 atual é implementada manualmente em Kotlin e não é um grafo PyTorch/TF/JAX exportável por um clique. Para testar LiteRT seria necessário reimplementar o mesmo grafo em uma estrutura suportada ou criar um conversor, mapear exatamente os pesos próprios e comparar logits/token a token em amostras fixas.
3. O primeiro baseline obrigatório permanece inferência Kotlin atual, com dataset de prompts fixos e medições no Poco F6. Só migrar se o resultado exportado for numericamente equivalente, suportar cache/generation e melhorar latência/RAM/energia sem quebrar o modo offline.
4. Não usar nenhum catálogo de modelos pré-treinados ou pesos externos. O caminho de exportação serve apenas para otimizar o modelo autoral da Kellvyn.

Fontes:
- https://developers.google.com/edge/litert/android/play_services
- https://developers.google.com/edge/litert/android/compiled_model_java
- https://developers.google.com/edge/litert/overview
- https://developers.google.com/edge/litert/android
- https://developers.googleblog.com/en/smaller-apps-and-faster-gpu-inference-with-litert-in-google-play-services/

### 7.3 Limite de escopo do treinamento móvel

A trilha oficial Google para treinar e implantar LLM em Android demonstra um fluxo educacional, mas o exemplo de geração usa fine-tuning de um GPT-2 pré-treinado e conversão TensorFlow Lite; não é evidência de pré-treinamento competitivo do zero no telefone. O documento oficial também destaca requisitos grandes de armazenamento e computação e que modelos menores podem ser executados em dispositivos modernos. Para a decisão autoral da Kellvyn, os exemplos e dados devem ser próprios/autorizados e as medições são obrigatórias.

Fonte:
- https://developers.google.com/learn/pathways/llm-on-android
- https://developers.google.com/edge/litert/overview


## 8. Agent Lab — ambiente de execução, observabilidade e experimentação

Esta camada é diferente do Agent Creation Studio:

- **Agent Creation Studio** mantém os 14 perfis, valida contrato/capabilities, versiona revisões e controla aprovação/rollback.
- **Agent Lab** acompanha execuções reais, investiga falhas, executa benchmarks, compara perfil baseline/candidato e mede o custo de recursos.
- O laboratório não substitui o cérebro e não cria modelos separados para os 14 papéis.

### 8.1 Padrões externos estudados

A documentação do OpenAI Agents SDK registra uma execução como trace e agrupa spans de modelo, ferramentas, handoffs, guardrails e eventos personalizados. A documentação de trace grading recomenda avaliar o fluxo completo, não apenas a resposta final; datasets e graders repetíveis ajudam a encontrar regressões de seleção de ferramentas e delegação.

Referências:
- https://developers.openai.com/api/docs/guides/agents/integrations-observability
- https://developers.openai.com/api/docs/guides/trace-grading
- https://developers.openai.com/api/docs/guides/agent-evals

O Cursor documenta subagentes com contexto separado e worktrees para isolar alterações. A vantagem é reduzir contaminação de contexto e conflitos de edição; o custo é criar ambientes/contexts adicionais. Na Kellvyn móvel, manter execução sequencial até medições demonstrarem capacidade real para concorrência.

Referências:
- https://cursor.com/docs/subagents
- https://cursor.com/docs/configuration/worktrees

A persistência baseada em checkpoint e store separa o estado necessário para retomar uma tarefa do conhecimento durável compartilhado. A Kellvyn já tem checkpoints autenticados; o trace complementa os checkpoints, não os substitui.

Referência:
- https://github.com/langchain-ai/docs/blob/main/src/oss/langgraph/persistence.mdx

### 8.2 Infraestrutura implementada nesta revisão

- `KellvynAgentLabTraceStoreRecorder`: gravação assíncrona em I/O, no armazenamento privado cifrado já existente.
- `KellvynAgent`: cria um trace por execução e registra início/fim, chamadas de modelo, decisões, ferramentas, falhas, cancelamentos e execução de etapas de plano.
- `KellvynOrchestrator`: liga os spans de delegação ao mesmo trace; cada especialista usa um ator filho, preservando o trace ID principal.
- `KellvynAgentLabStudio`: módulo independente, carregado sob demanda, com consulta do ambiente, lista/inspeção de traces e comparação de benchmarks.
- `KellvynSpecialistAgentBenchmarkExecutor`: executor real que roda o perfil compilado na mesma inferência, limita ferramentas pela matriz central, captura ferramentas solicitadas/executadas e recusa mutação/delegação aninhada.
- `KellvynSpecialistEvaluationHarness`: aceita executores com trace, registra cada caso e permite avaliar perfis candidatos em memória sem ativá-los.
- Snapshots de início/fim: memória disponível, utilização do heap, PSS, bateria e faixa térmica. São coletados sob demanda; não há polling permanente.
- A comparação baseline/candidato exibe estado por caso, duração, hash do perfil e snapshots de recursos.

### 8.3 Privacidade, integridade e limites

O schema de trace não recebe prompts, respostas brutas, argumentos de ferramentas, conteúdo de arquivos, memória nem credenciais. Os eventos usam trace ID, horário, ator, nome de ferramenta, hash de perfil, duração, resultado e código de detalhe. O armazenamento continua cifrado, privado ao app, encadeado por SHA-256 e limitado por número de traces/eventos.

A matriz de benchmark roda um caso por vez. A promoção continua fora das ferramentas do modelo. O executor não pode aprovar sua própria saída: um verificador separado continua obrigatório.

### 8.4 Limites que ainda impedem declarar o laboratório concluído

- O cérebro neural ainda não está ligado ao chat principal: `KellvynRuntime.isReady()` continua `false`. O executor está pronto para receber uma inferência aprovada, mas isso não prova que exista um backend conversacional de produção conectado.
- O verificador independente é injetado por código confiável; não existe ainda um grader de competência com validade demonstrada para todos os 28 cenários.
- Os testes do trace, executor, integração e comparador estão escritos, mas não foram executados por Gradle/JUnit/Android Runner nesta revisão.
- Não há APK, emulador ou medição física do Poco F6 nesta revisão.
- O workflow de livro de atividades está criado, mas a execução automática do GitHub Actions também está bloqueada antes dos steps por ausência de runner.

### 8.5 Próximos gates técnicos

1. Restaurar alocação de runner e executar a suíte completa antes de ampliar funcionalidades.
2. Corrigir apenas erros reproduzidos pela compilação/testes.
3. Desenvolver um verificador independente por tipo de cenário, com rubricas e evidências reproduzíveis; não usar autodeclaração do executor como aprovação.
4. Persistir metadados de experimento suficientes para reprodutibilidade: versão do corpus, hash do caso, perfil, configuração, backend e versão do executor.
5. Implementar workspaces/patches isolados antes de permitir especialistas de programação a editar em paralelo.
6. Medir RAM, PSS, heap, bateria, temperatura, latência e taxa de conclusão no Poco F6 antes de autorizar paralelismo.
7. Só conectar o benchmark à inferência de produção depois do gate de qualidade neural e de um teste de ponta a ponta.



## 8.6 Capacidade e custo de armazenamento do Agent Lab — atualização 18/11/2026

A primeira versão do trace tinha um teto de 128 eventos por execução e relia a cadeia inteira a cada append. Isso era insuficiente para um benchmark completo e gerava trabalho de I/O crescente. A implementação foi endurecida:

- Capacidade por trace: **1.536 eventos**.
- Retenção global: **até 16 traces**.
- Cada registro mantém limite de 2 KiB; o teto teórico do payload de eventos fica em torno de **48 MiB** para a capacidade máxima configurada (sem contar metadados do sistema de arquivos).
- O cursor de append mantém em memória somente contagem e hash do último evento, após validar a cadeia na primeira leitura.
- Se outra instância do armazenamento tiver escrito desde a última utilização do cursor, a presença do próximo arquivo força reconstrução do cursor a partir da cadeia autenticada antes de escrever.
- `historyTail` valida toda a cadeia criptográfica, mas retém apenas a janela final solicitada. Listagem e inspeção comum deixam de materializar todos os registros na memória; comparação tem teto explícito de 1.536 eventos por trace.
- A suíte de avaliação foi limitada aos 28 casos catalogados por trace para evitar crescimento arbitrário do histórico.

Os testes adicionados cobrem: 140 eventos (ultrapassando o antigo limite), leitura de uma janela de 12 eventos, reconstrução após criar uma nova instância do store, integridade da contagem/horários e conflito de cursores entre duas instâncias. Os testes estão **escritos, ainda não executados** por bloqueio de runner.

Trade-off deliberado: histórico por janela economiza RAM, mas ainda percorre todos os registros para validar a cadeia SHA-256. Não é correto apresentar isso como leitura O(1). O append sequencial fica O(1) depois do primeiro cursor validado; a primeira abertura, inspeção e comparação continuam O(n), com n limitado a 1.536.


## 8.7 Telemetria de CPU e limites de atribuição — atualização 18/11/2026

O snapshot local agora inclui:
- tempo de CPU acumulado do processo (Process.getElapsedCpuTime);
- tempo decorrido monotônico (SystemClock.elapsedRealtime);
- quantidade de processadores disponíveis;
- heap, PSS, RAM disponível, bateria e faixa térmica já existentes.

O trace de início/fim calcula uma aproximação percentual:

`CPU% ≈ ΔCPU do processo / (Δtempo decorrido × número de processadores) × 100`

A leitura é feita somente nas bordas das execuções; não existe polling permanente. O resultado é um indicador de uso de CPU do **processo inteiro** durante a janela, não uma medição exclusiva de um agente. Caso outra corrotina, ferramenta ou atividade do app trabalhe no mesmo intervalo, ela também contribui para o delta. Benchmarks sequenciais reduzem esse ruído; não o eliminam.

Limites:
- não é medição de energia em joules ou watts;
- o percentual de bateria é uma leitura inteira e pode não mudar em tarefas curtas;
- a faixa térmica é uma amostra de estado do Android, não a temperatura exata do chip;
- não extrapolar snapshots pontuais para afirmar consumo médio ou autonomia.

O painel diferencia módulos apenas registrados de motores carregados e mostra se a gravação de traces ainda não foi testada, está saudável, degradada ou recuperada com falhas anteriores.

## 8.8 Falha de persistência e trilha incompleta

A gravação de observabilidade é best-effort: erro de arquivo/Keystore não interrompe a tarefa principal. O gravador:
- mantém contador histórico de falhas e último código;
- identifica o trace e o tipo do evento que falhou;
- tenta persistir o próximo evento normalmente;
- informa estado recuperado sem apagar o histórico de falhas.

Uma recuperação de escrita **não recompõe retroativamente um evento perdido**. O painel expõe o trace/evento afetado para auditoria. Não declarar uma cadeia de atividade completa se houve falha de persistência durante a execução.

A limpeza de retenção está disponível apenas por API interna com confirmação explícita do proprietário. O store só remove traces terminais, antigos e com a cadeia inteira validada. Não existe ferramenta dos agentes para apagar traces.

## 8.9 Limite real da avaliação de competência

Os 28 casos têm critérios requiredEvidence e prohibitedBehaviors escritos em linguagem natural. O harness é capaz de exigir que um verificador independente produza evidências e referências, mas não há ainda um verificador semântico validado para todos esses critérios.

O executor real apenas executa o especialista e captura resposta, chamadas de ferramenta, violações de política e duração. Ele não dá a própria nota. Não converter rubricas textuais em aprovação por busca ingênua de palavras: uma frase pode mencionar o requisito sem satisfazê-lo, e uma resposta correta pode usar formulação diferente.

Próximo trabalho correto para avaliação:
1. Classificar cada critério como verificável por estado da ferramenta, artefato, teste, asserção estruturada, revisão humana ou avaliação semântica.
2. Criar oráculos determinísticos apenas quando existir evidência objetiva (por exemplo, ferramenta recusada, arquivo/diff/hash, saída de teste, ordem de etapas, artefato).
3. Marcar critérios sem oráculo como REVIEW_REQUIRED, não como PASSED.
4. Versionar rubrica e verificador e incluir ambos no fingerprint do experimento.
5. Não usar o mesmo especialista para aprovar sua própria saída. Um segundo passe pelo mesmo modelo não é, por si só, uma verificação independente.

