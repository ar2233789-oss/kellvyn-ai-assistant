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
