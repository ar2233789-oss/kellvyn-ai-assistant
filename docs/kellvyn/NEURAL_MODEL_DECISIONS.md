# Kellvyn — decisão vinculante da fundação neural própria

**Data:** 16/11/2026  
**Estado:** decisão aprovada para o projeto: construir o cérebro conversacional do zero.

## 1. Decisão central

O cérebro da Kellvyn será original. A arquitetura neural, o tokenizer, a inicialização, o código de treino, os pesos aprendidos e os checkpoints serão criados e mantidos neste repositório.

**Não usar como cérebro:** pesos pré-treinados de ChatGPT, Claude, Gemini/Android Studio, Replit, Gemma, Qwen, Llama ou de qualquer outro modelo externo. Não baixar um modelo alheio e apresentá-lo como se fosse o cérebro criado pela Kellvyn. Não fazer fine-tuning ou LoRA de pesos externos como substituto desta decisão.

A Kellvyn pode estudar modelos e agentes conhecidos para entender técnicas publicamente documentadas. Referências de arquitetura não são autorização para copiar código privado, pesos, prompts internos ou mecanismos proprietários.

## 2. O que pode ser aprendido com outras IAs

| Referência | O que observar | O que a Kellvyn implementará por conta própria |
|---|---|---|
| ChatGPT / OpenAI Agents | decomposição de tarefas, ferramentas tipadas, chamadas estruturadas, recuperação e verificação | protocolo próprio de ferramentas, orquestração, estado, memória e avaliação |
| Claude / Claude Code | planejamento longo, instruções em camadas, skills, hooks e revisão | gerenciador próprio de tarefas, skills e regras |
| Android Studio Agent Mode | ciclo plano → edição → build → teste → Logcat/correção | pipeline próprio de engenharia Android e evidência de execução |
| Replit Agent | fluxo Plan/Build, checkpoints, replay e testes funcionais | workspace próprio, checkpoints e validação independente |
| Cursor | mapa de código, contexto de repositório e diffs | indexador, busca e editor de mudanças próprios |
| OpenHands, Aider, SWE-agent, OpenCode | padrões públicos de agentes, ambientes, permissões e avaliação | estudar implementação pública e licenças; não copiar sem auditoria e atribuição |
| Pesquisas de Transformers | atenção, previsão autoregressiva e otimização de treinamento | implementação própria de uma arquitetura neural apropriada ao hardware-alvo |

Para cada referência, registrar fonte, versão/data, capacidade observada, ideia reaproveitada, diferença da implementação Kellvyn e teste que comprova o resultado. “Inspirado por” não deve esconder cópia.

## 3. Limite entre originalidade e componentes auxiliares

O núcleo que aprende linguagem e raciocínio deve ser autoral. Ferramentas auxiliares como armazenamento, serialização, compilador, operações matemáticas otimizadas ou runtime de execução podem ser estudadas e, quando necessário, incorporadas sob licença compatível e atribuição correta. Esses componentes não podem fornecer pesos pré-treinados nem funcionar como modelo substituto.

Quando um código aberto for incorporado, registrar:
- repositório e URL de origem;
- commit/tag exato;
- licença e avisos obrigatórios;
- arquivos importados ou modificados;
- diferenças mantidas pela Kellvyn;
- razão técnica de uso e testes.

Código proprietário não será copiado. Ideias gerais e técnicas publicadas serão reimplementadas de forma independente. Não usar acesso não autorizado, engenharia reversa proibida ou prompts internos vazados.

## 4. Arquitetura neural de trabalho — KBN-002 (proposta de implementação)

A próxima geração não será somente um preditor de byte. A proposta técnica é um pequeno modelo autoregressivo decoder-only, inicialmente treinado em escala experimental:

1. **Tokenizer próprio:** começar com byte UTF-8 como representação de referência e criar um tokenizer subword (BPE) treinado em corpus autorizado, com vocabulário/versionamento próprios.
2. **Embeddings:** tabela de tokens e codificação posicional.
3. **Blocos Transformer causais:** normalização prévia, atenção multi-cabeças causal e MLP gated (por exemplo, SwiGLU); mecanismos posicionais como RoPE podem ser implementados após testes.
4. **Cabeça de linguagem:** logits por token e cross-entropy para prever o próximo token.
5. **Treinamento:** inicialização aleatória reproduzível, retropropagação, otimizador AdamW, clipping de gradiente, agenda de taxa de aprendizado e checkpoints atômicos.
6. **Inferência:** geração incremental com cache KV quando validado, cancelamento, limites de contexto e critérios de parada.
7. **Avaliação:** dados de validação separados do treino, tarefas de português brasileiro, seguimento de instruções, consistência multi-turno, recuperação, código e chamadas de ferramenta.

A primeira versão deve ser deliberadamente pequena. Dimensões, parâmetros, comprimento de contexto e precisão numérica serão escolhidos depois de medir uso real de RAM, tempo, calor e bateria no Poco F6.

**KBN-002 ainda é uma especificação de trabalho, não código concluído.** Nenhuma arquitetura Transformer completa, tokenizer BPE, treino em corpus real ou geração conversacional de qualidade foi validada neste estado.

## 5. Dados e treinamento

Só usar dados cuja origem e permissão de uso estejam documentadas: material criado pelo projeto, conteúdo de domínio público, licenças permissivas/compatíveis ou conteúdo fornecido pelo proprietário com consentimento explícito. Não coletar dados privados nem tratar qualquer conteúdo público da Web como automaticamente livre para treinamento.

Separar e identificar:
- corpus de treino;
- corpus de validação;
- corpus de teste final;
- dados de alinhamento/instrução;
- exemplos de chamadas de ferramenta;
- material excluído por licença, privacidade ou qualidade.

Dedupe, normalização, detecção de dados sensíveis, proveniência, versão e hash precisam fazer parte do pipeline. Dados de validação/teste não devem vazar para o treino.

## 6. Realismo técnico do Poco F6

O aparelho-alvo tem 8 GB de RAM e execução local/offline. Isso é uma restrição séria. Treinar um modelo avançado comparável a ChatGPT ou Claude do zero exige enormes quantidades de dados, computação e tempo; o telefone sozinho não torna esse objetivo viável.

A estratégia é construir uma sequência verificável:
- primeiro um protótipo neural pequeno para provar tokenizer, forward, loss, gradientes, atualizações e checkpoint;
- depois um pequeno modelo de linguagem avaliado em corpus separado;
- em seguida otimizar inferência e medir RAM, latência, temperatura e bateria;
- ampliar somente se os dados e as medições justificarem.

Ferramentas, memória, pesquisa e compilação aumentam a utilidade prática do agente, mas não transformam um modelo pequeno em um LLM avançado nem substituem aprendizagem neural.

## 7. Implementação KBN-002 alpha

Foram acrescentados ao projeto:
- `KellvynBpeTokenizer.kt`: BPE byte-fallback com treinamento explícito, codificação reversível UTF-8, desempate determinístico, checkpoint versionado e SHA-256;
- `KellvynAttentionLanguageModel.kt`: modelo autoral inicializado aleatoriamente, com uma cabeça de atenção escalada sobre o contexto anterior, cabeça de previsão do próximo token, cross-entropy, gradientes manuais, AdamW, clipping, geração greedy e checkpoint incluindo os momentos do otimizador;
- `createForTokenizer()`: garante que o tamanho do vocabulário do modelo acompanhe o tokenizer treinado.

Esta alpha é **um protótipo de atenção autoregressivo de cabeça única**, não uma implementação Transformer completa. Ainda faltam multi-head attention, blocos residuais, normalização, MLP gated, RoPE/cache KV, treinamento em corpus real e inferência integrada ao chat.

### Verificação matemática de referência

Uma implementação numérica equivalente em Python/NumPy float32 foi usada como verificação independente das fórmulas:
- maior diferença absoluta entre gradientes analíticos e diferenças finitas nas amostras: aproximadamente (9,6\times10^{-7});
- treino do padrão alternado sintético: perda aproximada de 5,49 para 0,00392 após 1.100 atualizações.

Isso não é execução do código Kotlin, não é resultado do GitHub Actions e não é evidência de competência linguística. O gate Kotlin/Gradle e o teste no Poco F6 continuam pendentes.

## 8. Situação técnica confirmada em 16/11/2026

- KellvynByteBrain.kt (KBN-001) existe e inicializa seus próprios pesos;
- KellvynBpeTokenizer.kt foi acrescentado como primeiro componente do KBN-002: aprende merges BPE de um corpus passado explicitamente, mantém fallback de byte UTF-8, checkpoint versionado com SHA-256 e limites experimentais de CPU/memória;
- KellvynBpeTokenizerTest.kt cobre ida e volta Unicode/português, determinismo, restauração e rejeição de checkpoint alterado; a execução CI desta atualização ainda não foi confirmada;
- o modelo prevê o próximo byte com rede rasa, janela curta, embeddings, gradientes e checkpoints;
- os testes anteriores verificam UTF-8, redução de loss em dados sintéticos repetidos, gradientes por diferenças finitas e restauração de checkpoint;
- KBN-001 não possui tokenizer subword, Transformer, corpus de treinamento de linguagem, diálogo multi-turno, capacidade de seguir instruções ou integração com o chat;
- KellvynAgent, planner, memória, recuperação de contexto e ferramentas estão separados do modelo;
- KellvynRuntime.reply() ainda informa que a inferência não está conectada;
- nenhum peso de modelo externo foi selecionado ou integrado;
- nenhum benchmark de modelo no Poco F6 foi executado.

## 8. Critérios para chamar o cérebro de funcional

Não declarar “cérebro pronto” até que todos os itens mínimos sejam observados:
1. arquitetura neural e tokenizer próprios documentados;
2. pesos inicializados e efetivamente treinados pelo pipeline Kellvyn;
3. loss de treino e validação calculadas em corpus separado, sem vazamento;
4. geração de texto coerente em português além de memorizar frases repetidas;
5. teste de instruções com conjunto fixo e avaliação externa;
6. conversa multi-turno com contexto e cancelamento;
7. inferência integrada ao KellvynRuntime sem fallback externo;
8. agente consegue propor ferramenta estruturada e o código valida permissões/parâmetros;
9. testes automatizados aprovados e build Android;
10. teste no Poco F6 com RAM, latência, temperatura e estabilidade medidos.

## 9. Regra permanente para evitar regressões de decisão

Antes de propor qualquer modelo, peso ou adapter, reler este documento. A decisão de origem própria prevalece sobre textos anteriores que mencionem pesos abertos, modelo-base externo, fine-tuning ou LoRA. Só alterar esta regra mediante nova instrução explícita do proprietário.


## 10. Treinamento supervisionado de instruções — 17/11/2026

Foi implementada uma primitiva inicial de SFT própria sobre KBN-002 alpha:
- `trainCompletion(promptTokenIds, completionTokenIds)` condiciona a resposta no prompt, mas atualiza os pesos somente nos tokens da completion.
- `evaluateCompletionLoss()` mede somente a loss da resposta.
- `KellvynInstructionTrainingExample` registra prompt, resposta, partição e proveniência.
- `trainInstructions()` aprende BPE somente a partir de exemplos TRAIN, mede VALIDATION e TEST separadamente, recusa credenciais conhecidas, limita dados/trabalho e registra hashes/proveniência.
- Um mesmo prompt não pode aparecer em partições diferentes, ainda que as respostas variem.
- `KellvynRuntime.trainOwnInstructionModel()` oferece o treinamento local assíncrono e persiste o bundle próprio.

Testes foram adicionados para queda de loss, contagem dos passos apenas da resposta, checkpoint e separação das partições. A correção do parser para UTF-8 malformado também foi gravada. A execução de CI do HEAD `57ac899e` estava em andamento na última consulta; portanto, não declarar aprovação antes do resultado final.

**Limites:** esse SFT é apenas um mecanismo de aprendizagem, não demonstra qualidade conversacional. Não existe corpus de escala, benchmark em português, generalização, diálogo multi-turno ou execução de ferramentas validada. O modelo continua single-head alpha e o chat principal segue desconectado. A arquitetura Transformer completa continua como próximo marco obrigatório.
