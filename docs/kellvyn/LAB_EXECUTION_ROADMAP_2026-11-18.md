# Kellvyn — plano de execução do laboratório dos 14 papéis

**Data:** 18/11/2026  
**Branch:** `lab/kellvyn-intelligence-core-20261116`  
**Alvo:** Poco F6 / Android / 8 GB RAM  
**Modo:** local e offline-first; rede opcional e explicitamente habilitada  
**Interface:** conversa principal existente; nenhum painel de laboratório ou segunda IA visível

## 1. Decisões que não serão reabertas

1. Kellvyn é uma única inteligência central. Os 14 especialistas são papéis delegados pelo mesmo coordenador e não 14 modelos independentes.
2. O cérebro, tokenizer, pesos, treinamento e checkpoints neurais da Kellvyn são autorais. Referências externas servem para estudar comportamentos e técnicas públicas; não copiar código proprietário nem importar pesos alheios.
3. O laboratório é uma oficina Android **headless** compartilhada. Não criar 14 laboratórios, 14 aplicativos auxiliares ou uma IDE desktop dentro do telefone.
4. Cada motor é carregado apenas quando uma tarefa necessita dele e deve liberar memória/cache quando fica ocioso ou o Android sinaliza pressão.
5. Os especialistas permanecem READ_ONLY até que a ferramenta específica tenha contrato, autorização executada em código, isolamento, rollback e testes próprios.
6. Layout, animações, barras, controles e fluxo visual aprovados permanecem bloqueados durante esse trabalho.
7. Trabalho concluído exige evidência observável. Código escrito, teste criado, teste executado, teste aprovado, build, instalação e uso real são estados diferentes.

## 2. Estado real — sem inflar o progresso

### 2.1 Os 14 papéis registrados

O enum `KellvynSpecialistRole` e o teste de delegação cobrem:

1. Pesquisador
2. Explorador de código
3. Revisor
4. Analista de testes
5. Analista de raciocínio
6. Analista de aprendizado
7. Arquiteto de programação
8. Analista de memória
9. Analista de segurança
10. Analista de planejamento
11. Depurador
12. Auditor independente
13. Analista de operações
14. Analista visual

O código comprova contratos de papel, instruções próprias, isolamento de subtarefa e filtragem READ_ONLY. **Não comprova competência de 14 inteligências separadas.** Todos compartilham a mesma instância de inferência; não há recursão nem paralelismo de especialistas. Os papéis de programação, aprendizado, operações e visual ainda não têm permissões de edição, treino ou controle de dispositivo.

### 2.2 Fundação comum já existente

- `KellvynAgent`: ciclo de decisão, validação de ferramentas, limites, delegação e política de autorização.
- Planejador de tarefas com dependências, critérios de aceite, aprovação, rollback e verificação independente.
- Memória local criptografada, contexto limitado, recuperação lexical do projeto e checkpoints encadeados.
- `KellvynResourceGovernor` e `KellvynTaskBroker`: classes LIGHT/STANDARD/HEAVY, amostragem de recursos, admissão, dois permits e timeout de fila.
- `KellvynAndroidResourceMonitor`: snapshot sob demanda de RAM, heap, PSS, estado térmico, bateria e economia de energia.
- `KellvynPersistentTaskQueue`: SQLite, payload/result criptografados, prioridade, tentativas, adiamento, cancelamento pendente e recuperação de RUNNING.
- `KellvynTaskScheduler`: handlers por sessão, execução sob o broker, defer/retry/fail e lotes limitados.
- `KellvynAutomationDrainWorker`: WorkManager limitado a tarefas LIGHT/STANDARD; HEAVY não pode ser reivindicada em background.
- `KellvynResourceRuntime`: singleton compartilhado, `onTrimMemory`, reclaimers e drenagem da fila.
- `KellvynStudioModuleManager`: manifests, capacidades, leases, carregamento lazy e descarregamento de módulos ociosos.
- Módulos registrados: navegação de código, auditoria de segurança e arquitetura de dados.
- Workspace privado, indexador lexical, ferramentas de leitura e construtor Android local restrito já existem em outras partes da branch.

### 2.3 O que ainda não existe ou não está comprovado

- O bloqueio de ausência de handler foi endereçado em código: existe um handler embutido READ_ONLY de snapshot, registrado por allowlist no runtime e restaurado no Worker após recriação do processo. Ainda aguarda execução instrumental real.
- A sessão do Worker vazia, isoladamente, não prova execução de tarefa real; há agora um teste que enfileira uma tarefa e verifica `SUCCEEDED` via `doWork()`, mas ele ainda aguarda runner/execução.
- Seis módulos concretos estão registrados: navegação de código, auditoria de segurança, arquitetura de dados, status do compilador Android, pesquisa Web e Agent Creation. Isso não equivale aos oito estúdios completos da especificação; Android Build Status ainda é diagnóstico, não um compilador/test runner Android completo.
- O indexador atual é predominantemente lexical; ainda não é um analisador AST completo.
- Não há backend neural conversacional aprovado ligado ao chat principal.
- A suíte Android/Gradle ainda não tem resultado aprovado. O workflow focado foi criado e os runs recentes estão sendo reexecutados; jobs anteriores terminaram sem runner, steps ou logs. Não confundir falha de alocação com falha do código.
- Não há medição térmica, de bateria, latência ou RAM feita no Poco F6 físico nesta etapa.

## 3. O que foi aprendido das referências externas

Essas referências informam requisitos de comportamento; não serão copiadas como código fechado nem tratadas como prova de que o Android nativo já funciona.

- **Replit Agent:** o modo Plan decompõe trabalho, compara abordagens e permite revisar antes de alterar código; checkpoints ajudam a restaurar estados de trabalho. O App Testing documentado cobre aplicações web JavaScript full-stack e Python Streamlit, portanto não substitui automação de APK nativo.
- **Cursor Agent:** pesquisa o codebase, edita múltiplos arquivos, executa comandos e testa; para trabalhos maiores, usa um coordenador que planeja e delega. Seus checkpoints apoiam revisão e reversão de alterações.
- **Android Studio / Gemini Agent Mode:** a referência para ciclo Android nativo é compilar, instalar em emulador/aparelho, inspecionar tela, capturar screenshots, consultar Logcat e interagir por ADB. A Kellvyn deverá possuir sua própria implementação local e limitada desse ciclo.
- **Lovable:** separa discussão, plano e execução; o Build explora o projeto, altera múltiplos arquivos e verifica resultados. O browser testing usa um navegador virtual para cliques, formulários, screenshots, logs e validação responsiva. O Code editor permite inspeção/edição do fonte e o GitHub sync dá fluxo bidirecional. Esses mecanismos são úteis para engenharia de projeto web e para comparar a interface de referência, mas browser testing não certifica APK Android nativo.
- **OpenAI Agents, OpenHands, Aider e SWE-agent:** padrões úteis de ferramenta tipada, coordenação, limites, workspace, tracing, revisão de diff e avaliação separada.

Fontes oficiais principais:
- https://docs.replit.com/pt/references/agent/overview
- https://docs.replit.com/pt/references/agent/app-testing
- https://docs.replit.com/pt/references/version-control/checkpoints-and-rollbacks
- https://cursor.com/docs/agent/overview
- https://developer.android.com/studio/run
- https://developer.android.com/studio/gemini/features
- https://docs.lovable.dev/features/agent-mode
- https://docs.lovable.dev/features/browser-testing
- https://docs.lovable.dev/features/code-mode
- https://docs.lovable.dev/integrations/github

## 4. Estratégia de execução — ordem obrigatória

### Fase 0 — baseline e prova de CI

- Fixar branch e HEAD antes de qualquer mudança.
- Manter arquivos visuais protegidos intocados.
- Recuperar um workflow que realmente aloque runner, execute steps e publique logs.
- Categorizar corretamente falha de runner, falha de compilação, falha de teste e teste ignorado.
- Sem essa prova, os testes escritos são apenas cobertura planejada.

**Gate:** Gradle unit tests executados em runner real, com logs e commit identificável.

### Fase 1 — tornar a oficina operacional com um handler seguro

Primeiro caso real: diagnóstico READ_ONLY de recursos do aparelho, sem escrita, rede, shell ou controle de dispositivo.

Fluxo completo obrigatório:
1. Validar e enfileirar descritor versionado e limitado.
2. Resolver handler apenas por allowlist registrada.
3. Adquirir recursos via Task Broker e revalidar snapshot antes do trabalho.
4. Executar handler idempotente e produzir resumo estruturado limitado.
5. Criptografar e persistir resultado/estado.
6. Devolver evidência observável e estado final.
7. Testar retomada após morte de processo.

Testes obrigatórios:
- payload válido é aceito; inválido é rejeitado permanentemente sem retry inútil;
- handler registrado executa e termina em SUCCEEDED com resumo persistido;
- handler ausente é adiado sem bloquear handlers disponíveis;
- RUNNING interrompido é recuperado com semântica at-least-once;
- cancelamento, retry e maxAttempts mantêm estados consistentes;
- prioridade/FIFO respeitados;
- Worker de background nunca reivindica HEAVY.

**Gate:** ao menos um handler real executado por scheduler/queue, com resultado persistido e teste instrumental real. Não abrir ainda ferramentas de escrita genérica.

### Fase 2 — validar e completar ferramentas dos 14 papéis

**Já implementado — não recriar:** `KellvynSpecialistToolPolicy.kt` já contém uma matriz explícita de capacidades para os 14 papéis. `KellvynAgent` aplica essa matriz ao filtrar ferramentas READ_ONLY durante delegação; os especialistas não podem conceder capacidade a si mesmos. Há testes básicos e foi acrescentado um teste de contrato da matriz completa com os 14 papéis e os cinco níveis de risco.

O catálogo de cobertura real por papel e por ferramenta foi registrado em `AGENT_TOOL_AVAILABILITY_2026-11-18.md`. O que ainda falta é a execução dos testes e a integração cruzada de cada papel com os manifests/ferramentas reais carregados pelos módulos. Para cada papel, validar:
- capacidades que precisa;
- ferramentas exatas que pode descobrir e chamar;
- schema de entrada/saída;
- nível de risco e autorização;
- orçamento de RAM/CPU/tempo/bytes;
- critérios de sucesso e verificador;
- testes positivos, negativos e de prompt injection.

Não duplicar motores por papel: os 14 compartilham os mesmos módulos, com ferramentas autorizadas por política. Skills ensinam procedimentos; não concedem permissões.

**Gate:** teste exato da matriz de 14 papéis aprovado; isolamento/negação aprovados; e execução real de ao menos uma tarefa READ_ONLY de cada família de capacidade disponível.

### Fase 3 — Software Studio

Ordem:
1. Busca, navegação por pacote, classes, imports e referências.
2. Parser sintático incremental depois de auditoria de licença/ABI/tamanho.
3. Contexto do projeto e indexação incremental.
4. Diff multi-arquivo em workspace isolado.
5. Aplicação de alteração reversível após aprovação exata.
6. Build/teste, análise de erro, correção mínima e rollback.

Sem shell irrestrito e sem execução de comandos extraídos de arquivos externos.

**Gate:** editar um projeto de teste, produzir diff, rodar testes, detectar erro injetado, corrigir e provar resultado com logs/estado Git.

### Fase 4 — Android Build & Test Studio

- Validar projeto/Manifest/versões/dependências.
- Gerar artefato APK, verificar integridade e assinatura.
- Executar testes unitários e instrumentais.
- Instalar em AVD Android e executar smoke test.
- Capturar screenshots e Logcat; testar fluxos, falhas e recuperação.
- No aparelho físico, repetir o conjunto crítico e coletar métricas.

**Gate:** APK existente e íntegro, instalado, aberto e exercitado com evidência. Build verde isolado não vale como sucesso funcional.

### Fase 5 — estúdios visuais, dados e engenharia especializada

Implementar como módulos independentes e carregados sob demanda:
- UI/UX: estrutura, hierarquia, geometria, estados e comparação visual.
- Imagem raster/vetor: leitura, análise, operações de edição e exportação.
- 3D/CAD: cena, geometria, câmera, materiais, keyframes e validação.
- Dados/arquitetura: esquemas, relações, migrações e consistência.
- Web research: acesso de rede opcional, fontes, proveniência, datas e resistência a prompt injection.
- Automação: handlers allowlisted, agenda, cancelamento, aprovação e evidência.

Cada módulo tem sua própria suíte, perfil de recursos e gate de licença/dependência. Não inicializar os motores juntos.

### Fase 6 — Neural Studio próprio e competência

**Estado do cérebro confirmado no código:** KN-001 é o protótipo de previsão de bytes; KN-002 inclui tokenizer BPE byte-fallback e modelo de atenção inicial; KN-003 é um Transformer decoder-only experimental de um único bloco, com RoPE, atenção causal multi-head, Pre-LayerNorm e SwiGLU. O tokenizer, os pesos, o treinamento, os otimizadores e os checkpoints são implementações próprias; os pesos começam com inicialização aleatória reproduzível e são treinados apenas com exemplos/corpus fornecidos explicitamente.

**Limite atual:** KN-003 ainda é um bloco único; profundidade multi-bloco, parâmetros independentes por camada e backpropagation através de múltiplos blocos ainda são trabalho futuro. Existe adaptador de inferência estrito, mas `KellvynRuntime.isReady()` continua falso e `reply()` informa que o cérebro ainda não está conectado. Portanto, o cérebro autoral começou e possui implementações neurais reais, mas não está pronto como inteligência conversacional, não tem competência validada em corpus real e não está conectado ao chat.

- Preservar KN-001/KN-002/KN-003 e respectivos checkpoints.
- Avançar a arquitetura neural própria conforme a decisão vigente, com parâmetros independentes por bloco, forward causal, backpropagation e gradient checks.
- Executar overfit sintético, avaliação de português/instruções/ferramentas, checkpoints versionados e benchmark de inferência.
- Medir RAM, latência, calor e bateria; não presumir que o Poco F6 consegue pré-treinar um LLM grande.
- Conectar inferência ao agente e depois ao chat somente após gates de qualidade, formato de saída, estabilidade e segurança.

**Gate:** modelo autoral carregado, inferência determinística o suficiente para testar, qualidade mínima definida e aprovada; pesos aleatórios ou adaptador vazio falham fechados.

### Fase 7 — qualificação no Poco F6

- Cold start e repetição de sessão.
- Carregamento/descarregamento de cada módulo.
- Pico de heap/PSS e memória livre.
- Thermal status/headroom sob tarefas curtas e longas.
- Bateria e economia de energia.
- Cancelamento e retomada ao bloquear tela ou encerrar processo.
- Concorrência, fila e pressão de memória.
- Latência de resposta e tamanho do armazenamento/cache.

Calibrar as estimativas atuais. O alvo inicial é 8 GB, mas os limiares de RAM/heap, working set e número de tarefas são provisórios até medição física.

**Gate:** relatório do aparelho real com dados brutos, condições do teste, repetições e limites observados.

## 5. Modelo operacional e contenção de recursos

- Nenhum polling contínuo para medir recursos; amostragem sob demanda antes de admitir trabalho e antes de executar.
- LIGHT pode dividir um permit quando houver folga; STANDARD/HEAVY são exclusivos.
- HEAVY fica em primeiro plano e nunca é reivindicada pelo Worker de background.
- Limitar filas, lotes, resultados, arquivos indexados, bytes lidos, tentativas, timeouts e chamadas por tarefa.
- Cache tem limite de bytes e reclaimer; pressão de memória descarrega primeiro recursos regeneráveis e módulos sem lease.
- Persistir descritores e referências; não guardar closures, Activities, imagens grandes ou tensores na fila.
- Operações de escrita, instalação, comunicação e controle de dispositivo nunca são habilitadas implicitamente por prompt.

## 6. Próxima tarefa executável

A próxima tarefa continua sendo **fechar a Fase 1**: integrar o handler de snapshot READ_ONLY ao registro do runtime e ao caminho de drenagem, adicionar testes unitários/instrumentais do fluxo completo, executar o Gradle com runner real e registrar a evidência.

Não iniciar ainda Image Studio, 3D/CAD ou edição de código autônoma em agentes antes de provar o ciclo da fila. Não voltar para o layout. Não declarar o laboratório completo antes de cada gate.

## 7. Registro desta revisão

A branch foi auditada em 18/11/2026. O ponto de referência anterior era `937001c85205666b013aaf8854c1c830409617ef`. Durante a retomada foram acrescentados:
- rejeição permanente de payload inválido no scheduler, separada de erros transitórios;
- primeiro handler de diagnóstico READ_ONLY `KellvynResourceSnapshotTaskHandler`, com registro no runtime e restauração pelo Worker;
- testes unitários e instrumentais do scheduler/handler/Worker;
- teste de contrato completo da matriz de capacidades dos 14 papéis;
- workflow focado `agent-lab-tests.yml`, para unit tests e Android instrumented tests em AVD;
- restrição do workflow visual do Poco F6 às mudanças que afetam realmente a UI/manifest/resources, evitando executá-lo para toda alteração Kotlin do cérebro/agentes.

Os testes novos foram escritos, mas não há aprovação Gradle/AVD comprovada. Nesta sessão não existe Android SDK nem Gradle instalados no ambiente local; os workflows do GitHub são a via de validação e estão sendo observados. O estado desses gates precisa ser atualizado após logs e resultados reais.


### Memory Read concluído em código — 18/11/2026
- `KellvynMemoryTools.memory_search` foi adicionada ao registry local, com escopo global e projeto ativo calculado pelo runtime.
- O modelo não pode fornecer project_id nem outro seletor de escopo.
- A ferramenta retorna apenas registros `NON_SENSITIVE`; SENSITIVE_PERSONAL, CREDENTIAL e UNKNOWN são omitidos.
- Limite de cinco resultados, texto por item e saída total. Resultado é explicitamente marcado como dado não confiável.
- A política já entrega `MEMORY_READ` somente ao Analista de Memória.
- Testes unitários e instrumentais foram acrescentados; ainda não executados em Gradle/Android.
- O fonte isolado da ferramenta passou em uma verificação de sintaxe/tipos com kotlinc contra stubs mínimos; isso não comprova compilação do app nem substitui os testes reais.

### Evidência parcial local — Memory Read — 18/11/2026
- O código isolado da ferramenta e o algoritmo de ranking de memória foram compilados/testados localmente com kotlinc, interfaces do agente stubbadas e repositório falso. Casos de escopo, classificação de privacidade e limites passaram.
- A evidência não equivale ao Gradle da aplicação, SQLite/Keystore Android ou instrumentação no Poco F6.
- CI segue bloqueado: runs `44217467213`, `44217511064` e `44217511082` não disponibilizaram runner, etapas ou logs.


### Android Build status implementado — 18/11/2026
- Novo módulo `android-build-status` lazy, com ferramenta `android_build_environment_status` READ_ONLY.
- Relata somente SDK local cacheado, aceite da licença, versão compileSdk e metadados do APK mais recente; não compila, instala ou testa.
- A política já autoriza ANDROID_BUILD aos papéis Test Analyst, Security Analyst, Debugger e Auditor; teste instrumental da disponibilidade real foi atualizado para verificar que apenas esses papéis recebem a ferramenta.
- O backend de build atual continua limitado a Java simples via ECJ/D8. Não é Gradle/Android Studio completo nem Android Device Test.
- Fonte e testes foram adicionados, mas CI/Gradle continua sem runner/steps/logs.

## Fase transversal — Agent Creation Studio, Fase A — 18/11/2026

A auditoria confirmou que o gerenciador de módulos e a delegação dos 14 papéis já existiam, mas não havia registro versionado de perfis nem módulo de criação dos agentes. O bloco de instruções dos especialistas estava embutido no loop de KellvynAgent.

A Fase A foi escrita nesta branch:
1. Registro de 14 perfis versionados por código, com missão, instrução, formato de resposta e limites.
2. Módulo headless READ_ONLY com ferramentas para listar, inspecionar e validar perfis.
3. Integração do registro no loop de delegação.
4. Capacidades AGENT_DESIGN/AGENT_EVALUATION e política de acesso restrita a papéis determinados.
5. Testes unitários escritos para perfis, limites, hashes e acesso ao módulo.
6. Profile Compiler acrescentado: compila 14 perfis, resolve capacidades pela política central e produz SHA-256 determinístico; o hash do perfil é anexado ao resultado da delegação.

O runtime passa a registrar seis módulos. Isso é uma contagem de módulos de ferramenta registrados, não a conclusão dos oito motores descritos na especificação.

Estado de validação: não há saída de typecheck isolado registrada para esses módulos. Os testes JUnit/Gradle continuam sem execução: workflows falham sem etapas/logs. A validação estrutural 14/14 não mede competência cognitiva. Próxima etapa: destravar o runner e executar JUnit/Gradle, depois implementar armazenamento de versões/diff e harness independente de competência.


## Agent Creation Studio — Fase C inicial — 18/11/2026

- 28 cenários estruturados foram adicionados, dois por cada um dos 14 papéis.
- O Studio expõe catálogo resumido e rubrica filtrada por papel, sem executar o modelo.
- O harness de avaliação foi criado com executor e verificador injetáveis; só dá PASSED com hash esperado, todos os critérios observados por verificador independente, referência de evidência e nenhuma chamada/execução fora da allowlist.
- O runner processa a suite sequencialmente; o objetivo é não abrir 14 inferências concorrentes no Poco F6.
- Testes unitários foram escritos para fluxo aprovado, ferramenta indevida, hash divergente, ausência de prova, rede restrita e execução sequencial.
- Smoke harness local com stubs e cópias equivalentes passou 28/28 casos sintéticos e bloqueou escrita indevida. Não é teste do cérebro real, nem compilação Gradle/Android. O arquivo de 28 casos foi verificado estaticamente para contagem, cobertura de papéis e capabilities.
- Faltam: Gradle/JUnit, CI com steps/logs, executor real ligado ao backend neural, verificador independente factual e persistência de resultados.

### CI atualizado — 18/11/2026, 14:46 UTC

Runs `44327863319` (Agent Lab) e `44327863230` (Neural Core) concluíram em failure. Jobs retornaram `logs_url=null` e lista de steps vazia. Classificação: execução sem diagnóstico disponível; não classificar como falha de compilação e não classificar como aprovação.


## Atualização transversal — Agent Creation Studio E parcial — 18/11/2026
Foi acrescentado armazenamento privado de revisões de perfil com AES-GCM/Android Keystore, cabeçalho autenticado, cadeia SHA-256, CAS, preparação sem ativação, aprovação explícita por código confiável e rollback apenas para versão aprovada ou perfil-base. O Studio consulta histórico e valores antes/depois por ferramentas READ_ONLY. Testes unitários e instrumentados estão escritos, mas aguardam runner Gradle/Android; não declarar aprovação. Factories KellvynRuntime.createAgent e createProjectWorkspaceAgent tornaram-se suspend para carregar a registry ativa em I/O, sem bloquear a thread principal.
