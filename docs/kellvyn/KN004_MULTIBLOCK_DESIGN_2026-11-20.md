# KN-004 — projeto do Transformer multi-bloco

**Data:** 20/11/2026  
**Branch isolada:** `kellvyn-rnd/kn004-multiblock-20261120`  
**Base:** `kellvyn-rnd/polyglot-lab-foundation-20261119` no commit `52c3a1e518d797151d3fca0b06b5390e7f878c61` antes da criação desta branch.  
**Estado:** especificação técnica; implementação KN-004 ainda não iniciada neste documento.  
**Regra:** KN-003 e seus checkpoints continuam intactos e legíveis.

## 1. Decisão

O próximo marco neural é evoluir de um bloco Transformer para uma pilha decoder-only multi-bloco, mantendo o KN-003 como formato/implementação legado.

A estratégia é construir KN-004 como uma arquitetura versionada separada durante a fase experimental. Não alterar o checkpoint KN-003 para fingir compatibilidade com pesos multi-bloco. Não habilitar o chat de produção nem mudar `KellvynRuntime.isReady()` enquanto não houver modelo treinado e evidência comportamental suficiente.

## 2. Auditoria do ponto de partida

Arquivos observados na branch-base:

| Arquivo | Estado observado | Implicação |
|---|---|---|
| `KellvynTransformerLanguageModel.kt` | decoder-only, um bloco, 14 grupos de tensores, AdamW, gradientes analíticos, finite differences, checkpoint próprio | precisa de uma implementação multi-bloco com parâmetros/gradientes/caches por camada |
| `KellvynTransformerTrainingPipeline.kt` | BPE treinado somente em TRAIN; separa TRAIN/VALIDATION/TEST; treina tokens da completion; corpus máximo 500 KB e 1.000 exemplos | adaptar pipeline sem relaxar proveniência nem misturar partições |
| `KellvynTransformerCheckpointBundle.kt` | pacote KN-003 com tokenizer, modelo, manifesto e SHA-256 | criar novo magic/version para KN-004; manter leitor KN-003 |
| `KellvynNeuralModelStore.kt` | gravação atômica com temporário/backup para KN-002 e KN-003 | adicionar arquivos separados para KN-004, sem sobrescrever o bundle antigo |
| `KellvynTransformerInferenceEngine.kt` | acoplado diretamente ao tipo KN-003; protocolo KELLVYN/1 estrito | planejar contrato de inferência comum ou adaptador KN-004 separado |
| `KellvynRuntime.kt` | treina/restaura KN-003; `isReady() == false` | manter indisponível até os gates; adicionar API explícita de treino/restore KN-004 depois |
| `KellvynTransformerCapacityEstimator.kt` | estima parâmetros/estado para KN-003; exclui ativação e overhead Android | criar estimador multi-bloco e separar lower bounds de medição real |

### Um detalhe matemático que determina o desenho

O forward KN-003 atual calcula as projeções de todas as posições do contexto, mas produz a representação final para a última posição e aplica loss no próximo token. Isso funciona com um bloco. Em uma pilha multi-bloco, as representações de **todas** as posições precisam ser propagadas por cada bloco, porque as chaves e valores do bloco seguinte dependem das saídas do bloco anterior em todas as posições anteriores. Portanto, não basta colocar um loop `repeat(depth)` em torno do forward atual.

## 3. Arquitetura matemática do KN-004

Configuração versionada:

- `vocabularySize`: igual ao tokenizer associado ao checkpoint;
- `contextLength`: configurável, limitado pelo orçamento do aparelho;
- `embeddingSize`: múltiplo de `headCount`, com dimensão por cabeça par para RoPE;
- `headCount`: configurável;
- `feedForwardSize`: configurável;
- `depth`: número de blocos Transformer;
- `seed`: seed de inicialização reproduzível.

O padrão inicial deve ser **2 blocos**. Profundidades 1, 2, 4 e 8 podem ser usadas em testes/configurações controladas; 4 e 8 não serão promovidas ao aparelho sem estimativa, profiling e margem térmica/memória.

### 3.1 Tensores

Compartilhados:
- tabela de embeddings de tokens;
- projeção de saída para o vocabulário;
- bias de saída;
- LayerNorm final da pilha.

Por bloco `l`:
- `Wq[l]`, `Wk[l]`, `Wv[l]`, `Wo[l]`;
- `gammaAttention[l]`, `betaAttention[l]`;
- `gammaFeedForward[l]`, `betaFeedForward[l]`;
- `Wgate[l]`, `Wup[l]`, `Wdown[l]`.

AdamW mantém primeiro e segundo momentos para cada tensor, inclusive por bloco. O fingerprint e o checkpoint percorrem deterministicamente os tensores na ordem da configuração.

### 3.2 Forward por sequência

Para cada token `t`, começa-se com `x[0,t] = embedding(token[t])`.

Para cada bloco `l`:
1. `n1 = LayerNorm(x[l,t])`.
2. Projetar Q, K e V de todos os tokens para o espaço de embedding.
3. Aplicar RoPE em Q e K usando a posição absoluta do token.
4. Para cada posição de consulta `t`, calcular atenção causal apenas para chaves `j <= t`; usar softmax estável por cabeça.
5. Concatenar cabeças, projetar com `Wo[l]` e aplicar residual: `r[l,t] = x[l,t] + attention[l,t]`.
6. Aplicar segunda Pre-LayerNorm.
7. Projetar Gate e Up, calcular `SiLU(gate) * up`, projetar Down.
8. Aplicar o segundo residual: `x[l+1,t] = r[l,t] + ffn[l,t]`.

Após o último bloco, aplicar LayerNorm final na posição consultada e projeção para logits do vocabulário. A loss continua sendo next-token cross-entropy. A rotina de treino supervisionado pode conservar o contrato atual de prompt + completion, calculando cada target token com a janela de contexto correspondente.

## 4. Retropropagação correta por todas as camadas

A implementação deve armazenar ativações necessárias por camada e por posição durante o forward de treino. O backward começa na loss do último token:

1. gradiente da projeção de vocabulário e do LayerNorm final;
2. percorrer blocos em ordem reversa;
3. para cada bloco, retropropagar o residual da MLP, derivada SiLU, Gate/Up/Down e LayerNorm;
4. retropropagar o residual da atenção, projeção `Wo`, softmax causal e Q/K/V;
5. acumular contribuições de chave/valor de todas as posições de consulta que dependem de cada token;
6. aplicar RoPE inverso nas projeções Q/K e derivar os pesos de projeção;
7. acumular gradientes de embedding por token;
8. aplicar clipping global e AdamW sobre todos os grupos de todos os blocos.

Não reutilizar a fórmula de gradiente KN-003 sem ampliar a dimensão de posições/camadas. O fato de a loss supervisionar o próximo token não significa que os estados anteriores recebam gradiente zero: as K/V anteriores influenciam a representação final.

### Critério matemático obrigatório

O verificador numérico deve conferir coordenadas não nulas e nulas em **cada grupo e em cada camada**, não apenas um total agregado. O relatório precisa identificar `layerIndex`, `groupName`, quantidade de coordenadas, erro absoluto e erro relativo.

## 5. KV cache multi-camada

A sessão incremental deve manter, para cada bloco:
- cache de keys já rotacionadas;
- cache de values;
- contagem/posição absoluta;
- janela efetiva de contexto.

Ao receber um novo token:
1. obter o embedding;
2. passar pela camada 0, consultando apenas o cache daquela camada;
3. guardar K/V novos da camada 0;
4. encaminhar a nova representação para a camada 1 e repetir;
5. produzir logits após o último bloco;
6. avançar a posição absoluta e remover entradas antigas de todas as camadas quando a janela deslizar.

Os caches precisam ter o mesmo número de posições e o mesmo limite em todas as camadas. O cache de uma camada jamais pode ser reutilizado na outra.

### Equivalência obrigatória

Para seeds e pesos fixos, a previsão incremental com KV cache deve coincidir com a referência recalculada sobre o prefixo dentro da tolerância numérica definida. Cobrir:
- primeiro token e prefill;
- continuação sem eviction;
- janela exatamente cheia;
- múltiplas evictions;
- checkpoint restaurado;
- profundidades 1, 2 e 4.

## 6. Checkpoints e compatibilidade

KN-004 terá formato próprio, por exemplo `KVN4`, com:
- versão de formato;
- configuração completa, incluindo depth;
- tokenizer associado;
- pesos e momentos por camada;
- contadores de treino/optimizer;
- manifesto de proveniência e avaliação;
- SHA-256 do payload e limites de tamanho.

Regras:
- leitor KN-003 não muda;
- bundle KN-003 continua abrindo;
- KN-004 só abre se contagens, shapes, ordem de tensores, checksum, valores finitos e manifesto forem válidos;
- o store usa arquivo, temporário, backup e danificado próprios para KN-004;
- uma restauração KN-004 não substitui nem apaga automaticamente o KN-003.

## 7. Estimativa de capacidade e limites do Poco F6

Fórmula de parâmetros a validar contra os arrays reais:

```
P = 2 * V * D
  + depth * (4 * D * D + 4 * D + 3 * D * F)
  + 2 * D + V
```

Onde:
- `V` é o vocabulário;
- `D` é embedding size;
- `F` é feed-forward size;
- `depth` é o número de blocos.

Além de pesos e estados AdamW, reportar:
- caches KV: `2 * depth * contextLength * embeddingSize * sizeof(Float)`;
- estimativa de ativações de treino por camada/posição;
- gradientes temporários;
- buffers de softmax/atenção;
- tokenizer e corpus;
- overhead separado que a fórmula não cobre.

O estimador é um lower bound, nunca um “cabe no Poco” conclusivo. O gate físico mede RSS/PSS/heap, pico durante treino e inferência, latência, tokens/s, temperatura e bateria no Poco F6. Executar um treino pesado de cada vez; cancelar e liberar memória em pressão térmica ou de heap.

## 8. Plano de implementação em etapas

### Etapa 0 — congelar a linha de base
- registrar hashes dos arquivos KN-003, pipeline, bundle, store, runtime e testes;
- guardar o diff do trabalho do Android Studio;
- confirmar a branch e o commit-base;
- não tocar em UI, MainActivity ou arquivos protegidos.

### Etapa 1 — estrutura e forward KN-004
- adicionar Config multi-bloco e estruturas de parâmetros por camada;
- criar parâmetros deterministicamente com seeds;
- implementar forward full-sequence para todas as posições;
- validar shapes, causalidade (token futuro não pode influenciar token passado) e finitude.

### Etapa 2 — backward multi-bloco
- implementar reverse-mode para todas as camadas e posições;
- testes de finite differences por cada grupo/camada;
- testar vários seeds, head counts e profundidades;
- verificar atualização de todos os grupos e ausência de NaN/Inf.

### Etapa 3 — optimizer e checkpoint
- AdamW com momentos por tensor/layer;
- round-trip de checkpoint, fingerprint e compatibilidade KN-003;
- corrupção, truncamento, dimensões inválidas, bytes extras e limites.

### Etapa 4 — KV cache
- cache independente por camada;
- equivalência full-prefix vs incremental;
- janela deslizante, posição absoluta, cancelamento e restauração.

### Etapa 5 — pipeline de treinamento
- manifest KN-004 com depth/config completa;
- mesmos gates de origem/licença/consentimento e splits;
- Quality Gate mede loss de validação, held-out test e gap;
- dataset pequeno de mecânica serve só para testar treino, nunca para declarar competência geral.

### Etapa 6 — integração isolada
- adaptador de inferência KN-004 separado ou interface comum cuidadosamente testada;
- usar protocolo KELLVYN/1 estrito;
- não ativar `isReady()`;
- benchmark comportamental independente antes de qualquer integração do chat.

## 9. Matriz mínima de testes

| Área | Casos obrigatórios |
|---|---|
| Configuração | depth 1/2/4/8; dimensões inválidas; context e head constraints |
| Forward | shapes; finitude; causalidade; determinismo |
| Atenção | máscara causal; softmax estável; várias cabeças |
| Backward | finite differences por grupo e camada; gradientes zero/não zero |
| Treino | loss diminui em fixture pequeno; todas as camadas atualizam; cancelamento |
| Optimizer | AdamW determinístico; clipping global; checkpoint de momentos |
| KV cache | prefill; incremento; todas as camadas; eviction; equivalência |
| Checkpoint | round-trip; checksum; versão; truncamento; tamanho; compatibilidade KN-003 |
| Pipeline | provenance; TRAIN/VALIDATION/TEST sem vazamento; corpus limites |
| Recursos | estimator vs arrays reais; orçamento; profiling físico posteriormente |

## 10. Gates para não declarar conclusão falsa

- **Implementado:** classes e testes escritos.
- **Compilado:** Gradle/Kotlin compilou o commit exato.
- **Testado:** JUnit passou no commit exato.
- **Integrado:** pipeline, store e adaptador usam o KN-004 sem fallback externo.
- **Benchmark elegível:** checkpoint satisfaz Quality Gate e protocolo.
- **Comportamento comprovado:** benchmark comportamental fixo e independente passa.
- **Android validado:** instrumentado/emulador passa.
- **Poco F6 validado:** executado fisicamente com medições.

Enquanto runner/Gradle não executar, classificar a validação como NOT_RUN/BLOCKED. Nunca ligar o chat por conveniência e nunca declarar que KN-004 é IA geral apenas porque a loss diminuiu.

## 11. Regras de preservação

- Não copiar nem importar pesos/checkpoints/adapters de modelos externos.
- Não restaurar Gemma nem substituir o KN por modelo hospedado.
- Não criar agentes, subcérebros ou um novo orquestrador cognitivo.
- Ferramentas continuam adaptadores sob a autoridade do KN central.
- Não alterar interface, MainActivity, menus, efeitos, animações ou hash de proteção.
- Não mesclar à branch principal nem à branch ativa do Android Studio sem revisão e autorização explícita.
