# Kellvyn — decisão vinculante da fundação neural própria

**Data:** 16/11/2026  
**Estado:** decisão aprovada para o projeto: construir o cérebro conversacional do zero.

> **Estado técnico atual — 18/11/2026:** KN-003 existe como Transformer autoral de um único bloco e já possui cache KV incremental para esse bloco. Ainda não é um LLM geral nem está ligado ao chat principal. `KellvynRuntime.isReady()` continua `false` e `reply()` informa que a inferência não está disponível. Não há checkpoint conversacional treinado e aprovado nem teste de competência geral. Os testes mais antigos que passaram em 17/11 não validam as alterações posteriores desta branch, pois o runner do GitHub não está atribuindo máquina aos jobs em 18/11.
>
> A configuração padrão do pipeline KN-003 é deliberadamente experimental: contexto 256, embedding 32, 4 cabeças, feed-forward 64, no máximo 64 merges BPE, uma época por exemplo, limite de 100.000 passos e 500.000 bytes de corpus. Com os 64 merges todos usados, o vocabulário máximo do tokenizer seria 321 (256 bytes + 64 merges + EOS) e o modelo contém aproximadamente 31.233 parâmetros, calculados a partir dos arrays de embeddings, atenção, normalização, SwiGLU e projeção de saída. Com menos merges, tem menos parâmetros. Isso é um protótipo didático muito pequeno, não a capacidade de um assistente geral.
>
> Na árvore atual do repositório não foi encontrado corpus de treino independente de fontes de teste/documentação. O pipeline exige exemplos explícitos com proveniência e direitos de uso; o pequeno limite de corpus atual é adequado a experimentos e testes de mecânica, não a pré-treinar um assistente geral.
>
> Para evitar confusão entre estado histórico e estado atual: as seções abaixo registram decisões, protótipos e execuções anteriores; a situação acima e o arquivo `SESSION_CONTROL.md` prevalecem para o estado desta branch.


## 1. Decisão central

O cérebro da Kellvyn será original. A arquitetura neural, o tokenizer, a inicialização, o código de treino, os pesos aprendidos e os checkpoints serão criados e mantidos neste repositório.

**Não usar como cérebro:** pesos pré-treinados ou checkpoints de qualquer modelo externo. Não baixar, embutir, chamar por SDK nem apresentar um modelo alheio como se fosse cérebro desenvolvido pela Kellvyn. Não usar fine-tuning ou adapters de pesos externos como substituto desta decisão.

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

## 4. Arquitetura neural de trabalho — KN-002 (proposta de implementação)

A próxima geração não será somente um preditor de byte. A proposta técnica é um pequeno modelo autoregressivo decoder-only, inicialmente treinado em escala experimental:

1. **Tokenizer próprio:** começar com byte UTF-8 como representação de referência e criar um tokenizer subword (BPE) treinado em corpus autorizado, com vocabulário/versionamento próprios.
2. **Embeddings:** tabela de tokens e codificação posicional.
3. **Blocos Transformer causais:** normalização prévia, atenção multi-cabeças causal e MLP gated (por exemplo, SwiGLU); mecanismos posicionais como RoPE podem ser implementados após testes.
4. **Cabeça de linguagem:** logits por token e cross-entropy para prever o próximo token.
5. **Treinamento:** inicialização aleatória reproduzível, retropropagação, otimizador AdamW, clipping de gradiente, agenda de taxa de aprendizado e checkpoints atômicos.
6. **Inferência:** geração incremental com cache KV quando validado, cancelamento, limites de contexto e critérios de parada.
7. **Avaliação:** dados de validação separados do treino, tarefas de português brasileiro, seguimento de instruções, consistência multi-turno, recuperação, código e chamadas de ferramenta.

A primeira versão deve ser deliberadamente pequena. Dimensões, parâmetros, comprimento de contexto e precisão numérica serão escolhidos depois de medir uso real de RAM, tempo, calor e bateria no Poco F6.

**KN-002 ainda é uma especificação de trabalho, não código concluído.** Nenhuma arquitetura Transformer completa, tokenizer BPE, treino em corpus real ou geração conversacional de qualidade foi validada neste estado.

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

## 7. Implementação KN-002 alpha

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

- KellvynByteModel.kt (KN-001) existe e inicializa seus próprios pesos;
- KellvynBpeTokenizer.kt foi acrescentado como primeiro componente do KN-002: aprende merges BPE de um corpus passado explicitamente, mantém fallback de byte UTF-8, checkpoint versionado com SHA-256 e limites experimentais de CPU/memória;
- KellvynBpeTokenizerTest.kt cobre ida e volta Unicode/português, determinismo, restauração e rejeição de checkpoint alterado; a execução CI desta atualização ainda não foi confirmada;
- o modelo prevê o próximo byte com rede rasa, janela curta, embeddings, gradientes e checkpoints;
- os testes anteriores verificam UTF-8, redução de loss em dados sintéticos repetidos, gradientes por diferenças finitas e restauração de checkpoint;
- KN-001 não possui tokenizer subword, Transformer, corpus de treinamento de linguagem, diálogo multi-turno, capacidade de seguir instruções ou integração com o chat;
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

Foi implementada uma primitiva inicial de SFT própria sobre KN-002 alpha:
- `trainCompletion(promptTokenIds, completionTokenIds)` condiciona a resposta no prompt, mas atualiza os pesos somente nos tokens da completion.
- `evaluateCompletionLoss()` mede somente a loss da resposta.
- `KellvynInstructionTrainingExample` registra prompt, resposta, partição e proveniência.
- `trainInstructions()` aprende BPE somente a partir de exemplos TRAIN, mede VALIDATION e TEST separadamente, recusa credenciais conhecidas, limita dados/trabalho e registra hashes/proveniência.
- Um mesmo prompt não pode aparecer em partições diferentes, ainda que as respostas variem.
- `KellvynRuntime.trainOwnInstructionModel()` oferece o treinamento local assíncrono e persiste o bundle próprio.

Testes foram adicionados para queda de loss, contagem dos passos apenas da resposta, checkpoint e separação das partições. A correção do parser para UTF-8 malformado também foi gravada. A execução de CI do HEAD `57ac899e` estava em andamento na última consulta; portanto, não declarar aprovação antes do resultado final.

**Limites:** esse SFT é apenas um mecanismo de aprendizagem, não demonstra qualidade conversacional. Não existe corpus de escala, benchmark em português, generalização, diálogo multi-turno ou execução de ferramentas validada. O modelo continua single-head alpha e o chat principal segue desconectado. A arquitetura Transformer completa continua como próximo marco obrigatório.


## 11. Transformer KN-003 e parada aprendida — 17/11/2026

A implementação experimental KN-003 foi adicionada em código próprio:
- decoder-only autoregressivo;
- atenção causal multi-head com RoPE;
- Pre-LayerNorm, conexões residuais e MLP SwiGLU;
- treinamento supervisionado de prompt/resposta, atualizando somente os tokens da resposta;
- gradientes analíticos comparados com diferenças finitas;
- AdamW, clipping e checkpoints próprios com integridade;
- pipeline local com TRAIN/VALIDATION/TEST, proveniência e prevenção de vazamento entre partições.

**Escopo real:** a implementação atual tem um único bloco Transformer, dimensões pequenas e contexto máximo de 256 tokens. Ainda não é uma pilha de múltiplas camadas nem um modelo de linguagem de escala útil para conversa geral. As configurações do protótipo não devem ser confundidas com uma arquitetura final.

### Token EOS

O tokenizer BPE passou a reservar um ID dedicado de fim de sequência:
- não é emitido pela função encode(text) e não representa bytes UTF-8;
- checkpoints do tokenizer foram atualizados para versão 2;
- leitores mantêm compatibilidade com checkpoints versão 1, que não possuem EOS;
- o pipeline KN-003 adiciona EOS ao alvo supervisionado de cada resposta e o inclui na loss de validação/teste;
- a geração para quando prevê EOS, sem incluir o token de controle no texto retornado;
- o adaptador mantém fallback textual END somente para checkpoints legados sem EOS.

O EOS é treinável, não uma string detectada por coincidência no texto. O teste de modelo treina uma resposta de um token EOS, confirma a previsão e verifica que a geração termina sem expor o ID de controle.

### Validação

No commit f32bca0d1b2e14541ed7a52f8c0bd414f685084e, o workflow **Kellvyn neural core tests** run 44005947968 concluiu com sucesso. O log confirma gradle testDebugUnitTest e BUILD SUCCESSFUL. A tolerância matemática original foi mantida; o passo de diferenças finitas foi aumentado de 0,001 para 0,005 para reduzir cancelamento numérico em float32 após incluir a classe EOS no softmax.

O teste do laboratório virtual é um workflow separado. No último estado registrado, run 44005947963 ainda estava em andamento; isso não prova instalação, inicialização ou funcionamento em aparelho. Não houve teste físico no Poco F6.

### Limites que continuam bloqueando a integração

- pesos continuam experimentais e precisam de corpus autorizado de escala e avaliação independente;
- ainda não existe prova de conversa coerente/multi-turno ou raciocínio geral;
- o protocolo KELLVYN/1 exige exemplos de treinamento específicos para FINAL/PLAN/DELEGATE/CALL_TOOL;
- contexto 256 e geração limitada são pequenos para o prompt completo do agente;
- KN-003 tem um bloco, sem pilha profunda;
- geração recalcula chaves/valores por token; KV cache ainda não foi implementado;
- backend de inferência não está conectado ao chat: KellvynRuntime.isReady() permanece falso e reply() permanece bloqueado;
- sem teste de inferência sustentada, RAM, latência, temperatura ou bateria no Poco F6.

Próximo foco neural: avaliar a viabilidade de profundidade multi-bloco e custo de memória/gradientes; depois melhorar o comprimento de contexto e a inferência incremental com cache; construir conjunto supervisionado de decisões do protocolo com direitos documentados; medir generalização antes de integrar ao agente e ao chat.


## 12. Cache KV incremental para inferência — 17/11/2026

A sessão de geração KN-003 agora mantém um cache de chaves e valores do bloco Transformer:
- o prefixo é processado uma vez para preencher o cache;
- cada novo token calcula seus próprios Q/K/V e acrescenta K/V ao cache;
- a consulta do token atual usa as chaves/valores já calculados, sem reprojetar todos os tokens anteriores;
- o cache mantém no máximo contextLength entradas e remove a mais antiga ao ultrapassar a janela;
- as posições RoPE permanecem absolutas durante a sessão; a diferença relativa entre posições é preservada na janela deslizante;
- o adaptador local de inferência passou a gerar por meio dessa sessão;
- o token EOS é identificado antes de ser inserido no texto retornado.

**Escopo real:** o cache atende ao único bloco existente em KN-003. Não é um cache multi-camada. A geração de treinamento/avaliação continua usando o caminho de referência completo, e a sessão cacheada é usada na inferência incremental.

### Testes e validação
- comparação das probabilidades da sessão cacheada com o forward completo em cada token;
- repetição suficiente para provocar expulsão de tokens da janela;
- verificação do limite máximo de entradas no cache;
- verificação de que cada token só é processado uma vez no ciclo incremental;
- comparação da sequência greedy da sessão com uma referência que recalcula o contexto completo a cada token.

No commit de código 895eca04525b65b6c7c17175e42ff4d126d1e5ae, o workflow neural core tests run 44007839790 terminou com sucesso. O log confirma gradle testDebugUnitTest e BUILD SUCCESSFUL em 1m40s.

### O que ainda não foi demonstrado
- não há medição real de latência/throughput antes e depois;
- não houve benchmark de RAM, calor, bateria ou sessões longas no Poco F6;
- o cache não possui ainda um caminho de invalidação para treino concorrente sobre o mesmo objeto de modelo; a aplicação deve treinar um modelo separado e só promover o checkpoint após validação;
- o modelo continua com um único bloco, contexto máximo de 256 e sem evidência de qualidade conversacional.

Próximo marco arquitetural: profundidade multi-bloco com parâmetros independentes por bloco, forward causal para todas as posições e retropropagação completa através de todas as camadas. Cada bloco futuro precisará do seu próprio cache KV. Não promover crescimento de contexto ou largura sem testes de memória/desempenho.


## 10. Regra vinculante de originalidade e não plágio — confirmada em 17/11/2026

O cérebro Kellvyn deve ser uma implementação própria, não uma cópia ou colagem disfarçada de outra IA ou repositório.

- Não copiar código de Android Studio/Gemini, Replit, ChatGPT, Claude, Cursor, OpenHands, Aider, SWE-agent, OpenCode, nanochat, OLMo ou qualquer outro projeto para apresentar como código autoral.
- Não usar pesos pré-treinados, adapters ou checkpoints alheios como cérebro da Kellvyn.
- É permitido estudar artigos, documentação, algoritmos e código público para compreender conceitos. Princípios matemáticos publicados (atenção, Transformer, BPE, backpropagation, AdamW, RoPE, etc.) não são propriedade exclusiva de um produto; a implementação concreta deve ser escrita e mantida no projeto, com proveniência clara.
- Bibliotecas auxiliares externas só podem ser integradas mediante auditoria do commit, licença, NOTICE, dependências e atribuição. Sua autoria continua sendo de seus autores.
- Toda nova implementação neural deve registrar: autor/commit Kellvyn, objetivo, fontes conceituais, arquivos alterados, testes, e se qualquer código externo foi incorporado. Por padrão, declarar explicitamente “nenhum código externo incorporado” quando isso for verdadeiro.
- Não alegar uma auditoria de similaridade completa sem executá-la. A revisão de origem de cada componente e comparação de código são obrigações separadas.
- Se a melhor solução exigir código de terceiro, não o copiar escondido: interromper essa integração, apresentar fonte/licença/razão e manter alternativa autoral.

**Esta regra prevalece sobre qualquer texto ambíguo que possa ser entendido como autorização para copiar. A referência ensina; a implementação Kellvyn é própria.**


## 12. Nomenclatura oficial — 17/11/2026

Os protótipos neurais da Kellvyn usam exclusivamente os identificadores técnicos **KN-001**, **KN-002** e **KN-003** nesta sequência histórica. Não expandir a sigla, não criar uma identidade separada e não usar nomes alternativos para o núcleo neural. O protótipo inicial de previsão de bytes chama-se `KellvynByteModel`. Esta regra substitui qualquer nomenclatura anterior inconsistente.
