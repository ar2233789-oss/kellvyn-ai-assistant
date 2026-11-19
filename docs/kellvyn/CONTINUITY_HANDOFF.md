# KELLVYN — CONTINUIDADE OBRIGATÓRIA ENTRE CONVERSAS

**Atualizado:** 16/11/2026  
**Repositório de laboratório:** ar2233789-oss/kellvyn-ai-assistant  
**Branch de trabalho:** lab/kellvyn-intelligence-core-20261116  
**Repositório antigo:** reservado para publicar a versão final quando houver aprovação.  
**Alvo:** Poco F6, Android/HyperOS, 8 GB RAM, uso privado, local/offline primeiro.

> **REGRA ATUALIZADA — 18/11/2026:** o ponto único de entrada e a fonte atual de continuidade agora é [SESSION_CONTROL.md](SESSION_CONTROL.md). Leia-o primeiro e confirme o HEAD real da branch. Este arquivo continua preservado como histórico técnico detalhado; suas seções antigas de “próxima tarefa” e estados anteriores não devem sobrepor o estado mais recente em SESSION_CONTROL.md.

## 1. Missão — não desviar

Entregar a inteligência Kellvyn como uma IA própria, capaz de conversar em português, raciocinar, planejar, pesquisar, manter memória, usar ferramentas, programar, criar aplicativos e validar que eles funcionam.

O foco atual é o **cérebro**, não a aparência do aplicativo. Não editar interface, cores, chuva, raios, menus, barras, animações ou componentes já aprovados durante este trabalho.

## 2. Decisão permanente de autoria

O cérebro conversacional será **construído e treinado do zero**:
- arquitetura neural implementada no projeto;
- pesos inicializados pelo código da Kellvyn e aprendidos pelo treinamento próprio;
- tokenizer e vocabulário próprios;
- pipeline de dados, treino, avaliação, inferência e checkpoints próprios;
- nenhuma dependência obrigatória de servidor de IA, API paga ou modelo externo.

ChatGPT, Claude, Claude Code, Replit, Android Studio/Gemini, Cursor e outros podem servir como inspiração **somente pelas capacidades e técnicas publicamente documentadas**. Reimplementar as ideias com engenharia própria. Não copiar código proprietário, prompts internos, pesos privados ou mecanismos protegidos.

Código aberto auxiliar pode ser estudado ou incorporado quando a licença permitir, com origem, versão e atribuição. Isso não autoriza importar pesos pré-treinados ou adapters como cérebro. Não substituir esta decisão por um modelo baixado de outra empresa.

Documentos vinculantes:
- docs/kellvyn/NEURAL_MODEL_DECISIONS.md
- docs/kellvyn/ARCHITECTURE.md
- docs/kellvyn/MASTER_PLAN.md

Se qualquer documento antigo contradisser a decisão de pesos próprios, a decisão neural acima prevalece e a documentação antiga deve ser corrigida, não a ordem do proprietário.

## 3. O que já existe de verdade

### Camada cognitiva
- KellvynAgent.kt: orquestração e ciclo limitado de ferramentas, políticas, timeout e limites.
- KellvynTaskPlanner.kt: plano estruturado, dependências, risco, aprovação, pré-condições, critérios e evidência independente.
- KellvynMemory.kt: tipos de memória, escopo, consentimento, proveniência, confiança e busca lexical em português.
- KellvynEncryptedMemoryRepository.kt: SQLite local, AES-GCM, Android Keystore e isolamento por escopo.
- KellvynContextAssembler.kt: montagem de contexto com orçamento determinístico por caracteres.
- KellvynProjectContextRetriever.kt: recuperação lexical de arquivos/trechos do projeto ativo, com limites e filtros.
- KellvynTaskCheckpointStore.kt: snapshots append-only do estado de tarefas, validação da cadeia, SHA-256, compare-and-swap e payload cifrado por uma interface.
- KellvynAndroidTaskCheckpointCipher.kt: AES-GCM com Android Keystore.
- KellvynRuntime.kt: fábrica do agente, memória e checkpoint; ainda sem motor de inferência.

### Protótipo neural
KellvynByteModel.kt, nome de laboratório KN-001:
- rede rasa que prevê o próximo byte UTF-8;
- pesos próprios inicializados no código;
- embeddings, forward, softmax, gradientes, atualização de parâmetros e checkpoint;
- testes de UTF-8, loss em padrão repetido, diferenças finitas e restauração de pesos.

**Limite:** KN-001 não é uma IA conversacional. Não possui Transformer, tokenizer subword, corpus de linguagem suficiente, raciocínio multi-turno ou capacidade geral de seguir instruções. Não apresentar isso como cérebro pronto.

### Construção e testes de aplicativos
Existe um quality gate que injeta um projeto Android Java de teste no construtor local, compila, assina, valida, instala em emulador e interage com a tela. É uma prova do pipeline do construtor com fixture determinístico, não uma prova de que o cérebro criou o app pelo chat.

## 4. Onde paramos nesta sessão

A interrupção anterior foi causada por uma alteração incompleta no checkpoint store: a classe passou a exigir dois argumentos, mas runtime e testes ainda passavam somente a pasta. Corrigi:

1. KellvynRuntime agora cria KellvynTaskCheckpointStore com File privado e KellvynAndroidTaskCheckpointCipher.
2. KellvynTaskCheckpointStoreTest agora injeta um cifrador AES-GCM exclusivo de teste.
3. Atualizei ARCHITECTURE.md, NEURAL_MODEL_DECISIONS.md e MASTER_PLAN.md para fixar a decisão do cérebro original.
4. Atualizei STATUS.md e criei este arquivo de continuidade.

Commits mais importantes desta correção:
- runtime: d9fb31379a6373fe4e1082916705deb29ee23262
- teste de checkpoint: 7fb7ba187f5bd7537c0649d0fd17f329d51ab74d
- arquitetura: 90b7c83a0fad1623012d21823a9545a86b965f5a
- decisão neural: f400ffa0ab1f34237f5ed1bdb8e131c6f04d3536
- plano mestre: 1f443eee9b9f900e000bb881957666f1f647a11e
- tokenizer KN-002: a0704eba6815d2410a720d0ae5dd60ff0b0ab98d
- teste do tokenizer: 5cbf434850c80aea48178b635c31115b061ab74b
- limites do tokenizer: 61b382636a536cd627b162390ab39be37ab6005b e 5239dc19fd9eb368a690d254d60189fcac083571
- modelo atenção KN-002 alpha: e96ad13856bf2aa9d47e9b21bae8ee79f67bfc2b
- testes modelo: be2584b7724bbfcf3c917368a45b63d32cf86178
- fábrica tokenizer/modelo: de900376672b009bc94401784b333fbdac7cd7e8
- integração BPE/modelo em teste: 7ecd8c26792f2336204bc8a38de099b932b0a075
- leitura de checkpoint limitada/prefixo de geração: ad7a1327106ff2da847b80da0dd9ba01140090c6

**Não alegar que o CI passou.** A consulta de status para os commits de runtime/checkpoint e tokenizer retornou lista vazia; ainda não há check verificável nesta sessão. Os caminhos editados estão cobertos pelo filtro de push do workflow neural, mas falta confirmar a execução. Também não houve build Android nem teste no Poco F6 após as correções.

## 5. Próxima sequência obrigatória

### Passo 1 — fechar a correção que ficou pendente
- confirmar o HEAD atual da branch;
- consultar a execução do workflow neural associada aos commits recentes;
- se falhar, abrir logs, corrigir e repetir;
- verificar importações, versão Kotlin/Gradle e se os testes cobrem AAD, alteração de payload e sequência;
- planejar teste instrumentado Android Keystore; não confundir o cifrador de teste JVM com o Keystore real.

### Passo 2 — construir o cérebro próprio KN-002
Já existe o tokenizer BPE próprio e a primeira alpha neural `KellvynAttentionLanguageModel.kt`, com atenção autoregressiva de uma cabeça, gradientes, AdamW e checkpoint. Testes foram escritos e a ligação BPE→modelo foi adicionada. A revisão matemática de referência em NumPy float32 encontrou erro absoluto máximo ≈9,6e-7 em 21 coordenadas e reduziu loss de padrão alternado de 5,49 para 0,00392 em 1.100 atualizações simuladas; **isso não é build nem teste Kotlin**. Falta obter CI verificável.

Já existe o primeiro componente: KellvynBpeTokenizer.kt, com teste separado. Ele ainda não foi validado por CI. A implementação aplica a sequência de merges de forma simples e precisa de otimização antes de treinar em corpus grande. A base de implementação planejada está no NEURAL_MODEL_DECISIONS.md:
- tokenizer próprio: byte UTF-8 como base e BPE treinado em corpus autorizado;
- modelo autoregressivo decoder-only;
- atenção causal, embeddings e blocos neurais;
- loss de próximo token, retropropagação e AdamW;
- checkpoints versionados, reproduzíveis e íntegros;
- inferência incremental, limites de contexto e cancelamento;
- testes numéricos de gradiente, overfit de corpus minúsculo, validação separada e avaliações em português brasileiro.

Não começar por uma promessa de modelo enorme. Primeiro provar cada bloco matemático e o pipeline de aprendizagem com testes. Depois medir no Poco F6 RAM, latência, temperatura, bateria e estabilidade. Um modelo minúsculo treinado do zero não será equivalente a ChatGPT ou Claude; capacidade cresce com dados, parâmetros e computação. Dizer a verdade sobre isso.

### Passo 3 — ligar o cérebro ao agente
Só conectar KN-002 ao KellvynRuntime quando houver geração conversacional testada. Depois integrar o mesmo modelo ao agente, com uso de ferramentas estruturadas e permissões aplicadas pelo código. Nunca criar um fallback oculto de modelo externo.

### Passo 4 — testar criação de apps pelo chat principal
Separar:
- teste do compilador com fixture fixa;
- teste da IA gerando requisitos/código;
- teste do agente escrevendo no workspace;
- teste de compilação;
- instalação e interação real do APK.

Aprovar uma etapa não aprova as demais. O objetivo é o chat principal produzir apps funcionais e provar o funcionamento, não apenas armazenar blocos de código.

## 6. Travas contra os erros anteriores

Antes de cada edição, verificar:
1. Isto faz parte do cérebro ou de um bloqueio técnico direto do cérebro?
2. Já existe código para isso? Li o arquivo inteiro e os testes relevantes?
3. A alteração preserva as decisões anteriores e a interface bloqueada?
4. O código compila? Qual teste comprova?
5. O que ficou explicitamente sem validação?

Não inventar trabalho realizado. Distinguir sempre:
- pesquisado;
- especificado;
- escrito;
- compilado;
- testado;
- validado no aparelho;
- integrado ao chat;
- entregue.

Não declarar uma função concluída apenas porque o código foi escrito. Não reintroduzir seleção de modelo externo, treinamento LoRA de pesos alheios, redesign visual ou qualquer item que o proprietário recusou. Não perguntar de novo coisas já registradas neste arquivo.

## 7. Como manter a continuidade após cada chat

No fim de cada sessão de trabalho:
- atualizar STATUS.md com alterações, commits, CI e limitações;
- atualizar este CONTINUITY_HANDOFF.md com o ponto exato de interrupção e a próxima ação;
- registrar decisão nova em NEURAL_MODEL_DECISIONS.md ou MASTER_PLAN.md;
- deixar o repositório em estado verificável, sem dizer que um teste passou até ler o resultado.

**A conversa pode terminar; o registro técnico não pode ficar para trás.**

## 6. Atualização da revisão do agente — 16/11/2026

Pesquisa oficial revalidada:
- Replit Agent: modo Plan/Build, skills carregadas sob demanda, testes automáticos aplicáveis a apps web e checkpoints/rollback.
- Cursor Agent: busca de codebase, edição multi-arquivo, terminal, navegador, revisão de diffs, checkpoints, regras persistentes e coordenação de agentes.
- Android Studio Gemini Agent: plano multi-etapas, edição multi-arquivo, build, deploy em emulador/dispositivo, screenshots, Logcat e interação ADB.

Documento detalhado: `docs/kellvyn/AGENT_CAPABILITY_UPGRADE_2026-11-16.md`.

### Alteração de skills já presente na branch
- `KellvynSkillRegistry.kt`: seleção lexical determinística de procedimentos relevantes, com limite por tarefa e por caracteres.
- Skills iniciais: Android build/verification, diagnóstico de falhas, code review, pesquisa Web e teste funcional de apps.
- `KellvynAgent.kt`: injeta somente skills selecionadas no contexto de sistema; skill não amplia autorização nem cria ferramentas.
- `KellvynSkillRegistryTest.kt`: seleção, exclusão de skills irrelevantes, determinismo, orçamento e injeção.
- Essa camada ainda depende de aprovação de CI. A seleção é lexical, não busca semântica.

### Correções desta sessão
- Commit `9977a800e036f50ac114746ae52f48f137ba5450`: corrigiu a obtenção não nula do diretório de checkpoints e mudou `ZERO_HASH` de const inválida para propriedade.
- Commit `6282fd6d31241dd5451f8250590fabb70a6bf641`: moveu normalização/tokenização de skills para funções de arquivo reutilizáveis, corrigindo referências não resolvidas.
- Workflow neural do commit `6282fd6d31241dd5451f8250590fabb70a6bf641`: execução `43974766928`, ainda em andamento no último registro desta atualização. Não declarar sucesso antes da conclusão.
- Workflow do laboratório Android do mesmo commit: execução `43974766963`, ainda em andamento. Não equivale a teste físico no Poco F6.

### Próximas prioridades do agente
1. Fechar CI e testes antes de acrescentar mais módulos.
2. Conectar checkpoints ao ciclo real do agente (plano, etapa, tentativa, ferramenta, resultado, erro e retomada).
3. Integrar `KellvynTaskPlanner` à execução para tarefas multi-etapas, com gates de aprovação e dependências.
4. Emitir eventos estruturados de progresso e evidência.
5. Evoluir busca lexical para índice incremental de símbolos, imports, referências e dependências.
6. Integrar diff/revisão, build, instalação, screenshots, acessibilidade e Logcat por ferramentas autorizadas.
7. Não mexer na interface aprovada; não declarar agente funcional no chat principal enquanto o backend neural não estiver conectado e testado.

Limite atual: `KellvynRuntime.isReady() = false`; o chat principal ainda não executa o agente com um backend neural real.

## 8. Nova melhoria do agente — skills sob demanda (16/11/2026)

Foi pesquisada documentação oficial atual do Replit Agent, Cursor Agent e Gemini Agent Mode no Android Studio. O comparativo e as adaptações estão em `docs/kellvyn/AGENT_CAPABILITY_UPGRADE_2026-11-16.md`.

### Implementação escrita na branch

- `KellvynSkillRegistry.kt`: catálogo de skills procedurais internas; seleciona por relevância lexical, com desempate determinístico e limite de caracteres.
- `KellvynAgent.kt`: injeta até duas skills relevantes no contexto SYSTEM confiável. O texto de skill orienta o método, mas não fornece permissões nem substitui as verificações do código.
- `KellvynSkillRegistryTest.kt`: testes para carregamento relevante, exclusão de skills irrelevantes, orçamento, determinismo e injeção confiável.
- Workflow `neural-core-tests.yml` atualizado para observar mudanças no registro de skills.

Skills iniciais: Android build/verification, diagnóstico de falhas, revisão de código, pesquisa Web e teste funcional de aplicativos.

Commits de referência: `6cf8f68e772089224a8e2606c954c03470032b76`, `48936a1f59bacf1f810a763903e05635a0579762`, `e136c4d1a3cb05c8153633e8c209f907fd4fedc7`, `b640da81645d8a8606f2fae5ac97b90b8320865b`, `ee0f96ff86328d9740d53eba22c7ffa666417dab`, `b3f2804b5dcdcf919492855b557733b7f44a2e92`.

Um smoke test Kotlin isolado da lógica de seleção foi compilado/executado com sucesso. **Não equivale a compilar o projeto Android.** Não há confirmação de CI/Gradle para esses novos arquivos e não houve teste no Poco F6. O runtime neural continua sem backend conversacional ligado e a conversa principal ainda não utiliza o agente.

A última correção documental desta etapa também alinhou `REFERENCE_STUDY.md` à decisão de pesos próprios: `0c7ce817ede6d1ff3f42fc40de39b98580b376dd`.

### Próxima sequência

1. Confirmar execução do workflow da branch; se falhar, obter logs e corrigir.
2. Ligar o armazenamento de checkpoints ao ciclo real: início, etapas, resultados, provas, falhas, cancelamento e retomada.
3. Introduzir eventos de progresso auditáveis.
4. Evoluir busca lexical para mapa de símbolos/imports/referências no projeto.
5. Criar verificação independente de diff/testes e ciclo Android com build, instalação, screenshots, acessibilidade e Logcat.
6. Integrar ao chat principal somente depois de haver backend neural autoral funcional e testes; preservar toda a interface aprovada.

## 9. Continuidade — atualização do agente Plan/Build (16/11/2026)

### Pesquisa técnica consolidada

Fontes oficiais:
- Replit Agent overview: https://docs.replit.com/pt/references/agent/overview
- Replit Plan mode: https://docs.replit.com/pt/references/agent/plan-mode
- Replit Skills: https://docs.replit.com/pt/references/agent/skills
- Replit Code Optimizations: https://docs.replit.com/references/agent/code-optimizations
- Replit App Testing: https://docs.replit.com/pt/references/agent/app-testing
- Replit checkpoints: https://docs.replit.com/pt/references/version-control/checkpoints-and-rollbacks
- Cursor Agent: https://cursor.com/help/ai-features/agent
- Cursor Subagents: https://cursor.com/docs/subagents
- Android Studio Agent Mode: https://developer.android.com/studio/gemini/agent-mode
- Android Studio Agent features: https://developer.android.com/studio/gemini/features

### Código adicionado após a pesquisa

1. **Skills sob demanda**
   - `KellvynSkillRegistry.kt` com Android/build, diagnóstico, revisão, navegação Kotlin/Java, pesquisa técnica e teste funcional.
   - `KellvynAgent.kt` seleciona no máximo duas skills e insere o procedimento no contexto SYSTEM confiável.
   - Skills não têm poder de execução nem aumentam permissões.

2. **Índice estrutural de código**
   - `KellvynProjectCodeIndexer.kt` extrai pacote, imports, declarations Kotlin/Java e chamadas.
   - `KellvynProjectContextRetriever.kt` usa os identificadores para melhorar ranking e o contexto devolvido ao agente.
   - É um índice lexical limitado, não AST.

3. **Plan/Build persistente**
   - `KellvynTaskCheckpointRepository.kt` é o contrato de persistência.
   - `KellvynRuntime.kt` liga o contrato ao store local cifrado.
   - `KellvynAgent.kt` aceita `KellvynModelDecision.Plan`, valida ferramentas, riscos, dependências e critérios; grava o plano antes de executar.
   - `restoreTaskPlan`, `approveTaskStep` e `executePlannedStep` recuperam, registram aprovação e executam uma etapa.
   - A execução salva RUNNING antes da ferramenta e exige `KellvynIndependentVerifier` para salvar SUCCEEDED. Uma resposta de sucesso sem prova independente deixa a etapa FAILED.
   - Uma ferramenta por etapa executável nesta alpha; autorização continua separada da aprovação do proprietário.

4. **Testes escritos**
   - `KellvynSkillRegistryTest.kt`
   - `KellvynProjectCodeIndexerTest.kt`
   - `KellvynAgentPlanTest.kt`

### Validação e verdade operacional

O harness Kotlin isolado do indexador passou nos casos de pacote, imports, declarações, referências, comentários, strings e limite. Um smoke test independente da seleção de skills também passou. Isso não é Gradle/JUnit do repositório. Status combinado dos commits consultados veio vazio; a consulta de runs disponível só filtra por PR e não retornou execução. Portanto CI permanece não confirmado.

O backend neural não existe ainda; o chat principal não chama o agente; não houve build de APK ou teste no Poco F6. As decisões Plan/CallTool foram adicionadas ao contrato interno e ainda não são emitidas por um modelo real.

### Próximas ações obrigatórias

1. Conseguir execução verificável do workflow e corrigir falhas Kotlin/JUnit reais.
2. Testar cifrador Android Keystore com instrumentation no emulador.
3. Integrar a emissão estruturada Plan/CallTool a um backend neural Kellvyn validado.
4. Adicionar um executor de Build que avance dependências automaticamente, respeite uma ferramenta por etapa e persista cada transição/resultado.
5. Integrar progresso e aprovação ao chat principal sem alterar a interface.
6. Evoluir mapa de código para índice incremental de símbolos, assinaturas e referências, preservando limites e filtro de segredos.
7. Depois, criar especialistas internos com contexto isolado. Não chamar de subagentes enquanto não houver isolamento real.
8. Implementar ciclo de Android device testing: build, instalação, screenshots, acessibilidade, Logcat, interação, validação e correção.
9. Executar testes físicos no Poco F6 somente após os gates de build e segurança.

Atualizar este arquivo, STATUS.md e o relatório comparativo após cada novo marco.

## 10. Delegação especializada interna — 16/11/2026

### Implementação

`KellvynAgent.kt` agora aceita `KellvynModelDecision.Delegate` com os papéis RESEARCHER, CODE_EXPLORER, REVIEWER e TEST_ANALYST.

- Reutiliza a mesma instância de inferência do agente principal.
- Cria contexto/histórico separado e passa somente subtarefa/contexto autorizados.
- Não repassa memória nem recuperação automática do projeto.
- Filtra as ferramentas para a lista explicitamente pedida e somente risco READ_ONLY.
- Desabilita delegação recursiva, plano, checkpoints e ferramentas mutáveis no especialista.
- Limita quantidade, contexto, etapas, chamadas e timeout.
- Retorna resultado ao pai como observação UNTRUSTED.
- É sequencial, não paralelo.

`KellvynToolRegistry.readOnlySubset()` implementa a projeção de ferramentas permitidas.
`KellvynAgentDelegationTest.kt` testa contexto isolado, filtragem destrutiva, ferramenta de leitura, retorno e bloqueio recursivo. O teste foi corrigido para que o pai receba uma decisão final após a subtarefa completar.

### Últimas alterações de referência

- Delegação inicial: `4998632d4f171db3b5706a03bb58f8d09691bdba`
- Não herdar contexto implícito do projeto: `9b81e87ed1a8ab986245e056ead85b5302a72e8b`
- Teste corrigido para ciclo completo: `115267c41e2301d8a782c090d9cbb4d18f05e721`
- Teste exercita leitura permitida e observação: `83afb2b77e6c6c26159b15151c7c1b0f45397abb`

### Próximas ações

1. Confirmar compilação/CI de todas as alterações novas.
2. Rever e endurecer tratamento de cancelamento e falhas no executor de etapa.
3. Ligar progresso/checkpoints a uma execução coordenada de plano com dependências, mantendo aprovação por etapa.
4. Fazer o modelo neural próprio emitir decisões Plan, Delegate e CallTool somente após a interface de inferência ser validada.
5. Criar ferramentas autorizadas de build e teste no laboratório, incluindo device install, screenshots, acessibilidade e Logcat.
6. Não afirmar subagentes paralelos nem funcionamento no chat até que sejam implementados e testados.

## 11. Executor incremental do plano — 16/11/2026

`KellvynAgent.executeReadySteps()` foi adicionado para percorrer etapas READY de um plano persistido:
- recupera checkpoint mais recente;
- seleciona a próxima etapa pronta em ordem;
- exige exatamente uma ferramenta por etapa e verificador independente por ID;
- usa argumentos fornecidos por etapa e valida o schema da ferramenta;
- chama `executePlannedStep`, que persiste RUNNING antes da ação e finaliza após verificação;
- para quando nenhuma etapa READY existe (por exemplo, AWAITING_APPROVAL), falha ou limite de etapas é atingido;
- a nova chamada após a aprovação continua do estado persistido.

Teste novo em `KellvynAgentPlanTest.kt` cobre leitura → espera de aprovação → aprovação → gravação. Teste apenas escrito, ainda sem Gradle/CI confirmado.

**Estado real permanece:** backend neural ausente, chat principal não conectado a `createAgent`, sem build APK e sem teste Poco F6. O agente e suas decisões estruturadas são fundação de código, não ainda uma capacidade operacional no app.


## 12. Continuidade da sessão — 17/11/2026

**HEAD confirmado na última consulta:** `57ac899e98062ebd304927762be43afd1cbffd48` na branch `lab/kellvyn-intelligence-core-20261116`.

### Alterações feitas
- `KellvynLocalNeuralInferenceEngine.kt`: removido bloco duplicado que quebrava sintaxe; índice da última solicitação USER agora é mutável e obrigatório.
- `KellvynAttentionLanguageModel.kt`: adicionados `trainCompletion()` e `evaluateCompletionLoss()`; só a resposta supervisionada recebe loss/gradientes.
- `KellvynTrainingPipeline.kt`: adicionados `KellvynInstructionTrainingExample` e `trainInstructions()`, com BPE aprendido apenas em TRAIN, validação/teste separados, proveniência, hashes, limites, filtros de credenciais e prevenção de prompt leakage entre splits.
- `KellvynRuntime.kt`: adicionado `trainOwnInstructionModel()` para treino local e persistência do bundle.
- Testes: `KellvynCompletionTrainingTest.kt` e novos casos em `KellvynTrainingPipelineTest.kt`.
- `KellvynDecisionProtocol.kt`: UTF-8 inválido é convertido em rejeição controlada.

### Commits da sequência
`155f55a7`, `574352fb`, `f2a892c7`, `f8d371dc`, `2dacc181`, `f556f66b`, `f24a225d`, `45be9f44`, `529d6287`, `553d2760`, `57ac899e`.

### CI / ambiente
No commit `f2a892c7`, o Gradle compilou e executou 136 testes; 135 passaram e o parser falhou com `MalformedInputException`. O parser foi corrigido em `529d6287`. O workflow neural e o laboratório virtual do HEAD `57ac899e` estavam em andamento na última consulta. **Ainda não registrar CI aprovado** até consultar o resultado final. As execuções anteriores do laboratório falharam durante compilação ou antes da instalação, logo não validaram o aplicativo em emulador. Não houve teste físico no Poco F6.

### Bloqueios técnicos atuais
- single-head autoregressivo alpha; não Transformer completo;
- treino supervisionado é primitivo experimental com exemplos sintéticos;
- inferência local e protocolo ainda não foram provados em conversa útil;
- engine adapter não está ligado ao chat principal;
- `KellvynRuntime.isReady() == false`, `reply()` continua bloqueado;
- janela de contexto e protocolo de decisão são pequenos/ineficientes para o prompt real do agente;
- sem APK de entrega e sem medição física no Poco F6.

### Próxima tarefa obrigatória
1. Consultar o resultado final de `43986427597` (neural core tests) e `43986431670` (laboratório virtual) ou os runs mais recentes do mesmo HEAD.
2. Corrigir todas as falhas reais e repetir CI.
3. Depois de CI verde, avançar para o Transformer decoder-only original: multi-head causal attention, residuals, normalização e MLP gated com gradientes verificados.
4. Em seguida, implementar EOS/decodificação controlada e corpus supervisionado de português com direitos documentados.
5. Só depois integrar ao chat; preservar a interface aprovada.

Não apresentar a nova primitiva de SFT como inteligência conversacional pronta.


## 13. Continuidade da etapa neural — EOS e próximo marco (17/11/2026)

**Branch:** lab/kellvyn-intelligence-core-20261116  
**Último commit de código neural validado:** f32bca0d1b2e14541ed7a52f8c0bd414f685084e  
**Commit posterior de documentação:** cbdac5a4d673b4e080b3fa386d970ace8725d37a  
**Não alterar a interface aprovada.**

### Trabalho realizado
1. Tokenizador BPE atualizado com um ID especial EOS que não representa bytes UTF-8 e não aparece no encode de texto normal.
2. Checkpoint do tokenizer passou para versão 2; leitura versão 1 continua válida e retorna ausência de EOS para manter compatibilidade com bundles antigos.
3. Pipeline KN-003 acrescenta EOS ao final de cada completion usada em treino, validação e teste. Portanto, a loss agora ensina não só o conteúdo da resposta como também quando parar.
4. Geração do Transformer pode interromper no ID EOS sem incluí-lo no texto devolvido.
5. Adapter KN-003 verifica EOS nos bundles novos. Para tokenizer/checkpoint legado sem EOS, mantém o antigo marcador textual END.
6. Foi adicionado teste de contrato: o modelo pequeno aprende EOS para um prompt controlado, prevê o token e encerra a geração antes de expor esse ID.
7. Testes do tokenizador cobrem EOS dedicado e compatibilidade com checkpoint v1.

### Commits da sequência
- Tokenizador EOS e compatibilidade v1: 89b37687609034959ea550a4b2b796db9ccd5ce8
- geração configurável com parada EOS: 8ed02f873e5fa660973c45ec695943edaddc6f9b
- supervisão de EOS no pipeline KN-003: 181842fd9add2da92f97ba9817adabd4e70c2668
- adapter para parar no EOS: 550e5b47e719952f650dde157782c352762dd4d3
- testes do tokenizer e checkpoint legado: d04ffa531a2c9044b8e5d6ef021f5c56a4fd69a9
- contagem de EOS na loss/steps: 9297515456dfa2e1e4b61656c5d135b30a85788b
- teste de geração EOS: b82ae229c1d46b1f0e4bc844500fe41deaf0737b
- passo de diferenças finitas float32: f32bca0d1b2e14541ed7a52f8c0bd414f685084e
- decisão neural documentada: 9e5852c412c1640ab568bb6799186268f1725448
- status documentado: cbdac5a4d673b4e080b3fa386d970ace8725d37a

### CI confirmado
Workflow neural core tests run 44005947968 no commit f32bca0d1b2e14541ed7a52f8c0bd414f685084e terminou com sucesso. O log confirma Gradle testDebugUnitTest e BUILD SUCCESSFUL em 2m09s. A suíte inclui o teste dos quatro papéis internos criado nesta sessão anterior, testes do agente, tokenizer, modelo, treino e checkpoints.

O laboratório Poco F6 virtual run 44005947963 não estava concluído na última consulta. Não atribuir sucesso visual ou de dispositivo. Mesmo um AVD aprovado não substitui teste no Poco F6 físico.

### Correção durante a validação
A primeira execução após EOS reportou uma falha no teste de gradientes. Aumentar o tamanho do vocabulário em uma classe altera o softmax e torna a diferença finita float32 mais sensível ao passo. O passo passou de 0,001 para 0,005, mantendo os limites absolutos e relativos do teste. O CI posterior ficou verde. O teste de round-trip foi atualizado para contar o ID EOS adicional (256 bytes + merges + 1 EOS); o round-trip dos bytes continuou exato.

### Estado real do cérebro
- KN-003 é um decoder-only Transformer próprio com atenção causal multi-head/RoPE, Pre-LN, residual e SwiGLU.
- A implementação atual tem **um único bloco**, embeddingSize padrão 32, feedForwardSize padrão 64 e contexto máximo 256 tokens.
- Não há KV cache; chaves/valores são recalculados em cada token.
- O pipeline de treino e os dados são experimentais; exemplos sintéticos pequenos não estabelecem qualidade linguística.
- Não há prova de diálogo natural multi-turno, raciocínio geral, planejamento de qualidade ou programação autônoma.
- O adapter não está conectado ao chat principal. KellvynRuntime.isReady() segue falso e reply() segue bloqueado.
- Não houve validação física no Poco F6 ou medição prolongada de RAM, latência, calor e bateria.

### Próximo passo obrigatório do cérebro
A próxima mudança estrutural deve investigar e prototipar **profundidade multi-bloco** com:
1. estruturas de parâmetros separadas por bloco;
2. forward causal de múltiplas camadas;
3. retropropagação através de todos os blocos;
4. gradientes comparados por diferenças finitas em cada bloco;
5. teste de overfit sintético e checkpoint restaurável;
6. benchmark de RAM/tempo no Android antes de elevar dimensões.

Não conectar o chat nem promover APK nesta etapa. Depois da profundidade, revisar contexto e KV cache. Montar corpus de português e protocolo KELLVYN/1 autorizado com treino/validação/teste separados. Preservar todo o layout da IA sem alterações.


## 14. Continuidade — cache KV incremental concluído no CI (17/11/2026)

**Commit de código validado:** 895eca04525b65b6c7c17175e42ff4d126d1e5ae  
**Workflow neural:** run 44007839790 — SUCCESS  
**Última atualização documental nesta sequência:** 36301de2f5e9ccbf05a19ba98940a043ac4a06ad  
**Escopo:** cérebro/inferência; nenhuma mudança de layout.

### Código adicionado
- `GenerationSession` interno em `KellvynTransformerLanguageModel`.
- Preenchimento inicial do cache KV a partir do prefixo.
- Projeção Q/K/V apenas para cada token que entra.
- Consulta do token atual reutilizando K/V anteriores.
- Janela deslizante limitada a contextLength, com remoção da entrada mais antiga.
- Posições RoPE absolutas para preservar relações relativas na janela.
- `KellvynTransformerInferenceEngine` agora utiliza a sessão em vez de recalcular todo o contexto a cada token.
- Geração para no EOS antes de expor o token de controle.

### Testes
- Probabilidades da sessão incremental comparadas com o forward completo.
- Teste percorre vários passos após expulsão de tokens antigos da janela.
- Tamanho do cache limitado por contextLength.
- Contador de tokens processados confirma uma única projeção K/V por token inserido.
- Geração greedy da sessão comparada com referência que recalcula contexto completo a cada token.
- Run 44007839790 executou `gradle testDebugUnitTest` e terminou `BUILD SUCCESSFUL` em 1m40s.

### Falha e correção
A primeira versão do teste tentou iniciar com prefixo maior que o contextLength de 8 tokens; o construtor rejeitou corretamente a entrada na linha do teste. O prefixo foi reduzido para "Kellvyn" (no máximo 7 bytes UTF-8), mantendo 8 como janela. A execução seguinte passou. Não houve falha de equivalência numérica registrada na execução aprovada.

### Limites
- Cache é de um único bloco, porque KN-003 ainda tem um único bloco.
- Sem benchmark de velocidade real, RAM, bateria, calor ou teste físico Poco F6.
- O modelo continua com um bloco e contexto de até 256; não há conversa geral validada.
- Evitar treino concorrente no mesmo objeto usado por uma sessão cacheada; treinar em outra instância e promover bundle depois de validação.

### Próximo passo
Implementar uma pilha causal multi-bloco real com parâmetros independentes, sequência completa de estados por bloco e backpropagation de todas as posições/camadas. Cada bloco deverá possuir cache KV independente. Validar gradientes de cada bloco por diferenças finitas, overfit sintético, checkpoint/restauração e custo de Android antes de aumentar dimensões. Não conectar ao chat nem alterar o layout nesta etapa.


## 15. Regra de autoria confirmada pelo proprietário — 17/11/2026

O proprietário reiterou que Kellvyn é um cérebro próprio e não deve ser plágio. A regra vinculante está em `NEURAL_MODEL_DECISIONS.md`, seção 10. Em toda retomada: preservar essa decisão, não copiar implementação de outro projeto, não usar pesos externos e registrar fontes conceituais separadas de código incorporado. Estudar referências é permitido; plágio não é. Nunca reiniciar etapas prontas: confirmar HEAD, ler este handoff/STATUS e continuar da próxima tarefa verificada.


## 16. Expansão de papéis internos e próxima retomada — 17/11/2026

**Implementação enviada:**
- `KellvynAgent.kt`: enum e instruções ampliados de 4 para 14 papéis especializados.
- `KellvynAgentSpecialistEvaluationTest.kt`: a mesma prova de isolamento e ferramentas READ_ONLY percorre agora os 14.
- `AGENT_EVALUATION_PROTOCOL.md`, `AGENT_IMPLEMENTATION.md` e `STATUS.md`: documentação atualizada.
- Regra de não plágio e autoria própria foi registrada na seção 10 de `NEURAL_MODEL_DECISIONS.md` e nesta continuidade.

**Papéis cobertos:** pesquisador, explorador de código, revisor, analista de testes, analista de raciocínio, analista de aprendizado, arquiteto de programação, analista de memória, analista de segurança, analista de planejamento, depurador, auditor independente, analista de operações e analista visual.

**Limites:** o `KellvynAgent` central continua sendo o supervisor/orquestrador. Todos os especialistas usam a mesma instância de inferência; ferramentas somente READ_ONLY; sem recursão/paralelismo. Os papéis de programação, aprendizado, operações e visual ainda são de análise, sem edição, treino, controle de dispositivo ou mutações. Não afirmar que o cérebro já executa esses papéis com competência: a avaliação neural está bloqueada até haver modelo treinado com qualidade.

**Próxima ação técnica obrigatória:**
1. Confirmar o CI `neural-core-tests` no commit com o enum expandido e o teste com 14 papéis.
2. Se falhar, ler o log, corrigir o menor defeito e repetir CI.
3. Retomar o próximo marco neural já previsto: projetar e implementar profundidade multi-bloco no Transformer próprio, com parâmetros independentes por camada, forward causal em todas as posições, backpropagation entre blocos, gradient checks por bloco, overfit sintético, checkpoint versionado e cache KV por camada.
4. Preservar o KN-003 atual e seus checkpoints; não substituir até a nova arquitetura provar compatibilidade, treinamento e avaliação.
5. Não conectar ao chat principal nem editar a interface ainda.

**Regra de continuidade:** na próxima conversa, ler este arquivo, `STATUS.md`, `NEURAL_MODEL_DECISIONS.md` e conferir o HEAD real antes de agir. Não recomeçar estudos, papéis ou cache KV já implementados.


## 17. Nomenclatura oficial — 17/11/2026

Usar somente **KN-001 / KN-002 / KN-003** como identificadores técnicos das versões neurais. O primeiro protótipo chama-se `KellvynByteModel`. Não criar, usar ou sugerir uma entidade/identidade adicional com nome em inglês para o núcleo. Kellvyn é a única IA. A sigla KN é apenas nomenclatura técnica do projeto, sem expansão inventada.


## Laboratório Android invisível — especificação inicial — 17/11/2026

- Correção de direção confirmada pelo proprietário: não transportar IDEs/estúdios desktop completos para o telefone. Construir motores Android próprios e adaptados, sem UI de Studio exposta ao usuário.
- A oficina é interna e headless: ferramentas estruturadas chamadas pelos 14 agentes existentes. A conversa principal continua sendo a única superfície visível.
- Não criar novos agentes apenas para cada estúdio. Equipar os papéis atuais com ferramentas, skills, contratos, permissões, critérios e evidências adequados.
- Criada a especificação `docs/kellvyn/ANDROID_HEADLESS_STUDIO_SPEC_2026-11-17.md` no commit `1b3d5678960fcdfb3031a8d75eba9c35d6102000`.
- A especificação cobre núcleo compartilhado, Tool Registry, Task Broker, workspace/artefatos, parsing e indexação, Android Build & Test, UI/UX estruturado, Image Studio, 3D/CAD, Neural Studio próprio, Data/Architecture e Automation.
- Motores devem ser carregados sob demanda, não mantidos todos ativos. A ausência de UI reduz custo de interface e renderização persistente, mas o processamento real continua sujeito a RAM/CPU/GPU/temperatura/bateria.
- Foi definido Resource Governor com medições no Poco F6, fila, prioridade, cancelamento, checkpoint, cache limitado, parsing incremental e exclusão mútua de tarefas pesadas.
- Candidatos técnicos estudados: Tree-sitter/KTreeSitter, Android graphics APIs/BitmapRegionDecoder, AndroidSVG, Filament/SceneView e LiteRT/ExecuTorch como possíveis runtimes para pesos próprios. Nenhuma dessas dependências foi automaticamente aprovada; exige auditoria de licença, ABI, tamanho, manutenção e benchmark.
- NNAPI não será a base de aceleração, pois foi descontinuada no Android 15.
- Limites Android permanecem explícitos: ADB/UIAutomator, instalação de APK e Accessibility Service não são poderes implícitos de um APK comum. Cada capacidade exige mecanismo e autorização adequados.
- **Estado desta entrega:** pesquisa e especificação documental concluídas nesta etapa; nenhum motor novo ou agente foi implementado, nenhum layout foi alterado e nenhuma medição física de desempenho foi realizada.
- Próximo passo obrigatório: rever a especificação e transformá-la em contratos de ferramenta, Resource Governor e Task Broker com testes unitários/instrumentais. Antes de cada alteração, confirmar o HEAD e preservar a interface bloqueada.

 
## Fundação avançada de recursos do laboratório — 17/11/2026

- Direção confirmada: laboratório headless, sem UI de Studio exposta ao usuário; motores Android internos sob demanda, compartilhados pelos 14 papéis existentes. Não transportar IDEs desktop inteiros.
- Especificação atualizada em `docs/kellvyn/ANDROID_HEADLESS_STUDIO_SPEC_2026-11-17.md`, commit `2cea7175a22131b0f51e4dbbef86e506af3e0c62`.
- Resource Governor implementado em Kotlin puro: admite LIGHT/STANDARD/HEAVY com base em memória disponível, working set estimado, heap, thermal status/headroom, battery saver e nível de bateria.
- Task Broker compartilhado usa dois permits FIFO: tarefas leves podem dividir um permit quando há folga; STANDARD/HEAVY são exclusivos. Reamostra o estado do dispositivo após espera; fila com 10 s padrão retorna deferral, não inicia trabalho quando ocupada; libera permits parciais em timeout/cancelamento.
- Monitor Android sob demanda coleta ActivityManager.MemoryInfo, heap Runtime, Debug.getPss, PowerManager thermal/power saver, Thermal Headroom com cache mínimo de 1s e BatteryManager. Sem polling permanente.
- Singleton application-scoped `KellvynResourceRuntime` unifica recursos entre KellvynRuntime, agentes e LocalProjectBuilderBridge.
- Agente: metadados workload/working set em KellvynToolSpec; inferência classificada STANDARD (128 MiB inicial); indexação de projeto STANDARD (64 MiB inicial); chamadas de ferramentas passam pelo broker. Timeout de ferramenta começa depois da admissão. Cancelamento de chamador permanece cancelamento.
- Três pipelines de treinamento neural próprio passam como HEAVY; construção e retomada de APK local passam como HEAVY (768 MiB inicial).
- Nova ferramenta READ_ONLY `device_resource_snapshot` permite a OPERATIONS_ANALYST inspecionar RAM, heap, PSS, estado térmico e bateria.
- Deferral de etapa planejada persiste estado READY, mantém aprovação e tentativa, descarta evidência de pré-condições que deve ser verificada novamente.
- Testes puros Resource Governor/Broker: 8 testes executados com Kotlin/JVM local e passaram em harness: `RESOURCE_GOVERNOR_UNIT_TESTS_PASS`.
- Monitor Android compilado isoladamente contra stubs de API Android; não substitui Gradle real.
- Testes instrumentais/Planner adicionados, mas não têm execução Gradle/AVD válida ainda.
- CI atual: runs `44062178882` neural e `44062178854` Poco/AVD falharam sem steps/logs_url; API de logs retorna BlobNotFound. Nenhuma conclusão de falha de código pode ser feita; causa do bloqueio dos runners não comprovada.
- Limiares e estimativas são provisórios, não calibrados no Poco F6 físico. Não há medição real de RAM, bateria, temperatura ou latência do aparelho.
- Commits relevantes de código nesta sequência: Governor `322a0b84e8bf4d41f903ffb94009a918b0d15c4f`; monitor `51e197ab1bb4ac1b25ed1e72f662b9591d0fbdcc`; Agent `9a6840e2b6665d8c6ee6c23b41dadb7494d97677`; Broker queue timeout fix `4a46e2e53cf5d12a74cbbd6977a481ec01a70036`; ResourceRuntime `a5e75a578e561f6fea62362d6c1a86c7e936842d`; ResourceTools `8688e8e4ec899b7ccfeeca7d18d31c5781a43b7c`; APK bridge gate `36516acbc7754acdabe63ad30f0c957bac37b073`.
- Próxima sequência: fila persistente/prioridades e retomada; onTrimMemory + descarte central de caches; manifests de módulos com carregamento lazy; ferramentas por capacidade e por papel; métricas por tarefa; validação real Gradle/AVD e Poco F6 físico.
- Estado: primeira fundação de admissão/controle compartilhado escrita; laboratório inteiro e os estúdios de design/imagem/3D ainda não implementados. Cérebro conversacional continua sem inferência aprovada ligada ao chat. Interface não foi alterada.


## 18. Retomada de oficina após auditoria cruzada — 18/11/2026

**HEAD conferida:** `937001c85205666b013aaf8854c1c830409617ef` em `lab/kellvyn-intelligence-core-20261116`.

A auditoria cruzada confirmou que as três branches históricas do laboratório são linhas divergentes. Não fazer merge geral. Extrair operações úteis e adaptar para a arquitetura headless:
- Super Lab: auditoria web/Android, execução isolada, evidências e pipeline de cenas.
- Studio Workbench: comparação raster, análise temporal de efeitos, paletas, desenho, cenas 3D/4D e keyframes.
- Poco F6 upgrades: paridade visual, captura de raios, recuperação de tela e diagnóstico de AVD.

### Código da oficina atual confirmado
- Fila SQLite criptografada, com prioridade, tentativas, adiamento, cancelamento de tarefas pendentes e recuperação de tarefas RUNNING.
- Scheduler com handlers registrados em memória por sessão e execução sob Resource Broker.
- Worker WorkManager limitado a duas tarefas LIGHT/STANDARD por sessão; HEAVY permanece para execução em primeiro plano.
- ResourceRuntime com fila persistente, cache reclaimers, callbacks de pressão de memória e descarregamento de módulos ociosos.
- ModuleManager com manifests, seleção por capacidade, carregamento lazy, leases e verificação de que ferramentas carregadas correspondem ao manifesto.
- Módulos concretos registrados: Code Navigation, Security Audit e Data Architecture.
- Automation tool visível ao modelo é somente leitura. O runtime não registra handlers de produção nesta HEAD.

### Ponto de parada preciso
A fundação da oficina já foi além do broker efêmero descrito nas notas antigas: fila persistente e WorkManager foram escritos. O próximo bloqueio não é “criar fila”; é registrar e validar o primeiro handler concreto com contrato limitado, idempotência e evidência end-to-end. O teste atual do worker vazio não prova execução de tarefa real.

### Primeira tarefa da próxima sessão
Implementar um handler interno de baixo risco e sem mutação destrutiva, integrá-lo ao runtime e adicionar testes instrumentais para:
1. enqueue de payload válido e rejeição de payload inválido;
2. handler explicitamente registrado executa uma vez e grava resumo;
3. handler ausente é adiado sem bloquear tarefas de outros handlers;
4. reinício recupera RUNNING de forma at-least-once;
5. cancelamento, retry e maxAttempts mantêm estados consistentes;
6. worker em background nunca reivindica HEAVY.

Não habilitar ferramenta de escrita/execução genérica para agentes. Especialistas permanecem READ_ONLY até a confirmação do proprietário e gates independentes.

### Validação e limites
- Os testes unitários puros do Resource Governor e harnesses locais anteriores são evidência limitada; não substituem Gradle.
- Os últimos jobs com runner_id=0/zero steps não executaram código.
- Nenhuma validação física Poco F6 nem APK final está comprovada.
- Manter layout e interface aprovados sem alterações.

## 19. Primeiro handler seguro integrado — 18/11/2026

**HEAD de referência após esta atualização:** `878ed55d33cf2b72a238d70bcea31b63b24535e1` (branch `lab/kellvyn-intelligence-core-20261116`).

**Plano único de execução:** `docs/kellvyn/LAB_EXECUTION_ROADMAP_2026-11-18.md`. Esse documento reconcilia o estado real dos 14 papéis, a oficina invisível, as referências Replit/Cursor/Android Studio/Lovable e os gates de cada fase. Não reconstruir a estratégia em cada conversa.

### Verdade sobre os 14 papéis
- Os 14 papéis estão declarados e cobertos por teste de contrato/delegação.
- São papéis especialistas da única Kellvyn, não 14 modelos ou inteligências independentes.
- Compartilham a mesma instância de inferência. Contratos READ_ONLY e isolamento foram implementados; competência cognitiva dos papéis não foi comprovada por um modelo treinado.
- Não criar novos agentes para cada estúdio. Equipar os 14 papéis com ferramentas/skills apropriadas na oficina compartilhada.

### Alterações de código feitas após HEAD 937001c
- Scheduler: `KellvynPermanentScheduledTaskException` e `failPermanently` para rejeição definitiva de descritores inválidos.
- Novo `KellvynResourceSnapshotTaskHandler`: diagnóstico READ_ONLY, descritor versionado, resumo limitado e sem efeitos destrutivos.
- `KellvynResourceRuntime`: registro sincronizado da allowlist de handlers embutidos, operação de enfileiramento para o diagnóstico e consulta limitada de status.
- `KellvynAutomationDrainWorker`: restaura os handlers internos autorizados antes de drenar fila após recriação do processo.
- Teste unitário de contrato do handler.
- Teste instrumental Android para execução, rejeição permanente, handler ausente e retomada de RUNNING.
- Teste instrumental do próprio Worker para registrar handlers embutidos e drenar uma tarefa READ_ONLY persistida via `doWork()`.
- Roadmap e STATUS atualizados.

### Estado da prova
- Código e testes foram gravados na branch; ainda não há prova de compilação Gradle nem execução de teste Android desta revisão.
- Rerun do neural workflow `44212455036`, tentativa 2, permaneceu sem runner/steps/logs.
- Rerun do Poco F6 workflow `44212621981`, tentativa 2, também permaneceu sem runner/steps/logs; comparação final skipped.
- Novo run do Poco F6 workflow `44212948327`, no commit `878ed55d33cf2b72a238d70bcea31b63b24535e1`, falhou novamente sem runner/steps/logs; comparação final skipped.
- Portanto a suíte continua **não verificada**, não reprovada por bug demonstrado.
- Nenhum APK final, AVD funcional, teste físico Poco F6 ou integração neural/chat foi comprovado.

### Próxima ação — sem desvio
1. Restabelecer um runner/ambiente que realmente execute Gradle e os testes Android ou executar uma sessão local verificável.
2. Compilar e executar os testes; corrigir apenas falhas observadas, sem presumir aprovação.
3. Confirmar no teste instrumental o fluxo enqueue → handler autorizado → broker → resultado criptografado → SUCCEEDED e recuperação de RUNNING.
4. Só depois fechar a Fase 1 e avançar para a matriz de capacidades/permissões dos 14 papéis.
5. Nenhuma escrita genérica, shell irrestrito ou ferramenta mutável para especialistas antes de gates e aprovação.
6. Preservar interface e arquivos visuais protegidos.

**Não recomeçar:** fila SQLite, broker, Governor, ResourceRuntime, ModuleManager, memória, planejamento, checkpoints e catálogo de 14 papéis já existem. O próximo trabalho é validar/fechar a operação real e então equipar os papéis, não refazer esses fundamentos.

## 20. Continuação — política dos papéis, CI focado e estado neural — 18/11/2026

### Descobertas para não recriar trabalho pronto
- A matriz de capacidades dos 14 papéis já existe em `KellvynSpecialistToolPolicy.kt`. O coordenador aplica `permittedReadOnlyToolNames` e `readOnlySubset`; especialistas não ganham ferramentas de escrita, rede ou controle de dispositivo por pedido do modelo.
- Foram adicionados testes que fixam exatamente as capacidades de cada papel e verificam negação de ferramentas sem capacidade ou com risco mutável.
- O laboratório possui quatro módulos concretos registrados: Code Navigation, Security Audit, Data Architecture e Android Build Status. Os demais estúdios permanecem por construir; Android Build Status é somente diagnóstico, não compilador/test runner completo.

### Novas alterações nesta continuação
- Novo workflow `.github/workflows/agent-lab-tests.yml` para unit tests Gradle e instrumented Android tests no emulador API 35.
- Workflow visual `poco-f6-parity.yml` restringido a mudanças que podem alterar a UI, manifesto ou recursos; não deve disparar para qualquer Kotlin do runtime/agentes.
- Teste `KellvynSpecialistToolPolicyTest` agora cobre a matriz dos 14 papéis e cinco grupos de risco.
- Roadmap atualizado para reconhecer a matriz existente e distinguir capacidade contratual de competência real.

### Estado do cérebro — resposta precisa
- Autoria: tokenizer, implementação neural, inicialização de pesos, pipeline de treino e checkpoints estão escritos no repositório Kellvyn; não há pesos pré-treinados de terceiros usados como cérebro.
- KN-003 atual é explicitamente um Transformer decoder-only de um bloco: atenção causal multi-head, RoPE, Pre-LayerNorm e SwiGLU, com tokenizer BPE byte-fallback e treinamento próprio a partir de exemplos explicitamente fornecidos.
- Limite: não é uma arquitetura multi-bloco; falta profundidade com parâmetros independentes por camada e backpropagation completa entre blocos.
- Inferência: há adaptador estrito para KN-003, mas `KellvynRuntime.isReady() == false`; `reply()` informa que não há backend conversacional conectado.
- Portanto, é correto dizer “cérebro autoral feito a partir do zero em desenvolvimento”, não “cérebro conversacional pronto”. Não anunciar qualidade ou competência sem treino/avaliação reproduzíveis.

### Próxima ação
1. Os workflows focados foram verificados: `44214073878` (tentativa 2) e `44214499919` (agent lab), além de `44214499742` (neural core), terminaram sem runner/steps/logs. Classificar como NOT_RUN/BLOCKED, não como falha do código nem sucesso. Próximo passo é restabelecer execução de CI/runner ou executar em outro ambiente Android verificável.
2. Corrigir somente defeitos observados na compilação/testes.
3. Aprovar a Fase 1 com prova do fluxo enqueue → handler allowlisted → broker → resultado persistido → SUCCEEDED e recuperação após processo reiniciado.
4. Só então avançar para integração cruzada das capacidades dos 14 papéis com os três módulos já existentes e os estúdios que faltam.
5. Manter os especialistas READ_ONLY; não conectar inferência ao chat nem alterar interface até gates próprios.

## 21. Matriz real por papel e correção de fixtures — 18/11/2026

- Novo documento `AGENT_TOOL_AVAILABILITY_2026-11-18.md` compara capability policy com ToolSpecs e módulos que realmente estão registrados; deixa explícitas as lacunas de Web, Memory Read, Android Build/Test, UI/UX, imagem, vetor, 3D e CAD.
- Novo teste instrumental `KellvynAgentToolAvailabilityInstrumentedTest` valida a allowlist de todos os papéis em cima do runtime real e dos manifests reais.
- A auditoria estática encontrou fixtures de delegação desatualizadas: `read_source` não declarava a capability e, pela política fail-closed, seria corretamente ocultada. Ambas as suítes `KellvynAgentDelegationTest` e `KellvynAgentSpecialistEvaluationTest` foram corrigidas para declarar capability autorizada; a ferramenta de escrita mantém a mesma capability para que o teste prove bloqueio pelo risco.
- `KellvynTaskScheduler.registerHandler` agora faz rollback da entrada em memória se `markHandlerAvailable` falhar.
- Run `44215633371` foi lançado pelos novos testes instrumentais, mas job terminou sem runner/steps/logs; a suíte continua NOT_RUN/BLOCKED.
- Próxima ação continua restabelecer CI/execução Android válida; não presumir aprovação.

## 22. Memory Read implementado e limitado — 18/11/2026

- `KellvynMemoryTools.kt` adiciona `memory_search` como ferramenta READ_ONLY com capability `MEMORY_READ`.
- O runtime injeta na ferramenta endereços globais e do projeto ativo já validado. A ferramenta não aceita `project_id`, IDs de scope nem nenhum seletor de escopo vindo do modelo.
- Retorna somente `NON_SENSITIVE`; registros SENSITIVE_PERSONAL, CREDENTIAL e UNKNOWN são excluídos mesmo quando aprovados para armazenamento. O conteúdo devolvido é sempre rotulado como dado não confiável.
- Resultados limitados a cinco, 420 caracteres por entrada e 3.200 caracteres totais; inclui ID de referência, tipo, origem, escopo, confiança e proveniência.
- `KellvynRuntime.createAgent` reconstrói o registry e substitui qualquer ferramenta de nome `memory_search` passada externamente pela versão interna escopada, evitando injeção de uma implementação arbitrária.
- A política já autoriza `MEMORY_READ` apenas ao Analista de Memória; o teste instrumental da matriz de ferramentas atualiza a expectativa para `memory_search`.
- `KellvynMemoryToolsTest` cobre isolamento de projeto, filtro de sensíveis/credenciais/desconhecidos, argumento de escopo forjado, limites e metadados READ_ONLY.
- Verificação isolada: `kotlinc` compilou `KellvynMemoryTools.kt` contra stubs mínimos. Isso NÃO é compilação da aplicação nem teste Gradle.
- Runs `44217081776` e `44217081826` terminaram sem runner/steps/logs; testes continuam NOT_RUN/BLOCKED.
- Próxima prioridade: restabelecer runner/Gradle, executar toda a suíte e corrigir apenas defeitos demonstrados. Não promover escrita ou ferramentas operacionais para especialistas antes disso.

## 23. Verificação isolada e runner depois de Memory Read — 18/11/2026

- A fonte de `KellvynMemoryTools` foi compilada com `kotlinc` em um harness isolado; as interfaces da camada de agente foram stubs e o repositório de teste foi falso. O algoritmo de busca/classificação da memória foi incluído para verificar escopos, filtros de privacidade, limite, argumentos e saída. O harness executou e retornou `KELLVYN_MEMORY_TOOL_INTEGRATION_TESTS_PASS`.
- Limite da prova: não compilou o módulo Android inteiro, não usou o Keystore/SQLite real, não rodou JUnit/Gradle e não é teste instrumental.
- Runs atuais verificados: `44217467213`, `44217511064` e `44217511082`; jobs sem runner/steps/logs. Classificar NOT_RUN/BLOCKED.
- Não há ainda validação end-to-end Android da ferramenta nem aprovação CI; próxima prioridade permanece resolver o runner.

## 24. Diagnóstico Android Build/Toolchain para especialistas — 18/11/2026

- Criado módulo lazy `android-build-status`, registrado pelo runtime e compartilhando o `ProjectWorkspaceStore` existente.
- Tool `android_build_environment_status` tem capability ANDROID_BUILD e risco READ_ONLY; revela somente estado de cache/licença e metadados do último APK, sem caminho absoluto, sem construir, instalar ou executar artefato.
- Os papéis Test Analyst, Security Analyst, Debugger e Auditor recebem a ferramenta pela política existente. A matriz instrumental real foi atualizada, e teste específico cobre a saída e rejeição de argumentos.
- A ferramenta não deve ser confundida com Android Build & Test Studio completo: o compilador local existente só cobre perfil Java limitado via ECJ/D8. Kotlin/Compose/Gradle completo, instalação e UIAutomator continuam fora deste módulo.
- A nova classe passou em typecheck Kotlin isolado com stubs do Android/runtime/module APIs; isso não é compilação Gradle da aplicação.
- CI dos commits recentes continua NOT_RUN/BLOCKED: runs `44218653002`, `44218652911`, `44218608802` sem runner/steps/logs.
- Próxima ação: resolver o runner, executar unit/instrumented tests e validar o status tool no emulador; só depois criar ferramenta de build controlada com aprovação exata.

## 25. CI e typecheck depois do Android Build Status — 18/11/2026

- Módulo e teste instrumental foram adicionados; integração de capacidade atualizada para Test Analyst, Security Analyst, Debugger e Auditor.
- CI runs `44218653002`, `44218652911` e `44218608802` terminaram sem runner/steps/logs; classificar NOT_RUN/BLOCKED.
- A classe do módulo passou em typecheck Kotlin isolado com stubs mínimos; não substitui Gradle, AVD ou Poco F6 real.
- Não afirmar que o APK foi criado, instalado ou testado por essa ferramenta. Seu contrato só consulta o ambiente e o último artefato encontrado.

## 26. Agent Creation Studio invisível — Fase A — 18/11/2026

### O que existia antes

- Os 14 papéis estavam enumerados no KellvynAgent.
- A delegação interna já usava a mesma inferência e contexto separado, com limite de delegação aninhada igual a zero.
- KellvynSpecialistToolPolicy já filtrava ferramentas por papel e risco.
- KellvynStudioModuleManager, ResourceRuntime, ResourceGovernor, task broker e skills sob demanda já estavam na base.
- O desenho de oito estúdios headless existia em especificação, não como oito motores completos.
- Não existia Agent Creation Studio nem Profile Registry independente; as instruções de papéis estavam num when dentro do loop.

### Implementação acrescentada

- KellvynSpecialistProfileRegistry.kt — catálogo versionado e 14 missões/instruções; output estruturado de evidências, incertezas e próxima ação.
- KellvynAgentCreationStudio.kt — ferramentas READ_ONLY agent_profiles_list, agent_profile_inspect e agent_profiles_validate.
- KellvynAgent.kt — perfil selecionado pela role e limites aplicados ao agente especialista; mesma inferência, sem delegação recursiva e sem mutação.
- KellvynSpecialistToolPolicy.kt — ferramentas de design e avaliação atribuídas a papéis específicos.
- KellvynStudioModuleManager.kt — capacidades AGENT_DESIGN e AGENT_EVALUATION.
- KellvynRuntime.kt — o novo módulo passa a ser registrado no runtime comum.
- KellvynSpecialistProfileRegistryTest.kt — testes unitários adicionados; ainda aguardam execução.

### Pesquisa e documentação

- AGENT_CAPABILITY_RESEARCH_2026-11-18.md registra Lovable/Lovable Academy, Replit Agent, Android Studio Gemini, Cursor, OpenAI Agents SDK, LangGraph, Nano Banana e curso Android Basics with Compose.
- AGENT_CREATION_STUDIO_SPEC_2026-11-18.md descreve a arquitetura maior e as fases futuras.

### Estado exato e próxima sequência

- Há seis módulos de ferramenta registrados no runtime, não oito estúdios completos.
- A Fase A do Agent Creation Studio foi escrita, não validada por Gradle/CI.
- A versão atual do Studio é somente leitura; não cria/publica/promove perfil experimental.
- Nenhum resultado prova competência neural dos 14 papéis; todos compartilham o backend de inferência.
- Não há prova de integração do cérebro ao chat principal, APK de produção ou teste físico no Poco F6.

Próximo passo obrigatório: executar o CI, corrigir eventuais falhas, testar o acesso por papel e então construir o Profile Compiler + harness isolado, sem abrir escrita ou promoção automática.

## 27. Agent Creation Studio — Profile Compiler e matriz Android — 18/11/2026

### Continuação da implementação

- Criado `KellvynSpecialistProfileCompiler.kt`: valida cobertura dos 14 papéis, rejeita duplicidade/ausência, verifica capabilities derivadas da política central e limites; gera SHA-256 determinístico por perfil.
- O hash usa campos UTF-8 prefixados por tamanho para evitar ambiguidade causada por separadores no conteúdo.
- `KellvynAgentCreationStudio` consome o compiler: catálogo e inspeção revelam hash; validação passa somente quando os 14 perfis compilam.
- `KellvynAgent` só delega quando o catálogo completo compila; usa instrução e limites do perfil compilado e anexa o hash à observação devolvida.
- A política limita `agent_profiles_list/inspect` a Arquiteto de Programação, Analista de Planejamento e Auditor; `agent_profiles_validate` a Analista de Testes, Analista de Aprendizado e Auditor.
- `KellvynAgentToolAvailabilityInstrumentedTest` foi atualizado para conferir as novas ferramentas na matriz real do runtime Android.
- `KellvynSpecialistProfileRegistryTest` ganhou teste de hash estável e mudança de hash quando a instrução muda.

### Auditoria do estado e bloqueios

- O workflow `agent-lab-tests.yml` e `neural-core-tests.yml` retornou falha com `steps=[]` e `logs_url=null`. Não há base para afirmar que os testes compilaram ou falharam dentro do Kotlin/Gradle; não foi disponibilizado diagnóstico de execução.
- Smoke harness Kotlin/JVM local executado com kotlinc usando cópias equivalentes das três classes novas e stubs para contratos externos. Passou em: 14 perfis; hash estável e hash diferente após alteração; separação de fronteiras das seções; catálogo/inspeção/validação; recusa de role inválida. Não é compilação byte-for-byte da aplicação, nem Gradle/JUnit/AVD.
- A contagem passou a seis módulos registrados no runtime. O conjunto dos oito motores headless continua apenas parcialmente implementado; UI/UX, Image e 3D/CAD Studio ainda não estão completos.
- O Agent Creation Studio está nas fases A e B inicial, em somente leitura. Ainda não cria revisões experimentais persistentes, não promove perfis e não mede competência cognitiva real.
- Os 14 papéis compartilham a mesma inferência; contratos e roteamento não provam capacidade intelectual independente.

### Próxima sequência sem desvio

1. Destravar/observar um runner de CI que disponibilize steps e logs; não mascarar falhas.
2. Executar Gradle unit tests e instrumented tests e corrigir apenas erros reproduzidos.
3. Implementar harness independente de competência dos 14 papéis, com casos fixos e critérios por função.
4. Persistir perfis/revisões com hash, diff, aprovação explícita e rollback.
5. Continuar Android Build & Test Studio até build, assinatura, instalação, UIAutomator, screenshot e Logcat serem provas reais.

## 28. Agent Creation Studio — benchmark e harness inicial — 18/11/2026

### Código acrescentado

- `KellvynSpecialistBenchmarkRegistry.kt`: 28 cenários fixos, dois para cada um dos 14 papéis; rubricas de evidência esperada e comportamentos proibidos.
- `KellvynAgentCreationStudio.kt`: nova ferramenta READ_ONLY `agent_benchmark_catalog`; lista todos os cenários ou detalha as rubricas de um papel específico.
- `KellvynSpecialistEvaluationHarness.kt`: executor sequencial e fail-closed; filtra ferramentas pela política central; confere identidade/hash do perfil, chamadas e execuções autorizadas; exige critérios verificados por componente independente e referência de evidência.
- `KellvynSpecialistEvaluationHarnessTest.kt`: testes escritos para aprovação, ferramentas não autorizadas, hash divergente, evidência ausente, rede restrita e suite sequencial.
- `KellvynSpecialistProfileRegistryTest.kt` e `KellvynAgentToolAvailabilityInstrumentedTest.kt` atualizados para verificar catálogo de benchmark e acesso por papel.

### Verificações

- Conferência estática do registry real: 28 casos, 2 por papel, IDs únicos e capabilities compatíveis com a matriz central.
- Harness Kotlin/JVM local compilado e executado com stubs e cópias equivalentes dos módulos; suite sintética de 28 casos retornou 28/28 e o caso de escrita indevida falhou como esperado.
- Limite da prova: não compilou os arquivos completos do Android app byte-for-byte, não executou JUnit/Gradle, não chamou a inferência real, não instalou APK e não testou Poco F6.
- CI atual (runs `44327863319` e `44327863230`): ambos falharam sem steps e sem logs_url. Não existe diagnóstico de compilador.

### Estado e próxima tarefa

- Agent Creation Studio agora tem Fases A/B e parte da C: perfis, compilador/hash, catálogo de 28 testes e harness independente injetável.
- Ainda não há executor ligado ao cérebro real; nenhum resultado prova competência dos 14 papéis.
- Ainda não há armazenamento persistente de resultados, snapshots por execução, comparação de regressão, revisão humana ou promoção de perfil.
- Próximo passo: destravar CI/Gradle; depois integrar o executor ao backend neural só após aprovação do gate de inferência; o verificador não pode ser o próprio texto de resposta do especialista.

## 29. Última conferência da rodada — 18/11/2026, 14:53 UTC

- HEAD confirmado: `e521fcaf11239fd88d9fa396bc27a719b7575a4c`.
- A instrução do perfil foi polida para imprimir `PAPEL: ROLE_ENUM — Nome humano`; teste correspondente atualizado.
- CI runs `44329166238` (Agent Lab) e `44329185728` (Neural Core) falharam sem runner/steps/logs_url. Não há logs para diagnosticar build ou teste.
- Smoke local JVM com stubs/casos sintéticos: 28/28 passou, ferramenta de escrita foi reprovada; a contagem/capabilities do catálogo fonte real foi conferida separadamente.
- Próximo passo não é ampliar agentes indiscriminadamente: primeiro destravar Gradle/CI e executar a suíte Android; depois implementar o version store append-only, diff/revisão/aprovação e só depois conectar o executor ao backend neural aprovado.


## 30. Perfil persistente — Fase E parcial — 18/11/2026
- Criado KellvynSpecialistProfileStore.kt: histórico append-only de STAGED/APPROVED/ROLLED_BACK; AES-GCM; Android Keystore com alias separado; AAD autenticando o cabeçalho; hash SHA-256 encadeado; optimistic concurrency por hash da versão ativa e do ledger.
- Perfil preparado não se torna ativo. Promoção exige confirmação explícita no método confiável do app e hash exato do perfil preparado. Rollback só aceita versão previamente aprovada ou a versão-base original, sem apagar eventos.
- KellvynRuntime usa pasta privada specialist-profiles, expõe APIs internas de revisão e passa a carregar a lista ativa aprovada em factories suspend com leitura no Dispatchers.IO.
- KellvynAgentCreationStudio ganhou ferramentas de consulta READ_ONLY para histórico e diff completo limitado antes/depois. Ferramentas mutáveis de aprovação não são dadas ao modelo.
- Testes unitários escritos para persistência, staging, aprovação, rollback, CAS, hash, corrupção, permissões e diff; instrumented test Android escrito para persistência entre instâncias e alteração de AAD com Android Keystore.
- Ainda não houve execução Gradle/JUnit/AndroidRunner desta rodada. Não há build APK nem teste físico Poco F6.
- Não alterei MainActivity.java, interface-lock.sha ou o layout.
- O cérebro KN-003/inferência ainda não está ligado ao chat principal; perfil versionado e harness não demonstram competência cognitiva por si só.

### Ponto exato de retomada
1. Confirmar o HEAD da branch e executar os workflows; jobs recentes 44329185731 e 44329185728 falharam sem steps/logs e seus reruns repetiram ausência de logs reutilizáveis.
2. Revisar compilação Kotlin/Gradle do novo Profile Store e da alteração para factories suspend.
3. Corrigir somente falhas reproduzidas; atualizar a matriz instrumentada se a allowlist real diferir.
4. Executar teste Android Keystore; após aprovação, implementar a confirmação de revisão no código confiável sem expor essa ação ao modelo.
5. Só então integrar o executor de benchmark ao backend neural aprovado.


## 31. Estado de continuidade — 18/11/2026, 15:20 UTC
- HEAD confirmado: 6e07def97f59c7cf749e6e9a469eaf1093ec1dd5.
- O Profile Store foi limitado a 256 registros globais de até 32 KiB cada. Ferramentas de leitura reservam 24 MiB, usam carga STANDARD e a resposta de histórico mostra no máximo 12 eventos. Diff pendente retorna valores antes/depois e faz uma leitura do ledger.
- Runs do commit cf2434dc9f8f518b483c6484944efc1ddc9a8b4c: Agent Lab 44334107998 / job 134906002466; Neural Core 44334108000 / job 134906002719. Ambos failure sem steps/logs_url.
- Run do HEAD atual: Agent Lab 44334550347 / job 134907561447; failure sem steps/logs_url. Combined status vazio. Nenhum dos jobs chegou a fornecer evidência de execução do Gradle.
- O bloqueio de runner continua sem diagnóstico; verificar GitHub Actions -> Billing and licensing -> Usage, Actions included compute usage, spending limit e configuração da conta. Não presumir que o código falhou nem declarar aprovação.
- MainActivity.java não foi tocada. O hash ainda diverge do interface-lock.sha; não editar a interface nem a lock para esconder a divergência.
- Nenhum APK, instalação ou teste físico no Poco F6.
- Próximo passo: obter runner com logs; executar gradle testDebugUnitTest e gradle connectedDebugAndroidTest; corrigir apenas erros com evidência. Depois, construir o fluxo confiável de confirmação do proprietário para revisão de perfil. Não fornecer a ferramenta de promoção ao modelo.


## 32. Última conferência — 18/11/2026, 15:24 UTC
- HEAD confirmado: d3e0816b9d6fd37436c9b3afbb62cc4405b1d5a7.
- Estimativa de working set das ferramentas de leitura do Agent Creation Studio ajustada para 24 MiB STANDARD, preservando histórico máximo de 256 eventos/32 KiB por evento e saída limitada a 12 eventos.
- Workflow Agent Lab: run 44335158436 / job 134909733286, failure sem steps e sem logs_url. O job terminou antes de expor qualquer etapa; Gradle/JUnit/AndroidRunner permanecem NOT_RUN, não FAILED por diagnóstico de código.
- Próxima retomada: resolver a alocação de runner/logs na conta GitHub; depois executar unit tests e connected Android tests. Não editar interface-lock ou MainActivity para encobrir divergências preexistentes.


## 33. Rerun e bloqueio de CI — 18/11/2026, 15:25 UTC
- Rerun do run 44335158436 criou job 134910540861, que terminou failure sem steps/logs_url, repetindo o mesmo bloqueio anterior.
- HEAD da branch permanece 4452854361fedd728563898c3aae2f1c9f3c183c.
- Não existe diagnóstico de Gradle/Kotlin porque nenhuma etapa de workflow ficou disponível. Não repetir alterações cegas no código; primeiro resolver o runner/Actions compute e obter logs.


## 34. Retomada de Agent Lab — rastreamento de delegações — 18/11/2026
- Branch experimental isolada: `kellvyn-rnd/agent-trace-duration-port-20261118`, criada sobre o HEAD da bancada `lab/kellvyn-intelligence-core-20261116` (`4af672febef2cf81bbbe2a00ee8f32161711b4ec`). Não usar `main` como base para esta linha de trabalho.
- Corrigida uma lacuna real do trace: `KellvynOrchestrator.Session.dispatch()` agora calcula duração por relógio monotônico e grava `durationMillis` no evento final em sucesso, bloqueio por orçamento, falha e cancelamento. Cancelamento continua propagado; o registro terminal continua em `NonCancellable`.
- Quatro testes determinísticos sem `sleep` adicionados: sucesso (8 ms), cancelamento (5 ms + propagação), bloqueio (8/5 ms) e falha (9 ms).
- Commits: implementação `10e5d1af2517e8db929dcfccd7ad2d1b5d41ec2c`; testes `15813b58ecc2790672067facf6a33b1571c226ce`; experimento `cd6350efb2a1e8faea7d420294df507b731de309`. HEAD da branch na criação deste checkpoint: `cd6350efb2a1e8faea7d420294df507b731de309`.
- Escopo conferido no diff contra a base: somente `KellvynOrchestrator.kt`, `KellvynOrchestratorTest.kt` e `docs/kellvyn/rnd-studio/experiments/agent-delegation-duration-001.json`. Cérebro neural, `KellvynRuntime`, MainActivity, interface, modelos e pesos não foram alterados. Nenhuma branch anterior foi sobrescrita ou mesclada.
- GitHub Actions runs `44424776449` e `44424784548` falharam antes do primeiro step. Todos os jobs exibem `runner_id=0`, `runner_name` vazio e `steps=[]`. Classificação: **NOT RUN / runner não alocado**; não é aprovação nem falha de compilação/teste. Gradle/JUnit/AVD permanecem pendentes.
- Não mesclar nem ativar a mudança antes da suíte `agents` passar em runner real e da revisão do diff. Runtime de produção continua desligado; `KellvynRuntime.isReady() == false`.
- Próxima ação prioritária do proprietário: inspecionar Settings → Billing → Budgets and alerts, confirmar compute incluído restante e verificar se algum budget Actions está em `Usage blocked` com `Stop usage when budget limit is reached`. Conferir também Settings → Actions → General do repositório. **Não aumentar orçamento, adicionar cartão nem habilitar cobrança sem decisão explícita.** Se não houver bloqueio de budget/política, abrir chamado ao GitHub com os dois run IDs e jobs sem runner/steps.
- Depois de resolver a alocação, reexecutar o workflow; exigir evidência dos steps Python e Gradle, relatórios JUnit e, em seguida, instrumented tests no emulador. Não repetir edições cegas enquanto o runner não iniciar.


## 35. Pesquisa de capacidade e melhoria do CI — 19/11/2026
- Pesquisa técnica registrada em `docs/kellvyn/RESEARCH_LAB_CAPACITY_2026-11-19.md`: alternativas de runner, DroidRunner, riscos do PRoot, compatibilidade ARM64, recursos/temperatura e protocolo experimental dos agentes.
- DroidRunner v1.0.0 é candidato para PoC em aparelho secundário dedicado, não para instalação no Poco F6 pessoal: self-hosted runner executa o workflow no aparelho, PRoot não é sandbox forte, Docker/KVM/AVD aninhado não são suportados e a compatibilidade integral do Android SDK ARM64 deste repo ainda precisa ser provada. Nenhum APK/token/runner foi instalado ou criado.
- CI `.github/workflows/agent-lab-tests.yml` consolidado de três jobs em um job sequencial: Python CLI -> JDK/SDK -> Gradle JVM -> AVD instrumentado. Adicionado preflight (OS/arquitetura, CPU, RAM, disco, Python/Java/Gradle/ADB), snapshot final e artefatos de evidência. Escopo de testes preservado.
- Novo run `44426067190`, job `135231121117`: `runner_id=0`, nome vazio, `steps=[]`; o job único também não iniciou. Confirma que reduzir alocações não resolve a causa externa. Classificação: runner não alocado / NOT RUN, não falha de código.
- O novo relatório documenta como medir baseline, LIGHT, STANDARD, HEAVY, temperatura, bateria, PSS, CPU, integridade de traces e estados de segurança; mantém especialistas sequenciais e bloqueio de promoção sem verificador.
- HEAD confirmado após esta rodada: será atualizado na próxima verificação de Git ref. Não mesclar, não ativar Runtime e não instalar runner no telefone pessoal.
- Próximo passo: verificar billing/compute/política Actions na conta proprietária; se correto, escalar ao GitHub Support com os runs `44424776449`, `44424784548`, `44426067190`. Depois executar o workflow numa infraestrutura que realmente aloque runner.


## 36. CoGo local PoC e correção da execução Gradle do R&D Studio — 19/11/2026
- CoGo 26.47 R2 (17/11/2026) agora é o candidato local mais forte para uma compilação manual de referência no Android: IDE Kotlin/Java offline-capable, ARM64, AGP até 9.3.1, JDK21 e Kotlin2.3.21. Requisito estimado: 3,5 GB de armazenamento e 4 GB de RAM livre recomendada. Não é runner de workflow headless.
- Kellvyn atual usa AGP8.13.2, KGP2.3.20, Java/Kotlin target17, compileSdk35; CI usa Gradle8.13. As matrizes oficiais mostram essa combinação CI coerente. A versão de Gradle efetivamente usada pelo CoGo e a importação do projeto Groovy atual ainda precisam de PoC; não alterar a toolchain estável antes de build comparativo.
- Wiki CoGo contém um procedimento de migração para Gradle9.6.1/AGP9.3.1/Kotlin2.3.21; não aplicar automaticamente. Criar branch/cópia separada, preservar source version 17 e testar sync, unit tests, assemble, hashes e logs.
- Achado do R&D Studio: Android tree não contém Gradle Wrapper, embora o CI instale Gradle8.13. O código antigo exigia wrapper e não podia criar tasks na branch atual.
- Corrigido experimentalmente tools/kellvyn-rnd-studio/studio.py: prefere wrapper completo fixado; na ausência, aceita Gradle externo de host confiável, fora do repo, com versão (8.13 por default) e SHA-256 registrados; recusa hash/versão alterados antes/depois. Mantém compatibilidade dos manifests antigos.
- test_studio.py passou a 20 testes escritos, dos quais 3 cobrem system Gradle mode. Ainda NOT RUN (runner indisponível).
- Run 44427563917, job 135236233647: runner_id=0, steps=[]; Python/Gradle/Android não iniciaram. Não classificar como falha de código.
- Branch atual para o trabalho: kellvyn-rnd/agent-trace-duration-port-20261118; HEAD a confirmar após esta entrada. Não mesclar nem ativar runtime.
- Próximo passo técnico: obter um executor real (Actions após resolver a conta, ou CoGo manual em cópia isolada) e executar primeiro os 20 testes Python e as suítes Gradle. Para CoGo, medir RAM livre e temperatura antes; parar diante de pressão térmica/memória.


## 37. Telemetria térmica adaptativa — 19/11/2026
- Encontrada oportunidade na leitura do getThermalHeadroom: o cache anterior usava 1s para respostas válidas e inválidas. A API Android recomenda não consultar com frequência maior que aproximadamente 1/s; o guia ADPF orienta espaçar em 10s quando recebe NaN.
- Criado KellvynThermalHeadroomSampler sem timer/background polling: 1s para leituras válidas; 10s após NaN, valor negativo/infinito ou exceção; volta a 1s quando recupera.
- Integrado no KellvynAndroidResourceMonitor; removido cache/constante duplicada.
- Quatro cenários puros compilaram e passaram localmente com kotlinc 1.9.0/JRE21: 4/4. Evidência limitada à classe isolada; integração Android e Gradle/JUnit ainda NOT RUN.
- JUnit tests adicionados em KellvynThermalHeadroomSamplerTest.kt.
- Run 44428910941 / job 135240881720: runner_id=0, steps=[]; Gradle/JUnit/Android não iniciaram.
- HEAD confirmado nesta sequência antes do checkpoint: 7270287f2119edce943f92cc6d1e309b8d727a86. Não mesclar ainda; executar suíte completa quando houver runner real.


## 38. Capacidade de armazenamento e Worker sob restrição — 19/11/2026
- ResourceSnapshot passa a incluir availableAppDataBytes e totalAppDataBytes opcionais. O monitor lê StatFs(filesDir) durante snapshot on-demand; sem polling. A leitura representa o volume privado de dados do app, não o armazenamento total nominal do telefone.
- Snapshot diagnóstico passa a informar armazenamento privado disponível/total, processCpuTimeMillis e availableProcessors. Teste unitário de resumo cobre valores convertidos em MiB.
- WorkManager queue drain agora requer requiresBatteryNotLow e requiresStorageNotLow. Teste instrumentado verifica ambas as condições no WorkRequest publicado.
- APIs oficiais confirmadas: StatFs.availableBytes e Constraints.Builder.setRequiresStorageNotLow.
- Run 44430446144 / job 135246159400: runner_id=0, steps=[]; Python/Gradle/Android continuam NOT RUN.
- Harness local puro da política térmica/OEM: 5/5; depois da inclusão de campos de armazenamento e sua validação: 6/6 em cópia isolada da lógica. Sampler térmico puro: 4/4. Isso não prova Gradle/JUnit da aplicação, integração Android ou comportamento térmico/armazenamento no Poco F6.
- Próximo: obter runner local/hosted que execute testes; fazer CoGo PoC em cópia isolada após medir RAM livre/temperatura; sem alterar toolchain ou instalar runner no aparelho pessoal sem decisão explícita.


## 39. Portes Brain/Integration e auditoria de prontidão — 19/11/2026
- Portado seletivamente do branch brain-gradient-coverage-001 o KN-003 + testes; agora o checker cobre os 14 grupos de parâmetros e coordenadas zero/não-zero. Testes exigem erro abs <0,004 e relativo <0,06 nos head counts 1/2/4 com seeds determinísticas.
- Portado seletivamente de brain-agent-contract-001 o teste KellvynBrainAgentIntegrationContractTest: delegação KELLVYN/1 roteirizada, ferramentas least privilege, resultado UNTRUSTED e rejeição de wire inválido. É contrato com fake/script engine, não prova qualidade do modelo.
- Studio Python continua com 20 métodos de teste; a verificação da suíte Integration agora exige a classe de contrato.
- Branch está 31 commits à frente e 0 atrás da base no momento da comparação; 22 arquivos no diff em 19/11. Nenhum Runtime/MainActivity/layout foi alterado.
- Run 44432449442/job 135252947870: runner_id=0, steps=[]; NOT RUN.
- Sampler térmico puro Kotlin executado 4/4 localmente; selector do gradient checker simulado em Python em 36.848 combinações. Não equivale a Gradle/JUnit Android.
- Auditoria detalhada em docs/kellvyn/IA_READINESS_AUDIT_2026-11-19.md. O Runtime segue isReady=false, reply stub; não há bundle de pesos versionado nem corpus treinado.
- KN-003 default: vocabulário 512, contexto256, embedding32, 4 heads, FF64, um bloco, 43.648 parâmetros estimados. Treinador limita corpus a 500KB, até 1.000 exemplos, 100.000 passos e contexto 256. Ainda é protótipo pequeno, não LLM geral.
- Não ligar o Runtime até bundle/proveniência/Quality Gate/benchmark comportamental/Gradle/Android/AVD/Poco F6 passarem.
