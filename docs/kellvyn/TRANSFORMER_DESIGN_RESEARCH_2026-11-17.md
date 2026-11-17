# Kellvyn — pesquisa técnica para a próxima arquitetura neural

**Data:** 17/11/2026  
**Objetivo:** sair do KBN-002 alpha de uma cabeça e construir um decoder-only treinável, preservando pesos próprios e execução local primeiro.

## Decisões de arquitetura

### 1. Bloco decoder-only
A arquitetura deve ser autoregressiva: a posição atual só pode atender tokens anteriores ou o token atual. A máscara causal não é opcional; sem ela, o treino vaza o token-alvo para o próprio contexto. O paper original do Transformer descreve atenção mascarada no decoder, múltiplas cabeças, residuals, LayerNorm e FFN position-wise.

Fonte: https://arxiv.org/abs/1706.03762

### 2. Multi-head causal attention
Dividir a dimensão do modelo em várias cabeças menores permite que cada cabeça aprenda projeções/relacionamentos diferentes. Primeira configuração experimental conservadora para o Poco F6: dimensão pequena (p.ex. 32 ou 64), 2–4 cabeças e um bloco por vez; o número final deve ser definido por gradientes, perda e benchmark no Android, não por imitação de configuração de LLM grande.

### 3. Pre-LayerNorm
Para a primeira versão treinável do novo bloco, usar Pre-LN: normalizar antes de atenção e MLP, mantendo a conexão residual por fora. A pesquisa de Xiong et al. compara Pre-LN e Post-LN e relata gradientes mais comportados na inicialização para Pre-LN, reduzindo a dependência de warm-up no contexto estudado. Isto é uma decisão de estabilidade inicial, não garantia universal.

Fonte: https://arxiv.org/abs/2002.04745

### 4. MLP gated / SwiGLU
A MLP deve ser position-wise e testável de forma independente. SwiGLU é uma candidata porque o estudo de Shazeer comparou variantes GLU em FFN de Transformer e encontrou resultados melhores que ReLU/GELU em sua configuração experimental, mantendo orçamento de parâmetros/operações ao ajustar o tamanho interno. Deve ser comparada contra GELU em um teste pequeno; não presumir que vence em todo corpus.

Fonte: https://arxiv.org/abs/2002.05202

### 5. Codificação posicional
RoPE é candidata para a arquitetura KBN-003: aplica rotação a pares de dimensões de Q/K e representa posição relativa na atenção. Não é magia para contexto infinito; extrapolação de comprimento exige avaliação própria. A primeira versão pode começar com RoPE em contexto curto e testes manuais de posição antes de ampliar a janela.

Fonte: https://arxiv.org/abs/2104.09864

### 6. Execução Android e memória
A documentação Android do llama.cpp demonstra uma integração nativa Android por NDK/arm64, leitura de arquivos em armazenamento privado, prefill/batch decode e entrega incremental por Kotlin Flow. Também alerta que o tamanho do contexto pode aumentar a RAM e recomenda iniciar com um contexto moderado. Esses padrões podem orientar o runtime; não se importar pesos, tokenizer, prompts, nem tornar o llama.cpp o cérebro da Kellvyn.

Fonte: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md

## Plano de implementação em código

1. Criar `KellvynTransformerLanguageModel` sem remover o KBN-002 alpha atual.
2. Implementar e testar isoladamente operações numéricas: LayerNorm forward/backward, RoPE, softmax estável, projeções e SwiGLU.
3. Implementar um bloco Pre-LN com causal multi-head attention, residuals, MLP gated e cabeça de próximo token.
4. Implementar backpropagation manual e comparar gradientes analíticos contra diferenças finitas em coordenadas selecionadas.
5. Testar overfit de padrões sintéticos, queda de loss de completion e checkpoint determinístico.
6. Comparar GELU/SwiGLU e 1/2/4 cabeças com contagem de parâmetros equivalente.
7. Depois dos testes Kotlin/Gradle, conectar a arquitetura ao pipeline local de treino e serialização.
8. Medir no AVD e depois no Poco F6 real: pico de RAM, tempo de prefill, tokens/s, aquecimento e consumo de bateria.

## Guardrails

- Não baixar ou incorporar pesos pré-treinados.
- Não declarar que um modelo pequeno equivale a ChatGPT/Claude.
- Não integrar ao chat enquanto geração, treinamento, protocolo e cancelamento não passarem em testes.
- Não ampliar contexto só aumentando um número: calcular custo de KV cache e implementar inferência incremental antes.
- Preservar a UI aprovada; o trabalho deste documento é exclusivamente arquitetura, treinamento e runtime neural.
