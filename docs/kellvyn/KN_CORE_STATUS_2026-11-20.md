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
