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

