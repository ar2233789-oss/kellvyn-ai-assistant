# Estado da fundação neural própria Kellvyn — KN-003 / KN-004

**Data:** 20/11/2026  
**Escopo:** extração seletiva do pacote neural da pesquisa KN-004 para uma branch limpa baseada em `main`.

## O que esta extração traz

O pacote `com.kellvyn.assistant.neural` reúne somente código Kotlin/JVM do cérebro próprio:

- Tokenizer BPE com fallback byte UTF-8 e checkpoint próprio.
- KN-003: Transformer decoder-only de um bloco, atenção causal multi-head, RoPE, Pre-LayerNorm, residual e SwiGLU.
- KN-004: Transformer decoder-only multi-bloco, pesos independentes por bloco, backpropagation através de todas as camadas e cache KV por camada para geração incremental.
- Treinamento supervisionado local, com partições TRAIN / VALIDATION / TEST, registro de proveniência/licença, detecção de credenciais e limites de corpus/passos.
- Checkpoints versionados, checksum SHA-256 e armazenamento atômico com recuperação de backup.
- Estimativa de pesos, estados AdamW, buffers de treinamento e cache de geração.
- Testes unitários para tokenizer, forward/backward, gradientes por diferenças finitas, treino, checkpoints, cache incremental e estimativa de recursos.

Somente os arquivos de `android/app/src/main/java/com/kellvyn/assistant/neural/` e seus testes unitários foram selecionados da branch de pesquisa. Não foram trazidos os diretórios de laboratório, código de agentes, orquestradores, interface, workflows experimentais ou serviços externos.

## O que ainda NÃO significa

- O pacote não está conectado ao chat principal nem substitui `LocalBrain.kt`.
- Não há checkpoint conversacional KN-003/KN-004 treinado e aprovado nesta extração.
- Não há corpus de treinamento geral incluído. O pipeline de experimento limita o corpus total a 500.000 bytes; isso é útil para validar a mecânica, não para pré-treinar um assistente de linguagem geral.
- Uma loss menor em dados pequenos não comprova português natural, raciocínio geral, planejamento ou capacidade de executar ferramentas.
- O cálculo de memória é estimativa de arrays; não substitui PSS/heap medido no Android nem teste físico no Poco F6.
- A presença do código não significa que a inferência já esteja pronta para produção. A integração permanece bloqueada até checkpoint, quality gate, benchmark comportamental e validação no aparelho.

## Escala experimental e desempenho

A configuração experimental padrão do KN-004 usa contexto 256, embedding 32, 4 cabeças, feed-forward 64 e profundidade 2. Com até 64 merges BPE, o vocabulário máximo descrito pelo tokenizer é 321 tokens. Pela topologia de parâmetros implementada, essa configuração tem aproximadamente 41.601 parâmetros treináveis.

É uma rede pequena para testar matemática, gradientes, cache e checkpoint. Ela pode executar operações locais com baixo uso de memória em comparação com modelos de bilhões de parâmetros, mas esse tamanho também limita fortemente sua capacidade linguística. **Não se deve confundir rapidez de inferência com inteligência ou qualidade.**

O caminho técnico de desempenho que já existe no código é a geração incremental com cache KV por camada: depois do prefixo inicial, não é necessário recalcular toda a sequência para cada token novo. O estimador separa o custo de pesos/Adam, ativações, atenção e cache. Quantização, SIMD ou outras otimizações só devem ser introduzidas depois de benchmark de referência, teste de equivalência e medição no Android; não foram presumidas nesta extração.

## Próximos gates obrigatórios

1. Obter build e JUnit reais para o SHA desta extração. Falha de runner sem steps/logs é `NOT_RUN`, não sucesso.
2. Preparar corpus próprio/autorizado de tamanho e diversidade adequados, mantendo proveniência e splits sem vazamento.
3. Treinar KN-004 em ambiente controlado e registrar checkpoint, seed, métricas de validação/teste e hashes.
4. Aplicar quality gate e benchmark comportamental separado de loss; medir tarefas em português, contexto, instruções, erros e estabilidade.
5. Medir latência, tokens/s, PSS/heap, pico de memória, temperatura e bateria em Android; depois confirmar no Poco F6.
6. Só depois criar um adaptador pequeno que conecte um checkpoint validado ao chat principal. Continuar com uma única Kellvyn IA, sem sistema de agentes, sem outro chat e sem mudanças visuais no Lovable.

A extração é uma base de engenharia para acelerar o trabalho com código já existente, não uma declaração de que o cérebro está pronto.


## Avanço de engenharia — carregamento compacto para inferência

O KN-004 agora possui dois caminhos de leitura para o mesmo formato de checkpoint:

- `readCheckpoint`: preserva parâmetros e estados AdamW, permitindo continuar treinamento.
- `readInferenceCheckpoint`: valida o checksum do checkpoint e as dimensões dos arrays, carrega os pesos e salta os bytes dos estados AdamW sem alocar os respectivos `FloatArray`.

O armazenamento também oferece `loadMultiBlockForInference()`, mantendo `loadMultiBlock()` para a restauração completa de treinamento. Assim, a mesma arquitetura e os mesmos pesos servem aos dois modos, sem manter estados de otimizador no caminho de conversa.

O estimador passou a separar a estimativa de memória residente de treinamento da estimativa de inferência. A diferença corresponde aos dois estados AdamW que não são necessários para gerar texto. Para modelos maiores, essa separação reduz a parcela de memória dos arrays de parâmetros de 12 bytes por parâmetro (pesos + dois momentos) para 4 bytes por parâmetro (somente pesos), sem contar cache KV e overhead.

Foram adicionados testes para:
- igualdade do fingerprint e das probabilidades entre checkpoint completo e carregamento compacto;
- rejeição de tentativa de treinar um modelo aberto em modo somente inferência;
- restauração do bundle KN-004 via armazenamento compacto;
- diferença entre estimativa residente de treinamento e inferência.

**Runtime de conversa:** foi adicionado `KellvynNeuralChatRuntime`, uma fronteira local única para inicializar o pacote KN-004 em modo somente inferência e gerar continuação token a token com callback parcial e cancelamento entre tokens. Ele usa o formato supervisionado `Pergunta: ... / Resposta:`, aceita contexto montado pelo chat principal e recusa explicitamente operar sem checkpoint treinado válido. Não cria pesos aleatórios, não escolhe outro modelo e não altera interface. A geração ainda não está ligada a `LocalBrain.kt`/chat porque o repositório não contém um checkpoint conversacional aprovado; conectar um modelo não treinado criaria uma falsa impressão de inteligência. Testes cobrem ausência de checkpoint, inicialização válida, liberação e recusa de geração sem carga. A decodificação acumula os IDs de tokens e interpreta o conjunto como UTF-8, evitando corromper caracteres acentuados divididos entre tokens. O limite de processamento BPE reduz o prompt ao trecho mais recente conforme o número de merges, preservando o orçamento de CPU definido pelo tokenizer.

**Segurança de memória do treinamento:** o pipeline KN-004 agora consulta `estimatedTrainingWorkingSetBytes()` antes de alocar o modelo. O orçamento padrão foi fixado em 128 MiB, configurável dentro de limites explícitos; configurações acima do orçamento são recusadas antes da alocação de pesos. Foi adicionado um teste com uma configuração de 8 blocos que excede 64 MiB e precisa ser barrada pelo gate.

**Limite:** estes testes foram adicionados ao código, mas ainda precisam executar em um runner Android/Gradle real. A validação anterior da branch foi interrompida antes do primeiro step de CI, então não há resultado de compilação ou JUnit a declarar.


## Avanços adicionais do runtime e dos gates

- **Amostragem:** o runtime usa um sampler KN próprio com temperatura, top-k, top-p e penalidade de repetição. A semente aleatória é injetável, permitindo testes reproduzíveis. A amostragem é limitada a top-k de até 128 candidatos.
- **Qualidade antes de responder:** o runtime carrega um checkpoint mesmo para inspeção experimental, mas só autoriza geração quando o manifesto passa pelo `KellvynMultiBlockTrainingQualityGate`. Um modelo que apenas tem checksum e dimensões válidas, mas não melhorou o suficiente em validação/teste ou tem contexto curto, não é autorizado a responder no chat.
- **Separação explícita de estados:** `Status.ready` significa que os pesos foram estruturalmente carregados; `qualityApprovedForBenchmark` indica separadamente se o gate numérico foi aprovado. Aprovar o gate só libera a próxima fase de benchmark comportamental, não significa inteligência geral nem produção.
- **Orçamento de treinamento:** o KN-004 verifica a estimativa de working set antes da alocação dos tensores. O limite padrão é 128 MiB e cada treino pode reduzir esse teto; configurações que ultrapassem o limite são recusadas antes da criação do modelo.
- **Texto UTF-8:** o runtime acumula os IDs de tokens e decodifica a sequência como um todo, evitando emitir caracteres de português quebrados quando seus bytes estão distribuídos por tokens BPE diferentes.
- **Leitura de checkpoint:** a validação SHA-256 atualiza o digest diretamente sobre o array de bytes e usa uma visão limitada do payload, sem copiar o payload completo. O bundle entrega o bloco de pesos ao leitor KN-004 diretamente, evitando mais uma cópia integral do checkpoint durante a abertura. Os estados AdamW continuam verificados e ignorados no modo de inferência.

Foram acrescentados testes específicos para sampler determinístico, top-k/top-p e repetição; quality gate e gap de generalização; bloqueio por orçamento de memória; carregamento sem estados AdamW; e comportamento do runtime quando não há checkpoint ou quando o checkpoint ainda não passou pelo gate.


## Gate comportamental independente

O gate numérico de loss apenas autoriza o próximo exame. Ele não libera sozinho o runtime para responder. Foi adicionado `KellvynNeuralBehaviorBenchmark`, que exige pelo menos 20 exemplos TEST, IDs e prompts distintos, proveniência completa, correspondência exata normalizada mínima de 60% e F1 médio de tokens mínimo de 80%.

O runtime confere se cada exemplo pertence ao conjunto TEST registrado no manifesto e se o hash de prompt+resposta coincide com o checkpoint. Só executa o benchmark com esses exemplos. A aprovação fica vinculada ao fingerprint dos pesos carregados; recarregar ou descarregar o modelo remove a aprovação. Sem o gate numérico e o comportamental, a geração normal permanece bloqueada. O controlador retorna os exemplos TEST originais em memória quando instala um checkpoint aprovado pelo gate numérico, para que a próxima etapa possa executar o benchmark sem reconstruir os exemplos; esses dados não são gravados automaticamente no armazenamento do modelo. A aprovação permite apenas um ensaio controlado de inferência, não declara o cérebro pronto para produção.

## Pico de memória ao carregar checkpoints

A leitura de inferência agora identifica os intervalos dos blocos dentro do pacote verificado e passa o intervalo do modelo diretamente ao leitor KN-004. O buffer limitado de leitura expõe seu array interno e tamanho efetivamente escrito (não confia em `InputStream.available()` como tamanho final), evitando também a cópia integral feita por `ByteArrayOutputStream.toByteArray()`. O leitor calcula SHA-256 sobre o intervalo e usa `ByteArrayInputStream(bytes, offset, length)`, sem criar uma segunda cópia do bloco de pesos (que pode chegar a 128 MiB). Apenas tokenizer e manifesto, ambos pequenos, são copiados. Foi adicionado teste de checkpoint com prefixo/sufixo para validar leitura por offset e ausência dos estados AdamW na inferência.


## Teto de capacidade móvel — somente benchmark de memória

Foi adicionado `KellvynMultiBlockTrainingConfig.capacityCeilingForMobileBenchmark()`: contexto 256, embedding 128, 8 cabeças, feed-forward 512, 6 blocos e até 512 merges BPE. Com vocabulário de 769 entradas, o estimador calcula aproximadamente 1,77 milhão de parâmetros, cerca de 100,6 MiB de working set de treinamento e 8,27 MiB de residência inferior estimada para inferência.

**Esse perfil não é recomendado para o treino atual.** O limite atual é 100 mil passos de treinamento; para 1,77 milhão de parâmetros isso representa no máximo cerca de 0,056 token de treino por parâmetro. Como referência aproximada — não uma lei universal — o estudo Chinchilla encontrou alocação compute-optimal perto de 20 tokens por parâmetro nos modelos estudados. Portanto, esse perfil serve apenas para avaliar o teto de memória/latência quando houver dados e orçamento de treino muito maiores; não deve ser apresentado como cérebro treinado. Fonte: https://proceedings.neurips.cc/paper_files/paper/2022/hash/c1e2faff6f588870935f114ebe04a3e5-Abstract-Conference.html

Mesmo o perfil experimental padrão é limitado pelo corpus e pelo orçamento de treino. Nenhum desses perfis foi validado no Poco F6: overhead da JVM, PSS, tempo de treino, temperatura e bateria ainda precisam ser medidos.


## Revisão de integridade e cache — 20/11/2026

Último commit desta revisão: `e887b2d4a8ae78f373b46ccbbf3b310f2c22a009`.

- O benchmark comportamental agora compara, além do hash de prompt+completion, todos os campos de proveniência do exemplo TEST com a entrada correspondente no manifesto: permissão, licença, referência da fonte e evidência de direitos. Isso impede que metadados sejam trocados entre o treino e a autorização do benchmark sem alterar o texto.
- O cálculo RoPE do caminho de referência agora aplica o mesmo deslocamento absoluto a consultas e chaves. O produto interno causal deve permanecer invariante quando toda a janela sofre o mesmo deslocamento.
- A sessão KV mantém o contador total de tokens monotônico depois da expulsão de contexto. A posição absoluta usada pelo RoPE foi separada do índice local das listas KV; ao reconstruir uma janela deslizante, as entradas retidas mantêm suas posições absolutas e os índices dos buffers continuam limitados à janela.
- Testes de regressão adicionados: rejeição de proveniência alterada mesmo com texto/hash idênticos; invariância de RoPE sob deslocamento uniforme. O teste existente de equivalência entre KV e forward completo após expulsão também valida a contagem total processada.
- Os arquivos atualizados foram conferidos na branch. O GitHub Actions ainda não disponibilizou execução associada ao último commit; por isso, estes testes estão **adicionados, mas não executados**. Não há resultado Gradle/JUnit novo, APK ou validação no Poco F6.


## Salvamento com fluxo contínuo e identidade de pesos — 20/11/2026

Commits desta otimização:
- `abed9ae78c12a228f0a6e8e8663bf6dd352042a0` — o writer KN-004 calcula o tamanho serializado exato e escreve pesos + estados AdamW diretamente no destino, calculando o checksum durante a escrita, sem montar um ByteArray completo do modelo.
- `52b0cffc1d483fba320552fd8f2ee13d94584c9f` — o bundle externo escreve cabeçalho, tokenizer, tamanho do modelo, payload neural, manifesto e checksum direto no fluxo. Removidos o buffer completo do modelo e a segunda cópia do pacote integral. O formato/ordem dos campos e os dois níveis de SHA-256 são mantidos.
- `de451897ad59a45696f063690ae8c08e511390cf` — teste exige que o tamanho calculado do checkpoint corresponda exatamente ao número de bytes emitidos.
- `2f59eef48bc97016d4c12685bb792de69444eea2` — runtime calcula o fingerprint dos pesos imutáveis uma vez na inicialização e reutiliza esse valor nas respostas e no benchmark, em vez de percorrer/hashing de todos os parâmetros a cada chamada.

A validação estática confirmou que o bundle não contém mais o buffer `modelBytes` nem o `payloadBuffer` integral e que o writer do modelo usa `DigestOutputStream`. Foi compilado isoladamente um pequeno snippet Kotlin/JVM para confirmar disponibilidade das APIs/constantes de tamanho usadas (`Int.SIZE_BYTES`, `Float.SIZE_BYTES`, `DigestOutputStream`); isso **não** compila o pacote real.
A execução do CI associada ao commit `de451897...` voltou a falhar antes dos steps (Build APK run `44690047015`, laboratório `44690047029`). O commit mais recente `2f59eef...` ainda não apresentava workflow na consulta. Portanto, o teste real de round-trip/Gradle permanece **NOT_RUN**.


## Proteção de metadados do corpus — 20/11/2026

Os dois pipelines agora verificam os campos textuais persistidos no manifesto: identificador, licença, referência da fonte e evidência de direitos, além do conteúdo de treinamento. Foram incluídos testes negativos para campos de proveniência e identificadores com padrões semelhantes a credenciais.

A checagem é heurística e baseada em padrões conhecidos; não é garantia formal de ausência de dados sensíveis nem comprovação jurídica da autorização de uso. Os testes foram adicionados, mas ainda não executados pelo Gradle.


## Encerramento de resposta nos caminhos legados — 20/11/2026

A auditoria identificou que o pipeline de instruções/documentos KN-002 criava um token EOS no tokenizer, mas não o incluía nos targets de treino. Além disso, `generateText` não parava quando esse token era previsto. Foi corrigido:
- Instruções treinam o EOS como último target da completion.
- Cada documento de linguagem inclui EOS no fim da sequência; as contagens de passos refletem o target de encerramento.
- `generate` aceita um ID de encerramento opcional, interrompe antes de acrescentá-lo ao resultado e `generateText` usa o EOS do tokenizer.
- Checkpoints de tokenizer v1 sem EOS continuam aceitos no caminho de treino/g geração legado, sem presumir um token que não existe.
- Testes atualizados para a contagem adicional de passos e para a parada antes do token de controle.

Commits: `58872bd5a9fcbfe29585a631da5bc8aa2b7b8dc2`, `c54046d4114dc4c666c38a4a699a39e66db5598f`, `218c5063d0ce179d0a2638ad80ffd0b7f9ef4e2d`, `4db175db624abbbb0dd3cbbbce2b589daf6f7c4f`, `b31e624d067ec692b91f94e6cfa5b9e9df71e995`.

Os testes foram adicionados, mas não executados pelo Gradle.


## Filtro comum de dados sensíveis e auditoria de corpus PT-BR — 20/11/2026

### Proteção de ingestão

Foi removida a duplicação de filtros de credenciais dos três caminhos de treinamento (documentos/instruções legado, KN-003 Transformer e KN-004). Todos agora usam `KellvynTrainingInputGuard`, aplicado a:
- IDs;
- prompts e respostas ou texto de documentos;
- licença;
- referência da fonte;
- evidência de direitos.

O filtro reconhece padrões conhecidos de credenciais/chaves privadas, e-mail, números de telefone brasileiros formatados e CPF com dígitos verificadores válidos. Não é um detector completo de PII: pode deixar passar dados não reconhecidos e pode recusar texto legítimo que contenha esses padrões. É uma barreira de pré-ingestão, não anonimização automática nem prova de direitos autorais.

Testes adicionados:
- unidade para e-mail, telefone, CPF válido/inválido, texto comum, token de API e cabeçalho de chave privada;
- integração no pipeline legado (e-mail em documento);
- integração KN-004 (telefone em completion);
- integração KN-003 (e-mail em proveniência).

O helper compartilhado foi compilado em isolamento com Kotlin/JVM e passou 8 casos de verificação. Isso não é Gradle/JUnit do projeto, nem valida a integração completa.

### Triagem preliminar de fontes em português

Nenhum corpus foi baixado, importado ou treinado. A decisão é deliberada: tamanho e selo de licença no pacote não provam que cada conteúdo está autorizado nem que dados pessoais foram removidos.

1. **Comunidade-Synapse-BR/dataset-3m-ptbr-original** — o cartão declara Apache-2.0 e o conjunto tem milhões de exemplos; porém a prévia contém material adulto, spam e dados de contato/telefone. **Não ingerir bruto.** Exigiria proveniência por registro, filtragem/anonimização revisada, avaliação de qualidade e verificação dos direitos dos conteúdos.
   Fonte: https://huggingface.co/datasets/Comunidade-Synapse-BR/dataset-3m-ptbr-original

2. **Comunidade-Synapse-BR/Synapse-PT-75M-Dataset** — declara Apache-2.0 para o pacote, mas a amostra expõe um campo `src` com fontes como `wiki`. A licença do empacotamento não deve ser presumida como licença dos textos de origem. Conteúdo derivado da Wikipédia normalmente requer cumprir atribuição e os termos CC BY-SA/GFDL aplicáveis. **Não ingerir até resolver licença, atribuição e rastreabilidade por fonte.**
   Fonte: https://huggingface.co/datasets/Comunidade-Synapse-BR/Synapse-PT-75M-Dataset
   Termos Wikimedia: https://foundation.wikimedia.org/wiki/Policy:Terms_of_Use

3. **Madras1/corpus-ptbr-v1** — o cartão descreve milhões de documentos e bilhões de tokens estimados, com fontes como C4 PT/FineWeb2 e conteúdo sintético; declara ODC-BY. ODC-BY trata direitos sobre a base de dados, não concede automaticamente direitos autorais sobre cada texto, nem resolve privacidade/dados pessoais. **Candidato apenas para auditoria posterior por origem/registro**, não aprovado para treino bruto.
   Fonte: https://huggingface.co/datasets/Madras1/corpus-ptbr-v1
   Licença: https://opendatacommons.org/licenses/by/1-0/

4. **Dados públicos brasileiros** — podem ser candidatos melhores quando o conjunto específico publica licença aberta clara, permite reutilização e não contém dados protegidos/sensíveis. “Está em site público” não significa “está autorizado para treinar”. Avaliar cada conjunto, termos, atribuição, privacidade e qualidade antes de importar.
   Referência: https://dados.gov.br/

**Decisão da etapa:** nenhum dataset remoto aprovado. Prioridade imediata para o próximo estágio: criar corpus autoral/sintético em português com autoria clara, ou auditar conjuntos públicos registro a registro. O limite experimental atual de 500 KB continua insuficiente para pré-treinar um modelo generalista; não aumentá-lo sem pipeline de streaming, avaliação de dados, orçamento de memória/tempo e plano de escala. Esta triagem é técnica, não aconselhamento jurídico.


## Evidência de CI após triagem de dados — 20/11/2026

No commit de código `28c134c36b0c5604f7760b4a839eedd0df56fcc1`:
- Build APK [44692023327](https://github.com/ar2233789-oss/kellvyn-ai-assistant/actions/runs/44692023327), job `136139814823`: `runner_id=0`, sem steps e sem logs.
- Laboratório [44692023238](https://github.com/ar2233789-oss/kellvyn-ai-assistant/actions/runs/44692023238): Android `136139814525` e Web `136139815318` sem steps/logs; diff `136139832873` ignorado.

Falha ocorreu antes de checkout/Gradle/JUnit/Bun/emulador. A causa exata continua sem confirmação. O SHA documental seguinte é `1b44ec56f6d1a317e5779cc16e9e3df642da5bb0`.


## Ingestão local JSONL com amostra limitada — 20/11/2026

### Contrato de entrada

Foi adicionado `KellvynJsonlCorpusReader`, sem biblioteca JSON externa. O formato é JSONL UTF-8: um objeto por linha, com exatamente sete campos string obrigatórios:

```json
{"id":"ptbr-0001","prompt":"Pergunta: o que é energia?","completion":"Energia é a capacidade de realizar trabalho.","permission":"OWNER_CREATED","licenseId":"owner-created","sourceReference":"owner://meu-corpus/0001","rightsEvidence":"Texto autoral criado pelo proprietário."}
```

- `permission` deve ser um dos valores de `KellvynTrainingPermission`.
- `id` deve ser único na amostra.
- `licenseId`, `sourceReference` e `rightsEvidence` são obrigatórios; o leitor não presume direitos a partir de uma URL.
- O arquivo não escolhe a partição. O leitor normaliza o prompt e calcula a partição por SHA-256 + seed: 80% TRAIN, 10% VALIDATION e 10% TEST. Assim, variações de caixa, pontuação e espaços do mesmo prompt não atravessam partições.
- Objetos com campos extras, campos ausentes, valores que não sejam strings, chaves repetidas, escapes inválidos ou UTF-8 malformado não são aceitos.

### Leitura e limites

A leitura percorre o fluxo completo, sem carregar o arquivo todo na memória, e mantém uma amostra determinística com prioridade por hash:
- limite padrão da origem: 512 MiB; o leitor aceita configuração até 2 GiB;
- limite padrão de linhas: 10 milhões;
- limite de uma linha JSONL: 500.000 caracteres;
- amostra retida: até 1.000 exemplos e 480.000 bytes UTF-8 incluindo conteúdo e proveniência;
- orçamentos de bytes por partição: aproximadamente 2/3 TRAIN, 1/6 VALIDATION e 1/6 TEST;
- mínimos padrão: 100 TRAIN, 20 VALIDATION e 20 TEST;
- orçamento superior dos targets: `maxTotalTrainingSteps / epochsPerExample`, contando o token EOS;
- limite de 50.000 passos por exemplo, ajustado pela quantidade de épocas;
- orçamento BPE de 40 milhões de operações aproximadas, aplicado por texto e ao corpus TRAIN agregado.

A seleção mantém respostas alternativas para um mesmo prompt em TRAIN/VALIDATION, mas remove prompts repetidos normalizados em TEST para não inflar o benchmark comportamental. O limite de tokens de saída é estimado conservadoramente pelo número de bytes UTF-8 + EOS, pois BPE nunca produz mais tokens que bytes de entrada.

O leitor calcula SHA-256 e tamanho exato da origem, conta linhas válidas/inválidas/sensíveis e quantidades por partição. O fluxo pertence ao chamador: o leitor consome, mas não fecha o `InputStream`. A leitura é cancelável entre linhas.

### Controlador

`KellvynNeuralTrainingController.trainAndInstallFromJsonl(InputStream, ...)` executa o preflight em `Dispatchers.IO`. Se os mínimos de partição ou os orçamentos falharem, devolve apenas o relatório e não aloca o Transformer. Se passar, encaminha a amostra ao pipeline KN-004 existente. O resultado retém apenas o relatório; os exemplos TEST ficam no `Outcome` apenas se o gate numérico aprovar. A origem não é persistida nem enviada à rede.

Foram adicionados testes para:
- leitura e hash de um fluxo completo;
- seleção reproduzível mesmo com a ordem das linhas invertida;
- divisão estável e independente de TRAIN/VALIDATION/TEST;
- respostas alternativas em TRAIN e deduplicação de prompts de TEST;
- rejeição de e-mail e linha JSON malformada;
- ownership do `InputStream`;
- limites de bytes e linhas;
- recusa do treino pelo controlador quando as partições mínimas não existem;
- passagem da amostra segura ao pipeline existente quando o preflight está apto.

**Limite importante:** isto é ingestão/auditoria e amostragem limitada, não treino de todo um corpus de milhões de registros. O arquivo completo pode ser inspecionado até o teto configurado; o modelo atual ainda treina apenas a amostra limitada que cabe nos orçamentos do KN-004. A amostra de 480 KB não é suficiente para afirmar pré-treinamento generalista nem inteligência conversacional pronta.


### Extensão da auditoria de origem e orçamento — atualização 20/11/2026

O relatório tipado agora registra também:
- contagens TRAIN/VALIDATION/TEST de todos os registros válidos antes da seleção limitada;
- tamanho exato da origem e hash SHA-256;
- bytes mantidos na amostra e bytes TRAIN consumidos pelo BPE;
- teto BPE agregado permitido para a configuração;
- registros rejeitados especificamente pelo limite de processamento por texto e pelo limite de passos por exemplo.

Os limites BPE correspondem aos limites internos existentes no tokenizer: aproximadamente 40 milhões de operações `bytes × merges`, tanto por texto quanto no corpus TRAIN agregado. O limite por exemplo é 50.000 passos, dividido pelo número de épocas, com EOS incluído na estimativa. A amostra de treino respeita ainda `maxTotalTrainingSteps` e o limite de working set do KN-004 antes da alocação do modelo.

Novos casos de teste cobrem escapes JSON, aspas, quebras de linha, Unicode/português, chaves duplicadas, BPE por texto/agregado, passos por exemplo, dados sensíveis, limites de origem e a passagem pelo controlador. Os testes estão **adicionados, não executados pelo Gradle/JUnit**.


### Relatório tipado e validação JSONL — continuação

O relatório de preflight agora expõe `tokenizerWorkRejectedRecords` e `perExampleStepRejectedRecords` como contagens numéricas, além de `validTrainRecords`, `validValidationRecords` e `validTestRecords`. O leitor só marca `readyForTraining` quando os mínimos de partição, o teto de bytes retidos, o limite agregado BPE e o limite agregado de passos estão satisfeitos.

Registros malformados ou com PII/credenciais reconhecíveis são excluídos e contabilizados; o preflight ainda pode prosseguir com a parte limpa se ela cumprir os mínimos e os orçamentos. Isso não comprova que os campos de licença/proveniência são verdadeiros, nem que a detecção heurística encontra todo dado pessoal. A aprovação da fonte continua sendo uma responsabilidade explícita de quem fornece o arquivo.

Mais testes adicionados: escapes com aspas/quebras de linha, Unicode português/emoji, rejeição de chaves duplicadas no mesmo objeto, respostas alternativas de TRAIN e deduplicação normalizada do TEST, e contagens exatas de recusas BPE.


### Limites rígidos de leitura e admissão de memória — 20/11/2026

O leitor JSONL não usa mais `readLine()`, que poderia alocar uma string enorme antes de validar seu tamanho. Ele lê em blocos de 8.192 caracteres, mantém no máximo 500.000 caracteres da linha e descarta o restante de uma linha excedente sem armazená-lo. A próxima linha é preservada no buffer de retorno. LF e CRLF são cobertos por teste.

O wrapper de bytes limita cada leitura ao espaço restante do teto da origem; após atingir o limite, faz no máximo uma leitura de prova para diferenciar EOF exato de arquivo maior. Um teste conta os bytes consumidos e confirma o teto + no máximo um byte de prova. Um harness Kotlin/JVM isolado da lógica de linhas/bytes passou: LF/CRLF, read-ahead, linha no limite exato, descarte/recuperação de linha grande, EOF e byte de prova. Isso **não** é compilação do pacote nem Gradle/JUnit do app.

O preflight estima também o working set KN-004 com o vocabulário máximo possível (`256 + maxMerges + EOS`). Se a estimativa exceder `maxTrainingWorkingSetBytes` ou se a configuração violar o limite de atenção, o relatório fica `readyForTraining=false` e o controlador não aloca pesos. É uma estimativa de admissão baseada na fórmula interna; não substitui medição de PSS, temperatura ou bateria no Poco F6.


### Revisão manual obrigatória em fontes sujas — 20/11/2026

Política final do preflight: qualquer registro de origem marcado como sensível (credencial, e-mail, telefone brasileiro ou CPF reconhecível) ou malformado faz `needsManualReview=true` e `readyForTraining=false`, mesmo quando a amostra limpa restante atingiria os mínimos. O controlador não inicia o treino. O operador deve corrigir/limpar o arquivo e submetê-lo novamente. Registros recusados apenas por limites computacionais (BPE, tamanho de exemplo ou steps) continuam contabilizados separadamente e podem ser excluídos da amostra se os restantes cumprirem todos os limites.

Isso evita que o caminho de ingestão inicie o treino automaticamente a partir de uma origem que ainda contém dados sensíveis ou registros malformados. A detecção continua heurística e não substitui uma revisão de direitos/licenças.
