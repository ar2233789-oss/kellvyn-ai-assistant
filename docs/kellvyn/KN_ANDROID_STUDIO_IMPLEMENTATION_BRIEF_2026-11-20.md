# KN — Pacote de implementação para Android Studio
Data: 2026-11-20
Branch de trabalho: `kellvyn-rnd/reasoning-alternatives-20261119`

Este documento é a instrução de execução para a IA de programação aberta no Android Studio. O objetivo é acelerar a implementação do cérebro KN sem sacrificar correção matemática, compatibilidade, testes ou os limites do produto.

## 1. Missão

Evoluir o modelo neural próprio KN-003 (Transformer decoder-only experimental de um bloco) para a próxima versão funcional, KN-004, com profundidade configurável e **um único Transformer central**, treinável de ponta a ponta.

O foco imediato é o núcleo textual treinável e verificável. Não declarar o cérebro concluído, não ativar respostas no chat principal e não iniciar multimodalidade antes de cumprir os critérios de aceitação abaixo.

## 2. Restrições que não podem ser violadas

1. Uma única rede neural central toma as decisões e aprende. Todas as camadas são blocos da mesma rede, conectados por ativações e treinados por uma única retropropagação de ponta a ponta. Não criar modelos independentes por camada, especialistas, roteadores de inteligências ou coordenadores de modelos.
2. Pesos, tokenizer e treinamento devem ser próprios do projeto. Não baixar, incorporar ou iniciar pesos/checkpoints/adapters/LoRAs de Gemma, Llama, Qwen ou qualquer outro modelo pré-treinado. Referências externas podem informar algoritmos, nunca fornecer o cérebro ou seus pesos.
3. Execução final local e offline-first no Android. Internet só pode ser usada quando o usuário habilitar. Não adicionar API paga, tokens, backend hospedado ou dependência de nuvem para inferência.
4. Não alterar interface, layout Lovable, animações, cores, navegação, telas, botões ou recursos visuais. Não tocar em arquivos de UI.
5. Preservar KN-003 e seus checkpoints existentes. Não reinterpretar bytes de checkpoint antigo como se fossem KN-004.
6. Não declarar teste aprovado sem executar o comando e observar o resultado. Não declarar teste físico no Poco F6 sem dispositivo conectado e execução real.
7. Não aumentar dimensões indiscriminadamente. Usar `KellvynTransformerCapacityPlanner` para comparar profundidade e memória estimada. A estimativa é apenas o núcleo FP32; deixar margem para ativações, JVM, runtime, aplicativo e sistema Android.

## 3. Inventário confirmado do estado atual

### Modelo
Arquivo:
`android/app/src/main/java/com/kellvyn/assistant/neural/KellvynTransformerLanguageModel.kt`

KN-003 é um decoder-only Transformer de um único bloco, com:
- embeddings de tokens;
- causal multi-head self-attention;
- RoPE;
- Pre-LayerNorm;
- caminhos residuais;
- SwiGLU;
- projeção de saída e softmax;
- cache KV incremental de uma camada;
- otimizador Adam com clipping;
- checkpoint SHA-256 versão 1.

Configuração experimental padrão:
- vocabulário: 512;
- contexto: 256;
- embedding: 32;
- cabeças: 4;
- feed-forward: 64.

A classe tem aproximadamente 1.481 linhas. A retropropagação manual em `computeGradients` deriva a perda de um único token-alvo na última posição. `trainTokensEpoch` e `trainCompletionEpoch` percorrem alvos e executam atualizações Adam individuais. A sessão de geração mantém um único cache K/V.

### Treinamento
Arquivo:
`android/app/src/main/java/com/kellvyn/assistant/neural/KellvynTransformerTrainingPipeline.kt`

O pipeline já separa TRAIN, VALIDATION e TEST, aprende o tokenizer somente no TRAIN, registra proveniência, verifica material secreto, possui limites experimentais e salva manifesto. Não remover essas salvaguardas.

### Qualidade e orçamento
Arquivos:
- `android/app/src/main/java/com/kellvyn/assistant/neural/KellvynTransformerTrainingQualityGate.kt`
- `android/app/src/main/java/com/kellvyn/assistant/neural/KellvynTransformerCapacityPlanner.kt`
- `android/app/src/test/java/com/kellvyn/assistant/neural/KellvynTransformerCapacityPlannerTest.kt`

O quality gate atual só determina elegibilidade para benchmark de protocolo com base em losses/proveniência; ele não prova competência conversacional. O capacity planner calcula parâmetros, pesos FP32, dois momentos Adam e cache KV FP32. A estimativa não inclui grafo de ativações ou overhead real do Android.

### Integração
Antes de alterar a integração, inspecione:
- `android/app/src/main/java/com/kellvyn/assistant/KellvynTransformerInferenceEngine.kt`
- `android/app/src/main/java/com/kellvyn/assistant/KellvynRuntime.kt`
- `android/app/src/main/java/com/kellvyn/assistant/KellvynDecisionProtocol.kt`

O runtime principal ainda não considera o modelo pronto. Manter essa proteção até passar os critérios.

## 4. Arquitetura requerida para KN-004

Implementar uma rede decoder-only de profundidade configurável. Começar com 2 blocos para provar o encadeamento correto. O parâmetro `layerCount` deve permitir experimentos controlados com 2, 4 e 8 blocos, respeitando limites de configuração e capacidade.

Fluxo de alto nível:
1. token IDs -> uma única tabela de embeddings compartilhada;
2. para cada bloco, em sequência:
   - LayerNorm pré-attention;
   - projeções Q/K/V próprias daquele bloco;
   - RoPE com posição absoluta;
   - atenção causal multi-head;
   - projeção de saída e conexão residual;
   - LayerNorm pré-MLP;
   - SwiGLU;
   - projeção de retorno e conexão residual;
3. cabeça de saída compartilhada -> logits do próximo token.

Cada bloco possui seus próprios parâmetros treináveis. As ativações de saída do bloco i são a entrada do bloco i+1. Não criar uma rede completa separada por bloco. O embedding de tokens e a cabeça de saída devem continuar únicos e compartilhados pela rede.

### Retropropagação obrigatória
Implementar backpropagation through all layers, na ordem inversa:
- gradiente da cabeça de saída para a última ativação;
- em cada bloco, propagar gradientes pelos dois caminhos residuais;
- derivar corretamente SwiGLU e suas três matrizes;
- derivar LayerNorm e seus parâmetros gamma/beta;
- derivar projeções de atenção Q/K/V/O;
- derivar softmax e scores de atenção;
- inverter a transformação RoPE no gradiente de Q/K;
- acumular o gradiente das ativações para o bloco anterior;
- acumular gradientes na tabela de embeddings compartilhada;
- atualizar todos os blocos e a cabeça de saída em uma única etapa Adam para cada objetivo de treinamento.

Não basta empilhar módulos no forward: todos os pesos de todos os blocos devem receber gradientes matematicamente corretos quando o objetivo depende deles.

### Cache de inferência
Substituir o cache único por uma estrutura indexada por camada:
- um K cache e um V cache por camada;
- cada token novo deve percorrer sequencialmente todos os blocos e alimentar o cache correspondente;
- RoPE deve usar posição absoluta mesmo quando a janela de contexto deslizar;
- manter limites de contexto e comportamento de janela deslizante;
- não reutilizar K/V de uma camada em outra.

### Checkpoints
Criar formato versionado para KN-004, armazenando:
- identificador de arquitetura e versão;
- configuração completa, incluindo layerCount;
- pesos de embedding, todos os parâmetros de cada bloco e cabeça de saída;
- momentos Adam e contadores;
- checksum SHA-256;
- limites de tamanho e validação de formas antes de alocar arrays.

A leitura de checkpoint KN-003 versão 1 deve continuar funcionando pelo caminho legado. Nunca interpretar checkpoint V1 como V2. Testar round-trip de KN-003 e KN-004 separadamente.

## 5. Sequência de implementação — não pular etapas

### Fase A — base arquitetural
1. Ler a classe completa, todos os testes de KN-003, pipeline, quality gate, capacity planner e pontos de integração.
2. Registrar plano curto com arquivos exatos e riscos de compatibilidade.
3. Refatorar o núcleo para suportar parâmetros por bloco sem duplicar modelos completos.
4. Implementar KN-004 com dois blocos primeiro.
5. Preservar todas as APIs necessárias ao pipeline e a compatibilidade KN-003, usando adaptador explícito se necessário.

### Fase B — provar matemática
Criar testes unitários pequenos e determinísticos:
- modelo de 2 blocos tem mais parâmetros do que 1 bloco e menos do que 4 blocos;
- gradientes finitos e verificação numérica por diferença central;
- pelo menos uma coordenada não nula por grupo de parâmetros em cada camada;
- garantir que gradientes chegam às camadas inferiores e superiores;
- testar caminhos residuais e dimensões;
- comparar logits de execução em lote/prefixo completo com inferência incremental via cache;
- ausência de NaN/Infinity;
- determinismo com a mesma seed;
- perda de treino diminui em um corpus minúsculo construído no teste;
- pesos e momentos Adam persistem corretamente no checkpoint;
- checkpoint V1 KN-003 continua carregando;
- checkpoint V2 KN-004 carrega e preserva fingerprint e contadores.

Não aceitar apenas teste que demonstra redução de loss: isso não valida comportamento nem a correção de todas as camadas.

### Fase C — treinamento
Depois de passar a Fase B:
1. adaptar pipeline e manifesto para registrar layerCount e arquitetura;
2. treinar objetivos de próximo token com avaliação separada em VALIDATION e TEST;
3. preservar separação de dados, proveniência, filtro de segredos e determinismo;
4. só então avaliar acumulação de gradientes/múltiplas posições e minibatches;
5. introduzir scheduler de learning rate somente com testes de determinismo e comparação justa.

Não misturar várias melhorias de treinamento ao mesmo tempo. Medir cada uma separadamente.

### Fase D — runtime e dispositivo
Somente após gates matemáticos e de treinamento:
1. avaliar integração no inference engine e no runtime atual;
2. adicionar verificação explícita de arquitetura/checkpoint;
3. medir latência, tokens/segundo, memória de processo, pico durante geração, aquecimento e estabilidade;
4. em dispositivo real, testar no Poco F6 quando conectado;
5. deixar runtime principal indisponível se checkpoint, tokenizer ou qualidade falharem.

Não afirmar que o Poco F6 pode pré-treinar um modelo geral de fronteira. O objetivo imediato é construir um modelo original pequeno, correto e expansível, e medir honestamente o que ele consegue fazer.

## 6. Tarefas que não fazem parte deste ciclo

- não criar nem introduzir outros modelos de decisão;
- não criar modelo especialista por tarefa;
- não adicionar orquestração de inteligências;
- não importar pesos externos nem destilar de um professor sem autorização específica;
- não iniciar implementação de áudio/imagem/vídeo nesta etapa;
- não adicionar APIs pagas, cloud ou tokens;
- não alterar UI, navegação, animações ou tema;
- não habilitar o runtime conversacional antes de os gates passarem.

Visão futura: um único Transformer central poderá consumir uma sequência unificada de tokens de texto e modalidades. Codificadores/decodificadores de mídia, quando existirem, serão transdutores de representação, sem raciocínio ou decisões independentes. Não implementar isso agora.

## 7. Testes e comandos de validação

Primeiro executar os testes neurais específicos e registrar o resultado real. Depois executar:

```bash
cd android
gradle testDebugUnitTest --no-daemon --stacktrace
gradle assembleDebug --no-daemon --stacktrace
```

Ou, se o wrapper estiver presente e configurado, usar `./gradlew`.

Se algum teste falhar:
- localizar a causa;
- corrigir a implementação, não remover ou enfraquecer o teste;
- repetir a execução;
- reportar logs resumidos e comando exato.

Se o build não puder iniciar por falta de SDK, JDK, wrapper ou dispositivo, reportar precisamente o bloqueio. Não registrar como sucesso nem como falha de compilação o que não chegou a compilar.

## 8. Arquivos a inspecionar e anexar como contexto

No Android Studio, anexar com `@` quando necessário:
- `KellvynTransformerLanguageModel.kt`
- `KellvynBpeTokenizer.kt`
- `KellvynTransformerTrainingPipeline.kt`
- `KellvynTransformerTrainingQualityGate.kt`
- `KellvynTransformerCapacityPlanner.kt`
- testes do modelo, gradientes, geração e checkpoint em `android/app/src/test/java/com/kellvyn/assistant/neural/`
- `KellvynTransformerInferenceEngine.kt`
- `KellvynRuntime.kt`

Também verificar `docs/kellvyn/KN_UNIFIED_BRAIN_RESEARCH_2026-11-20.md` para as decisões de arquitetura já registradas.

## 9. Entrega obrigatória da IA de programação

Ao terminar, apresentar:
1. lista de arquivos criados/alterados;
2. desenho real da arquitetura final e contagem de parâmetros por configuração;
3. explicação de como o gradiente percorre cada camada;
4. formato de checkpoint e compatibilidade V1/V2;
5. testes realmente executados, comandos, resultado e logs de falha se houver;
6. resultado de build APK, apenas se executado;
7. limitações conhecidas e próximos bloqueios;
8. confirmação explícita de que UI, pesos externos, serviços cloud e inteligências independentes não foram introduzidos.

Não responder apenas com um plano ou pseudocódigo. Implementar, testar, corrigir e apresentar diffs reais. Se o escopo inteiro não couber em uma execução, terminar a maior fase verificável e apontar exatamente o que ficou pendente, sem fingir conclusão.

---

## 10. Prompt curto para iniciar a sessão no Android Studio

Leia este documento inteiro e trate-o como especificação obrigatória do KN-004. Inspecione os arquivos existentes, confirme o estado real do código e execute as Fases A e B primeiro. Não pare em uma lista de sugestões: implemente no projeto aberto, escreva os testes e execute-os. Comece com dois blocos, mas deixe a profundidade configurável. Garanta uma única rede Transformer conectada de ponta a ponta, retropropagação real por todas as camadas, cache KV separado por camada, checkpoints versionados com leitura legada KN-003, e compatibilidade com o pipeline atual. Não altere interface/UI, não use pesos externos, não adicione APIs/cloud e não conecte o modelo ao chat até passar os gates. Use o capacity planner existente; não faça expansão arbitrária. Execute `gradle testDebugUnitTest --no-daemon --stacktrace` e `gradle assembleDebug --no-daemon --stacktrace` quando o ambiente permitir. Corrija falhas reais e devolva diffs, testes e resultados verificáveis.

## Referências oficiais de fluxo no Android Studio

- Agent Mode e alterações em vários arquivos, execução de build e verificação em dispositivo: https://developer.android.com/studio/gemini/agent-mode
- Recursos de IA, contexto persistente via AGENTS.md, busca web e interação com dispositivo: https://developer.android.com/studio/gemini/features
- Anexar arquivos com `@` na conversa: https://developer.android.com/studio/gemini/attach-file
