# Kellvyn — registro atualizado do laboratório

**Data:** 16/11/2026  
**Branch:** `lab/kellvyn-intelligence-core-20261116`  
**Repositório:** `ar2233789-oss/kellvyn-ai-assistant`

## Decisão de autoria e fundação neural

Kellvyn será uma inteligência e um sistema próprios. Nenhum modelo de terceiros foi escolhido, baixado ou integrado como cérebro da Kellvyn. Sistemas proprietários são referências somente pelas capacidades publicamente documentadas. Código, bibliotecas, pesos e dados externos só poderão ser usados com origem, licença, termos e atribuições preservados. Nenhum componente externo será apresentado como criação integral do projeto.

## Pesquisa consolidada

A matriz `CAPABILITY_RESEARCH_2026-11-16.md` estuda:
- Claude Code: regras, skills, hooks, subagentes e gestão de contexto;
- ChatGPT / OpenAI Agents: ferramentas, tracing, guardrails e orquestração;
- Replit: planejamento, aprovação, testes autônomos, replay, checkpoints e continuidade;
- Cursor: navegação de codebase, edição multi-arquivo, delegação e revisão;
- Android Studio: plano multi-etapas, build, testes, dispositivo, screenshots e Logcat;
- OpenHands, Aider, SWE-agent, mini-SWE-agent e OpenCode: arquiteturas públicas de agentes, workspaces, ferramentas, permissões e avaliação.

A pesquisa foi transformada em módulos próprios. Não foram copiados código privado, prompts internos, pesos privados ou identidade visual de terceiros.

## Código implementado no laboratório

### Planejamento e execução
- `KellvynTaskPlanner.kt`: plano estruturado com dependências, critérios, riscos, permissões, rollback e prova independente;
- bloqueio de ferramentas não registradas e de subestimação de risco;
- comprovação separada de pré-condições antes da execução;
- limite de chamadas, repetição e timeout no agente;
- o agente não chama retorno positivo de ferramenta de verificação independente.

### Memória local KLM-001
- `KellvynMemory.kt`: tipos de memória, origem, proveniência, confiança, escopo, consentimento, classificação de privacidade, expiração e ranker lexical em português;
- `KellvynEncryptedMemoryRepository.kt`: SQLite privado, payload AES-GCM, chave Android Keystore, leitura/escrita em thread de IO, limite de capacidade e isolamento por escopo;
- chave composta de banco por escopo e ID;
- inferência e conteúdo externo não entram automaticamente na memória duradoura;
- credenciais são recusadas; dados pessoais sensíveis exigem aprovação específica;
- backup automático e transferência D2D do app foram desativados para manter dados, históricos, tokens criptografados e memória locais; migração futura será manual e criptografada;
- o agente consegue receber memórias explicitamente aprovadas como contexto não confiável e restrito ao projeto.

### Contexto e busca em projetos
- `KellvynContextAssembler.kt`: orçamento determinístico de contexto;
- mantém instruções de sistema e a última solicitação;
- preserva ordem cronológica e metadados de confiança;
- descarta mensagens antigas quando o orçamento não comporta tudo;
- `KellvynProjectContextRetriever.kt`: busca lexical no projeto explicitamente ativo, com limite de arquivos, trechos e resultados, filtros de caminhos/segredos e origem marcada como não confiável;
- `ProjectWorkspaceStore.readFileExcerpt()`: lê trechos limitados sem carregar o arquivo inteiro;
- `KellvynRuntime.createAgent()`: monta o agente com memória local e busca opcional do projeto ativo; a conversa principal ainda não chama essa fábrica porque não há backend neural conectado;
- o orçamento de contexto usa estimativa de caracteres, não contagem exata de tokens; o tokenizer real só poderá ser usado após integração neural.

### Protótipo neural
- `KellvynByteBrain.kt`: KBN-001, preditor experimental de próximo byte com pesos inicializados, gradientes, atualização de parâmetros e checkpoint próprios;
- KBN-001 demonstra mecânica de treino, não é um cérebro conversacional nem um modelo de linguagem avançado.

## Validação automatizada

As execuções concluídas do GitHub Actions passaram para:
- planejador e teste de risco/consentimento;
- política de memória, escopo e recuperação lexical;
- agente com memória injetada como contexto não confiável;
- montagem/limite de contexto;
- protótipo KBN-001 e verificação de gradientes em execuções anteriores.

A execução `43928133784` passou após corrigir a interpolação do trecho com número de linha. A execução `43928462023` passou com a fábrica do runtime. A execução mais recente `43929599677`, no commit `adaf662112f946e4447669d9e3305206b3aa0f1e`, também passou após desativar o backup automático no manifesto. O workflow executa testes unitários e compila as fontes; não produz APK nem comprova execução no aparelho.

## Limites e bloqueios que permanecem

- `KellvynRuntime.kt` ainda não possui backend de inferência conversacional conectado;
- a fábrica do runtime prepara memória e busca de projeto para um agente, mas `MainActivity` ainda não invoca essa fábrica; o chat ainda não oferece operação de lembrar/apagar e não foi testado em aparelho;
- o armazenamento AES-GCM/Keystore e a leitura contextual do workspace ainda precisam de testes de integração Android em emulador/aparelho;
- armazenamento AES-GCM/Keystore precisa de teste de integração Android real ou emulador; exportação/migração segura entre telefones ainda não existe;
- a recuperação é lexical, não embeddings/busca semântica;
- nenhuma base neural está selecionada; nenhum benchmark físico de modelo foi feito no Poco F6;
- não há teste físico no Poco F6 desta etapa;
- `MainActivity.java` permanece divergente do hash de interface protegido em `.github/interface-lock.sha`; não editar a lock nem promover APK enquanto esse bloqueio não for resolvido sem alterar a interface aprovada;
- a interface protegida e a branch principal não foram modificadas pelo trabalho cognitivo desta etapa;
- a branch permanece experimental e nenhum APK dela está validado para instalação.

## Próximas etapas

1. terminar e inspecionar a execução CI atual;
2. adicionar testes de persistência/criptografia com Android test harness;
3. implementar armazenamento de checkpoints e histórico de tarefas com hashes;
4. ampliar o indexador de projetos com mapa de símbolos/imports e cache incremental, preservando limites de segurança;
5. melhorar esquemas de ferramentas e aprovação de ações mutáveis;
6. preparar benchmark de qualidade e execução offline sem pré-selecionar modelo;
7. desenhar interface de memória pelo chat principal sem tocar na aparência já aprovada;
8. integrar inferência neural somente depois de decisões documentadas de arquitetura, licenciamento e compatibilidade;
9. build, teste de instalação e execução real no Poco F6 em etapas independentes.

Cada capacidade só será declarada concluída com código, testes e evidência correspondente.

## Validação funcional do construtor de aplicativos — 16/11/2026

Foi acrescentado ao laboratório um teste instrumentado que fornece um projeto Android Java determinístico ao mesmo fluxo de saída usado pelo construtor: parser, validador estrutural, workspace privado, compilador ECJ, D8, manifesto binário, assinatura com Android Keystore e verificação com ApkVerifier. O teste guarda o APK produzido para a etapa seguinte do workflow instalar no emulador e tocar no botão de incremento, exigindo a mudança observável de `Contagem: 0` para `Contagem: 1`.

O workflow `.github/workflows/poco-f6-parity.yml` agora também é disparado por push na branch de laboratório. A configuração de build adiciona AndroidJUnitRunner e as dependências instrumentadas de teste. A estratégia, as limitações e os comandos estão em `docs/kellvyn/APP_BUILDER_QUALITY_GATE.md`.

**Execução observada nesta atualização:** o workflow `43938924136` começou para o commit `cdb805ac427aaa3b4b3c169918169909d6bee73c`. No job web, os testes, o build web, a captura da interface e os oito fluxos de interação concluíram com sucesso e sem erros JavaScript. O upload dos artefatos falhou porque a cota de armazenamento de artefatos do GitHub Actions foi atingida. O job Android estava em execução durante a última inspeção; compilação/instalação/interação do APK de demonstração ainda não estavam confirmadas.

**Limite essencial:** o teste usa uma saída de código fixa, não uma resposta produzida por inferência neural. Ele verifica o mecanismo real de compilação e execução, mas não mede se o cérebro já consegue projetar e gerar apps complexos. O backend neural ainda não está ligado ao chat principal. O teste de emulador também não substitui validação física no Poco F6.

A branch principal, a interface aprovada e `.github/interface-lock.sha` não foram alteradas.

Também foi criado `docs/kellvyn/APP_BUILDER_BENCHMARK.md`: uma escada de dez tarefas em português, com critérios externos por app, bloqueios de aprovação, pontuação sem autoavaliação do modelo e métricas para comparar versões do agente. Os casos que excedem o compilador Java atual estão explicitamente marcados como dependentes de novas capacidades, não como testes já aprovados.

**Correção de escopo da prova:** o smoke test injeta um fixture diretamente em `LocalProjectBuilder`; não é uma tarefa gerada pelo modelo através do chat. A inspeção do `MainActivity.java` e de `KellvynRuntime.kt` confirmou que a conversa chama `KellvynRuntime.reply()`, que ainda retorna indisponibilidade de inferência. O callback legado do chat apenas grava blocos de arquivo no Editor e informa que nenhum APK foi compilado. Portanto, o compilador pode ser testado isoladamente, mas o agente principal ainda não cria/compila apps pelo chat. Essa ligação continua como bloqueio de integração, além da ausência do backend neural.

## Continuidade e correções da sessão — 16/11/2026, atualização da noite

O arquivo **CONTINUITY_HANDOFF.md** tornou-se a primeira leitura obrigatória ao retomar o projeto em outro chat. Ele registra as decisões vinculantes, o branch, o estado real, o último ponto de interrupção e a ordem de trabalho. Não usar apenas resumos da conversa como fonte de verdade.

### Correção aplicada ao armazenamento de tarefas

- O construtor de KellvynTaskCheckpointStore agora recebe explicitamente uma implementação de KellvynTaskCheckpointCipher.
- KellvynRuntime injeta KellvynAndroidTaskCheckpointCipher, que usa AES-GCM com chave do Android Keystore.
- Os testes unitários usam um cifrador AES-GCM exclusivo de teste; não usam a chave Keystore de produção.
- Commits: runtime `d9fb31379a6373fe4e1082916705deb29ee23262`; testes `7fb7ba187f5bd7537c0649d0fd17f329d51ab74d`.
- A atualização aciona o workflow de testes por push, mas a aprovação do CI ainda precisa ser consultada. Não declarar a correção validada antes do resultado.
- O uso real do Android Keystore ainda precisa de teste instrumentado e/ou no Poco F6.

### Decisão neural alinhada

Os documentos ARCHITECTURE.md, MASTER_PLAN.md e NEURAL_MODEL_DECISIONS.md foram atualizados para eliminar a antiga alternativa de usar pesos externos ou LoRA. A decisão atual é cérebro neural próprio, treinado do zero. O documento NEURAL_MODEL_DECISIONS.md tem precedência sobre texto histórico que diga o contrário.

### Situação sem maquiagem

O cérebro conversacional ainda NÃO está entregue: KBN-001 é um preditor de byte, e KellvynRuntime.reply() continua sem inferência. O agente/planner/memória/contexto/checkpoints são módulos úteis, mas não conseguem substituir um modelo de linguagem treinado. Nenhum build/teste desta atualização foi confirmado; nenhum teste físico foi feito no Poco F6.

### Primeiro componente KBN-002 escrito — tokenizer BPE próprio

- Criado `KellvynBpeTokenizer.kt` com fallback nos 256 bytes UTF-8, aprendizado de pares frequentes, desempate determinístico, codificação/decodificação e checkpoint versionado com SHA-256.
- O corpus só é lido quando passado explicitamente a `train()`; não há scraping, coleta oculta nem uso automático de conversas privadas.
- Limites experimentais de corpus, número de merges, tamanho de token, custo de processamento, entrada, saída e checkpoint.
- Criado `KellvynBpeTokenizerTest.kt` para round-trip de português/Unicode, determinismo, checkpoint e corrupção.
- Commits: tokenizer inicial `a0704eba6815d2410a720d0ae5dd60ff0b0ab98d`; testes `5cbf434850c80aea48178b635c31115b061ab74b`; limites de CPU/memória e correção de contadores `61b382636a536cd627b162390ab39be37ab6005b` e `5239dc19fd9eb368a690d254d60189fcac083571`.
- A implementação é experimental e ainda não foi aprovada pelo CI nem medida no Poco F6. É um tokenizer, não um modelo neural completo. O caminho de codificação aplica merges em sequência e precisa ser otimizado antes de uso em corpus grande.
- Os status/checks de CI consultados para esses commits retornaram vazios; não há evidência suficiente para dizer que os testes passaram.

### Modelo neural KBN-002 alpha escrito — 16/11/2026

- Criado `KellvynAttentionLanguageModel.kt`: modelo original com inicialização aleatória própria, atenção causal de uma cabeça para previsão do próximo token, embeddings de token/posição, projeções Q/K/V, softmax, tanh de saída, cross-entropy, gradientes manuais, AdamW, clipping, geração greedy e checkpoint de pesos/momentos.
- Criado `KellvynAttentionLanguageModelTest.kt`: testes para gradientes por diferenças finitas, aprendizagem de padrão sintético, checkpoint e entrada fora do vocabulário, além de integração BPE→modelo.
- Commits iniciais: modelo `e96ad13856bf2aa9d47e9b21bae8ee79f67bfc2b`; teste `be2584b7724bbfcf3c917368a45b63d32cf86178`; fábrica tokenizer-aware `de900376672b009bc94401784b333fbdac7cd7e8`; teste integração `7ecd8c26792f2336204bc8a38de099b932b0a075`; leitura de checkpoint limitada e limite de prefixo `ad7a1327106ff2da847b80da0dd9ba01140090c6`.
- Revisão matemática externa por implementação equivalente em Python/NumPy float32: diferença máxima absoluta de gradientes em 21 coordenadas testadas ≈ (9,6\times10^{-7}); padrão alternado sintético caiu de loss ≈ 5,49 para ≈ 0,00392 após 1.100 passos simulados.
- **Limite:** isso não executou a classe Kotlin nem substitui testes Gradle. Os status checks consultados não retornaram resultados; CI, build e teste no Poco F6 ainda não estão aprovados.
- O modelo é uma alpha de atenção de cabeça única, não Transformer completo, não assistente conversacional e não está ligado a `KellvynRuntime.reply()`.

## Atualização do agente — skills procedurais sob demanda (16/11/2026)

Foi feita nova comparação com documentação oficial do Replit Agent, Cursor Agent e Gemini Agent Mode no Android Studio. O mapeamento e as fontes estão em `AGENT_CAPABILITY_UPGRADE_2026-11-16.md`.

Implementação acrescentada:
- `KellvynSkillRegistry.kt`: catálogo de procedimentos de Android/build, diagnóstico de falhas, revisão de código, pesquisa e teste funcional.
- `KellvynAgent.kt`: seleciona no máximo duas skills relevantes por tarefa e injeta o procedimento no contexto de sistema confiável; limite padrão de 6.000 caracteres. Skills não concedem permissões nem executam ações.
- `KellvynSkillRegistryTest.kt`: testes escritos para relevância, seleção determinística, orçamento e integração no contexto do agente.
- `.github/workflows/neural-core-tests.yml`: filtro de push atualizado para incluir o novo arquivo de produção.

Commits de implementação:
- registro inicial: `6cf8f68e772089224a8e2606c954c03470032b76`
- integração no agente: `48936a1f59bacf1f810a763903e05635a0579762`
- testes: `e136c4d1a3cb05c8153633e8c209f907fd4fedc7`
- correção de contexto e assertions: `b640da81645d8a8606f2fae5ac97b90b8320865b`, `ee0f96ff86328d9740d53eba22c7ffa666417dab`
- filtro de CI: `b3f2804b5dcdcf919492855b557733b7f44a2e92`

Também foram alinhados os documentos de arquitetura e pesquisa para remover a permissão antiga e contraditória de utilizar pesos pré-treinados externos; a decisão vigente é treinamento de pesos próprios do zero.

**Validação:** um smoke test Kotlin isolado da lógica de normalização/seleção foi compilado e executado com sucesso. Isso não é o Gradle do repositório, não executa os testes JUnit reais e não valida Android. O GitHub não retornou checks/status verificáveis para os commits consultados. Portanto, os testes do projeto continuam pendentes de CI confirmado. Nenhuma integração com o chat principal nem teste físico no Poco F6 ocorreu nesta atualização.

Próximo passo obrigatório: confirmar o CI da branch, corrigir falhas reais e só então integrar checkpoints/progresso ao ciclo do agente.
