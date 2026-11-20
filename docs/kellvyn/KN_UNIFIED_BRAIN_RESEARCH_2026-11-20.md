# KN — pesquisa e desenho do cérebro neural único multimodal

**Data:** 20/11/2026  
**Branch de pesquisa:** `kellvyn-rnd/reasoning-alternatives-20261119`  
**Estado:** pesquisa técnica e decisões para a próxima etapa; não representa capacidade já entregue.

## 1. Decisão de arquitetura que governa este trabalho

A Kellvyn terá **um único modelo neural central**, original, treinado a partir de inicialização própria. O mesmo modelo deve receber contexto, raciocinar, decidir e produzir saídas para todas as tarefas.

Não haverá modelos de linguagem independentes para programação, conversa, visão, áudio ou vídeo. A arquitetura-alvo é um único Transformer autoregressivo multimodal, com:
- um único conjunto central de blocos Transformer e estado de contexto;
- um vocabulário compartilhado de tokens de controle e de texto, acrescido de tokens de modalidades;
- uma única função de previsão autoregressiva para aprender sequências multimodais;
- tokenizadores/decodificadores de mídia como transdutores de representação, não como inteligências que tomam decisões;
- memória persistente como armazenamento de dados recuperáveis, sem uma segunda rede de raciocínio.

Os transdutores de mídia também precisam ser desenvolvidos e treinados no projeto, sem importar pesos externos. Eles convertem pixels, amostras de áudio e quadros de vídeo em representações que o modelo central entende e, no sentido inverso, convertem as sequências geradas em mídia reproduzível.

## 2. O que o repositório realmente contém hoje

### KN-003 — estado verificado no código desta branch

- Decoder-only Transformer de **um único bloco**.
- Atenção causal multi-head, RoPE, Pre-LayerNorm, residuals e SwiGLU.
- Cache KV incremental para esse único bloco.
- Tokenizer BPE byte-fallback próprio.
- Treino autoregressivo com AdamW e atualização manual de gradientes.
- Checkpoint próprio com SHA-256 e estado do otimizador.
- Pipeline com partições TRAIN, VALIDATION e TEST e registro de proveniência.
- Adaptador de inferência experimental; o caminho de conversa principal ainda não usa o modelo.

Configuração-padrão documentada no código: vocabulário 512, contexto 256, embedding 32, 4 cabeças e feed-forward 64. Para essa configuração, o modelo tem aproximadamente **43.648 parâmetros**. Isso é um protótipo de mecânica neural; não é um assistente geral nem uma base multimodal pronta.

### Bloqueios estruturais identificados

1. **Profundidade fixa em um bloco.** O conjunto de parâmetros, o forward, a retropropagação e o cache foram modelados especificamente para um bloco. A própria documentação da classe declara que múltiplos blocos e gradientes/cache por camada ainda são uma etapa futura.
2. **Treino limitado por posição final.** O caminho de treino calcula a perda para o próximo token no fim do contexto; ainda não é um treino em lote com perda paralela sobre todas as posições.
3. **Vocabulário e janela pequenos.** 512 tokens e contexto 256 são limites de protótipo, não parâmetros adequados para um assistente robusto.
4. **Ausência de corpus amplo e permitido.** Não foi verificado um corpus independente, amplo e documentado suficiente para pré-treino geral.
5. **Sem competência demonstrada.** Não há checkpoint conversacional aprovado, avaliação ampla de raciocínio, teste multimodal, APK validado ou benchmark físico concluído no Poco F6.

**Consequência:** não conectar este checkpoint ao chat nem chamar a experiência de “cérebro pronto”. A próxima etapa é uma evolução arquitetural testável, não apenas aumentar dimensões na configuração.

## 3. Arquitetura recomendada para a próxima geração KN

### 3.1 Núcleo textual denso e empilhado

A primeira arquitetura profunda deverá ser um decoder-only Transformer denso, causal e com pesos próprios. O objetivo imediato é implementar profundidade real e provar a retropropagação em todas as camadas antes de aumentar a largura.

Estrutura proposta:
1. Embedding de tokens.
2. Sequência configurável de blocos Transformer.
3. Em cada bloco: normalização prévia, atenção causal, conexão residual, segunda normalização prévia, MLP SwiGLU e conexão residual.
4. Normalização final.
5. Projeção para o vocabulário.
6. Predição do próximo token.

**Decisão de engenharia:** começar com 2 blocos em configuração reduzida para validar o algoritmo; depois testar 4 e 8 blocos. Não assumir que mais camadas são melhores até comparar perda de validação, estabilidade dos gradientes, parâmetros, latência e memória.

O formato de checkpoint precisa registrar explicitamente número de camadas, dimensões, configuração de atenção, versão do tokenizer, versão do modelo, passos do otimizador e proveniência. Checkpoints KN-003 não podem ser interpretados silenciosamente como checkpoints da nova arquitetura.

### 3.2 Atenção e memória de inferência

Manter atenção causal exata como referência inicial. Antes de alterar a atenção, medir:
- custo do prefill em diferentes tamanhos de contexto;
- tempo por token gerado;
- pico de RAM do KV cache;
- equivalência entre inferência incremental e inferência do prefixo completo;
- qualidade em dependências curtas e longas.

A pesquisa de Grouped-Query Attention mostra que agrupar cabeças de consulta em menos cabeças de chave/valor pode reduzir o custo de memória e acelerar a inferência, mantendo qualidade próxima à atenção multi-head em seus experimentos. Para a Kellvyn, GQA é uma otimização a comparar depois que a implementação multi-head profunda estiver correta; não é um atalho para pular a validação.

FlashAttention reduz movimentação de dados usando tiling e consciência da hierarquia de memória de GPU. A técnica é relevante como referência de otimização, mas não deve ser copiada cegamente para o caminho Kotlin/CPU do Android: a implementação móvel precisa ser escolhida por benchmark no hardware real.

### 3.3 Treinamento que a nova arquitetura precisa suportar

Antes de ampliar o corpus, o treino deve evoluir para:
- minibatches e acumulação de gradientes;
- perda de próximo token em múltiplas posições válidas;
- máscara correta para padding e fronteiras de documentos;
- embaralhamento reprodutível por época;
- warm-up e decaimento da taxa de aprendizado;
- clipping global de gradiente e monitoramento de valores não finitos;
- validação e teste nunca usados para atualizar pesos;
- checkpoints atômicos com recuperação do otimizador;
- métricas de perda, perplexidade, tokens/s e memória.

O estudo Chinchilla demonstra que tamanho do modelo e quantidade de tokens de treino precisam crescer juntos sob orçamento de computação; modelos maiores com dados insuficientes podem ficar subtreinados. A regra empírica daquele estudo não deve ser transplantada literalmente para um pequeno experimento móvel, mas a conclusão importa: aumentar parâmetros sem aumentar dados de qualidade não resolve a limitação do KN.

## 4. Como texto, imagem, áudio e vídeo cabem no mesmo cérebro

### 4.1 Representação unificada

A referência mais importante encontrada para essa direção é Emu3, publicado na Nature em 28 de janeiro de 2026. O trabalho descreve uma arquitetura decoder-only treinada do zero com previsão do próximo token para sequências intercaladas de texto, imagem e vídeo. Texto e mídia são transformados em tokens discretos; o mesmo Transformer central modela a sequência completa.

Essa evidência demonstra que a unificação multimodal num único Transformer é uma direção real de pesquisa, não apenas uma ideia abstrata. **Não** demonstra que um modelo de 43 mil parâmetros possa reproduzir os resultados: o Emu3 depende de grande escala de dados, tokens, parâmetros e computação.

O formato de sequência da Kellvyn deverá permitir:
- tokens de início/fim de modalidade;
- texto e metadados;
- tokens visuais discretos;
- tokens acústicos/speech;
- marcadores temporais, quadros e duração;
- intercalamento de texto e mídia numa mesma conversa.

### 4.2 Imagem

Fluxo desejado:
1. O codificador visual próprio converte a imagem em uma sequência compacta de tokens visuais.
2. Esses tokens entram no mesmo fluxo de contexto do texto.
3. O Transformer central interpreta a imagem em relação à pergunta e ao histórico.
4. Para criar/editar uma imagem, o mesmo Transformer gera uma sequência visual condicionada por texto e contexto.
5. Um decodificador visual próprio transforma tokens em pixels.

O estudo de Nano Banana/Gemini deve observar especialmente edição conversacional, manutenção de consistência de objetos/personagens, combinação de referências e compreensão visual ligada ao contexto. Os detalhes internos e pesos da família Nano Banana são proprietários; só capacidades divulgadas e resultados observáveis podem ser estudados, nunca supostos como arquitetura pública.

### 4.3 Áudio e voz

Fluxo desejado:
1. O áudio recebido é convertido por um codec/tokenizador acústico próprio em tokens com marcadores de tempo e, quando necessário, de locutor.
2. Os tokens são colocados no contexto compartilhado.
3. O Transformer central compreende fala, conteúdo não verbal, intenção e relação com o histórico.
4. Para responder por voz, o mesmo modelo produz texto e/ou tokens acústicos condicionados.
5. O decodificador acústico próprio reconstrói o áudio.

Capacidades a avaliar: reconhecimento de fala, transcrição, compreensão de eventos sonoros, tradução falada, fala em streaming, prosódia, pausas, entonação e geração de voz. A documentação pública de voz em tempo real da OpenAI descreve a opção de uma única sessão/modelo para interpretar áudio, raciocinar e responder em áudio; isso é uma referência de comportamento e produto, não divulgação da implementação interna do modelo.

### 4.4 Vídeo

Vídeo pode ser representado como sequência temporal de quadros visuais compactados:
- marcador de vídeo;
- timestamp e taxa de quadros;
- tokens visuais do quadro;
- marcador do quadro seguinte;
- texto/áudio intercalados quando disponíveis.

O modelo central precisa aprender relações temporais, continuidade, causa e efeito e previsão do próximo estado visual. Para geração, ele emite uma sequência de quadros e metadados; o decodificador reconstrói os quadros e o fluxo final.

O custo é alto: sem compressão temporal/espacial, um vídeo vira uma quantidade enorme de tokens. Logo, o tokenizador de vídeo e o orçamento de contexto são parte central do projeto, não detalhes posteriores.

### 4.5 Por que isso ainda é um plano, não uma função pronta

Os componentes acima exigem datasets autorizados, tokenizadores próprios, perdas e testes próprios. O KN-003 atual não tem entrada visual, vocabulário acústico, codec visual, decodificador de mídia nem treinamento multimodal. Não anunciar compreensão ou geração de imagem, áudio ou vídeo até que esses caminhos estejam implementados e medidos.

## 5. Memória e raciocínio no mesmo núcleo

Memória de conversa não exige outro modelo de linguagem. A arquitetura pode combinar:
- janela de contexto e KV cache para contexto imediato;
- armazenamento persistente local para fatos e eventos autorizados;
- busca textual/semântica para recuperar trechos relevantes;
- política de atualização, esquecimento e correção de memórias;
- marcadores de proveniência e data para evitar confundir fato antigo com atual.

O trabalho Titans, publicado nos anais NeurIPS 2025, explora memória neural de longo prazo que aprende a memorizar informação durante o uso e complementa a atenção de contexto curto. É uma referência promissora para estudo posterior, mas aumenta bastante a complexidade de treino e inferência. A primeira versão da Kellvyn deve provar antes uma memória externa local, verificável e recuperável, com o Transformer central como único sistema de interpretação e decisão.

Raciocínio precisa ser treinado e avaliado como comportamento do próprio modelo: exemplos com passos verificáveis, problemas matemáticos, explicações, comparação de alternativas, revisão de respostas e capacidade de admitir incerteza. Um formato de saída estruturado pode facilitar a execução segura, mas não cria raciocínio por si só.

## 6. O que aprender com as referências solicitadas

| Referência | Capacidade observável a estudar | Tradução para a Kellvyn |
|---|---|---|
| Android Studio AI / Gemini no Android Studio | compreender projeto Android, navegar arquivos, explicar erro, compilar, interpretar logs e validar no dispositivo | ensinar o modelo central com exemplos de Kotlin/Java/Gradle e resultados reais de build/teste |
| ChatGPT / Codex | raciocínio geral, código, uso de ferramentas e produção de entregas | treinamento multitarefa e avaliação de resposta + artefato executável |
| Claude / Claude Code | análise de repositório, mudanças coerentes em vários arquivos, contexto persistente e revisão | exemplos longos de edição, diffs, testes e correções verificáveis |
| GitHub Copilot e Cursor | completar/explicar código e manter contexto de projeto | domínio de código, busca de contexto e previsão de mudanças |
| Replit | transformar especificação em aplicação funcional, iterar e testar | treino em trajetórias completas: requisito, código, execução, erro, correção, teste |
| Lovable | converter intenção em interface e aplicação utilizável | domínio de especificação visual e correspondência entre requisito, componente e estado |
| Gemini / Nano Banana | compreensão visual, edição conversacional e consistência de sujeito | representação visual compacta e dados de edição antes/depois |
| Dola | assistência cotidiana, contexto de tarefas, lembretes e organização | tarefas temporais e memória contextual; confirmar qual produto/versão Dola foi mencionado antes de inferir mecanismos |
| Modelos de voz (ex.: Whisper, Gemini Audio/TTS, ElevenLabs) | percepção de fala, transcrição, alinhamento temporal e fala expressiva | codec acústico próprio e tokens compartilhados com o modelo central |
| Modelos de vídeo (ex.: Veo, Sora, Runway, Kling, Luma) | continuidade temporal, condicionamento textual, movimento e edição de cenas | tokenizador temporal próprio e sequência visual autoregressiva |

Produtos proprietários são referências comportamentais e de qualidade. Não se presume acesso à arquitetura interna, pesos, dados, prompts privados ou implementação proprietária.

## 7. Android e execução no Poco F6

A documentação atual do Google AI Edge descreve LiteRT como runtime de inferência local em dispositivos e apresenta a API CompiledModel do LiteRT 2.x para aceleração de hardware. LiteRT-LM documenta recursos como streaming conversacional e KV cache dinâmico. Isso torna a família LiteRT importante para avaliar implantação nativa; **não** autoriza usar pesos de Gemma ou qualquer outro modelo como cérebro.

A escolha do runtime deve vir depois de:
1. estabilidade numérica e testes do modelo próprio;
2. formato de pesos documentado;
3. conversão sem perda inesperada e comparação de logits;
4. benchmark CPU como baseline;
5. comparação GPU/NPU somente se o backend do dispositivo for comprovado;
6. medição de RAM, prefill, tokens/s, temperatura e bateria.

O runtime executa operações matemáticas do único modelo; não decide a resposta nem fornece uma inteligência substituta.

## 8. Limite de computação — sem maquiagem

Treinar do zero um modelo geral multimodal no nível dos serviços de fronteira exige quantidade de dados e computação muito além do que um telefone consegue fornecer. O Poco F6 pode ser o dispositivo-alvo de inferência e pode executar experimentos pequenos de treino; não é realista prometer pré-treinar nele, sozinho, um modelo geral competitivo com ChatGPT, Claude ou Gemini.

Como o projeto exige pesos próprios e operação final offline, o caminho honesto é:
1. validar profundamente a arquitetura e o pipeline;
2. obter dados próprios/licenciados suficientes e medir a escala possível;
3. treinar o maior modelo que o orçamento real permitir, sem alegar capacidades não medidas;
4. otimizar e validar a inferência local;
5. ampliar capacidade multimodal gradualmente, mantendo sempre um único modelo central.

Sem computação e dados de pré-treino em escala, um modelo autoral pequeno continuará limitado, independentemente de quantos recursos externos o aplicativo possua.

## 9. Próxima sequência de engenharia

1. Fechar a bateria numérica de KN-003: gradientes por diferenças finitas, determinismo, checkpoint e equivalência de cache.
2. Projetar KN-004 como Transformer de profundidade configurável; começar com dois blocos e testes de forward/backward em cada camada.
3. Evoluir o treino para minibatch e perda em múltiplas posições; manter os três conjuntos de dados estritamente separados.
4. Versionar tokenizer e checkpoint sem quebrar leitura do KN-003.
5. Comparar 2/4/8 blocos, largura, contexto, custo, qualidade e RAM.
6. Só então iniciar tokenização visual e acústica próprias para integrar no mesmo fluxo autoregressivo.
7. Conectar o modelo ao chat somente depois de passar os gates de inferência, segurança e qualidade.

## 10. Fontes primárias consultadas

1. **Emu3 — Multimodal learning with next-token prediction for large multimodal models**, Nature, publicado em 28/01/2026: https://www.nature.com/articles/s41586-025-10041-x
2. **Titans: Learning to Memorize at Test Time**, NeurIPS 2025: https://proceedings.neurips.cc/paper_files/paper/2025/hash/a4ca07aa108036f80cbb5b82285fd4b1-Abstract-Conference.html
3. **An empirical analysis of compute-optimal large language model training (Chinchilla)**, NeurIPS 2022: https://proceedings.neurips.cc/paper_files/paper/2022/hash/c1e2faff6f588870935f114ebe04a3e5-Abstract.html
4. **GQA: Training Generalized Multi-Query Transformer Models**, EMNLP 2023: https://aclanthology.org/2023.emnlp-main.298/
5. **FlashAttention: Fast and Memory-Efficient Exact Attention with IO-Awareness**, NeurIPS 2022: https://proceedings.neurips.cc/paper/2022/hash/67d57c32e20fd0a7a302cb81d36e40d5-Abstract.html
6. **RoFormer: Rotary Position Embedding**: https://arxiv.org/abs/2104.09864
7. **LiteRT overview**, documentação Google AI Edge: https://developers.google.com/edge/litert/overview
8. **Android Studio Gemini Agent Mode**, documentação Android Developers: https://developer.android.com/studio/gemini/agent-mode
9. **OpenAI Voice Agents**, documentação pública: https://developers.openai.com/api/docs/guides/voice-agents
10. **Nano Banana / Gemini Image**, Google DeepMind: https://deepmind.google/models/gemini-image/
11. **Replit Agent**, documentação oficial: https://docs.replit.com/pt/references/agent/overview

---
**Estado final desta pesquisa:** o caminho mais consistente com a exigência de um único cérebro é um decoder-only Transformer denso, profundo e multimodal, treinado por previsão do próximo token sobre sequências unificadas. O KN-003 é a prova inicial de mecânica; a prioridade de engenharia agora é construir e validar profundidade real e treino eficiente, não conectar o protótipo atual ao chat nem simular capacidades multimodais.
