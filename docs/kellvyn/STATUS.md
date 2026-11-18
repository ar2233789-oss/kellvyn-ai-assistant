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
- `KellvynByteModel.kt`: KN-001, preditor experimental de próximo byte com pesos inicializados, gradientes, atualização de parâmetros e checkpoint próprios;
- KN-001 demonstra mecânica de treino, não é um cérebro conversacional nem um modelo de linguagem avançado.

## Validação automatizada

As execuções concluídas do GitHub Actions passaram para:
- planejador e teste de risco/consentimento;
- política de memória, escopo e recuperação lexical;
- agente com memória injetada como contexto não confiável;
- montagem/limite de contexto;
- protótipo KN-001 e verificação de gradientes em execuções anteriores.

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

O cérebro conversacional ainda NÃO está entregue: KN-001 é um preditor de byte, e KellvynRuntime.reply() continua sem inferência. O agente/planner/memória/contexto/checkpoints são módulos úteis, mas não conseguem substituir um modelo de linguagem treinado. Nenhum build/teste desta atualização foi confirmado; nenhum teste físico foi feito no Poco F6.

### Primeiro componente KN-002 escrito — tokenizer BPE próprio

- Criado `KellvynBpeTokenizer.kt` com fallback nos 256 bytes UTF-8, aprendizado de pares frequentes, desempate determinístico, codificação/decodificação e checkpoint versionado com SHA-256.
- O corpus só é lido quando passado explicitamente a `train()`; não há scraping, coleta oculta nem uso automático de conversas privadas.
- Limites experimentais de corpus, número de merges, tamanho de token, custo de processamento, entrada, saída e checkpoint.
- Criado `KellvynBpeTokenizerTest.kt` para round-trip de português/Unicode, determinismo, checkpoint e corrupção.
- Commits: tokenizer inicial `a0704eba6815d2410a720d0ae5dd60ff0b0ab98d`; testes `5cbf434850c80aea48178b635c31115b061ab74b`; limites de CPU/memória e correção de contadores `61b382636a536cd627b162390ab39be37ab6005b` e `5239dc19fd9eb368a690d254d60189fcac083571`.
- A implementação é experimental e ainda não foi aprovada pelo CI nem medida no Poco F6. É um tokenizer, não um modelo neural completo. O caminho de codificação aplica merges em sequência e precisa ser otimizado antes de uso em corpus grande.
- Os status/checks de CI consultados para esses commits retornaram vazios; não há evidência suficiente para dizer que os testes passaram.

### Modelo neural KN-002 alpha escrito — 16/11/2026

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

Também foram alinhados os documentos de arquitetura, pesquisa e estudo de referências para remover a permissão antiga e contraditória de utilizar pesos pré-treinados externos; a decisão vigente é treinamento de pesos próprios do zero (correção adicional em REFERENCE_STUDY.md: 0c7ce817ede6d1ff3f42fc40de39b98580b376dd).

**Validação:** um smoke test Kotlin isolado da lógica de normalização/seleção foi compilado e executado com sucesso. Isso não é o Gradle do repositório, não executa os testes JUnit reais e não valida Android. O GitHub não retornou checks/status verificáveis para os commits consultados. Portanto, os testes do projeto continuam pendentes de CI confirmado. Nenhuma integração com o chat principal nem teste físico no Poco F6 ocorreu nesta atualização.

Próximo passo obrigatório: confirmar o CI da branch, corrigir falhas reais e só então integrar checkpoints/progresso ao ciclo do agente.

## Evolução do agente — skills, indexação e Plan/Build (16/11/2026)

A pesquisa oficial foi atualizada e as melhorias foram transformadas em código na branch de laboratório.

Código adicionado:
- `KellvynSkillRegistry.kt`: seis skills internas carregadas sob demanda, seleção determinística e limite de contexto.
- `KellvynProjectCodeIndexer.kt`: índice lexical limitado para package, imports, declarações Kotlin e métodos Java, chamadas e referências.
- `KellvynProjectContextRetriever.kt`: ranking usa sinais estruturais junto com texto e caminho.
- `KellvynTaskCheckpointRepository.kt`: contrato suspendável para persistência/recuperação de planos.
- `KellvynAgent.kt`: decisão estruturada Plan; validação de ferramentas/risco; checkpoints; restauração; aprovação persistida; execução de uma etapa pronta com timeout e verificador independente.
- `KellvynRuntime.kt`: conecta a interface do agente ao armazenamento local de checkpoints criptografados.
- Testes escritos: `KellvynSkillRegistryTest.kt`, `KellvynProjectCodeIndexerTest.kt` e `KellvynAgentPlanTest.kt`.
- Workflow neural atualizado para disparar com os novos arquivos de produção.

Restrições desta primeira versão:
- cada etapa executável declara exatamente uma ferramenta;
- operações mutáveis exigem aprovação explícita e continuam sujeitas à política de autorização;
- uma ferramenta que relata sucesso não conclui a etapa sem prova independente de todos os critérios;
- o índice não é AST nem compilador;
- as skills são procedimentos, não permissões.

Validação:
- harness Kotlin isolado do indexador executado com sucesso para pacote/imports/declarações/referências, filtragem de comentários/strings e limites;
- smoke test independente da seleção de skills executado com exemplos em português;
- os testes JUnit do repositório foram escritos, mas não há execução Gradle/CI confirmada;
- status combinado consultado não retornou checks e a consulta limitada de workflow runs também não retornou execuções;
- sem build de APK, sem teste real no Poco F6 e sem integração no chat principal.

O runtime continua informando que a inferência neural ainda não está pronta. O modelo ainda não está conectado e, portanto, ainda não pode produzir decisões Plan ou chamadas de ferramenta no aplicativo.

## Especialistas internos com contexto isolado (16/11/2026)

Foi acrescentada a decisão `KellvynModelDecision.Delegate` ao ciclo de `KellvynAgent`, com quatro papéis: pesquisador, explorador de código, revisor e analista de testes.

Cada especialista usa a mesma instância de inferência Kellvyn, com histórico separado e somente a subtarefa/contexto explicitamente fornecidos. A delegação não é recursiva, não herda memória ou recuperação automática do projeto e recebe apenas ferramentas registradas explicitamente permitidas com risco READ_ONLY. A resposta retorna ao agente principal como observação não confiável. Limites de quantidade, contexto e timeout são aplicados. **A execução é sequencial, não paralela.**

Foi adicionado `KellvynToolRegistry.readOnlySubset()` e criado `KellvynAgentDelegationTest.kt`, com testes escritos para isolamento, ferramentas somente leitura e bloqueio de delegação recursiva. Os testes ainda não foram executados pelo Gradle/CI. Não há integração do backend neural ou no chat principal.

## Executor incremental do plano — 16/11/2026

Adicionada `KellvynAgent.executeReadySteps()`: avança as etapas READY em ordem, usando ferramenta única e argumentos por etapa, exige verificador independente, persiste cada RUNNING/final e pára em aprovação pendente, falha ou orçamento de etapas. Ao chamar novamente depois da aprovação, continua do último checkpoint, sem repetir etapa já SUCCEEDED. O cancelamento durante a verificação tenta registrar o estado CANCELLED em contexto não cancelável.

Foi acrescentado teste de cadeia: inspeção somente leitura → parada aguardando aprovação → aprovação → etapa mutável concluída com prova. O teste está escrito, mas não foi executado pelo Gradle/CI.


## Atualização do cérebro próprio — 17/11/2026

### Implementado nesta sessão
- Corrigido o bloco duplicado no adaptador local que causava erros de sintaxe.
- Corrigido o índice da última mensagem do usuário no truncamento do histórico e exigida a presença de mensagem USER.
- Adicionados `trainCompletion()` e `evaluateCompletionLoss()`: o prompt condiciona a saída, mas somente os tokens da resposta recebem loss/atualização.
- Adicionados `KellvynInstructionTrainingExample` e `KellvynTrainingPipeline.trainInstructions()`, com tokenizer treinado apenas em TRAIN, partições de validação/teste, proveniência, limites, filtro de dados sensíveis e hashes.
- Bloqueado o uso do mesmo prompt em partições diferentes, mesmo quando as respostas forem diferentes.
- Adicionado `KellvynRuntime.trainOwnInstructionModel()` para treinar em Dispatchers.Default e persistir o bundle local.
- Adicionados testes para redução de loss supervisionada, contagem de passos, checkpoint e prevenção de vazamento.
- Corrigido o parser para converter UTF-8 malformado em rejeição controlada.

### Validação
HEAD consultado: `57ac899e98062ebd304927762be43afd1cbffd48`.
A execução no commit `f2a892c7` compilou Kotlin e executou 136 testes: 135 passaram e 1 falhou em UTF-8 malformado. A falha foi corrigida no commit `529d6287`. A execução do workflow neural no HEAD `57ac899e` e o laboratório virtual no mesmo HEAD estavam em andamento na última consulta. **Não há aprovação final confirmada neste registro.**

### Limites
O modelo ainda é uma alpha de atenção causal com uma cabeça, não um Transformer completo. O treino supervisionado é uma primitiva experimental; não comprova conversa, raciocínio ou uso de ferramentas. Os exemplos usados são pequenos e sintéticos. O adaptador ainda não está ligado ao chat principal; `KellvynRuntime.isReady()` continua falso e `reply()` bloqueado. Não houve teste físico no Poco F6 nem APK de entrega.

### Próximos passos
1. Fechar CI e laboratório no HEAD atual, corrigindo qualquer falha observada.
2. Implementar Transformer decoder-only próprio com atenção causal multi-head, residual, normalização e MLP gated, acompanhado de testes numéricos de gradiente.
3. Reduzir custo do protocolo e implementar EOS/decodificação controlada.
4. Construir corpus de instruções de português com direitos documentados e avaliação separada.
5. Só então ligar inferência treinada ao agente e ao chat, sem mudar a interface aprovada.


### CI atualizado — 17/11/2026

O workflow **Kellvyn neural core tests** no commit `57ac899e98062ebd304927762be43afd1cbffd48` concluiu com sucesso: run `43986427597` (`https://github.com/ar2233789-oss/kellvyn-ai-assistant/actions/runs/43986427597`). O log registra compilação Kotlin, compilação Java, compilação dos testes e `BUILD SUCCESSFUL` em `testDebugUnitTest`. A falha anterior de UTF-8 foi corrigida antes desse commit. O laboratório virtual Android é outro workflow e ainda não tinha conclusão confirmada nesta atualização.


## Atualização neural — KN-003 + EOS — 17/11/2026

**HEAD de código validado:** f32bca0d1b2e14541ed7a52f8c0bd414f685084e.

### Alterações desta sequência
- Tokenizador BPE com token EOS dedicado e checkpoint versão 2.
- Compatibilidade de leitura preservada para checkpoint de tokenizer versão 1 sem EOS.
- Pipeline KN-003 passa a anexar EOS a cada completion de treino, validação e teste; a loss supervisiona a parada.
- O Transformer pode encerrar a geração ao prever o ID EOS, sem expor o token de controle.
- O adaptador usa EOS em checkpoints novos e mantém fallback textual END para bundles legados.
- Testes cobrem round-trip Unicode, token EOS dedicado, compatibilidade de checkpoint antigo, EOS aprendido e parada de geração.
- Especialistas internos: o teste de contrato que percorre RESEARCHER, CODE_EXPLORER, REVIEWER e TEST_ANALYST passou no CI anterior do mesmo branch (run 44003722972).

### Validação
- Workflow neural core tests: run 44005947968, commit f32bca0d1b2e14541ed7a52f8c0bd414f685084e — **sucesso**.
- O log confirma execução de gradle testDebugUnitTest e BUILD SUCCESSFUL em 2m09s.
- Teste de geração EOS e treinamento supervisionado passou dentro da suíte.
- A verificação de gradiente mantém os limites de erro absoluto e relativo; o passo de diferença finita foi ajustado para 0,005 em float32 após aumento da dimensão do softmax.
- Workflow Poco F6 virtual comparison lab: run 44005947963 estava em andamento na última consulta. Não registrar como aprovado até ver o resultado. Mesmo que passe, é um AVD, não o aparelho físico.

### Limites reais
- KN-003 ainda tem um único bloco Transformer, embeddings 32 por padrão e contexto máximo de 256 tokens.
- `GenerationSession` já mantém cache KV incremental com janela deslizante para o bloco único; o ganho de latência/RAM ainda precisa ser medido no Poco F6.
- O corpus de testes continua sintético e pequeno; não existe corpus linguístico grande com direitos documentados.
- A geração tem como alvo o protocolo KELLVYN/1; ainda não demonstra diálogo natural geral, planejamento confiável ou programação autônoma.
- O adaptador neural permanece separado do chat principal. KellvynRuntime.isReady() continua falso e reply() continua bloqueado.
- Não há inferência validada no Poco F6 físico, nem medição de RAM, latência, temperatura, bateria ou sessão longa.

### Próxima ordem neural
1. Avaliar uma pilha de múltiplos blocos decoder-only com backpropagation por camada e testes numéricos independentes.
2. Criar avaliação de capacidade do modelo em português e decisões KELLVYN/1 usando exemplos sintéticos autorizados e holdout.
3. Dimensionar contexto real e desempenho; medir custo do cache KV já implementado antes de crescer a janela.
4. Melhorar geração incremental e cancelamento.
5. Só após evidência de qualidade e estabilidade considerar ligação ao agente; não alterar a interface.


## Inferência incremental com cache KV — 17/11/2026

### Implementação
- `KellvynTransformerLanguageModel.GenerationSession` mantém K/V rotacionados por RoPE para o contexto ativo.
- O prefixo é pré-processado uma vez; cada novo token calcula apenas as próprias projeções Q/K/V.
- A atenção do token atual reutiliza K/V anteriores e respeita a janela causal.
- O cache mantém no máximo `contextLength` tokens e remove o token mais antigo quando a janela desliza.
- As posições RoPE continuam absolutas para conservar as diferenças relativas entre tokens mantidos.
- `KellvynTransformerInferenceEngine` usa a sessão incremental, incluindo parada EOS.

### Validação
O workflow neural core tests run 44007839790 no commit de código 895eca04525b65b6c7c17175e42ff4d126d1e5ae passou:
- `gradle testDebugUnitTest` executado;
- `BUILD SUCCESSFUL` em 1m40s;
- teste de comparação de probabilidades com forward completo;
- teste de janela deslizante e limite do cache;
- teste de sequência greedy cacheada versus referência com recálculo integral.

### Limites
O cache é apenas para o único bloco Transformer atual. Ainda não foi medido ganho de latência/throughput, RAM, temperatura ou bateria no Poco F6. Não existe cache por camada porque ainda não existe pilha multi-bloco. Evitar treinar e inferir simultaneamente no mesmo objeto de modelo; treinar em instância separada e promover checkpoint validado.

### Próximo trabalho neural
Construir profundidade multi-bloco com parâmetros independentes por bloco, forward causal de todas as posições, retropropagação completa entre camadas, gradient checks por camada e checkpoint versionado. Cada camada deverá ter seu próprio cache KV. Preservar a interface.


## Expansão dos especialistas internos — 17/11/2026

### Alterações no código
O enum `KellvynSpecialistRole` e as instruções específicas agora cobrem 14 papéis:
1. pesquisador;
2. explorador de código;
3. revisor;
4. analista de testes;
5. analista de raciocínio;
6. analista de aprendizado;
7. arquiteto de programação;
8. analista de memória;
9. analista de segurança;
10. analista de planejamento;
11. depurador;
12. auditor independente;
13. analista de operações;
14. analista visual.

O teste `KellvynAgentSpecialistEvaluationTest` foi ampliado para percorrer os 14 papéis. Para cada papel verifica instrução correta, contexto e tarefa delegados, isolamento do pedido privado do coordenador, ferramentas somente READ_ONLY e retorno classificado como não confiável.

### Supervisão e limites
- O coordenador é o próprio `KellvynAgent`; não foi criado um segundo modelo nem outra identidade.
- Especialistas compartilham a mesma instância de inferência; não existe recursão ou paralelismo na delegação.
- Os papéis de programação, aprendizado, memória, operações e visual são atualmente analistas de leitura. Não receberam permissão para editar arquivos, alterar pesos, treinar modelos, controlar o dispositivo ou executar ações operacionais.
- O teste de contrato não comprova competência cognitiva. Isso dependerá de avaliação com modelo treinado e tarefas holdout.

### Validação
**Validação posterior:** a suíte completa `gradle testDebugUnitTest` passou no run 44019204700, no commit `a778637606e2af6210805f7774ccfbac251b180c`; por incluir a suíte unitária do projeto, ela também executa o contrato dos 14 papéis. Isso comprova contrato, isolamento e permissões, não competência cognitiva.

## Estado consolidado da sequência — 17/11/2026

### Código e testes confirmados
- Branch de laboratório: `lab/kellvyn-intelligence-core-20261116`.
- O registro histórico deste bloco foi atualizado em commits posteriores; consulte o HEAD atual da branch para a revisão mais recente.
- KN-003 preservado como modelo de um bloco; documentação agora reconhece corretamente o cache KV incremental já existente.
- Adicionado teste que treina um fixture pequeno, salva e restaura o checkpoint e compara as probabilidades cacheadas com o forward integral por 16 passos, inclusive depois de a janela deslizante atingir o limite.
- Workflow neural core tests no run 44019204700: `gradle testDebugUnitTest` terminou com `BUILD SUCCESSFUL` em 2m19s.
- Alterações de interface/layout: nenhuma nesta sequência.

### Laboratório Android
- No run 44019205073, o Gradle compilou os testes e o APK nativo; o emulador Android 15 instalou e abriu a Kellvyn. A navegação por Histórico, Anexos/Web, Navegador (sem abrir endereço externo), Editor, Bots e Terminal passou antes de a automação parar ao procurar um CheckBox inexistente para o chip clicável `Raios`.
- O workflow foi ajustado para tratar labels clicáveis corretamente, repetir a abertura de Configurações após cold start e separar a captura visual preliminar da interação funcional obrigatória.
- O run 44021575534 no commit `2992b0008f18ba48c5d32a64c1a73abdc627ead4` e sua repetição terminaram antes de qualquer etapa dos jobs; a API não disponibilizou logs nem etapas. Portanto, a versão mais recente do laboratório ainda não tem resultado funcional verificável.
- O upload de artefatos também já encontrou limite de quota do GitHub Actions. A captura comparativa de pixels não foi concluída.

### Bloqueios que permanecem
- KN-004 multicamadas ainda não foi implementado.
- Não existe corpus português grande, licenciado e treinado que sustente um assistente geral.
- `KellvynRuntime.isReady()` permanece falso e `reply()` permanece bloqueado. O Transformer experimental não foi ligado ao chat.
- Os 14 papéis compartilham uma mesma inferência; são contratos de especialistas, não 14 modelos. Sem modelo conversacional treinado, não há prova de desempenho cognitivo.
- O construtor local tem um smoke test com saída Java determinística, não gerada pelo modelo; compilação ponta a ponta por um agente autônomo ainda não foi comprovada.
- Não há teste no Poco F6 físico, medições de RAM/latência/temperatura/bateria, nem APK final de lançamento.
- Auditoria integral de proveniência, licenças e similaridade ainda não foi concluída.

**Conclusão:** a base estrutural e os testes unitários do cérebro passaram; a Kellvyn ainda não está pronta como assistente operacional. Não ativar o runtime com pesos aleatórios nem declarar a construção autônoma de apps como concluída.

## Integração do chat com o compilador Android local — 17/11/2026

### Alterações escritas
- Adicionada a ponte `LocalProjectBuilderBridge`, que executa o compilador em coroutine de fundo e devolve progresso/resultado ao chat na thread principal.
- Pedidos explícitos de APK Android recebem o contrato do primeiro compilador: projeto Java nativo, sete arquivos obrigatórios, sem Kotlin/Compose, WebView ou dependências externas.
- Quando o modelo devolver arquivos completos, `MainActivity` encaminha a resposta para `LocalProjectBuilder`; o caminho de APK deixa de ser confundido com o antigo salvamento de arquivos no Editor.
- Se o compilador pedir consentimento, a frase exata de aceite do Android SDK retoma o projeto pendente antes da checagem do cérebro. A saída só é chamada de APK pronto depois de compilação, assinatura e verificação confirmadas pelo compilador.
- O teste instrumental `GeneratedAppBuildSmokeTest` foi alterado para usar a mesma ponte que o chat chama, em vez de invocar o compilador diretamente.

### Validação real desta integração
- Código da ponte, integração Java e teste instrumental foram escritos nos commits `9a2fc45`, `d264a42`, `69fb6ae` e `327184d`.
- A workflow neural foi atualizada para compilar `MainActivity.java`, a ponte Kotlin e o pacote workspace.
- Run neural `44023311758` no commit `65a65dcffeb56e5631b224e99726e0ca7b297316`: falhou em aproximadamente 2 segundos, com `runner_id=0` e zero etapas. A repetição também falhou antes de iniciar etapas.
- Run do laboratório `44023141961` no commit `327184df16c8991241db0f578a562071a97f289f`: jobs web e Android terminaram em aproximadamente 2 segundos, ambos com `runner_id=0` e zero etapas. Portanto, nenhum dos dois verificou a compilação da nova integração.
- Não declarar a ponte compilada ou testada até uma execução de CI com runner e etapas reais.

### Limite que continua bloqueando o uso
`KellvynRuntime.isReady()` continua falso e `reply()` continua devolvendo indisponibilidade. Portanto, embora o caminho chat → compilador esteja ligado no código, ele só poderá receber código do modelo quando existir um modelo conversacional treinado e aprovado. A ponte, por si só, não dá inteligência à IA nem prova que o modelo consiga gerar um aplicativo funcional.

**Estado:** integração escrita; validação pendente; cérebro neural conversacional ainda não operacional.

### Revisão de concorrência / thread — 17/11/2026
- O bridge mantém compilação/validação do workspace em `Dispatchers.IO`, não na thread da interface.
- Atualizações de progresso retornam para `Dispatchers.Main.immediate`; a conclusão também é entregue pelo escopo principal.
- O escopo é cancelado junto ao ciclo de vida da Activity. A compilação pode ser cancelada se a Activity for destruída; ainda não existe serviço persistente de build.
- Esta correção foi escrita nos commits `fbef033fe1796df2748d36916a2b6b82caf1399a` e `7ee1f68c312d314f9372bbb3ef46f0c9e3fd5322`. Não houve build Android de CI posterior válido para comprová-la.

### Diagnóstico do bloqueio de CI — 17/11/2026
- Os workflows mais recentes `44024582833` (neural) e `44024582640` (Poco/AVD), no commit `7ee1f68c312d314f9372bbb3ef46f0c9e3fd5322`, terminaram em ~2 segundos, com `runner_id=0`, nome de runner vazio e zero etapas. Não há logs; a repetição dos jobs reproduz o bloqueio.
- O repositório é privado e a conta usa GitHub Free. A documentação atual do GitHub informa US$ 12/mês de computação incluída para runners padrão de repositórios privados; a cota pode ser bloqueada quando consumida sem método de pagamento válido. A documentação também informa que runners padrão de repositórios públicos e self-hosted não consomem essa cota.
- Há um relato público de 15/11/2026 com assinatura de falha idêntica (`runner_id=0`, zero etapas, `BlobNotFound`) e bloqueio de Actions depois de atingir a franquia Free. Isso torna o limite de cobrança uma hipótese forte, não uma causa confirmada para esta conta.
- A integração disponível não expõe o painel de faturamento/consumo desta conta. É necessário conferir em GitHub → Settings → Billing and licensing → Usage e verificar GitHub Actions included compute usage, spending limit e método de pagamento. Nenhuma alteração de visibilidade ou cobrança foi feita.

## Ferramentas locais e autorização de mutação dos agentes — 17/11/2026

### Implementado no código
- Novo catálogo local com cinco ferramentas: listar projetos, listar arquivos, ler arquivo, gravar/substituir arquivo e remover arquivo.
- As três operações de leitura são `READ_ONLY`; especialistas recebem somente esse subconjunto.
- Gravação e exclusão têm riscos separados (`REVERSIBLE_WRITE` e `DESTRUCTIVE`) e exigem aprovação de uso único mantida pelo app.
- A autorização vincula projeto, caminho relativo normalizado e, em gravação, SHA-256 do conteúdo exato. Expira após 120 segundos, não é reutilizável e é perdida ao encerrar o processo. O modelo não recebe nem gera token secreto; o argumento de ID é opcional para chamadas confiáveis.
- `KellvynRuntime` agora expõe `createProjectWorkspaceToolRegistry()`, `createProjectWorkspaceAgent()`, `approveProjectFileWrite()` e `approveProjectFileDeletion()`.
- O prompt de sistema do coordenador explicita que ele nunca deve inventar, alterar ou reutilizar tokens de aprovação.
- Documento de referência: `docs/kellvyn/WORKSPACE_AGENT_TOOLS.md`.

### Testes escritos
- Unitários da autorização: projeto/caminho/conteúdo divergentes, replay, expiração, separação escrita/exclusão, caminhos inseguros e bloqueio de rede/controle de dispositivo.
- Instrumentais Android: leitura real, gravação aprovada sem token no prompt, recusa sem autorização, exclusão aprovada sem token no prompt e filtro READ_ONLY.
- Instrumentais do agente: especialista lê arquivo real sem receber ferramentas de mutação; coordenador grava com aprovação exata sem receber token secreto.
- Esses testes ainda não têm resultado Gradle/Android válido. Não registrar como aprovados enquanto não executarem em um runner.

### Verificação local independente
- O compilador Kotlin local aceitou sintaticamente a nova camada de ferramentas com tipos Android/workspace simulados.
- Um harness local de contrato executou verificações de autorização vinculada, replay, expiração, caminhos inválidos, ID de 48 hexadecimais e filtragem somente leitura: **`WORKSPACE_APPROVAL_GATE_PASS`**.
- O quality gate KN-003 foi compilado isoladamente com o manifesto tipado simulado e passou os cenários de aceitação, ausência de melhoria, sobreajuste, contexto curto e proveniência incompleta: **`QUALITY_GATE_PASS`**.
- Esses harnesses usam tipos simulados e não substituem `gradle testDebugUnitTest`, `assembleDebug` ou `connectedDebugAndroidTest`.

## Gate conservador de qualidade para KN-003 — 17/11/2026

- O runtime agora recusa criar o adapter de inferência KN-003 quando o checkpoint não passa no gate de validação.
- Exige contexto mínimo de 128 tokens, TRAIN/VALIDATION/TEST, proveniência preenchida, melhoria relativa mínima de 5%, validation/test abaixo de 90% da loss uniforme e diferença de generalização até 0,50.
- Passar apenas habilita um benchmark posterior de protocolo; não prova conversa natural, raciocínio, programação ou prontidão de produção.
- O chat principal continua deliberadamente desativado. Não conectar um modelo experimental só porque ele superou uma loss simples.

### Bloqueios atuais, sem mudança
- KN-004 multicamadas com backpropagation por bloco ainda não foi implementado.
- Ainda falta corpus português amplo, licenciado e treinado; os exemplos existentes são pequenos/sintéticos.
- Os 14 papéis continuam compartilhando uma única inferência. A nova camada de ferramentas está no runtime, mas o chat não a utiliza enquanto o cérebro não estiver aprovado.
- O caminho chat → compilador local está escrito, mas falta build Android verificável; geração de app por inferência autônoma não foi demonstrada.
- Não há validação no Poco F6 físico, medições de RAM/latência/temperatura/bateria, APK final nem auditoria integral de proveniência/similaridade.

### Estado da CI gratuita — 17/11/2026
- Runs `44027697451` (neural) e `44027697495` (Poco/AVD), no commit `ca01f8304d49742216479710268d2fea69b31106`, falharam em ~2 segundos: `runner_id=0`, runner vazio, zero etapas e sem logs.
- Isso não é falha dos testes de código; os testes nem chegaram a iniciar. Também não prova, por si só, qual limite/condição impediu a alocação do runner.
- Nenhuma cobrança, cartão, plano ou mudança de visibilidade foi ativada. Nenhuma alternativa paga é necessária para manter o projeto privado.

### Correção de desenho da aprovação — 17/11/2026
- A primeira versão exigia que o modelo reproduzisse um ID aleatório guardado no app; isso não era viável para uma inferência real.
- O gate foi corrigido: se um ID confiável opcional não vier nos argumentos, a ferramenta local procura uma aprovação pendente que corresponda exatamente a projeto, caminho, operação e (para escrita) hash SHA-256 do conteúdo.
- O ID aleatório continua sob controle do app e pode ser usado por chamadas confiáveis, mas não é exposto ao modelo.
- Os testes unitários e instrumentais foram atualizados para cobrir aprovação sem token no prompt. Escrita e exclusão seguem de uso único e expiram após 120 segundos.
- Limite de integração: ainda não existe no chat principal uma tela/ação de confirmação para emitir essas aprovações; a API de runtime está disponível, mas a confirmação do proprietário precisa ser conectada antes de oferecer edição autônoma no chat.

 
## Fundação do laboratório invisível e governança de recursos — 17/11/2026

### Código acrescentado
- Resource Governor em Kotlin puro para classes LIGHT/STANDARD/HEAVY, working set, RAM disponível, heap, pressão de memória, estado térmico/headroom, economia de bateria, nível de bateria e carga.
- Broker compartilhado de 2 permits, fila FIFO e exclusividade para STANDARD/HEAVY. LIGHT pode compartilhar sob folga. Revalida o snapshot após a aquisição.
- Timeout de fila separado do timeout da operação; fila ocupada devolve RESOURCE_QUEUE_BUSY. Permits parciais são liberados em timeout/cancelamento.
- Monitor Android on-demand: ActivityManager.MemoryInfo, Runtime heap, Debug.getPss, PowerManager thermal/power saver, headroom em cache >=1s e BatteryManager.
- ResourceRuntime singleton compartilhado entre agente, treinamento e construtor APK.
- Agent ToolSpec agora inclui workloadClass e estimatedWorkingSetBytes.
- Inferência classificada como STANDARD (128 MiB inicial); indexação de projeto STANDARD (64 MiB inicial); compilador APK HEAVY (768 MiB inicial); treinos neurais próprios HEAVY (524 MiB padrão).
- Nova ferramenta READ_ONLY device_resource_snapshot.
- Deferral de etapa planejada retorna READY e grava checkpoint; mantém approval/attempt e exige nova evidência de pré-condição.
- Timeouts de execução só começam depois da admissão; cancelamento externo não é convertido em falha de timeout.
 
### Verificações
- 8 testes unitários puros do Resource Governor/Broker compilados e executados localmente com Kotlin/JVM: RESOURCE_GOVERNOR_UNIT_TESTS_PASS.
- Monitor Android compilado isoladamente contra stubs das APIs Android usadas. Não é build Gradle/Android.
- Teste unitário para retry de plano e teste instrumental para snapshot/singleton foram acrescentados, sem execução Gradle/AVD verificada.
- Workflows mais recentes: run 44062178882 (neural) e 44062178854 (Poco/AVD) falharam com jobs sem steps nem logs_url. API de logs retorna BlobNotFound. Não há evidência de que Gradle tenha iniciado; não classificar como falha de código nem como sucesso.
- Nenhuma medição física do Poco F6, build APK ou teste end-to-end foi concluído nesta sequência.

### Limitações e próximos passos
- Thresholds de 6%/192 MiB, 82%/93% heap, estimativas e 2 slots são iniciais; precisam de calibração no Poco F6.
- O broker atual é admissão/concurrency gate, não fila persistente com prioridades ou retomada automática.
- Falta reação central a onTrimMemory e descarte de caches/motores.
- Falta modularizar ferramentas em manifests de estúdio e carregar motores sob demanda.
- O laboratório não está completo; Image Studio, UI/UX, 3D/CAD, full Android toolchain e ferramentas específicas de todos os papéis ainda não estão implementados.
- O cérebro não está aprovado nem conectado ao chat principal; manter isReady=false.

Documentação:
- ANDROID_HEADLESS_STUDIO_SPEC_2026-11-17.md
- CONTINUITY_HANDOFF.md


## Retomada e auditoria cruzada da oficina — 18/11/2026

**HEAD auditada:** `937001c85205666b013aaf8854c1c830409617ef` na branch `lab/kellvyn-intelligence-core-20261116`.

### Reconciliação das linhas históricas
- `workshop/super-lab-tools-20261024`, `lab/studio-workbench-20261024` e `lab/poco-f6-studio-upgrades-20261024` são linhas divergentes, não ancestrais integrados automaticamente na branch headless atual.
- Seus resultados continuam sendo referências reutilizáveis; não fazer merge indiscriminado, pois as branches antigas contêm superfícies de bancada/interface que não pertencem ao desenho headless aprovado.
- Portar algoritmos e testes individualmente, preservando a proveniência e reescrevendo adaptadores para os contratos internos Android.

### Estado confirmado no código da HEAD
- `KellvynPersistentTaskQueue`: SQLite, payload/result criptografados, prioridade, not-before, limite de tentativas, retenção, cancelamento de tarefas ainda não iniciadas, recuperação de RUNNING após morte do processo e reexecução at-least-once.
- `KellvynTaskScheduler`: registro de handlers em memória por sessão, execução sob Resource Broker, defer/retry/fail, recuperação lazy e limite de lote.
- `KellvynAutomationDrainWorker`: WorkManager drena no máximo duas tarefas LIGHT/STANDARD por sessão e nunca reivindica HEAVY em segundo plano.
- `KellvynResourceRuntime`: agenda trabalho único, integra `onTrimMemory`/`onLowMemory`, cache reclaimers e descarregamento de módulos ociosos.
- `KellvynStudioModuleManager`: manifests registrados sem carregar motores; adaptadores de ferramenta são expostos por capacidade e motores carregam sob demanda com lease e controle de recursos.
- Três módulos de estúdio registrados na runtime: navegação de código, auditoria de segurança e arquitetura de dados. Não são oito estúdios completos.
- Ferramentas de automação expostas ao modelo continuam somente leitura (`automation_queue_status`). Não existe ferramenta genérica do modelo para enfileirar/cancelar trabalho, por desenho de segurança.

### Lacuna operacional prioritária
- O scheduler suporta handlers, mas o runtime não registra handlers de produção nesta HEAD. O worker de teste vazio comprova apenas que uma sessão vazia termina; o teste de agendamento comprova apenas a criação de WorkManager único.
- Portanto, persistência, recuperação e despacho estão implementados como infraestrutura, mas ainda não há prova end-to-end de tarefa real criada por um fluxo autorizado, executada por handler concreto, retomada e verificada.
- Antes de ligar agentes à mutação, definir handlers tipados e allowlistados por capacidade, validação estrita de payload, idempotência, artefatos/evidências e política de aprovação. Não criar um executor genérico de comandos.

### Próxima sequência obrigatória
1. Criar e testar o primeiro handler real, limitado e idempotente, para uma operação interna sem efeitos destrutivos; cobrir enqueue → drain → resultado → reinício/recuperação.
2. Integrar o handler ao registro de runtime e validar que o worker só executa handlers explicitamente registrados.
3. Adicionar testes para prioridade/FIFO, handler indisponível, cancelamento, retry, limite de tentativas, HEAVY fora do worker e retomada.
4. Restabelecer CI com runner/steps reais; os jobs sem runner e sem steps não contam como testes.
5. Portar recursos das branches antigas por capacidade, sem importar interfaces.
6. Atualizar manifests e ferramentas por agente; manter especialistas READ_ONLY até gates de execução e autorização.
7. Só depois expandir motores de imagem, UI/UX, 3D/CAD e Android Build & Test.

**Não comprovado:** build Gradle da HEAD, testes Android/AVD na HEAD, instalação de APK desta revisão, medição física Poco F6, execução real de handler, ou integração operacional dos 14 agentes com a oficina.

## Primeira operação real do laboratório — 18/11/2026

**Roadmap vinculante:** `LAB_EXECUTION_ROADMAP_2026-11-18.md`.

### Implementado nesta continuação
- `KellvynResourceSnapshotTaskHandler.kt`: primeiro handler interno READ_ONLY, limitado e idempotente para produzir snapshot de recursos.
- `KellvynPermanentScheduledTaskException` e `failPermanently`: descritor inválido vai a FAILED com código específico, sem retries transitórios inúteis.
- `KellvynResourceRuntime.ensureBuiltInScheduledTaskHandlers()`: registro explícito e sincronizado da allowlist interna.
- `enqueueResourceSnapshotProbe()`: enfileira somente o descritor versionado do diagnóstico; não é ferramenta genérica exposta ao modelo.
- `KellvynAutomationDrainWorker`: restaura o registro dos handlers autorizados antes de reivindicar tarefas depois de reinício do processo.
- `scheduledTaskStatus()`: consulta limitada do estado e resumo para auditoria.
- Teste unitário do contrato do handler e teste instrumental Android para execução persistente, payload inválido, handler ausente e recuperação de RUNNING.

### Estado de validação
- Os testes estão escritos; não há resultado Gradle ou Android instrumentado aprovado nesta revisão.
- Workflow neural run `44212455036`, tentativa 2, continua com job sem runner/steps/logs.
- Workflow Poco F6 run `44212621981`, tentativa 2, continua com jobs sem runner/steps/logs; comparação final permaneceu skipped.
- A repetição dos jobs confirmou que o bloqueio de execução persiste. Não classificar isso como falha do código nem como sucesso dos testes.
- Sem validação física do Poco F6, sem APK validado e sem integração do cérebro ao chat.

### Próximo bloqueio
Restabelecer execução real de CI/Gradle ou obter uma execução Android local verificável; então corrigir qualquer falha de compilação/teste observada. Não avançar para handlers mutáveis até a Fase 1 ter prova end-to-end. A interface continua intocada.


### Atualização de cobertura do Worker — 18/11/2026
- `KellvynAutomationDrainWorkerInstrumentedTest` agora também cria uma tarefa READ_ONLY persistente autorizada e invoca `doWork()`, verificando estado SUCCEEDED, uma tentativa e resumo salvo.
- Execução de CI gerada pelo commit `878ed55d33cf2b72a238d70bcea31b63b24535e1`: run `44212948327`, tentativa 1, jobs sem runner/steps/logs e diff skipped.
- O teste do Worker foi acrescentado, mas continua não executado. A Fase 1 não está aprovada.

## Continuação: handler operacional, política dos 14 papéis e CI focado — 18/11/2026

### Oficina
- O primeiro handler seguro foi registrado: `KellvynResourceSnapshotTaskHandler`, somente leitura, payload versionado, resultado limitado e idempotente.
- `KellvynResourceRuntime` mantém uma allowlist explícita do handler embutido; o Worker restaura o registro antes de drenar a fila após reinício.
- Scheduler passou a rejeitar payload inválido permanentemente, sem retries inúteis.
- Testes adicionados: contrato do handler; fluxo do scheduler; rejeição permanente; handler ausente sem bloquear outro; recuperação de RUNNING; execução persistida pelo Worker via `doWork()`.

### 14 papéis
- A matriz de capacidades já existia em `KellvynSpecialistToolPolicy.kt`; ela não deve ser recriada.
- O coordenador aplica essa política e fornece aos especialistas apenas ferramentas READ_ONLY com capacidade expressamente autorizada.
- `KellvynSpecialistToolPolicyTest.kt` ganhou uma prova explícita dos 14 mapeamentos, cobrindo ferramentas read-only permitidas, capacidades não permitidas, tools não classificadas e todos os riscos mutáveis/de controle.
- Isso prova contratos e restrições, não competência cognitiva dos papéis. A matriz ainda precisa de validação cruzada com ferramentas reais dos módulos e testes end-to-end.

### CI e isolamento da validação
- Criado `.github/workflows/agent-lab-tests.yml`: executa unit tests Gradle e Android instrumented tests em emulador API 35; publica relatórios como artefatos.
- `poco-f6-parity.yml` agora é acionado por mudanças em MainActivity, recursos, manifesto e UI web; mudanças Kotlin internas deixam de disparar comparação visual desnecessária.
- Runs `44214073878` (agent lab, tentativa 2) e `44214499919` (agent lab, nova execução) terminaram sem runner/steps/logs. Run `44214499742` (neural core) também terminou sem runner/steps/logs. São estados NOT_RUN/BLOCKED; não há falha Kotlin diagnosticada nem teste aprovado.
- O ambiente local desta sessão não tem Gradle nem Android SDK. Não afirmar build ou teste local.

### Cobertura real dos papéis — atualização 18/11/2026
- Documento `AGENT_TOOL_AVAILABILITY_2026-11-18.md` registra as ferramentas que realmente aparecem nos manifests/runtime e o que continua ausente por papel.
- Novo teste instrumental `KellvynAgentToolAvailabilityInstrumentedTest.kt` abre `KellvynRuntime`, obtém os ToolSpecs efetivamente registrados e confere o conjunto permitido para cada um dos 14 papéis.
- Fixtures antigas de `KellvynAgentDelegationTest` e `KellvynAgentSpecialistEvaluationTest` foram corrigidas: agora a ferramenta READ_ONLY declara capability autorizada para o papel; a ferramenta REVERSIBLE_WRITE declara a mesma capability para testar que o risco, sozinho, ainda a bloqueia.
- `KellvynTaskScheduler.registerHandler` agora remove do allowlist em memória um handler cuja atualização durável falhar, evitando estado parcial de registro.

### Runner
- Run `44215633371` do workflow de laboratório após o teste de cobertura real terminou sem runner, steps ou logs. Não executar inferência sobre o código a partir desse estado.

## Memory Read do Analista de Memória — 18/11/2026

- Implementada `KellvynMemoryTools.memory_search`, adicionada ao registry de ferramentas local e reconstruída em `createAgent` com a lista exata de endereços autorizados da conversa.
- Escopo disponível: memória GLOBAL e, quando validado no workspace privado, memória do projeto ativo. O modelo não escolhe projeto nem escopo.
- Só retorna `NON_SENSITIVE`; registros `SENSITIVE_PERSONAL`, `CREDENTIAL` e `UNKNOWN` são excluídos.
- Ferramenta READ_ONLY com capability `MEMORY_READ`; somente o papel Analista de Memória recebe essa ferramenta durante delegação. O coordenador central também consegue utilizá-la para contexto próprio.
- Saída limitada a 5 memórias, 420 caracteres por conteúdo e 3.200 caracteres no total; inclui IDs de referência, proveniência, origem, confiança e aviso de conteúdo não confiável.
- Testes unitários criados para escopo, sensíveis, credenciais, desconhecidos, argumentos forjados e limites. Teste instrumental de disponibilidade atualiza o papel de Memória para `memory_search`.
- A fonte isolada `KellvynMemoryTools.kt` passou por `kotlinc` com stubs mínimos: verificação isolada de sintaxe/tipos, não build Gradle do app.
- Runs recentes `44217081776` (agent lab) e `44217081826` (neural) continuam sem runner, steps e logs: NOT_RUN/BLOCKED.

### Runner após Memory Read — 18/11/2026
- Runs `44217467213` (agent lab), `44217511064` (agent lab) e `44217511082` (neural core) foram consultados.
- Todos terminaram sem runner_id, steps ou logs_url. Estado NOT_RUN/BLOCKED; não demonstram falha de código nem aprovação.
- A ferramenta `KellvynMemoryTools.kt` passou em verificação isolada local: compilação Kotlin com stubs mínimos para interfaces da camada de agente e harness com repositório falso usando o algoritmo de busca de memória. Resultado: `KELLVYN_MEMORY_TOOL_INTEGRATION_TESTS_PASS`.
- Isso valida o fluxo isolado da nova ferramenta; não é compilação Gradle do aplicativo, execução Android/Keystore, nem prova de integração em dispositivo.

## Android Build status read-only — 18/11/2026

- Registrado módulo lazy `android-build-status` em `KellvynRuntime`.
- Nova ferramenta `android_build_environment_status`, capability `ANDROID_BUILD`, risco READ_ONLY, sem argumentos, workload LIGHT.
- Relata cache de `android.jar`, aceite da licença SDK e metadados limitados do último APK; declara explicitamente que não executa instalação ou teste e não prova funcionamento do APK.
- Reusa o `ProjectWorkspaceStore` que o runtime já possui, sem abrir uma store adicional.
- Política entrega ferramenta apenas a Test Analyst, Security Analyst, Debugger e Auditor. Teste instrumental da matriz real foi atualizado e teste próprio cobre output/argument rejection.
- O backend existente é Java-only limitado (ECJ → D8 → empacotamento/assinatura); este módulo não adiciona compilação Kotlin/Compose, Gradle completo ou execução UIAutomator.
- CI atual: runs `44218653002` e `44218652911` terminaram sem runner/steps/logs. Continuam NOT_RUN/BLOCKED.
- Verificação isolada de tipos do novo módulo com Kotlin compiler/stubs mínimos passou; não substitui Gradle ou teste Android.

### CI após módulo Android Build Status — 18/11/2026
- Runs `44218653002` (agent lab), `44218652911` (neural core) e `44218608802` (agent lab) foram verificados; jobs sem runner, steps e logs_url.
- O workflow ainda não executou testes na revisão do quarto módulo. Teste instrumental e contrato estão escritos.
- Typecheck isolado da classe `KellvynAndroidBuildStatusStudio` com stubs do Android/runtime/module APIs mínimos passou; não é Gradle nem compilação Android real.

## Agent Creation Studio invisível — Fase A — 18/11/2026

### Achados da auditoria

- A fundação compartilhada já existia: KellvynStudioModuleManager, ResourceRuntime/ResourceGovernor, workspace, task broker, skills sob demanda e delegação dos 14 papéis.
- Não havia um módulo de criação/gestão de perfis. As instruções dos papéis estavam embutidas no bloco when de KellvynAgent.
- O desenho completo do Agent Creation Studio estava apenas em documentação preliminar. A oficina de recursos não deve ser confundida com o construtor de perfis.

### Código acrescentado nesta fase

- KellvynSpecialistProfileRegistry.kt: 14 perfis únicos, versão inicial, missão, instrução, contrato de saída e limites por papel.
- KellvynAgentCreationStudio.kt: módulo headless READ_ONLY com catálogo, inspeção e validação estrutural.
- KellvynAgent.kt: delegação agora obtém a missão/instrução e limites do registro compilado em vez do bloco inline; cada retorno registra o SHA-256 do perfil usado.
- KellvynSpecialistProfileCompiler.kt: compilação determinística, validação fail-closed e hash SHA-256 sobre perfil + capacidades derivadas da política central.
- KellvynSpecialistToolPolicy.kt: acesso às ferramentas do Studio limitado a Arquiteto de Programação/Planejamento/Auditor para inspeção e Analista de Testes/Aprendizado/Auditor para validação.
- KellvynStudioModuleManager.kt: novas capacidades AGENT_DESIGN e AGENT_EVALUATION.
- KellvynRuntime.kt: registro do Agent Creation Studio no runtime comum.

### Testes e estado

- Criado KellvynSpecialistProfileRegistryTest.kt cobrindo 14 perfis, unicidade, versão, limites, contrato de saída, escopo por papel, rejeição de argumentos inválidos, estabilidade e mudança de hash.
- Atualizado o teste de matriz exata de permissões para incluir AGENT_DESIGN/AGENT_EVALUATION nos cinco papéis autorizados.
- Smoke harness Kotlin/JVM executado localmente com kotlinc usando cópias equivalentes das três classes novas e stubs dos contratos externos: compilou e passou 14 perfis, estabilidade/mudança de SHA-256, separação dos limites de seções, catálogo, inspeção, validação e rejeição de papel inválido. Limite: não é compilação byte-for-byte de toda a árvore Android, nem Gradle/JUnit/AVD.
- Os testes JUnit foram escritos, mas ainda não foram executados em Gradle/CI. Os workflows falharam antes de apresentar qualquer etapa ou log; o resultado não diagnostica falha de compilação do código.
- O módulo eleva a contagem de módulos registrados para seis. Isso não significa oito estúdios completos. A Fase B começou com o Profile Compiler; promoção/publicação e harness de competência ainda não existem.
- O catálogo estrutural não comprova competência cognitiva. O cérebro conversacional continua sem integração aprovada ao chat e a qualificação física no Poco F6 ainda não foi executada.

### Pesquisa relacionada

- Pesquisa documentada em AGENT_CAPABILITY_RESEARCH_2026-11-18.md: Lovable/Lovable Academy, Replit Agent, Android Studio Gemini, Cursor, OpenAI Agents SDK, LangGraph, Nano Banana e curso Android Basics with Compose.
- Especificação do construtor de perfis em AGENT_CREATION_STUDIO_SPEC_2026-11-18.md.


### Agent Creation Studio — catálogo de competência e harness — 18/11/2026

- Criado `KellvynSpecialistBenchmarkRegistry.kt` com 28 casos de avaliação em português: dois por papel, incluindo qualidade, segurança, prompt injection, regressão, recursos, persistência, auditoria, depuração e análise visual.
- Verificação estática do arquivo no HEAD: 28 IDs únicos, exatamente dois casos para cada um dos 14 papéis e nenhuma capability de caso fora da matriz central.
- O módulo `KellvynAgentCreationStudio` expõe `agent_benchmark_catalog`: sem argumento lista os 28 cenários; com `role` retorna rubricas detalhadas. A ferramenta é READ_ONLY e fica disponível apenas aos papéis com AGENT_EVALUATION.
- Criado `KellvynSpecialistEvaluationHarness.kt`: execução sequencial, lista restrita de ferramentas, validação do hash do perfil, detecção de solicitações/execuções não autorizadas e aprovação apenas quando um verificador independente comprova todos os critérios e anexa referências.
- Criados testes em `KellvynSpecialistEvaluationHarnessTest.kt` para aprovação com evidência, bloqueio de ferramenta indevida, divergência de hash, referência ausente, isolamento de Web Research e execução sequencial de suite.
- Smoke harness local Kotlin/JVM com stubs e cópias equivalentes das classes de perfil/Studio/harness: compilou e executou suite sintética de 28 casos, com 28/28 aprovados; também reprovou tentativa de ferramenta de escrita. Não compilou byte-for-byte o arquivo de 28 cenários, não usou o módulo Android/Gradle e não usou o cérebro real. Isso valida a mecânica do harness, não a competência cognitiva.
- CI mais recente: runs `44327863319` e `44327863230` falharam sem steps/logs_url; não há diagnóstico de compilação e os testes JUnit continuam sem execução comprovada.
- O harness ainda não foi conectado a uma inferência neural aprovada nem possui verificador factual de produção. O catálogo não deve ser anunciado como benchmark cognitivo já executado.


### Última revisão e CI — 18/11/2026, 14:53 UTC

- Revisão final de perfis/harness concluída; instrução interna agora identifica explicitamente enum do papel + nome humano.
- HEAD de código/documentação nesta rodada: `e521fcaf11239fd88d9fa396bc27a719b7575a4c`.
- Runs disparados após a última alteração: `44329166238` (Agent Lab) e `44329185728` (Neural Core). Ambos concluíram em failure, com jobs sem runner/steps/logs_url. A causa permanece indisponível e não deve ser atribuída ao código.
- A cópia Kotlin/JVM de smoke usada na validação local continua sendo apenas equivalente com stubs e casos sintéticos. Não é Gradle, JUnit real, AVD, APK nem benchmark do cérebro.


## Agent Creation Studio — revisão persistente, auditoria e rollback — 18/11/2026

### Código acrescentado nesta rodada
- KellvynSpecialistProfileStore.kt: diário append-only de eventos STAGED/APPROVED/ROLLED_BACK, payload AES-GCM cifrado, chave Android Keystore dedicada, AAD sobre o cabeçalho, hash SHA-256 encadeado e compare-and-swap por hash do ledger e hash do perfil ativo.
- A versão ativa só muda por evento APPROVED com confirmação explícita e hash exato do candidato. ROLLED_BACK aponta para uma revisão já aprovada ou para o perfil-base original; o histórico não é apagado.
- KellvynRuntime: cria o store numa pasta privada separada, oferece APIs internas para preparar, aprovar e reverter revisões e atualiza novas factories com o conjunto aprovado. createAgent e createProjectWorkspaceAgent agora são suspend para carregar perfis do disco em Dispatchers.IO.
- KellvynAgentCreationStudio: catálogo/inspeção/validação usam perfis ativos; foram adicionadas ferramentas READ_ONLY agent_profile_history e agent_profile_pending_diff, com valores antes/depois limitados. A aprovação não é uma ferramenta de agente.
- Testes unitários escritos para staging sem ativação, confirmação obrigatória, hash incorreto, concorrência obsoleta, baseline rollback, revisão não aprovada, corrupção e diff. Teste Android instrumentado escrito para persistência entre instâncias e autenticação AAD com Android Keystore real.
- A matriz de ferramentas unitária e instrumentada foi atualizada para as duas ferramentas de auditoria READ_ONLY.

### Limites atuais
- O código e os testes foram escritos; ainda não há resultado Gradle/JUnit/Android instrumentado aprovado para esta alteração.
- As APIs de stage/approve/rollback são Kotlin internas confiáveis; não são expostas ao modelo como ferramentas mutáveis. A aplicação ainda precisa de um fluxo explícito de confirmação do proprietário antes de chamar approve.
- A inferência neural ainda não foi aprovada/ligada ao chat principal. Nenhum dos 28 cenários mede competência real neste momento.
- O histórico é encadeado e autenticado por AES-GCM; a proteção anti-replay/anti-truncamento do diretório contra um atacante com controle integral do armazenamento ainda não é uma garantia.
- A última tentativa disponível dos workflows de CI terminou sem runner/steps/logs reutilizáveis; não atribuir a falha ao código nem declarar a suíte aprovada.
- A interface e MainActivity.java não foram alterados.

### Próxima ação
1. Executar Gradle unit tests e instrumented tests num runner real; corrigir apenas falhas com logs.
2. Implementar o fluxo confiável de revisão visual do diff e confirmação do proprietário, sem disponibilizar aprovação ao agente.
3. Conectar resultados de benchmark a uma inferência aprovada, nunca a respostas sintéticas.
4. Retomar os outros Studios por necessidade real, sem declarar oito motores completos.


## Auditoria do último commit — 18/11/2026, 15:20 UTC

- HEAD atual: 6e07def97f59c7cf749e6e9a469eaf1093ec1dd5.
- O Profile Store limita o ledger a 256 registros globais e cada registro a 32 KiB; as ferramentas de inspeção/histórico reservam 24 MiB e executam como STANDARD. A consulta de histórico mostra apenas os 12 eventos mais recentes.
- O diff de revisão lê o ledger uma vez e mostra campo, valor anterior e valor proposto, limitado a 6.500 caracteres.
- Workflow Agent Lab: run 44334550347; job 134907561447 terminou em failure sem steps e sem logs_url. Não houve execução observável de Gradle/JUnit/Android instrumented.
- Workflows do commit anterior cf2434dc9f8f518b483c6484944efc1ddc9a8b4c: Agent Lab 44334107998 / job 134906002466 e Neural Core 44334108000 / job 134906002719; ambos também encerraram sem steps/logs_url.
- get_commit_combined_status para 6e07def97f59c7cf749e6e9a469eaf1093ec1dd5 retornou lista vazia. Não classificar a causa como erro Kotlin/Gradle nem afirmar CI aprovado.
- MainActivity.java permaneceu sem alteração nesta rodada; hash observado 8e1aea5406920ef273a50d5c91a5a8be6434c0b3, enquanto a entrada histórica em interface-lock.sha é bc7de61c36a3c46fc90b9962600cb48d6c3ed2c8. Divergência preexistente, não corrigida por alteração visual.
- Nenhum APK foi compilado, assinado, instalado ou testado no Poco F6 nesta rodada.


## Última conferência da branch — 18/11/2026, 15:24 UTC
- HEAD atual: d3e0816b9d6fd37436c9b3afbb62cc4405b1d5a7.
- O orçamento de leitura do Agent Creation Studio foi elevado para 24 MiB (classe STANDARD); ledger continua limitado a 256 registros globais e 32 KiB por registro, com histórico de saída limitado a 12 eventos.
- Workflow Agent Lab no HEAD: run 44335158436, job 134909733286, failure sem steps e sem logs_url. A execução encerrou em aproximadamente três segundos; não existe evidência de que Gradle tenha iniciado.
- O histórico de execução não apresenta logs reutilizáveis. Não classificar como erro de compilação ou teste do código, nem como CI aprovado.
- Não houve build APK, instalação, execução em emulador ou teste físico no Poco F6.


## Rerun controlado — 18/11/2026, 15:25 UTC
- Rerun solicitado para o run 44335158436; novo job 134910540861 também terminou em failure, sem steps e sem logs_url.
- A repetição controlada confirma que não é um erro reproduzido numa etapa de compilação/teste: o runner continua sem fornecer sequer o início dos steps. A causa exata permanece não exposta pelo GitHub.
- Não fazer novas alterações de fonte para tentar adivinhar o problema; verificar disponibilidade de Actions compute/runner na conta.
