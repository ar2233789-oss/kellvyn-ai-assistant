# KELLVYN — CONTINUIDADE OBRIGATÓRIA ENTRE CONVERSAS

**Atualizado:** 16/11/2026  
**Repositório de laboratório:** ar2233789-oss/kellvyn-ai-assistant  
**Branch de trabalho:** lab/kellvyn-intelligence-core-20261116  
**Repositório antigo:** reservado para publicar a versão final quando houver aprovação.  
**Alvo:** Poco F6, Android/HyperOS, 8 GB RAM, uso privado, local/offline primeiro.

> **Leia este arquivo antes de qualquer alteração em uma nova conversa.** Consulte o GitHub para confirmar o HEAD real da branch antes de trabalhar. Este documento é a fonte persistente de continuidade; não confiar só na memória do chat, em resumos ou em suposições.

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
KellvynByteBrain.kt, nome de laboratório KBN-001:
- rede rasa que prevê o próximo byte UTF-8;
- pesos próprios inicializados no código;
- embeddings, forward, softmax, gradientes, atualização de parâmetros e checkpoint;
- testes de UTF-8, loss em padrão repetido, diferenças finitas e restauração de pesos.

**Limite:** KBN-001 não é uma IA conversacional. Não possui Transformer, tokenizer subword, corpus de linguagem suficiente, raciocínio multi-turno ou capacidade geral de seguir instruções. Não apresentar isso como cérebro pronto.

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
- tokenizer KBN-002: a0704eba6815d2410a720d0ae5dd60ff0b0ab98d
- teste do tokenizer: 5cbf434850c80aea48178b635c31115b061ab74b
- limites do tokenizer: 61b382636a536cd627b162390ab39be37ab6005b e 5239dc19fd9eb368a690d254d60189fcac083571
- modelo atenção KBN-002 alpha: e96ad13856bf2aa9d47e9b21bae8ee79f67bfc2b
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

### Passo 2 — construir o cérebro próprio KBN-002
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
Só conectar KBN-002 ao KellvynRuntime quando houver geração conversacional testada. Depois integrar o mesmo modelo ao agente, com uso de ferramentas estruturadas e permissões aplicadas pelo código. Nunca criar um fallback oculto de modelo externo.

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
3. Pipeline KBN-003 acrescenta EOS ao final de cada completion usada em treino, validação e teste. Portanto, a loss agora ensina não só o conteúdo da resposta como também quando parar.
4. Geração do Transformer pode interromper no ID EOS sem incluí-lo no texto devolvido.
5. Adapter KBN-003 verifica EOS nos bundles novos. Para tokenizer/checkpoint legado sem EOS, mantém o antigo marcador textual END.
6. Foi adicionado teste de contrato: o modelo pequeno aprende EOS para um prompt controlado, prevê o token e encerra a geração antes de expor esse ID.
7. Testes do tokenizador cobrem EOS dedicado e compatibilidade com checkpoint v1.

### Commits da sequência
- Tokenizador EOS e compatibilidade v1: 89b37687609034959ea550a4b2b796db9ccd5ce8
- geração configurável com parada EOS: 8ed02f873e5fa660973c45ec695943edaddc6f9b
- supervisão de EOS no pipeline KBN-003: 181842fd9add2da92f97ba9817adabd4e70c2668
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
- KBN-003 é um decoder-only Transformer próprio com atenção causal multi-head/RoPE, Pre-LN, residual e SwiGLU.
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
- Cache é de um único bloco, porque KBN-003 ainda tem um único bloco.
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
4. Preservar o KBN-003 atual e seus checkpoints; não substituir até a nova arquitetura provar compatibilidade, treinamento e avaliação.
5. Não conectar ao chat principal nem editar a interface ainda.

**Regra de continuidade:** na próxima conversa, ler este arquivo, `STATUS.md`, `NEURAL_MODEL_DECISIONS.md` e conferir o HEAD real antes de agir. Não recomeçar estudos, papéis ou cache KV já implementados.
