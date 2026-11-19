# Auditoria ampliada de pesquisa e construção do cérebro Kellvyn
Data: 19/11/2026
Repositório: ar2233789-oss/kellvyn-ai-assistant
Branch desta auditoria: kellvyn-rnd/reasoning-alternatives-20261119

## 1. Escopo e decisão atual

A prioridade desta etapa é exclusivamente o cérebro Kellvyn. A implementação de novos agentes e a expansão do laboratório de agentes ficam pausadas. Produtos de agentes podem ser estudados como referência, mas não serão construídos nesta etapa. A interface Android já aprovada permanece fora do escopo e protegida contra alterações.

Kellvyn deve ser uma única inteligência e uma única identidade para o usuário. A unidade da inteligência não significa que uma única rede neural precise executar diretamente todas as operações físicas e criativas. O cérebro próprio compreende o objetivo, planeja, escolhe capacidades, acompanha a execução, interpreta resultados e verifica se o objetivo foi cumprido. Ferramentas especializadas são extensões operacionais subordinadas à mesma Kellvyn, não personalidades nem provedores de conversa concorrentes.

## 2. Regras de originalidade e propriedade

- Tokenizer, arquitetura neural própria, inicialização, pesos aprendidos, treinamento, checkpoints, protocolo decisório e identidade cognitiva de Kellvyn pertencem ao projeto.
- Não utilizar Gemma.
- Não importar pesos pré-treinados, checkpoints, adapters ou LoRA de outros modelos para constituir ou substituir o cérebro.
- Não copiar código proprietário, prompts internos, pesos privados ou mecanismos não públicos de Claude, ChatGPT, Gemini, Replit ou qualquer outro produto.
- Estudar documentação pública, artigos, padrões e algoritmos conhecidos; implementar independentemente as ideias que forem adequadas.
- Bibliotecas auxiliares não neurais podem ser avaliadas somente após auditoria de licença, atribuição, segurança e compatibilidade com Android.
- Conectores e serviços de geração externos, caso sejam autorizados, serão ferramentas opcionais. Não serão o cérebro, não trocarão a identidade de Kellvyn e não poderão ser anunciados como disponíveis offline.
- Nenhum arquivo pessoal, conversa privada, credencial ou dado sem direitos claros poderá entrar em treinamento.
- Cada dependência, fonte de dados, checkpoint e artefato deve ter proveniência registrada.

## 3. Auditoria do cérebro que já existe

O repositório já contém uma fundação neural experimental própria. Isso é progresso real de engenharia, mas ainda não representa um assistente geral.

### KN-001
Primeiro preditor de bytes com embeddings treináveis, camada oculta, softmax, retropropagação, atualização de parâmetros e checkpoint próprio. É um experimento de previsão do próximo byte, não um assistente conversacional.

### KN-002
Modelo autoregressivo experimental de atenção, CPU-only, com tokenizer BPE byte-fallback. O adaptador local converte a saída em decisões estruturadas estritas. A própria documentação o classifica como protótipo e não como assistente geral.

### KN-003
Transformer decoder-only experimental de um único bloco, com atenção causal multi-head, RoPE, Pre-LayerNorm, conexões residuais, SwiGLU e cache KV incremental de um bloco. O tokenizer parte dos 256 bytes UTF-8 e aprende merges a partir do corpus autorizado.

Configuração padrão documentada:
- Vocabulário: 512 tokens.
- Contexto: 256 tokens.
- Embedding: 32 dimensões.
- Cabeças: 4.
- Feed-forward: 64.
- Profundidade: um bloco.
- Estimativa de parâmetros nessa configuração: 43.648.

Limites documentados da pipeline:
- Até 1.000 exemplos.
- Até 500.000 bytes de corpus.
- Até 100.000 passos planejados por chamada.
- Partições TRAIN, VALIDATION e TEST obrigatórias.
- Proveniência, licença, evidência de direitos e hash dos exemplos.

### Bloqueios reais encontrados
- KellvynRuntime.isReady() continua retornando false.
- O chat principal não está conectado ao KN-003.
- Não há bundle KN-003 conversacional treinado e aprovado no repositório.
- Não foi encontrada evidência de corpus autorizado de escala adequada nem de treinamento conversacional real concluído.
- O Quality Gate só dá elegibilidade para benchmark de protocolo; não demonstra conversa natural, raciocínio, programação, planejamento ou capacidade geral.
- Auditoria anterior registrou execução de CI sem runner alocado e sem steps. Gradle, JUnit e testes Android devem ser classificados como NOT RUN, não como aprovados nem como falhos.
- Não existem medições físicas suficientes no Poco F6 para latência, memória, bateria e comportamento térmico do cérebro.

Conclusão técnica: há protótipos neurais próprios e infraestrutura de treino/avaliação em código. Ainda falta transformá-los num modelo treinado com dados suficientes, comportamento medido e inferência integrada ao chat. Não ligar o runtime prematuramente e não aumentar dimensões apenas para produzir números maiores.

## 4. Pesquisa das referências e pontos fortes a aproveitar

### 4.1 Engenharia Android e programação

| Referência | Capacidades públicas a estudar | Requisitos para a Kellvyn |
|---|---|---|
| Android Studio + Gemini Agent Mode | Planejamento multi-etapas, navegação por projeto, edição de vários arquivos, Gradle, implantação em dispositivo, inspeção de tela, Logcat e correção iterativa | Ciclo Android fechado: plano, diff, build, instalação, captura/logs, correção e teste de regressão |
| Claude Code | Regras persistentes, skills, hooks, subagentes, permissões e continuidade de tarefas | Regras versionadas, procedimentos sob demanda, hooks determinísticos, política de ferramenta e recuperação de contexto |
| ChatGPT / Codex | Decisões estruturadas, uso de ferramentas, busca, execução, computer use e rastreamento | Protocolo tipado, ciclo ação-observação, evidência real, tracing e verificação |
| Cursor Agent | Mapa de código, busca contextual, alterações multi-arquivo, terminal, diffs, depuração e coordenação | Indexador de símbolos/dependências, contexto mínimo relevante, diffs pequenos e revisão de impacto |
| GitHub Copilot | Assistência no editor, compreensão do workspace e tarefas de programação | Separar proposta, explicação, edição e execução; validar o resultado no projeto |
| Replit Agent | Plan antes de Build, decomposição, construção end-to-end, testes funcionais, correções iterativas, checkpoints e replay | Especificação executável, plano de tarefas, critério de aceite, execução, teste funcional e rollback |
| Lovable | Transformação de requisitos em protótipos e interfaces com iteração visual | Usar especificações e comparação visual sem redesenhar a interface aprovada |
| OpenHands | Sessões, eventos, ferramentas explícitas, workspaces e execução isolada | Ambientes isolados, eventos auditáveis, limites e recuperação |
| Aider | Repo map, seleção de contexto, edição guiada por diff e integração Git | Mapa de símbolos, seleção de arquivos e alterações revisáveis |
| SWE-agent | Reproduzir issues, atuar por ciclos observáveis e medir resolução | Reprodução antes da correção, registro de observações e benchmark por tarefa |
| OpenCode | Planejamento versus execução, permissões por ferramenta e organização de sessão | Separação de análise e mutação; permissões fora do modelo |
| OpenAI Agents SDK, Google ADK e LangGraph | Estado, handoffs, ferramentas, interrupções, avaliação e observabilidade | Estudar contratos e padrões; não iniciar implementação de agentes nesta etapa |
| n8n e UiPath | Integrações, workflows e automação de processos | Adaptadores tipados, consentidos, limitados e auditáveis |

Replit fica explicitamente incluído na categoria de agentes de automação e desenvolvimento. Deve-se estudar o fluxo completo de planejar, construir, testar, diagnosticar, corrigir e recuperar um estado anterior, bem como ferramentas, integrações e automações agendadas documentadas. O runtime final continua sendo Android local/offline-first; Replit não será requisito de hospedagem, backend ou construção do APK.

### 4.2 Imagem, vídeo, música, voz e áudio

| Família e exemplos | Capacidades públicas a estudar | Requisitos para Kellvyn |
|---|---|---|
| Imagem: Nano Banana, GPT Image, Adobe Firefly, FLUX e Stable Diffusion | Geração, edição por instrução, imagem de referência, refinamento e consistência | Brief visual estruturado, geração/edição, versões, inspeção e exportação |
| Vídeo: Luma Dream Machine, Runway, Kling, Sora e Veo | Texto/imagem/vídeo como entrada, movimento, câmera, storyboard, extensão e edição | Planejamento de cenas, continuidade visual, duração, formato, controle de quadros e revisão |
| Música: Suno, Udio e Lyria | Composição guiada por tema, estilo, letra, andamento, extensão e stems | Brief musical, parâmetros criativos, prévia, revisão por partes e exportação |
| Voz e áudio: ElevenLabs e Whisper | Texto-para-fala, fala-para-texto, transcrição, timestamps, dublagem e geração de áudio | STT/TTS, controle de idioma e voz, transcrição e inspeção |
| Produção e edição: FFmpeg, DaVinci Resolve, CapCut e ferramentas gráficas | Conversão, composição, corte, processamento, exportação e edição | Pipeline de mídia verificável, formatos explícitos e não destrutivo por padrão |

Esses sistemas são referências de capacidades, não componentes a copiar. Serviços online dependem de rede, limites, termos, custo e credenciais. Para operar offline, as modalidades precisarão de modelos próprios especializados ou algoritmos locais, dimensionados e validados para o hardware disponível. Não prometer geração local de vídeo ou música sem comprovar o modelo, a memória e o tempo de execução.

### 4.3 Treinamento e pesquisa neural

| Referência | O que estudar | Aplicação independente |
|---|---|---|
| nanochat | Tokenizer, pré-treinamento, ajuste supervisionado, avaliação, inferência e benchmark | Pipeline reproduzível; compreender a escala computacional necessária para modelos úteis |
| OLMo-core | Ingestão, batching, infraestrutura de treino, paralelismo, checkpoints, métricas e reprodutibilidade | Separar dados, treino, checkpoint, avaliação e recuperação |
| Llama, Qwen, DeepSeek, Kimi, GLM, Mistral e MiniMax | Relatórios técnicos, artigos e capacidades publicamente documentadas | Comparar capacidades e métodos públicos; não importar pesos |
| MCP e A2A | Contratos de integração e comunicação entre ferramentas/sistemas | Adaptadores explícitos, com autenticação, escopo e permissões externas ao modelo |

## 5. Pontos fortes prioritários para uma IA de engenharia Android

Ordem sugerida para a Kellvyn:

1. Entender o projeto inteiro: indexar arquivos, símbolos, dependências, módulos e estado do Git; recuperar apenas contexto relevante.
2. Planejar antes de editar: declarar objetivo, tarefas, pré-condições, arquivos afetados, riscos, ferramentas e critérios de aceite.
3. Editar de forma controlada: mudanças pequenas, diff visível, validação, rollback e bloqueio de alterações em partes protegidas.
4. Executar Gradle, ADB e testes reais: capturar saída, código de retorno, logs e versões de ferramentas.
5. Diagnosticar: correlacionar erro de compilação, Logcat, dependências, ambiente e mudanças recentes.
6. Validar visualmente: capturas antes/depois, comparação de layout, navegação e regressão sem alterar a interface protegida.
7. Manter continuidade: checkpoints de objetivo, plano, estado, arquivos alterados, erros, testes pendentes e próxima ação segura.
8. Pesquisar documentação e bugs com fontes e datas, separando fatos, hipóteses e decisões.
9. Proteger operações: permissões mínimas, sandbox, confirmação de ações destrutivas, limites de recursos, cancelamento e auditoria.
10. Construir e validar APKs: verificar pacote, assinatura, integridade, instalação, abertura e funcionamento no dispositivo.

O modelo neural deve sugerir decisões e argumentos tipados; o código confiável do aplicativo valida permissões, parâmetros e risco antes de executar qualquer ferramenta.

## 6. Jogos: capacidade que precisa ser construída e demonstrada

Jogar não é apenas reconhecer uma imagem ou enviar toques. Requer um ciclo fechado:

1. Observação: captura de tela autorizada e leitura da árvore de acessibilidade quando existir.
2. Compreensão: detectar HUD, menus, objetos, posição, objetivo, eventos e mudanças entre imagens.
3. Planejamento: escolher uma ação curta e reversível, com limite de duração.
4. Controle: enviar toque, arraste ou entrada compatível com o Android e com o jogo.
5. Verificação: capturar novamente, observar o resultado e decidir se a ação funcionou ou se o plano deve mudar.

Android fornece APIs de captura e acessibilidade, mas MediaProjection depende de consentimento do usuário e pode ser revogada; AccessibilityService precisa declarar a capacidade de gestos e não garante compatibilidade com todo jogo. Captura protegida, latência, multitouch, anti-cheat, regras de cada jogo e limitações de acessibilidade podem impedir automação. Modo de jogo deve ser opcional, transparente, cancelável e testado jogo por jogo. Não prometer que a Kellvyn jogará qualquer jogo.

## 7. Arquitetura integrada proposta

Uma única identidade, cérebro e interface, com capacidades modulares invocadas pelo mesmo sistema:

USUÁRIO
  -> CONVERSA PRINCIPAL KELLVYN
  -> CÉREBRO NEURAL PRÓPRIO
  -> CONTEXTO E MEMÓRIA LOCAL
  -> PLANEJAMENTO E ESTADO PERSISTENTE DA TAREFA
  -> SELEÇÃO DE CAPACIDADE
      -> Engenharia Android e programação
      -> Pesquisa e navegação
      -> Documentos e dados
      -> Imagem e design
      -> Vídeo
      -> Música, voz e áudio
      -> Interação Android e jogos
  -> EXECUTOR COM POLÍTICAS, LIMITES E APROVAÇÕES
  -> OBSERVAÇÃO DOS RESULTADOS REAIS
  -> VERIFICADOR INDEPENDENTE
  -> MEMÓRIA DA EXPERIÊNCIA E RESPOSTA COMPROVÁVEL

Os módulos não são modelos com identidades concorrentes. Um serviço externo, quando autorizado, é um instrumento limitado e claramente identificado, nunca um substituto oculto do cérebro.

## 8. Sequência de construção e gates

### Gate A — validar a base existente
- Conferir branch, HEAD e diffs antes de qualquer edição.
- Rodar testes reais de tokenizer, formas, máscara causal, estabilidade numérica, gradientes e equivalência do cache KV com inferência por prefixo completo.
- Cobrir parâmetros de todos os grupos com gradientes zero e não zero em seeds e configurações distintas.
- Auditar separação TRAIN/VALIDATION/TEST, duplicatas, vazamento e proveniência.
- Obter executor CI que realmente aloque runner; registrar NOT RUN enquanto isso não ocorrer.

### Gate B — evoluir o modelo neural
- Estabelecer baseline de qualidade, velocidade, memória e estabilidade antes de aumentar a arquitetura.
- Evoluir para múltiplos blocos somente quando backpropagation por bloco, cache por camada, checkpoint e restauração tiverem testes reprodutíveis.
- Separar pré-treino, ajuste supervisionado, preferências e avaliação.
- Construir corpus autorizado e deduplicado com conjuntos realmente independentes.
- Criar benchmarks held-out em português para conversa, instruções, raciocínio, matemática, código, planejamento e recusa segura.
- Não tratar redução de loss como prova de inteligência.

### Gate C — conectar cognição e ferramentas
- Estabelecer contratos entre modelo, memória, contexto, planejador, executor e verificador.
- Texto livre do modelo nunca executa comandos.
- Ferramentas exigem esquemas, permissões, limites, logs, aprovação de risco e cancelamento.
- Só marcar tarefa como concluída após observar e validar o resultado.

### Gate D — modalidades e jogos
- Definir contratos e benchmarks por modalidade.
- Testar primeiro entradas e saídas controladas; depois integrar captura, voz, imagem e ferramentas criativas.
- Separar explicitamente capacidades online de capacidades offline.
- Implementar jogos por títulos e cenários de teste, não por promessa genérica de compatibilidade.

### Gate E — integração e desempenho Android
- Alvo continua sendo Poco F6 com 8 GB de RAM e operação offline-first.
- Medir RAM/PSS, CPU, temperatura, bateria, armazenamento, latência e comportamento após longas sessões.
- Carregar módulos sob demanda e descarregar quando não utilizados.
- Preservar interface e arquivos protegidos.
- Ligar o cérebro ao chat somente após os gates comportamentais, de runtime e de segurança.

## 9. Limites e definição de pronto

Não é tecnicamente honesto garantir hoje paridade com ChatGPT, Claude, Gemini, Replit ou capacidade de jogar qualquer jogo. O objetivo pode ser construir e testar essas capacidades por etapas; o resultado precisa ser demonstrado.

A configuração KN-003 atual é um protótipo de aproximadamente 43 mil parâmetros, contexto curto e um bloco. Não é um LLM geral. O Poco F6 é o alvo de execução móvel, não evidência de que possa pré-treinar localmente um modelo geral competitivo. Escala e competência dependem de dados autorizados, computação, arquitetura, otimização e benchmarks.

Uma capacidade só pode ser marcada como pronta quando houver: teste funcional representativo, teste de falha e recuperação, medição de recursos, permissões verificadas e evidência ligada à execução. Código criado, teste escrito, build aprovado, execução no emulador e validação física no Poco F6 são estados diferentes.

## 10. Fontes primárias consultadas

### Engenharia, agentes e automação
- Android Studio Gemini overview: https://developer.android.com/studio/gemini/overview
- Android Studio Agent Mode: https://developer.android.com/studio/gemini/agent-mode
- Android Studio build/run: https://developer.android.com/studio/run
- Cursor Agent: https://cursor.com/docs/agent/overview
- Claude Code: https://code.claude.com/docs
- OpenAI tools: https://developers.openai.com/api/docs/guides/tools
- OpenAI agents: https://developers.openai.com/tracks/building-agents
- OpenAI computer use: https://developers.openai.com/api/docs/guides/tools-computer-use
- Replit Agent overview: https://docs.replit.com/references/agent/overview
- Replit Plan mode: https://docs.replit.com/references/agent/plan-mode
- Replit app testing: https://docs.replit.com/references/agent/app-testing
- Replit agent automation tutorial: https://docs.replit.com/build/build-an-agent
- Google ADK: https://docs.cloud.google.com/gemini-enterprise-agent-platform/build/adk
- OpenHands: https://github.com/All-Hands-AI/OpenHands
- Aider: https://github.com/Aider-AI/aider
- SWE-agent: https://github.com/SWE-agent/SWE-agent
- OpenCode: https://opencode.ai/docs/
- nanochat: https://github.com/karpathy/nanochat
- OLMo-core: https://github.com/allenai/OLMo-core

### Imagem, vídeo, música e voz
- Nano Banana: https://deepmind.google/models/gemini-image/
- GPT Image: https://developers.openai.com/api/docs/guides/image-generation
- Runway API: https://docs.dev.runwayml.com/
- Runway Gen-4.5: https://help.runwayml.com/hc/en-us/articles/46974685288467-Creating-with-Gen-4-5
- Kling API: https://kling.ai/dev/document-api/apiReference/model/
- Luma Dream Machine: https://lumalabs.ai/learning-hub/modify-video-dream-machine
- Suno stems: https://help.suno.com/en/articles/13925185
- Udio: https://help.udio.com/en/articles/10715838-create-your-first-song
- ElevenLabs: https://elevenlabs.io/docs/overview/intro

### Android e jogos
- Accessibility Service: https://developer.android.com/guide/topics/ui/accessibility/service
- AccessibilityService gestures: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- MediaProjection: https://developer.android.com/media/platform/av-capture
- Android touch and input: https://developer.android.com/develop/ui/views/touch-and-input/input

## 11. Nomes ainda não confirmados

“Dolar/Dola” e “SLA” mencionados nas conversas não foram identificados com confiança. “Lula” foi interpretada provisoriamente como Luma AI, mas a identificação deve permanecer marcada como pendente até confirmação. Não substituir nomes incertos por produtos escolhidos por adivinhação.

## 12. Estado desta auditoria

Pesquisa documental inicial e arquitetura-alvo registradas. Ainda falta leitura aprofundada de cada referência, benchmark prático comparativo e execução de testes reais do projeto. Nesta sessão, não foi executado treinamento neural, Gradle, JUnit, instalação Android nem teste físico no Poco F6. Não declarar essas etapas como concluídas.


## 13. Implementação incremental posterior à auditoria inicial — 19/11/2026

Foram aplicadas alterações pequenas e isoladas na pipeline KN-003:

1. **Treino por épocas intercaladas:** antes, cada exemplo de treino recebia todas as suas épocas antes do próximo exemplo. Agora os exemplos são ordenados canonicamente por ID e embaralhados com seed derivada da seed global por época. Isso reduz dependência da ordem de entrada e evita concentrar todas as atualizações de um exemplo antes de aprender os restantes.
2. **Primitive de treino de uma época:** adicionado `trainCompletionEpoch`, que aplica uma época sem calcular perdas inicial/final redundantes. A pipeline passa a medir validação nos limites apropriados, evitando avaliações repetidas para cada exemplo em cada época.
3. **Testes adicionados:** reprodutibilidade quando a ordem dos exemplos de entrada muda e equivalência entre o método tradicional de uma época e a nova primitive, incluindo fingerprint dos parâmetros e contagem de passos.

Commits:
- `7e9de61750948029ade47d0b92e1b2118bf9ec41` — treino intercalado por época com shuffle determinístico.
- `9923010bd6b43d11636ca1f66eec016e74023eeb` — teste de invariância à ordem de entrada.
- `a5962bb5bcb9b58440de426392550d6a5d0d3f91` — primitive de treino sem avaliações de loss redundantes.
- `7347f44c9f736ad9e459dcab8c38669aad27d288` — pipeline usa a primitive eficiente.
- `6353f0a9edf47fe6e4bd0cadb0979ec224ca435d` — teste de equivalência da primitive.

**Validação:** os arquivos foram relidos no GitHub e os trechos/nomes dos testes confirmados. O status de checks do commit ainda não mostra execução e não há workflow run associado ao commit consultado. Portanto os novos testes estão escritos, mas continuam **NÃO EXECUTADOS** nesta sessão. Não há aprovação Gradle/JUnit ou APK resultante.


## 14. Método de aceleração sem reduzir qualidade — pesquisa e implementação

### Princípios verificados nas referências

- **Android Studio Agent Mode:** tarefas complexas são divididas em plano e ações, mudanças são revisadas, builds são executados e erros corrigidos iterativamente. A documentação também descreve threads independentes para tarefas paralelas e, no fluxo de dispositivo, inspeção visual e Logcat. A Kellvyn deve aproveitar isso como protocolo de engenharia, nunca editar arquivos de interface bloqueados.
- **Replit Agent:** Plan separa ideação/decomposição de Build; App Testing permite teste funcional autônomo com intervenção humana quando há login; checkpoints tornam possível retornar a estados anteriores. Seu tutorial de automação destaca Skills como instruções carregadas sob demanda e MCP como ferramentas/acessos — distinção útil para manter o contexto enxuto. O tutorial de automação agendada usa SDK/integrações e chave de serviço externo, logo não será dependência offline da Kellvyn.
- **Replit Agent modes:** a própria documentação diferencia alterações rápidas de tarefas difíceis e recursos extras de teste/otimização. O princípio aproveitável é selecionar profundidade de verificação conforme o risco, não saltar testes para obter velocidade.
- **LiteRT Android:** a documentação atual descreve CompiledModel como API moderna para aceleração em CPU/GPU/NPU, enquanto Interpreter fica como compatibilidade. É uma possibilidade de benchmark para operadores compatíveis, não uma obrigação e nem motivo para importar pesos de terceiros.
- **GPU:** a documentação do LiteRT avisa que operações não suportadas podem dividir o grafo entre GPU e CPU; sincronizações CPU/GPU podem deixar a execução mais lenta que CPU apenas. Portanto, não assumir aceleração por ligar GPU: medir o grafo inteiro e verificar fallbacks.
- **Escala de treino:** o estudo Chinchilla mostra que qualidade depende do balanço entre parâmetros, tokens e computação, e não apenas aumentar o modelo. Para Kellvyn, isso reforça a decisão de medir dados, passos e custo antes de aumentar dimensão/profundidade.

### Protocolo operacional da Kellvyn R&D

1. Fixar branch, HEAD, escopo, arquivos protegidos e critério de aceite antes de iniciar.
2. Fazer três trilhas de leitura independentes em paralelo: matemática/treino, runtime Android/desempenho e referências de engenharia/capacidades.
3. Só uma trilha pode gravar em determinado arquivo de cada vez. Evitar conflitos e alterações concorrentes no mesmo código.
4. Toda hipótese de código precisa de: causa observada, mudança mínima, teste que falha antes/passaria depois e rollback claro.
5. Rodar primeiro o teste unitário do componente modificado; depois testes da pipeline neural; por último Gradle/build Android completo.
6. Após cada commit, reler o diff/arquivo no GitHub e registrar SHA, checks e estado real. Não confundir teste escrito com teste executado.
7. Manter checkpoints curtos de continuidade: o que foi alterado, o que foi confirmado, o que está bloqueado e a próxima ação verificável.
8. Usar otimizações adaptativas apenas quando equivalência/correção estiver coberta por teste; qualquer aceleração de runtime exige benchmark no Poco F6 com latência, PSS/RAM, temperatura e bateria.

### Ganho concreto já aplicado no KN-003

Antes, a pipeline chamava `trainCompletion(..., epochs = 1)` para cada exemplo e época. Esse método media loss inicial e final do próprio exemplo a cada chamada. Assim, o treino repetia passagens de avaliação sobre os dados de treino para cada amostra, além de avaliar validação global.

Agora:
- a pipeline mede loss inicial/final no conjunto de validação;
- cada exemplo recebe uma única passada de gradiente por época;
- a ordem dos exemplos é embaralhada de forma determinística por época;
- os parâmetros são atualizados na mesma rotina matemática de uma época original;
- foi adicionado teste de equivalência do método novo com uma época do método antigo e teste de reprodutibilidade quando a lista de entrada é invertida.

Isso remove avaliações redundantes por amostra e reduz o viés de ordem sem alterar a regra de atualização por token. O ganho real em tempo ainda precisa ser medido em execução do Gradle/benchmark; não atribuir um percentual sem medição.

### Próxima otimização a estudar, ainda não implementada

O custo mais relevante que permanece é a atualização AdamW e a retropropagação por token, que percorrem/instanciam arrays grandes repetidamente. Opções a investigar, com testes de equivalência:
- acumulação de gradientes em micro-lotes antes de atualizar AdamW;
- minibatches determinísticos e agendamento de learning rate;
- pré-alocação/reuso de buffers temporários sem compartilhar estado entre tarefas;
- operadores matriciais nativos ou acelerados apenas se o grafo inteiro suportar sem fallback caro;
- perfil de CPU/GPU/PSS por comprimento de sequência antes de aumentar contexto ou profundidade.

Não implementar essas opções por suposição. Primeiro criar benchmarks reproduzíveis e verificar os efeitos numéricos.

### Estado do CI nesta rodada

A execução associada ao commit `c27f4e96557bdde52a9c76e5ee91a0b915ee8de6` terminou como `failure`, mas o job retornou `steps=[]` e `logs_url=null`; o download de logs também não encontrou blob. O retry do job voltou a encerrar sem steps. Não há evidência de que Gradle tenha começado. Classificação correta: **CI sem execução/logs disponíveis — infraestrutura/runner bloqueando a verificação**, não falha de compilação comprovada. Os novos testes permanecem escritos e não executados.

Fontes adicionais:
- LiteRT Android API e CompiledModel: https://developers.google.com/edge/litert/android
- LiteRT GPU delegates e limitações de fallback: https://developers.google.com/edge/litert/performance/gpu
- Chinchilla, compute-optimal training: https://arxiv.org/abs/2203.15556
- Android Studio Agent Mode: https://developer.android.com/studio/gemini/agent-mode
- Replit Plan mode: https://docs.replit.com/pt/references/agent/plan-mode
- Replit App Testing: https://docs.replit.com/references/agent/app-testing
- Replit Agent automation tutorial: https://docs.replit.com/pt/build/build-an-agent


### Otimização adicional do laço AdamW

Commit `e9f71106861604d919056a4d13b5679ccc3b6962`:
- Views/listas que apontam para arrays dos parâmetros e dos dois momentos de AdamW são criadas uma vez na construção do modelo e reutilizadas.
- O laço de treino agora cria uma única lista de grupos de gradientes por atualização e reutiliza essa referência para norma, clipping e atualização.
- Antes eram criadas 5 listas por atualização sem clipping, ou 6 quando o clipping ocorria; agora são 1. Isso reduz de 4 a 5 objetos-lista temporários por passo, sem alterar os arrays, parâmetros, gradientes ou a matemática do otimizador.
- É uma otimização de alocação no caminho quente; não reduz o número de operações matemáticas e ainda precisa de benchmark para medir impacto em tempo/GC.

Testes relevantes já gravados:
- `epochTrainingIsReproducibleWhenInputExamplesArriveInDifferentOrder` — estabilidade da seed e da ordem.
- `directEpochPrimitiveMatchesSingleEpochTrainingExactly` — equivalência de parâmetros/fingerprint entre um passo antigo de uma época e a primitive nova.
- `supervisedCompletionTrainingReducesLossAndUpdatesOnlyResponseSteps` — preserva treinamento supervisionado e contagem de passos.

Estado: fonte e testes confirmados por releitura no GitHub; não executados pelo Gradle nesta sessão.
