# Estudo comparativo de IAs e tradução para o laboratório Kellvyn

**Data:** 20/11/2026  
**Branch:** `kellvyn-rnd/polyglot-lab-foundation-20261119`  
**Escopo desta atualização:** pesquisa e documentação; nenhum código Kotlin/Java, modelo neural, runtime, interface ou teste existente foi alterado.  
**Objetivo:** estudar capacidades públicas de IAs e ferramentas atuais para orientar o laboratório completo da Kellvyn, preservando o avanço já existente no KN e no trabalho Android Studio.

## 1. Decisões que governam este estudo

1. Kellvyn tem uma única inteligência central: KN. Não criar cérebros concorrentes, modelos especialistas independentes ou delegação multiagente como requisito.
2. Ferramentas, motores de mídia, conectores e executores são adaptadores subordinados ao KN. Eles executam operações; não tomam decisões cognitivas em nome do KN.
3. A interface aprovada permanece preservada. Esta frente não altera UI, navegação, animações ou controles.
4. O cérebro neural conversacional deve ser próprio, com pesos inicializados e treinados pelo projeto. Pesos, checkpoints e adapters de Gemma, Gemini, Claude, GPT, Llama, Qwen ou qualquer outro modelo externo não entram como cérebro.
5. Capacidades remotas podem ser estudadas como referência, mas não podem virar dependência obrigatória, paga ou hospedada para a experiência básica local/offline.
6. Não confundir capacidade demonstrada pelo produto de referência com capacidade já implementada pela Kellvyn.
7. Toda integração precisa de contrato, licença/proveniência, orçamento de recursos, política de permissão, teste e evidência verificável.

## 2. O que o estudo confirma sobre as referências

### 2.1 Android Studio com Gemini Agent Mode

**Capacidades documentadas:** recebe um objetivo de alto nível, formula um plano multi-etapas, pesquisa arquivos, usa ferramentas da IDE, altera múltiplos arquivos, executa builds e itera em correções. Pode implantar no dispositivo conectado, inspecionar a tela, capturar screenshots, consultar Logcat e interagir com o app via ADB. O usuário pode revisar/aprovar alterações.

**O que isso ensina ao KN:** a inteligência precisa trabalhar com um ambiente real, não apenas produzir texto ou código. O ciclo essencial é objetivo → plano → ferramentas → execução → observação → correção → verificação. A IDE fornece ferramentas Android especializadas; a competência do agente depende da combinação do modelo com contexto, ferramentas e ambiente.

**Tradução Kellvyn:** manter o trabalho Android Studio já feito; usar os contratos e adaptadores existentes; criar uma trilha de build/teste/dispositivo com resultados observáveis; nunca substituir o trabalho avançado por uma reimplementação paralela.

Fontes:
- https://developer.android.com/studio/gemini/agent-mode
- https://developer.android.com/studio/gemini/features
- https://android-developers.googleblog.com/2026/09/build-your-way-use-any-ai-agent-of-your-choice-in-android-studio.html

### 2.2 Claude Code

**Capacidades documentadas:** ferramentas de leitura/edição de arquivos e execução de comandos; regras persistentes; Skills sob demanda; hooks acionados por eventos; MCP para sistemas externos; permissões e isolamento de contexto; continuidade/compactação em tarefas longas.

**O que isso ensina ao KN:** procedimentos repetíveis devem ser descritos como Skills versionadas; verificações determinísticas devem ocorrer em hooks/guards do executor, não depender de o modelo lembrar; cada ferramenta precisa de permissão explícita; contexto longo exige resumos que preservem objetivo, restrições e decisões.

**Tradução Kellvyn:** catálogo de Skills próprio, carregado sob demanda; ganchos de pré-condição e pós-validação; integração MCP apenas como adaptador opcional; compactação de contexto testada para não perder proibições nem estado da tarefa.

Fonte:
- https://code.claude.com/docs
- https://support.claude.com/en/articles/14553413-claude-code-cheatsheet

### 2.3 ChatGPT / Codex / ferramentas de agentes

**Capacidades documentadas:** function calling, web search, file search, tool search, MCP, execução de código em sandbox, computer use, geração/edição de imagens e ciclos multi-turno com observações. Computer use não é apenas “clicar”: o sistema precisa capturar estado, enviar ações ao executor, receber nova observação e verificar o resultado. O runtime é responsável por sessão, permissões, limites e encerramento.

**O que isso ensina ao KN:** o cérebro decide *qual* operação pedir; o aplicativo valida o esquema e a autorização; um executor confinado realiza a operação; o resultado retorna como dado não confiável; o cérebro decide o próximo passo. A ferramenta não pode declarar sucesso por conta própria sem prova.

**Tradução Kellvyn:** interfaces tipadas e estáveis; seleção dinâmica de ferramentas para economizar contexto; sandbox por workspace; screenshot/observação após ações de interface; limites de passos/tempo/recursos; confirmação para efeitos colaterais; verificador que consulta o estado real.

Fontes:
- https://developers.openai.com/api/docs/guides/tools
- https://developers.openai.com/api/docs/guides/tools-computer-use
- https://developers.openai.com/api/docs/guides/tools-code-interpreter
- https://developers.openai.com/tracks/building-agents
- https://learn.chatgpt.com/docs/cloud

### 2.4 Replit Agent

**Capacidades documentadas:** Plan Mode para decompor objetivos e comparar abordagens sem modificar o projeto; Build Mode para implementar; criação de apps, bancos e lógica; teste do app em navegador real; simulação de uso, análise de falhas, correção iterativa e checkpoints/rollback. O ambiente é fortemente integrado à nuvem e aos serviços do Replit.

**O que isso ensina ao KN:** separar planejamento de execução; exigir aprovação do plano antes de mudanças relevantes; testar como usuário real; preservar checkpoints antes de mudanças; corrigir e repetir em vez de apenas dizer que o código parece correto.

**Tradução Kellvyn:** Plan/Execute como estados internos do KN, sem criar outra IA; workspace local; executor browser/app sob demanda; testes E2E e screenshots; rollback de artefatos. Não copiar a hospedagem Replit nem depender dela para APK/offline.

Fontes:
- https://docs.replit.com/pt/references/agent/overview
- https://docs.replit.com/pt/references/agent/plan-mode
- https://docs.replit.com/pt/references/agent/app-testing

### 2.5 Lovable

**Capacidades documentadas:** transformar descrições em aplicações com telas, lógica, banco de dados, autenticação e publicação; permitir refinamento por conversa ou edição direta; conectar serviços externos.

**O que isso ensina ao KN:** geração de aplicação precisa abranger requisitos, interface, lógica, persistência, integrações, testes e entrega. Gerar telas isoladas não equivale a criar um produto funcional.

**Tradução Kellvyn:** manter a UI aprovada; aproveitar o conceito de geração full-stack como meta do construtor interno; separar gerador de código, banco, compilador, testes e verificador. Lovable é referência, não runtime nativo nem dependência de hospedagem.

Fonte:
- https://lovable.dev/build/app-builder

### 2.6 Nano Banana / Gemini Image

**Capacidades documentadas:** geração e edição de imagens, refinamento conversacional, consistência de elementos/personagens e renderização de texto, com variantes voltadas a velocidade/custo e qualidade/controle. A oferta via Gemini API é remota e tem condições de acesso/custo próprias.

**O que isso ensina ao KN:** imagem exige mais que um prompt: entradas de texto e referência visual, edição localizada, preservação de elementos, iteração e validação visual. A ferramenta de imagem deve retornar artefato, dimensões, formato, metadados e proveniência.

**Tradução Kellvyn:** Image Studio modular com operações de criar, editar, recortar, compor, remover fundo, vetorizar e comparar versões. O KN formula e avalia a intenção; um motor de imagem executa. Para uso offline, começar por edição clássica local e avaliar modelos locais somente após benchmark; Nano Banana permanece referência/conector remoto opcional, nunca cérebro.

Fonte:
- https://deepmind.google/models/gemini-image/
- https://ai.google.dev/gemini-api/docs/pricing

### 2.7 Música e áudio

**Eleven Music:** gera faixas completas a partir de linguagem natural; permite controlar gênero, estilo, estrutura, instrumental/vocal e editar seções. A API documentada é paga e remota. Music v2.5 é a versão atual documentada; music_v1 tem retirada anunciada para 31/01/2027.

**O que isso ensina ao KN:** criação musical precisa de estrutura (intro, verso, refrão, ponte, final), gênero, andamento, instrumentação, letra/vocal e edição por seção. Gerar música não é o mesmo que cortar, mixar, converter ou masterizar áudio.

**Tradução Kellvyn:** Music Studio com compositor/arranjador de prompts, editor de timeline, mixer e renderizador; motores generativos online são conectores opcionais. Para offline, começar por edição, síntese simples e MIDI/arranjo programático; não afirmar geração neural local de alta qualidade sem prova no Poco F6.

Fontes:
- https://elevenlabs.io/docs/overview/capabilities/music
- https://elevenlabs.io/docs/eleven-api/guides/cookbooks/music

### 2.8 Vídeo e mundos interativos

**Veo:** geração de vídeo a partir de instruções e referências visuais é uma capacidade generativa especializada, tipicamente remota e dependente de modelo/versão, limites e custo.

**Genie 4 / Project Genie:** cria ambientes interativos em tempo real a partir de texto/imagens, com edição por linguagem natural, eventos e áudio. A disponibilidade documentada é limitada a planos/preview específicos; não é um motor Android local pronto.

**O que isso ensina ao KN:** vídeo requer roteiro, cenas, duração, continuidade visual, áudio, montagem e renderização. Mundo interativo exige estado espacial, câmera, física, eventos e observação contínua.

**Tradução Kellvyn:** Video Studio e 3D/World Studio separados por contratos de ferramenta; edição/transcodificação local com FFmpeg pode ser útil no telefone, mas geração neural de vídeo/mundo permanece remota/experimental até benchmark viável.

Fontes:
- https://deepmind.google/models/veo/
- https://deepmind.google/models/genie/
- https://blog.google/innovation-and-ai/models-and-research/google-deepmind/genie-4-now-available-project-genie/

### 2.9 Jogar e interagir com jogos: SIMA

**SIMA 2:** pesquisa da DeepMind sobre um agente que percebe ambientes 3D, interpreta instruções, raciocina sobre objetivos e age por controles visuais comuns. A publicação descreve limitações em tarefas longas, memória curta e ações motoras precisas. É pesquisa, não SDK pronto para incorporação.

**O que isso ensina ao KN:** jogar requer percepção visual repetida, memória do estado, planejamento de curto e longo prazo, ações discretas/contínuas, feedback, exploração e verificação de objetivo. Não basta reconhecer a tela ou disparar toques aleatórios.

**Tradução Kellvyn:** Game Interaction Studio com captura autorizada de tela, reconhecimento de elementos, estado de jogo, mapeamento de controles, política de ação, observação pós-ação e botão de interrupção. Começar com jogos/simulações próprias e ambientes de teste controlados; não prometer desempenho geral em qualquer jogo.

Fontes:
- https://deepmind.google/blog/sima-2-an-agent-that-plays-reasons-and-learns-with-you-in-virtual-3d-worlds/
- https://deepmind.google/blog/from-atari-to-eve-online-building-on-15-years-of-ai-research-in-games/

## 3. Automação no Android: o que é possível e o que não é

- AccessibilityService pode inspecionar elementos de acessibilidade e executar ações como clicar, rolar e acionar ações globais. Exige serviço habilitado explicitamente pelo proprietário e não substitui APIs específicas de cada app.
- MediaProjection permite capturar tela/áudio conforme o tipo de sessão, mas exige consentimento do usuário para cada sessão e pode ser revogada.
- Android restringe início de foreground services a partir do background; WorkManager é adequado para trabalho adiável/persistente, não para manter conexão interativa ilimitada.
- A política do fabricante (HyperOS) pode aplicar restrições adicionais de bateria e background.

**Implicação:** o laboratório deve oferecer automação observável, com níveis de permissão, sessão ativa visível quando exigido, cancelamento, limites, registro sem segredos e confirmação antes de ações irreversíveis. Não existe promessa técnica honesta de controle irrestrito ou execução 24/7 garantida no Poco F6.

Fontes:
- https://developer.android.com/guide/topics/ui/accessibility/service
- https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- https://developer.android.com/media/grow/media-projection
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/develop/background-work/background-tasks

## 4. Bots: diferenças importantes por plataforma

- **Telegram:** Bot API oferece long polling ou webhook; long polling é o candidato mais simples para uma prova de conceito móvel sem servidor público, sujeito a conectividade e limites do Android.
- **Discord:** Gateway WebSocket exige heartbeat, reconexão, sequenciamento, permissões e intents apropriadas.
- **WhatsApp:** a API oficial Business usa webhooks para receber eventos; operação bidirecional oficial normalmente exige endpoint público HTTPS. Isso conflita com a restrição de não depender de hospedagem externa.

**Implicação:** gerenciador de bots precisa guardar tokens em armazenamento seguro, validar credenciais, controlar permissões, deduplicar eventos, limitar taxa, reconectar com backoff e mostrar estado real. “Ativo” só pode significar conexão/worker confirmado. Não usar automação não oficial de WhatsApp Web como substituto estável.

Fontes:
- https://core.telegram.org/bots/api
- https://discord.com/developers/docs/topics/gateway
- https://developers.facebook.com/documentation/business-messaging/whatsapp/overview/

## 5. Arquitetura de laboratório recomendada

O laboratório é uma infraestrutura de ferramentas para o KN, não uma segunda inteligência. Cada módulo declara um manifesto:

- identificador e versão;
- capacidades e operações;
- formatos de entrada/saída;
- dependências locais/remotas;
- permissões e risco;
- orçamento estimado de RAM, CPU, GPU, armazenamento, temperatura e bateria;
- timeout, cancelamento e idempotência;
- requisitos de rede e credenciais;
- evidência necessária para comprovar sucesso;
- procedimento de liberação de recursos e recuperação.

### Domínios de capacidade a cobrir

1. **Estudo e pesquisa:** web, documentação, cursos, papers, comparação de ferramentas, proveniência e atualização temporal.
2. **Software e engenharia:** indexação, navegação, edição multi-arquivo, diff, lint, compilação, testes, debugging e rollback.
3. **Android e APK:** Gradle, SDK, build, assinatura, instalação, ADB, screenshots, Logcat e teste no Poco F6.
4. **Criação de aplicações:** requisitos, arquitetura, UI, lógica, banco, integrações, testes e entrega.
5. **Bots e integrações:** Telegram, Discord e conectores oficiais com limites reais.
6. **Automação/computer use:** navegador, acessibilidade, tela, teclado, mouse/toques e workflows autorizados.
7. **Jogos e simulação:** percepção, estado, planejamento de ação, controle e avaliação em ambientes permitidos.
8. **Imagem e design:** geração, edição raster/vetor, composição, inspeção e comparação.
9. **Áudio e voz:** reconhecimento de fala, síntese, diarização, separação, efeitos e edição.
10. **Música:** composição, letra, arranjo, MIDI, geração, mixagem e exportação.
11. **Vídeo:** roteiro, cenas, edição, legendas, áudio, transcodificação e exportação.
12. **3D/CAD e mundos:** modelagem, cena, materiais, animação, exportação e simulação.
13. **Dados e documentos:** leitura, extração, análise, planilhas, relatórios e visualizações.
14. **Segurança e auditoria:** análise de dependências, permissões, segredos, sandbox, integridade e proveniência.

Os domínios não exigem que todos os motores fiquem carregados. O KN seleciona o adaptador necessário, verifica pré-condições, executa uma operação por vez por padrão e descarrega recursos ociosos.

## 6. O que já existe no repositório e deve ser preservado

Na branch experimental `kellvyn-rnd/polyglot-lab-foundation-20261119`, a inspeção encontrou:

- `KellvynCognitiveKernel.kt`: loop central único, com delegação desabilitada; ferramentas e memória são capacidades sob autoridade do KN.
- `KellvynTaskPlanner.kt`: valida planos, dependências, riscos, ferramentas registradas, pré-condições, aprovação e recuperação.
- `KellvynStudioModuleManager.kt`: registra manifestos sem carregar todos os motores; carrega módulo sob demanda, mantém leases e permite liberar recursos ociosos.
- `KellvynResourceGovernor.kt` e runtime/broker: controlam orçamento, pressão de memória/heap e condições térmicas.
- `KellvynDecisionProtocol.kt`: protocolo estruturado estrito para decisões neurais, chamadas de ferramenta e planos.
- memória local criptografada, recuperação de contexto, traces, checkpoints e ferramentas de workspace já possuem implementações no repositório.
- commits de 19/11 adicionaram o kernel de cognição central, desativação da delegação como padrão e estimador determinístico de capacidade do Transformer.

**Importante:** a existência desses módulos não significa que todos os estúdios estejam completos ou integrados ponta a ponta. A documentação atual classifica UI/UX Studio, Image Studio e 3D/CAD Studio como não concluídos; Android Build Status é diagnóstico READ_ONLY, não uma IDE Gradle completa.

## 7. Estado real do cérebro KN e proteção do trabalho Android Studio

Na branch inspecionada:

- o modelo neural atual é KN-003, Transformer decoder-only de um único bloco, com atenção causal multi-head/RoPE, Pre-LayerNorm, residual e SwiGLU;
- a classe do modelo registra explicitamente que profundidade multi-bloco, backpropagation entre camadas e caches por camada são marcos seguintes;
- `KellvynRuntime.isReady()` retorna `false`; o cérebro não está conectado ao chat principal;
- o treinamento atual é experimental e depende de exemplos locais autorizados; não há evidência de corpus geral, checkpoint conversacional aprovado ou benchmark comportamental amplo;
- o estimador de capacidade é uma ferramenta de planejamento de memória/parâmetros, não uma prova de que o modelo caiba ou funcione no aparelho em carga real;
- o controle de sessão exige confirmar o HEAD e ler commits posteriores antes de editar; a branch/PR do laboratório permanece experimental e não deve ser mesclada automaticamente.

**Regra de preservação:** não reimplementar o kernel, planner, módulo manager, resource governor, protocolo, memória ou traces. Primeiro auditar código, testes, integração e CI. O próximo avanço neural deve ser feito como uma alteração isolada, com compatibilidade de checkpoint e testes de gradiente por camada. Nenhum arquivo do trabalho Android Studio deve ser sobrescrito por suposição.

## 8. Sequência técnica recomendada depois do estudo

1. Confirmar o HEAD atual da branch e os commits mais recentes do Android Studio.
2. Criar uma linha de base imutável: hashes, diff, testes existentes, CI e estado de integração.
3. Auditar a fundação KN-003: shapes, inicialização, forward, backward, gradientes, optimizer, checkpoints, tokenizer e KV cache.
4. Projetar KN-004 multi-bloco preservando KN-003 como formato legado: configuração de profundidade, 2 blocos primeiro, depois profundidades maiores apenas quando testes e orçamento permitirem.
5. Implementar retropropagação através de todos os blocos e gradientes por grupo/camada; testar finite differences, determinismo, NaN/Inf, loss e checkpoint round-trip.
6. Implementar KV cache por camada e provar equivalência entre geração incremental e forward completo.
7. Medir parâmetros, ativações, gradientes, buffers, JVM/Android overhead, latência e temperatura; comparar estimativa com medições reais no Poco F6.
8. Só depois treinar com corpus autorizado, suficientemente grande e separado em TRAIN/VALIDATION/TEST; medir generalização e comportamento.
9. Integrar ao chat somente após checkpoint elegível, protocolo estável e benchmark independente.
10. Evoluir os estúdios multimodais e automações como adaptadores carregados sob demanda, começando pelas capacidades que podem operar localmente.

## 9. Critério de aceite do estudo

Este estudo é uma síntese documental e uma orientação de engenharia, não implementação nem validação em aparelho. Uma capacidade só pode ser marcada como concluída quando houver:

- fonte oficial/primária e versão consultada;
- decisão arquitetural registrada;
- código e testes no repositório;
- build/testes realmente executados;
- evidência de execução e verificação independente;
- medição de recursos quando usar o Poco F6;
- limitações e dependências declaradas.

## 10. Fontes primárias consultadas em 20/11/2026

- Android Studio Agent Mode: https://developer.android.com/studio/gemini/agent-mode
- Android Studio agent tools/features: https://developer.android.com/studio/gemini/features
- Android Studio Bring Your Own Agent: https://android-developers.googleblog.com/2026/09/build-your-way-use-any-ai-agent-of-your-choice-in-android-studio.html
- Claude Code: https://code.claude.com/docs
- Claude Code tools/skills/MCP/hooks: https://support.claude.com/en/articles/14553413-claude-code-cheatsheet
- OpenAI tools: https://developers.openai.com/api/docs/guides/tools
- OpenAI computer use: https://developers.openai.com/api/docs/guides/tools-computer-use
- OpenAI code interpreter: https://developers.openai.com/api/docs/guides/tools-code-interpreter
- Codex Cloud: https://learn.chatgpt.com/docs/cloud
- Replit Agent: https://docs.replit.com/pt/references/agent/overview
- Replit Plan Mode: https://docs.replit.com/pt/references/agent/plan-mode
- Replit App Testing: https://docs.replit.com/pt/references/agent/app-testing
- Lovable App Builder: https://lovable.dev/build/app-builder
- Nano Banana: https://deepmind.google/models/gemini-image/
- Eleven Music: https://elevenlabs.io/docs/overview/capabilities/music
- SIMA 2: https://deepmind.google/blog/sima-2-an-agent-that-plays-reasons-and-learns-with-you-in-virtual-3d-worlds/
- Genie 4: https://deepmind.google/models/genie/
- Android AccessibilityService: https://developer.android.com/guide/topics/ui/accessibility/service
- Android MediaProjection: https://developer.android.com/media/grow/media-projection
- Android background tasks: https://developer.android.com/develop/background-work/background-tasks


## 11. Atualização da pesquisa com documentação oficial adicional — 20/11/2026

### Android Studio: o ponto mais importante para preservar a evolução existente

Em 28/10/2026, Android Studio Rabbit 2 passou a oferecer **Bring Your Own Agent (BYOA)**: mais de 40 agentes compatíveis podem ser escolhidos no próprio Android Studio, incluindo Claude Agent, Codex, Junie e Google Antigravity. Esses agentes recebem acesso às ferramentas Android Studio, incluindo Emulator, Build Systems e Logcat, além do gerenciador de Android Skills. Isso significa que o progresso visto no Android Studio pode envolver um agente ACP externo usando o ambiente de desenvolvimento da IDE; não se deve presumir que o executor seja necessariamente o Gemini nativo.

O Gemini Agent Mode também documenta ciclo de plano → seleção de ferramentas → ação → retorno/avaliação, com aprovações e threads paralelas; as skills oficiais são carregadas sob demanda para não ocupar o contexto imediato. Para a Kellvyn, o aprendizado é: manter a IA externa como ambiente de execução/engenharia enquanto aproveitamos o diff e as provas; não reescrever o trabalho dela nem confundir a IDE com o cérebro KN.

Fontes:
- https://developer.android.com/blog/posts/ai-agents-android-studio-integration
- https://developer.android.com/studio/gemini/agent-mode
- https://developer.android.com/studio/gemini/skills
- https://developer.android.com/studio/run

### Lovable: ciclo completo e sincronização Git

A documentação atual distingue três modos:
- Chat: conversar e discutir, sem alterar código;
- Plan: investigar o projeto e escrever um plano estruturado para revisão/edição/aprovação antes de escrever código;
- Build: executar as alterações, explorar a codebase, editar múltiplos arquivos e verificar o resultado.

O Browser Testing atua em navegador virtual real, com cliques, formulários, navegação, screenshots, console/network logs, erros de runtime e tamanhos de tela. A sincronização GitHub é bidirecional e permite branches, PRs, revisão e trabalho em IDE externa.

**Tradução KN:** separar investigação, plano aprovado, patch e prova; preservar a possibilidade de usar branch/diff; o browser test é útil para a prévia web, mas não substitui build Android, emulador nativo ou Poco F6.

Fontes:
- https://docs.lovable.dev/features/plan-mode
- https://docs.lovable.dev/features/agent-mode
- https://docs.lovable.dev/features/browser-testing
- https://docs.lovable.dev/integrations/github

### Replit: limite importante do App Testing

O Replit Agent atual documenta Plan Mode, modos Lite/Economy/Power, testes e checkpoints. Porém, a própria documentação do App Testing lista suporte para aplicativos web JavaScript Full Stack e Python Streamlit. Portanto, o replay/browser testing do Replit é uma referência para validar fluxos de web apps, não uma prova de que ele consiga testar um APK Android nativo sem ferramentas Android próprias.

Fontes:
- https://docs.replit.com/pt/references/agent/overview
- https://docs.replit.com/pt/references/agent/plan-mode
- https://docs.replit.com/pt/references/agent/app-testing
- https://docs.replit.com/pt/references/version-control/checkpoints-and-rollbacks

### Claude Code e Codex: padrões úteis para o KN

Claude Code documenta MCP para conexão com ferramentas externas, hooks determinísticos que podem bloquear/validar ações, Skills reutilizáveis e checkpoints/rewind. Codex documenta Skills como diretórios versionados de instruções/arquivos de apoio, além de function calling, shell, computer use e sandbox. Computer use exige um driver fornecido pela aplicação, estado de sessão preservado, capturas de tela de retorno, permissões, limites e verificação do resultado real.

**Tradução KN:** usar Skills para procedimentos operacionais carregados sob demanda; hooks/guards para validações determinísticas fora do modelo; tool registry com permissões e schemas; executor de computador que devolve screenshot/estado para o próximo ciclo; não depender de serviços/API hospedados para o modo offline.

Fontes:
- https://support.claude.com/pt/articles/14554922-faq-do-claude-code
- https://developers.openai.com/api/docs/guides/tools-skills
- https://developers.openai.com/api/docs/guides/tools-computer-use

### Nano Banana: identificar a versão real, não usar um nome de marketing como API

A página Google DeepMind atualmente apresenta a família com Nano Banana 2 Lite, Nano Banana 2 e Nano Banana Pro. A tabela da Gemini API identifica:
- `gemini-3.1-flash-image` como Nano Banana 2;
- `gemini-3.1-flash-lite-image` como Nano Banana 2 Lite;
- versões Pro com capacidades/condições diferentes.

A API é remota, com cobrança por tokens/imagem conforme resolução e modalidade. A tabela oficial consultada não oferece nível gratuito para esses modelos de imagem no endpoint de API. Isso reforça que Nano Banana é referência/conector online opcional, não motor offline nem cérebro KN.

Fontes:
- https://deepmind.google/models/gemini-image/
- https://ai.google.dev/gemini-api/docs/pricing

### SIMA 2 e Genie/Project Genie: separar pesquisa de ferramenta disponível

SIMA 2 demonstra percepção visual, instruções de linguagem, raciocínio e ações em jogos 3D; a publicação também declara limitações em tarefas longas, memória curta e precisão de controles de baixo nível. É uma pesquisa limitada, não uma biblioteca Android pronta.

Project Genie, segundo a página oficial de ajuda consultada, é um protótipo de pesquisa com acesso por assinaturas Google AI Ultra e lista de espera/rollout para Pro. A página identifica latência e controle imperfeito como limitações conhecidas. É referência para arquitetura de mundos e loops interativos, não um motor local incluível no APK.

Fontes:
- https://deepmind.google/blog/sima-2-an-agent-that-plays-reasons-and-learns-with-you-in-virtual-3d-worlds/
- https://support.google.com/labs/answer/16875695

### Música: atualização de versão

A documentação Eleven Music informa que `music_v1` será removido em 31/01/2027 e recomenda `music_v2_5`. A geração permite controlar gênero, estrutura, voz/instrumental e editar seções. A Music API exige plano pago. Portanto, ela é uma referência/conector remoto opcional; o primeiro conjunto offline de Kellvyn deve priorizar edição, mixagem, conversão, MIDI e arranjo simples, sem prometer geração neural musical avançada local.

Fonte:
- https://elevenlabs.io/docs/overview/capabilities/music

## 12. Princípios que passam a ser gates obrigatórios do laboratório

1. **Não mexer no código neural do Android Studio antes de reconciliar o HEAD, o diff, os testes e os arquivos que o agente já modificou.**
2. Registrar hash e commit-base dos arquivos neural, runtime, pipeline, store, tokenizer, inferência e testes antes de cada etapa.
3. Planejar com base em estado observado; não inferir progresso por texto de conversa nem por nome de branch.
4. Toda alteração deve ficar numa branch isolada; nenhuma promoção para `main` sem autorização do proprietário.
5. Distinguir ferramenta detectada, ferramenta disponível, ferramenta executada e ferramenta validada.
6. Ferramentas de mídia e automação são adaptadores subordinados ao mesmo KN; não introduzir cérebros separados.
7. Modelo local/remoto de referência dentro de ferramenta de desenvolvimento não se converte em modelo-base do KN. A proibição de pesos de terceiros como cérebro do produto permanece absoluta.
8. Para cada ferramenta, registrar versão, origem, licença/termos, custos, dependências de rede, limites de execução e requisitos de memória/CPU/GPU/bateria.
9. A oficina deve carregar módulos sob demanda e liberar recursos ao terminar; não manter todos os motores pesados em RAM.
10. Build, testes unitários, testes instrumentados, instalação, execução no emulador e execução no Poco F6 são gates distintos.

## 13. Estado desta atualização

- Pesquisa documental comparativa ampliada e registrada neste arquivo.
- Nenhum arquivo da interface foi alterado.
- Nenhum código Kotlin/Java do KN-003 foi alterado nesta atualização.
- A branch `kellvyn-rnd/kn004-multiblock-20261120` é uma linha isolada de trabalho; a branch Android Studio/laboratório original permanece preservada.
- A pesquisa conclui a etapa de levantamento inicial; o avanço seguinte deve ser o desenho e a implementação isolada do KN-004 multi-bloco, sem promover o runtime e sem ligar o chat antes dos gates técnicos e comportamentais.
