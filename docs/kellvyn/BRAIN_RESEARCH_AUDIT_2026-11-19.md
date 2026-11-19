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


### Medição e perfilamento Android para acelerar com evidência

A pesquisa oficial do Android confirma um conjunto de ferramentas complementares:
- **Macrobenchmark:** mede jornadas maiores do app e gera resultados JSON e traces; adequado para início de conversa, carregamento do cérebro e execução de uma tarefa real.
- **Microbenchmark:** mede trechos pequenos, útil para tokenizer, camada linear, atenção e atualização AdamW isoladamente.
- **Android Studio Profiler:** identifica hotspots de CPU, alocações, heap e GC; o perfilamento de alocação Java/Kotlin exige build debuggable e tem overhead, portanto não deve ser usado como medição final de desempenho.
- **Perfetto/system tracing:** correlaciona threads, CPU, memória, escalonamento e atividade do dispositivo.

Plano para o Poco F6:
1. Baseline em build de perfil/produção, mesma temperatura inicial e mesmo conjunto de prompts.
2. Medir inferência por token, pré-processamento, treino por passo e tempo total, em execuções frias e aquecidas.
3. Coletar PSS/heap, alocações e GC; em seguida trace Perfetto para identificar gargalo.
4. Medir temperatura/thermal headroom e bateria durante tarefas longas.
5. Repetir após cada otimização, mantendo apenas mudanças que melhorem tempo ou memória sem piorar loss, comportamento, estabilidade ou segurança.
6. Guardar JSON/trace e versão exata do APK/código ligados ao resultado.

Fontes:
- Macrobenchmark: https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
- Android Studio Profiler: https://developer.android.com/studio/profile
- System tracing / Perfetto: https://developer.android.com/topic/performance/tracing
- Android memory metrics: https://developer.android.com/topic/performance/memory


### Otimização RoPE — frequências, escala e reuse query/key

Commits:
- `7814658d1bb87651fde54f5f2a265f3e5359bb2e` — pré-cálculo das frequências RoPE e fatores de escala da atenção.
- `73c42aa8e1c1b3e7dbcda72988d0a34167461891` — expressão de escala explícita.
- `cc1d656483306d5b09b7fdd1ff097557f7778436` — reutilização de seno/cosseno entre query e key na mesma posição.
- `e134daa73662a7debacf6377d20a64254c333556` — ajuste do caminho de retropropagação para reutilizar fatores também na rotação inversa.

Mudanças:
- Frequências de RoPE e escalas da atenção são calculadas uma vez por configuração do modelo, em vez de repetir `pow()` e `sqrt()` durante todos os tokens.
- O cálculo de seno/cosseno para uma posição é compartilhado entre query e key nessa mesma posição.
- A retropropagação reaproveita os mesmos fatores na rotação inversa de query e key.

A fórmula matemática, a inicialização e os pesos não mudam. O ganho esperado é menos chamadas matemáticas transcendentais e menos trabalho repetido no caminho quente. Não há percentual de aceleração medido.

Verificação estática: todas as chamadas de `rotateInPlace` foram relidas após a mudança; forward, geração e gradientes agora usam `RotaryFactors`. Testes existentes que cobrem esses caminhos: `multiHeadTransformerGradientsMatchFiniteDifferences`, `gradientCheckerCoversEveryParameterGroupAcrossHeadCountsAndSeeds`, `incrementalKvCacheMatchesFullPrefixAcrossContextEviction`, `incrementalGenerationAndFullPrefixGreedyGenerationProduceSameTokens` e `restoredCheckpointRetainsIncrementalKvCacheEquivalenceAfterWindowSlides`.

CI do commit `e134daa73662a7debacf6377d20a64254c333556`: workflow run `44627801410` terminou como failure; o job ainda não apresenta steps nem logs. Portanto os testes acima continuam **NÃO EXECUTADOS** nesta sessão.


### Reuso de fatores RoPE entre forward e retropropagação

Commit `187fdf3635f1486a9ebba6c415fd8651756571cb`:
- `ForwardPass` agora mantém os fatores RoPE calculados para cada posição.
- O cálculo forward usa o mesmo conjunto de fatores para query e key da última posição.
- A retropropagação usa o mesmo conjunto salvo para desfazer a rotação de query e de todas as keys, sem recalcular seno/cosseno.
- O custo extra de memória é proporcional a comprimento do contexto × pares por cabeça; para a configuração padrão é pequeno e precisa ser medido no aparelho.

Revisão estática confirmou um único construtor de `ForwardPass`, todas as chamadas de rotação usando `RotaryFactors` e o array de fatores propagado até a retropropagação. Um snippet Kotlin isolado também compilou a sintaxe de `Array(length, ::createRotaryFactors)` e acesso à constante do companion object; isso é apenas uma verificação de sintaxe auxiliar, **não** uma compilação do projeto Android.

CI do commit mais recente deste bloco ainda encerra sem steps/logs. Os testes de gradiente e de equivalência do KV cache permanecem não executados.


### Otimizações adicionais no caminho de atenção e geração

Commits:
- `de9173ef8f17bfaa20dcfa81f722e1e9aa3d7ab2` — buffers de score e derivada reutilizados entre cabeças.
- `e78022eed6650c46887081b0473f99cd76350bca` — geração gulosa seleciona pelo maior logit sem calcular softmax do vocabulário.
- `4192d9c5277c2418b05edcb5b4a36f600c47412b` — teste de equivalência entre argmax dos logits e argmax das probabilidades softmax.

Detalhes:
- No forward, o array de scores por posição é alocado uma vez e reutilizado entre cabeças; as probabilidades ficam no array `attentionWeights` que precisa ser preservado para o backward.
- No backward, o buffer `dAttentionProbability` é alocado uma vez e sobrescrito para cada cabeça.
- Na geração, o mesmo array de score é reutilizado para guardar exponenciais e pesos normalizados; remove uma alocação de buffer por cabeça.
- Na decodificação greedy, `predictNext()` agora compara logits diretamente. Como softmax preserva a ordem de logits finitos, o token vencedor é o mesmo; a implementação exige logits finitos. O método que devolve probabilidades segue disponível e segue usando softmax quando necessário.
- Um teste adicional verifica que o token escolhido diretamente pelos logits coincide com o argmax das probabilidades em 12 passos de geração e mantém comparação com a geração por prefixo completo.

Impacto esperado: menor churn de objetos/arrays e menos exponenciais/divisões no vocabulário em geração greedy. O ganho em tempo, GC e bateria ainda **não foi medido**.

### Snapshot atual de implementação e validação

Arquivos relidos após as alterações:
- `KellvynTransformerLanguageModel.kt`
- `KellvynTransformerTrainingPipeline.kt`
- `KellvynTransformerLanguageModelTest.kt`
- `KellvynTransformerTrainingPipelineTest.kt`

Testes existentes que cobrem as mudanças:
- `directEpochPrimitiveMatchesSingleEpochTrainingExactly`
- `epochTrainingIsReproducibleWhenInputExamplesArriveInDifferentOrder`
- `multiHeadTransformerGradientsMatchFiniteDifferences`
- `gradientCheckerCoversEveryParameterGroupAcrossHeadCountsAndSeeds`
- `incrementalKvCacheMatchesFullPrefixAcrossContextEviction`
- `incrementalGenerationAndFullPrefixGreedyGenerationProduceSameTokens`
- `restoredCheckpointRetainsIncrementalKvCacheEquivalenceAfterWindowSlides`

CI mais recente associado ao commit `4192d9c5277c2418b05edcb5b4a36f600c47412b`: run `44628706417`, job `135929303787`, conclusão `failure`, porém sem steps, sem log URL e sem blob de logs. Não há evidência de que a etapa Gradle tenha iniciado. Classificação: **runner/infraestrutura não disponibilizou execução observável**. Os testes continuam escritos e não executados.


### Ajuste adicional do plano de execução acelerada

Nesta rodada, o processo de trabalho foi ajustado para reduzir tempo de investigação que não termina em alteração:
- pesquisas documentais, auditoria de dependências e revisão de testes podem ocorrer em trilhas independentes;
- commits de código no mesmo arquivo continuam sequenciais para evitar conflitos e perda de alterações;
- cada bloco de implementação tem um teste alvo ligado ao contrato;
- nenhuma otimização permanece só por parecer rápida: é necessário benchmark de tempo, alocações, GC, memória e resultado comportamental;
- a verificação estática do código não é registrada como compilação Android.

Uma checagem sintática Kotlin isolada com o compilador instalado foi executada para as construções novas de referência de função, array de fatores e validação de logits finitos: **PASS**. Essa checagem usa um snippet mínimo e não compila a classe completa, não valida dependências nem substitui Gradle/JUnit.

Acesso de rede do ambiente local de compilação não está disponível nesta sessão; a tentativa de resolver o host do repositório falhou por DNS. Os jobs do GitHub Actions continuam terminando com failure sem steps e sem logs; não repetir retries cegos. O próximo avanço de validação depende de recuperar um runner real ou de obter um ambiente de build funcional conectado ao projeto.

## 15. Resumo do que mudou desde a auditoria inicial

Código:
- treinamento intercalado e determinístico por época;
- treino de uma época sem avaliação de loss redundante por exemplo;
- arrays de parâmetros/momentos e buffers de atenção reutilizados;
- frequências e escalas RoPE pré-calculadas;
- fatores RoPE compartilhados entre query/key e reutilizados no backward;
- geração gulosa diretamente por logits, sem softmax do vocabulário.

Testes adicionados:
- invariância do treino à ordem de entrada;
- equivalência entre primitive de uma época e treino tradicional;
- equivalência entre argmax direto dos logits e argmax após softmax.

**Resultado validado até agora:** arquivos e commits presentes no GitHub; snippet sintático isolado aprovado. **Resultado ainda não validado:** Gradle, JUnit, qualidade neural, medição de velocidade no Poco F6 e APK. Não há ganho percentual medido.


### Otimização do tokenizer BPE em treino e codificação

Commits:
- `506ebc67d0d0a92e82444ed67ae8136564a091d2` — codificação aplica merges em um `IntArray` compactado no lugar, eliminando uma lista nova por merge.
- `32b55a23c4d68f669973e95d511a322c34124ac3` — treinamento do BPE mantém sequências em buffers primitivos e as compacta in-place após cada merge; remove cópias/listas intermediárias por sequência e rodada.

Detalhes:
- `encode(text)` cria um buffer de IDs UTF-8 e compacta o prefixo ativo conforme cada merge consome pares. O tamanho nunca aumenta, então o buffer inicial sempre basta.
- Durante o treino do tokenizer, cada documento tem buffer próprio de IDs e tamanho ativo. A contagem de frequências percorre apenas a região ativa e as atualizações não substituem a sequência por uma nova `List<Int>`.
- A lógica de escolha dos pares, frequência, desempate por IDs e ordem dos merges permanece a mesma.

Verificação auxiliar executada com Kotlin/JVM instalado no ambiente:
- Implementação in-place comparada com a referência baseada em lista.
- 25.000 sequências aleatórias, cada uma com séries aleatórias de merges.
- **733.926 operações de merge comparadas, todas equivalentes**.
- Isso valida o algoritmo de compactação isoladamente, mas não compila a classe real do app nem substitui os testes de tokenizer no Gradle.

Testes do projeto que continuam aplicáveis:
- `byteFallbackRoundTripsPortugueseAndUnicodeExactly`
- `trainingIsDeterministicForTheSameOrderedCorpus`
- `checkpointRestoresMergeTableAndRoundTripBehavior`
- `endOfSequenceTokenIsDedicatedAndNeverProducedByTextEncoding`
- `legacyVersionOneCheckpointRestoresWithoutInventingAnEosToken`

O benchmark de velocidade real de tokenização ainda não foi medido; a redução de alocações é estrutural e o ganho de tempo precisa ser medido no aparelho.


## 16. Nova capacidade neural: pré-treinamento de documentos em KN-003

**Problema identificado:** KN-003 só tinha pipeline de treino supervisionado com prompt/completion. O treinamento com documentos estava implementado apenas no KN-002. Isso limitava o Transformer principal a aprender respostas de exemplos de instrução, sem uma primeira fase explícita de previsão contínua do próximo token sobre documentos.

**Implementação adicionada:**
- `KellvynTransformerLanguageModel.trainTokensEpoch`: aplica uma época de previsão do próximo token sem avaliações de loss redundantes.
- `KellvynTransformerTrainingPipeline.pretrainDocuments`: treina tokenizer e KN-003 a partir de documentos locais autorizados.
- O tokenizer aprende merges somente com documentos TRAIN.
- VALIDATION e TEST são mantidos fora do treinamento e usados somente para medir generalização.
- Verificações de IDs duplicados, conteúdo repetido entre partições, tamanho por documento, tamanho do corpus, credenciais/segredos e limites de passos.
- Os documentos são ordenados por ID e embaralhados deterministicamente por época.
- Manifesto de proveniência inclui permissão, licença, fonte, evidência de direitos, hash de conteúdo e configuração.
- `KellvynRuntime.pretrainOwnTransformerFromDocuments` executa o trabalho na fila de tarefa pesada, respeita cancelamento e salva o mesmo bundle KN-003 local usado pelo treino supervisionado.

Testes adicionados:
- `rawTokenEpochPrimitiveMatchesSingleEpochTrainingExactly`
- `pretrainingUsesOnlyTrainDocumentsAndKeepsHeldOutSplitsUntouched`
- `documentPretrainingIsReproducibleWhenInputOrderChanges`
- `documentPretrainingRejectsDuplicateContentAcrossPartitions`

Commits:
- `581e3f5ef3b3b9773db6ec90ccb79db340ef66a0` — primitive de treino de uma época para sequências de tokens.
- `806c160fe3c6686fc80ff22b6cc5817f44dffd43` — pipeline de pré-treinamento por documentos.
- `db4287c9c4e3e1319be190259923210fe4ef53f8` — testes da pipeline de documentos.
- `b285096b84a272bf2eb05756d1c39400586a5a6f` — teste de equivalência da primitive de tokens.
- `6b95309078ab46a599c550912cc3d088524241b8` — integração da operação no runtime.

**Limite:** o modelo continua sendo KN-003 de um bloco e a pipeline continua sujeita aos limites experimentais de corpus/passo já documentados. Adicionar pré-treinamento de documentos melhora a etapa de aprendizagem de linguagem, mas não cria conhecimento sem dados adequados nem demonstra conversa geral. O treino real ainda depende de documentos autorizados fornecidos pelo proprietário e de testes executados.

Validação nesta sessão: os seis arquivos do modelo, pipeline, runtime e testes foram relidos no GitHub e os pontos de integração foram confirmados. O CI continua sem executar Gradle/JUnit (jobs falham sem steps/logs); esses novos testes permanecem **NÃO EXECUTADOS**.


## 17. Encadeamento do pré-treinamento com ajuste fino supervisionado

**Bloqueio identificado:** adicionar pré-treinamento por documentos não bastava. O método antigo `KellvynTransformerTrainingPipeline.train(examples)` inicializa um novo Transformer aleatório. Se fosse chamado após pré-treinamento, ele não continuaria os pesos anteriores; faria outro treinamento do zero e poderia substituir o bundle pré-treinado.

**Correção implementada:**
- Novo `KellvynTransformerFineTuneConfig` com épocas, learning rate mais conservador, weight decay e limite de passos.
- Novo `KellvynTransformerTrainingPipeline.fineTuneInstructions(base, examples, ...)`.
- O ajuste fino exige um modelo previamente treinado, valida consistência entre dimensões do modelo/tokenizer/manifesto e exige TRAIN/VALIDATION/TEST no manifesto base.
- O checkpoint base é serializado e restaurado para uma cópia antes de qualquer mutação. Se validação falhar, treinamento for cancelado ou execução lançar erro, o bundle base em disco permanece intacto.
- O tokenizer e EOS do pré-treinamento são preservados; o vocabulário não é treinado novamente com os exemplos de ajuste fino.
- O treino continua dos pesos e momentos AdamW anteriores, adiciona novos passos, combina proveniência no manifesto e mede loss inicial/final de validação e loss held-out do novo conjunto.
- Novo `KellvynRuntime.fineTuneOwnTransformerModel` carrega o bundle, executa a operação sob `runHeavyTask` e só substitui o bundle armazenado depois de receber o resultado completo.

Testes adicionados:
- `supervisedFineTuningContinuesFromPretrainedBundleWithoutMutatingIt`: confirma que o bundle original não muda, os pesos ajustados mudam, tokenizer/EOS são preservados, os passos são incrementais e o manifesto contém ambos os conjuntos.
- `fineTuningFromSameBaseAndSeedIsReproducible`: confirma fingerprint idêntico para a mesma base, dados, seed e ordem canônica.
- `rawTokenEpochPrimitiveMatchesSingleEpochTrainingExactly`: equivalência da primitive usada no pré-treinamento com a atualização existente de uma época.

Commits:
- `99bf084221bc1f2e73a6d476ad2b33cae0ace4fc` — pipeline de ajuste fino a partir de bundle KN-003 treinado.
- `6b10cd05001745f6320a46b02933885578d898a2` — operação exposta no runtime.
- `e863dc10444ffff0fe1c70c1b26644e9de01e536` — testes de continuidade e não mutação da base.
- `6301329d061eeb5dc2ab9a17cbbcccbd1465ea4d` — import de configuração no runtime.
- `fc5b2b9ea4e075fe048d4c72c943985d467cd017` — validação consistente do bundle base.
- `9fc2b0f45e087365b08fedb5a407b6253be55fb5` — teste explícito de preservação do EOS.

Fluxo agora disponível no código:
1. `pretrainOwnTransformerFromDocuments(...)` — aprende linguagem de texto contínuo a partir de documentos TRAIN.
2. `fineTuneOwnTransformerModel(...)` — continua dos mesmos pesos para aprender instruções/respostas.
3. `createOwnTransformerInferenceEngine()` — restaura o bundle combinado e só cria o adaptador após checksum, contexto e Quality Gate.

Esse fluxo está implementado em código, mas ainda **não executado pelo Gradle** nesta sessão. Não há bundle treinado real nem teste de competência conversacional aprovado.


### Delimitação de documentos no pré-treinamento

O KN-003 agora acrescenta o token EOS dedicado ao final de cada documento no pré-treinamento. Assim, cada documento fornece alvos de previsão para seus tokens de texto e para o encerramento; a próxima amostra começa como uma nova sequência, sem concatenar documentos independentes como se fossem texto contínuo. O conjunto de validação e teste também inclui o alvo EOS correspondente. Os testes de passos foram ajustados para contar esse token de encerramento.

Commits:
- `5deeffef0fb1b20d344200079020a7791cf46576` — acrescenta EOS por documento.
- `d8ca28306f32399c64ed0f612c2c8f0dd79dd09d` — ajusta a contagem e a verificação do EOS no teste.


### Proteção adicional da continuação do treinamento

- Antes de aplicar qualquer atualização de fine-tuning, a cópia do checkpoint base é restaurada a partir do próprio formato KN-003, incluindo momentos do AdamW.
- O pipeline compara fingerprint SHA-256 dos parâmetros e contador de passos entre a base e a cópia restaurada; qualquer divergência interrompe o ajuste fino.
- O bundle que já estava persistido só é substituído após o treino, avaliação de validação/teste e retorno bem-sucedido do pipeline.

Commit: `d06c7655fbb9dfa8e94b34a68d644398714f2c16`.

Com isso, a sequência de aprendizado no código não reinicializa o modelo ao passar de pré-treinamento para ajuste supervisionado. A correção estática e os testes foram gravados; ainda precisam de execução Gradle/JUnit para serem aprovados.


## 19. Pesquisa complementar: agentes Android, Replit e aceleração on-device

### Android Studio Agent Mode — práticas atuais observadas
A documentação oficial descreve:
- objetivos grandes convertidos em planos e subtarefas;
- ferramentas invocadas pelo agente e alterações em múltiplos arquivos;
- ciclo iterativo de build/reparo;
- aprovação/revisão de alterações;
- possibilidade de conversas paralelas no painel;
- deploy em dispositivo, captura de tela, inspeção visual e consulta ao Logcat para confirmar erros e mudanças.

**Aplicação ao processo Kellvyn:** executar pesquisas independentes (treinamento, arquitetura, runtime Android e avaliação) em paralelo; concentrar alterações por arquivo em uma sequência serializada para evitar conflitos; exigir diffs e testes. A verificação Android futura deve combinar build, instalação, tela, logs e regressão.

### Replit Agent — práticas atuais observadas
A documentação oficial atual distingue:
- **Plan:** planejamento, decomposição ordenada, arquitetura e aprovação de tarefas sem editar projeto;
- **Build:** implementação;
- **App Testing:** teste funcional no navegador e correção;
- **Checkpoints:** restauração de estado anterior;
- **Skills:** instruções carregadas sob demanda para não ocupar continuamente o contexto;
- **MCP/integrations:** acesso a ferramentas e serviços externos;
- **Scheduled Deployments:** execução agendada que depende de hospedagem/integrações e segredos, portanto não é uma solução offline-first.

A documentação também descreve modos de esforço e um Turbo mais rápido, mas com custo potencialmente maior. Para Kellvyn, a lição é escolher profundidade de raciocínio conforme risco e manter verificações determinísticas; não adotar um modo caro nem depender de API keys/hospedagem como fundação do aplicativo.

### LiteRT — limite entre runtime e cérebro
A documentação Google AI Edge atual recomenda a API `CompiledModel` para inferência on-device, com opções de CPU/GPU e acesso a NPU conforme dispositivo/runtime. A API oferece buffers e execução assíncrona. Isso pode ser avaliado como runtime auxiliar para **pesos Kellvyn próprios**, desde que exista um grafo/exportação compatível e o resultado seja comparado com a implementação de referência.

Não é uma solução para treinar o Transformer atual do zero, não fornece os pesos Kellvyn e não deve ser adicionado por popularidade. A documentação explica que o backend/operadores suportados importam; cada export precisa de equivalência numérica e benchmark no Poco F6. Não usar Gemma nem qualquer checkpoint oferecido nos exemplos da documentação.

### Benchmark Android — protocolo mais rápido e confiável
- **Microbenchmark:** medir o custo de funções isoladas como encode do BPE, RoPE, atualização AdamW e previsão greedy.
- **Macrobenchmark:** medir o fluxo completo do app, desde abrir/retomar chat até responder/usar ferramenta; gera resultados e traces.
- **Android Studio Profiler:** localizar CPU hotspots, alocações, heap e GC; usar build debuggable para gravar alocações, lembrando que essa instrumentação altera o custo observado.
- **Build profileable/release:** usar para medir desempenho próximo do uso real, sem overhead do debugger.

A rotina de aceleração deve ser: encontrar hotspot com profiler → microbenchmark isolado → otimizar → microbenchmark de regressão → macrobenchmark do fluxo → validação física no Poco F6.

Fontes oficiais consultadas:
- Android Studio Agent Mode: https://developer.android.com/studio/gemini/agent-mode
- Android Studio AI overview: https://developer.android.com/studio/gemini/overview
- Android Studio Agent Mode com device, screenshots e Logcat: https://developer.android.com/blog/posts/llm-flexibility-agent-mode-improvements-and-new-agentic-experiences-in-android-studio-otter-3-feature-drop
- Replit Plan: https://docs.replit.com/pt/references/agent/plan-mode
- Replit Agent modes: https://docs.replit.com/pt/references/agent/agent-modes
- Replit Agent overview: https://docs.replit.com/pt/references/agent/overview
- Replit scheduled automation / Skills / MCP: https://docs.replit.com/pt/build/build-an-agent
- LiteRT overview: https://developers.google.com/edge/litert/overview
- LiteRT on-device inference: https://developers.google.com/edge/litert/inference
- Android Macrobenchmark: https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
- Android Profiler: https://developer.android.com/studio/profile
- Android Java/Kotlin allocations: https://developer.android.com/studio/profile/record-java-kotlin-allocations

**Decisão de engenharia:** não integrar LiteRT ainda. Primeiro concluir correção e validação do KN-003; depois avaliar exportabilidade do grafo e comparar CPU pura versus CompiledModel no hardware-alvo.


## 20. Escopo final do cérebro e portões de entrega

Foi criada a especificação persistente `docs/kellvyn/BRAIN_FINAL_CAPABILITIES_AND_GATES_2026-11-19.md`. Ela registra as capacidades finais pretendidas, a divisão entre núcleo Kellvyn e ferramentas subordinadas, as garantias de privacidade/segurança, a ordem de conclusão e os critérios que impedem declarar uma função pronta sem integração, testes e validação no hardware.

O escopo final cobre: conversa e raciocínio; memória global e por projeto; pesquisa online opcional com fontes; arquivos e documentos; desenvolvimento Android/software; ferramentas e automações; orquestração/verificação interna quando o laboratório for reativado; expansão multimodal própria; análise/interação assistida com jogos; e gestão de RAM/CPU/bateria/temperatura. Tudo deve permanecer sob uma única identidade Kellvyn, sem pesos de terceiros nem provedores externos como substituto do cérebro.

A especificação também registra os bloqueios atuais, incluindo o chat ainda desconectado, o KN-003 experimental de aproximadamente 43,6 mil parâmetros, ausência de corpus treinado e ausência de execução Gradle/JUnit confirmada. O plano foi dividido em portões: CI/build; escalabilidade arquitetural; dados e treinamento; ligação da inferência ao chat; memória/ferramentas; laboratório; multimodal; validação final no Poco F6.

A criação deste documento é uma entrega de arquitetura e auditoria, não uma implementação das capacidades listadas. Nenhum novo teste de aplicativo foi executado por esta alteração documental.


## 21. Redução do custo do protocolo de resposta e recuperação do build Android

### Saída final em texto natural
Foi ampliado o parser KELLVYN/1 para aceitar dois formatos em decisões FINAL:
- \`TEXT\t<hex UTF-8>\`: formato antigo, mantido para compatibilidade.
- \`TEXT_RAW\t<texto UTF-8 em uma linha>\`: novo formato natural, limitado a 8.000 bytes e sem caracteres de controle.

Os campos de CALL_TOOL, PLAN e DELEGATE continuam em hexadecimal, preservando a separação de campos e evitando que conteúdo arbitrário altere a estrutura. TEXT_RAW rejeita quebras de linha, NUL e outros caracteres de controle; a resposta não é executada como instrução nem altera a semântica de ferramentas.

O prompt do adaptador KN-003 agora instrui FINAL a produzir TEXT_RAW natural e mantém HEX para os campos estruturados. Isso elimina a expansão aproximada de 2x do hexadecimal em respostas finais, mas não remove o limite de tokens da geração nem prova que o modelo aprendeu a emitir esse formato.

Testes adicionados ao código:
- aceita resposta natural UTF-8 com acentos e pontuação;
- continua aceitando o formato HEX legado (teste existente);
- rejeita resposta multilinha e caracteres de controle em TEXT_RAW;
- rejeita texto acima do teto de 8.000 bytes.

Commits:
- \`8b9bac7c73e82f29bf3bc4390a4e87d5697d55fd\` — parser compatível com TEXT_RAW.
- \`1d7beb64648b1f6e5c8a38a0e6cc58d60bc93873\` — prompt de inferência atualizado.
- \`e347fdab11c725080668cf2f209f5a9cc52440ff\` — testes de formato natural.
- \`6f4414cd0a1b0ac59c546c59fd932c7714c38727\` — teste do limite de bytes.

**Validação:** os testes foram escritos e os arquivos foram relidos no GitHub; não foram executados por Gradle/JUnit nesta sessão.

### Diagnóstico adicional do ambiente de build
A branch contém \`android/build.gradle\` com Android Gradle Plugin 8.13.2 e Kotlin Android plugin 2.3.20; \`android/app/build.gradle\` usa compileSdk 35, e \`android/settings.gradle\` inclui o módulo \`:app\`. Não foram encontrados \`gradlew\`, \`gradlew.bat\`, \`gradle/wrapper/gradle-wrapper.properties\` ou \`gradle-wrapper.jar\` nos caminhos usuais da branch.

A documentação oficial Android declara que AGP 8.13 exige no mínimo Gradle 8.13. A action oficial \`gradle/actions/setup-gradle\` permite instalar uma versão explícita de Gradle e executar builds em projetos sem wrapper. Fonte: https://developer.android.com/build/releases/about-agp e https://github.com/gradle/actions/blob/main/docs/setup-gradle.md.

Para remover a dependência de um wrapper ausente, foi criada uma execução independente:
- \`.github/workflows/kellvyn-android-validation.yml\`
- provisiona Java 17, Android SDK/API 35 e Gradle 8.13;
- executa \`:app:testDebugUnitTest\` e \`:app:assembleDebug\`;
- publica relatórios de teste e APK debug como artifacts quando gerados;
- é acionada na branch de pesquisa, em PR para main e manualmente.

Commit: \`1ec3144d840b39a49148a6ce9c5b19df1331db31\`.

**Estado:** workflow criado, ainda sem resultado de execução verificado nesta auditoria. O job anterior observado continua classificado como falha sem steps/logs; isso não é prova de erro Kotlin nem de build aprovado. Próximo passo objetivo: observar a nova execução e corrigir qualquer erro real de ambiente, compilação ou teste mostrado pelos logs.

### Por que não foi iniciada agora a expansão para vários blocos
O arquivo KN-003 declara explicitamente ser um Transformer de bloco único e reserva multi-block depth, backpropagation por camada e caches por camada para um marco arquitetural posterior. Fazer essa migração sem um build/teste executável arriscaria quebrar checkpoints, gradientes e inferência sem detectar o defeito. A expansão de camadas continua necessária; foi deliberadamente colocada depois da recuperação do ciclo de validação.
