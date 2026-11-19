# Estudo web para evolução do cérebro KN — 19/11/2026

**Escopo autorizado:** estudar primeiro; não modificar código do KN nesta etapa.  
**Direção aprovada:** encerrar a expansão do laboratório de agentes e concentrar a evolução no cérebro central KN. Não criar novos agentes nem arquitetura multiagente. As ferramentas serão adaptadores subordinados ao KN; os módulos de agentes já existentes ficam preservados como histórico/legado até auditoria, sem expansão ou exclusão destrutiva.  
**Objetivo:** fundamentar a arquitetura que permita ao KN pesquisar, planejar, escolher ferramentas, executar, observar, depurar, comparar alternativas, verificar resultados e acumular conhecimento reutilizável.

## 1. Síntese executiva

O ganho principal não virá de adicionar centenas de ferramentas nem de multiplicar agentes. Virá de combinar:

1. Uma inteligência central capaz de interpretar objetivos, manter contexto, produzir planos e escolher ações.
2. Um ciclo de controle observável: planejar → executar uma ação tipada → observar o resultado real → avaliar → corrigir ou concluir.
3. Ferramentas especializadas sob demanda, sem ficarem todas carregadas simultaneamente.
4. Pesquisa e memória fundamentadas em fontes, com data, licença, confiança e evidência de validação.
5. Ambientes isolados para experimentos, com diff, testes, checkpoints e rollback.
6. Limites de segurança e recursos para proteger os dados, a aplicação e o Poco F6.

A pesquisa da Anthropic recomenda começar com um LLM aumentado por recuperação, ferramentas e memória, e só introduzir maior complexidade quando necessário. Também descreve o padrão evaluator-optimizer e enfatiza feedback do ambiente, limites de iteração, sandbox e guardrails. Isso sustenta uma arquitetura central única, sem exigir agentes independentes concorrentes.  
Fonte: https://www.anthropic.com/research/building-effective-agents

## 2. Arquitetura cognitiva a estudar e implementar

### 2.1 KN como autoridade única

O KN deve interpretar o pedido e suas restrições; determinar critérios objetivos de conclusão; identificar o que sabe e o que precisa pesquisar; montar e atualizar um plano; selecionar ferramentas a partir de um catálogo controlado; interpretar resultados de ferramentas como dados não confiáveis; decidir continuar, tentar outra abordagem, pedir autorização ou interromper; validar o resultado final e explicar evidências e limitações.

O executor de ferramentas não será uma segunda inteligência: será um conjunto de funções determinísticas, com contratos e permissões explícitos. Não haverá transferência de autoridade para agentes paralelos.

### 2.2 Ciclo central de execução

1. **Entender:** converter o pedido em objetivo, contexto, restrições e critérios de sucesso.
2. **Planejar:** dividir em etapas pequenas, dependências e riscos; identificar lacunas de conhecimento.
3. **Pesquisar:** buscar o necessário, priorizando documentação oficial, código-fonte, benchmarks e cursos técnicos.
4. **Selecionar:** escolher uma ferramenta compatível com a tarefa, aparelho, rede e permissões.
5. **Executar:** executar uma operação por vez em ambiente adequado, com timeout e limites.
6. **Observar:** recolher retorno real, logs, arquivos, resultados de testes e métricas.
7. **Avaliar:** comparar com os critérios definidos antes da execução.
8. **Recuperar:** localizar a causa, gerar hipóteses, testar alternativa e reverter quando necessário.
9. **Consolidar:** registrar descoberta, fonte, versão, teste, limitações e condições de validade.
10. **Entregar:** mostrar o que foi feito, o que foi validado e o que continua pendente.

Não aceitar “parece funcionar” como aprovação. Uma alteração de código só passa quando testes relevantes e verificação de regressão sustentarem o resultado.

### 2.3 Catálogo de ferramentas

Cada ferramenta deve ter manifesto tipado: identificador, versão, finalidade, entradas e saídas; capacidades exigidas e risco; dependência de internet, credenciais, consentimento ou serviço ativo; carga estimada de RAM, CPU, armazenamento e bateria; timeout, cancelamento, idempotência e política de repetição; localização do sandbox e estratégia de rollback; evidência mínima para declarar sucesso.

Carregar ferramenta leve quando necessária; motores pesados apenas sob demanda. Manter verificação independente do gerador de ação, mas não um agente separado.

## 3. Pesquisa, estudo e aprendizado orientado por necessidade

O Centro de Estudos deve ter dois caminhos:

**Estudo acionado por tarefa:** ao receber um pedido de construir aplicativo, criar bot, editar vídeo ou gerar imagem, o KN identifica lacunas e pesquisa documentação, exemplos, licenças, requisitos e limitações antes de decidir o caminho.

**Estudo proativo limitado:** iniciar pesquisa quando detectar documentação desatualizada, falha recorrente, dependência vulnerável, nova versão relevante ou alternativa potencialmente melhor. Não fazer navegação incessante nem consumir bateria/dados sem objetivo.

Cada registro de conhecimento precisa guardar a afirmação aprendida e data da consulta; URL, título, organização/autoria e data da fonte; tipo de fonte (documentação oficial, paper, repositório, curso ou experiência); versão da ferramenta/modelo e licença; confiança e conflitos entre fontes; se foi apenas lido, reproduzido em experimento ou validado no dispositivo; data para rever conteúdo que pode envelhecer.

**Memória não é treinamento de pesos.** Guardar uma solução validada na memória técnica não significa que o KN aprendeu novos parâmetros. Fine-tuning, treinamento neural e atualização de código são processos separados, versionados e com gates.

### Navegação sem WebView

O app final não precisa incorporar WebView. O KN pode consultar páginas por cliente HTTP nativo quando a página permitir; extrair texto, metadados e links; abrir páginas para inspeção humana no navegador externo/Custom Tabs quando necessário; guardar fontes e trechos com atribuição; e utilizar automação de navegador somente quando houver mecanismo permitido, com permissões explícitas e observabilidade.

Páginas que exigem JavaScript, login, CAPTCHA ou proteção contra robôs podem não ser legíveis pelo cliente HTTP. Nesse caso, o KN deve abrir o navegador para o usuário ou declarar a limitação — não fingir que navegou.

## 4. Construção de software, depuração e reparo

### Referências estudadas

- **Android Studio Gemini Agent Mode:** planeja trabalho em múltiplas etapas, escolhe ferramentas disponíveis, pesquisa arquivos, altera vários arquivos e itera na correção de bugs; mudanças podem exigir aprovação. Referência forte para desenvolvimento Android.  
  https://developer.android.com/studio/gemini/agent-mode
- **Replit Agent:** Plan mode divide projetos em tarefas ordenadas e compara abordagens; o Agent escreve, testa, corrige e utiliza checkpoints/rollback. Copiar os princípios, não a infraestrutura hospedada.  
  https://docs.replit.com/pt/references/agent/overview
- **Claude Code:** MCP conecta ferramentas externas; hooks executam verificações determinísticas antes/depois de eventos. Útil para skills sob demanda, integração e validação automática.  
  https://support.claude.com/pt/articles/14554922-faq-do-claude-code
- **Codex sandbox:** separar limite técnico do sandbox da política de aprovação; comandos e ferramentas herdam os limites do ambiente. Princípio essencial para proteger o workspace.  
  https://developers.openai.com/pt-BR/docs/sandboxing
- **Cursor Agent:** catálogo explícito de busca, leitura, edição, terminal, MCP e regras de execução; ferramentas podem ser habilitadas/desabilitadas e guardrails controlam execução.  
  https://docs.cursor.com/en/agent/tools
- **Google Jules:** cada tarefa é executada em VM nova, com clone do repositório, instalação de dependências e ambiente configurável. Boa referência de isolamento, mas não é infraestrutura local Android.  
  https://jules.google/docs/
- **SWE-agent / SWE-bench:** problemas reais de repositórios são avaliados aplicando patch e rodando a suíte de testes; a trajetória de ações é inspecionável.  
  https://github.com/SWE-agent/SWE-agent  
  https://www.swebench.com/verified

### Ciclo de depuração do KN

1. Reproduzir o defeito com entrada, ambiente e commit fixados.
2. Capturar stacktrace, Logcat, saída de compilação, testes e estado do sistema.
3. Encontrar a menor área provável; verificar dependências e regressões recentes.
4. Produzir hipóteses alternativas e ordená-las por evidência, custo e risco.
5. Criar checkpoint e área de trabalho isolada.
6. Aplicar uma correção pequena e explicável.
7. Executar testes específicos, testes de módulo, regressão e build.
8. Comparar comportamento antes/depois.
9. Rejeitar, ajustar ou reverter se a falha persistir ou houver regressão.
10. Registrar a causa e o teste que impede reincidência.

Benchmarks como SWE-bench ajudam a avaliar capacidade de reparo, mas não substituem a suíte Kellvyn nem provam compatibilidade com Android/Poco F6.  
Fonte: https://www.swebench.com/verified

## 5. Base de inteligência: achado crítico

A auditoria existente descreve o KN-003 com configuração padrão de 43.648 parâmetros, embedding 32, um único bloco Transformer, contexto de 256 tokens e nenhum pacote de pesos pré-treinados incorporado ao projeto. A própria auditoria corretamente classifica isso como prova de conceito, não como LLM geral.

**Conclusão:** não é tecnicamente responsável prometer que somente mais ferramentas e melhores prompts farão essa rede experimental virar um cérebro de qualidade geral. A decisão de arquitetura deve comparar explicitamente:
- continuar o modelo próprio do zero, com pesquisa, dados, compute e cronograma necessários;
- integrar como runtime local um modelo aberto pré-treinado, mantendo controle, identidade, memória, ferramentas e interface Kellvyn próprios;
- permitir backend online opcional, sem torná-lo requisito para a experiência básica offline.

A documentação Google apresenta implantação de modelos Gemma no Android via MediaPipe LLM Inference/LiteRT e AI Edge Gallery. MLC LLM também documenta Android com backend OpenCL em Adreno. llama.cpp documenta OpenCL para Adreno, mas a matriz pública de hardware verificado lista alguns chips Snapdragon e **não prova compatibilidade específica com o Snapdragon 8s Gen 3 do Poco F6**. Logo, qualquer runtime precisa de PoC e benchmark no aparelho real.  
Fontes:  
https://ai.google.dev/gemma/docs/integrations/mobile  
https://ai.google.dev/gemma/docs/conversions/hf-to-mediapipe-task?hl=pt-br  
https://github.com/mlc-ai/mlc-llm  
https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/OPENCL.md

O Google AI Edge Gallery é uma referência útil para testar modelos locais e comparar velocidade/qualidade no hardware, não uma prova de que qualquer modelo ou tamanho será adequado ao aparelho.  
https://play.google.com/store/apps/details?id=com.google.ai.edge.gallery

## 6. Android, execução local e recursos

- **WorkManager:** opção preferencial para trabalho adiável/persistente; o Android recomenda evitar polling constante e usar push quando adequado.  
  https://developer.android.com/develop/background-work/background-tasks
- **Foreground Services:** destinados a trabalho perceptível ao usuário, com notificação e restrições de início/execução. Não são uma licença para manter qualquer processo oculto indefinidamente.  
  https://developer.android.com/develop/background-work/services/fgs
- **Android 15+:** serviços dataSync e mediaProcessing têm limite agregado de seis horas por 24h em segundo plano para apps que os direcionam à API 35+, com callback obrigatório e parada. Android 16 também altera cotas para jobs iniciados por serviços em primeiro plano.  
  https://developer.android.com/develop/background-work/services/fgs/timeout  
  https://developer.android.com/develop/background-work/services/fgs/changes
- **Armazenamento:** modelos grandes não devem ser empacotados cegamente no APK. Usar instalação/transferência sob demanda, armazenamento de app com espaço verificado, cache separado e opção de remover modelos.  
  https://developer.android.com/training/data-storage/app-specific

O Resource Governor existente é relevante: o KN deve descarregar modelos e ferramentas quando ocioso, limitar concorrência, interromper tarefas pesadas em pressão térmica/memória e guardar progresso recuperável.

## 7. Bots e automação: viabilidade por plataforma

### Discord

A API Gateway usa conexão WebSocket e exige tratamento de heartbeat, reconexão, sequência, permissões e limites. Conteúdo de mensagens pode exigir Message Content Intent privilegiada, que deve ser habilitada e justificada; solicitar somente intents necessárias.  
Fontes:  
https://discord.com/developers/docs/topics/gateway  
https://support-dev.discord.com/hc/pt-br/articles/6207308062871-O-que-s%C3%A3o-inten%C3%A7%C3%B5es-privilegiadas

### Telegram

A Bot API aceita long polling com getUpdates ou webhooks. Long polling evita exigir endpoint público HTTPS no celular, tornando Telegram um primeiro candidato para PoC on-device. Ainda depende de serviço ativo, conectividade e limitações de background do Android.  
Fontes:  
https://core.telegram.org/bots/api  
https://core.telegram.org/bots/faq

### WhatsApp

A plataforma oficial é WhatsApp Business Platform/Cloud API. O recebimento de mensagens é baseado em webhooks; o servidor receptor precisa expor URL HTTPS pública e validar as requisições. Isso entra em conflito direto com a restrição de não depender de hospedagem externa. Sem servidor público, não prometer bot oficial bidirecional totalmente on-device. Não recorrer a scraping, automação de WhatsApp Web ou bibliotecas não oficiais como solução estável.  
Fontes:  
https://developers.facebook.com/documentation/business-messaging/whatsapp/overview/  
https://developers.facebook.com/docs/graph-api/webhooks/getting-started/?locale=pt_BR

### Consequência arquitetural

O KN precisa ter um gerenciador de bots com criação/configuração, validação de tokens, permissões mínimas, estado do serviço, heartbeat, reconexão com backoff, deduplicação de mensagens, rate limiting, logs sem segredos, comandos para pausar/retomar e testes de disponibilidade.

Porém, o requisito “sempre ativo” deve ser separado por plataforma e política do Android. No Poco F6, serviço em primeiro plano pode sustentar uma sessão enquanto permitido e visível, mas não garante uptime ilimitado. WorkManager serve para trabalho adiável, não para manter WebSocket em tempo real. Uma disponibilidade 24/7 confiável normalmente exige host sempre ligado; se hospedagem externa continuar proibida, a limitação precisa ser aceita e informada.

## 8. Imagem, vídeo, áudio e música

### Imagem

- **Nano Banana / Gemini Image:** família de geração e edição de imagens baseada em Gemini, com modelos Lite/rápido e Pro/maior precisão. A API tem cobrança e não deve virar dependência obrigatória offline.  
  https://deepmind.google/models/gemini-image/  
  https://ai.google.dev/gemini-api/docs/pricing
- **GPT Image:** API suporta geração, edição iterativa, referência de imagens e máscaras; exige serviço/API online e pode depender de verificação de organização.  
  https://developers.openai.com/api/docs/guides/image-generation
- **ComfyUI:** boa referência de workflows visuais com nós, modelos e operações reproduzíveis. A implementação local normalmente depende de ambiente de computação mais potente; não presumir execução completa no Poco.  
  https://github.com/comfyanonymous/ComfyUI_examples

### Vídeo

- **LTX-2.5:** pesos abertos, vídeo e áudio sincronizados; documentação recomenda no mínimo GPU NVIDIA com 32 GB+ VRAM, 32 GB RAM e 100 GB livres. Isso está muito acima do Poco F6.  
  https://docs.ltx.io/models/ltx-2-5  
  https://docs.ltx.io/open-source-model/getting-started/system-requirements/
- **Kling:** oferece modelos de vídeo/imagem e API paga por pacotes/unidades; não é motor local/offline.  
  https://kling.ai/dev/api  
  https://kling.ai/document-api/quickStart/userManual
- **Runway:** API centraliza vários modelos e autentica com chave; é serviço remoto.  
  https://docs.dev.runwayml.com/api/
- **Veo / Gemini video:** modelos remotos com recursos de vídeo e áudio; versões preview podem ser encerradas/substituídas, logo o catálogo precisa de versões e health checks.  
  https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/veo/3-1-generate

### Áudio e música

- **sherpa-onnx:** suporte Android offline para speech-to-text, text-to-speech, VAD, diarização, separação e outros recursos. Forte candidato para voz local modular.  
  https://github.com/k2-fsa/sherpa-onnx
- **whisper.cpp:** exemplo oficial de app Android; o repositório recomenda modelos tiny ou base para aparelhos Android. Candidato para transcrição offline, sujeito a teste de velocidade/memória.  
  https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android
- **MusicGen / AudioCraft:** geração de música aberta; o modelo medium de ~1,5B requer GPU com pelo menos 16 GB de memória para inferência. Não é viável presumir execução do modelo medium no Poco F6.  
  https://github.com/facebookresearch/audiocraft/blob/main/docs/MUSICGEN.md
- **FFmpeg:** referência para corte, concatenação, codecs, filtros e renderização local. Deve ser avaliado como motor de edição/transcodificação, distinto de um gerador de conteúdo.  
  https://ffmpeg.org/documentation.html

**Separação obrigatória:** gerar conteúdo por modelo generativo é diferente de editar, converter, mixar ou renderizar mídia. O Poco pode ser testado para edição/transcodificação e algumas funções de voz; geração avançada de vídeo/música deve ser tratada como remota/experimental até existir benchmark que prove o contrário.

## 9. Simulação e jogos

- **SIMA 2** pesquisa um sistema que percebe ambientes 3D, segue instruções, raciocina e aprende em mundos virtuais. É uma referência conceitual para loop percepção → ação → observação, não um SDK Android pronto para incorporar.  
  https://deepmind.google/blog/sima-2-an-agent-that-plays-reasons-and-learns-with-you-in-virtual-3d-worlds/
- **Genie 4** é uma pesquisa/plataforma recente de simulação interativa com controle por linguagem e áudio nativo; acesso ainda limitado segundo anúncios consultados.  
  https://deepmind.google/blog/genie-4-a-new-generation-of-world-simulation/

No Kellvyn, começar com simulações controladas e benchmarks de ferramentas; não carregar um modelo de mundo pesado no celular.

## 10. Cursos e trilhas de estudo prioritárias

1. **Android Basics with Compose / Android app architecture / testing:** Kotlin, arquitetura Android, rede, persistência, testes e boas práticas.  
   https://developer.android.com/courses
2. **Google Machine Learning Crash Course:** fundamentos de ML, treino, avaliação e modelos.  
   https://developers.google.com/machine-learning/crash-course
3. **OpenAI Learn — Building agents:** fundamentos e implementação, além de otimização e avaliação.  
   https://developers.openai.com/learn
4. **DeepLearning.AI:** cursos sobre memória para agentes, coding agents, tool execution em sandbox, avaliação de agentes, multimodalidade, imagem/vídeo, voz, on-device AI e MCP. Selecionar os cursos técnicos específicos em vez de consumir catálogo aleatoriamente.  
   https://www.deeplearning.ai/courses/
5. **Gemini/Android Studio Agent Mode:** documentação, tutorial e exemplos práticos de plano, ferramenta, alteração e reparo.  
   https://developer.android.com/studio/gemini/agent-mode
6. **SWE-agent tutorials + SWE-bench:** estudar experimentos de reparo, execução isolada e métricas.  
   https://swe-agent.com/  
   https://www.swebench.com/

A trilha deve ser orientada pelos bloqueios reais de implementação; cursos não substituem protótipos e testes no dispositivo.

## 11. Plataformas e produtos solicitados

- **Replit Agent:** estudar Plan mode, execução de tarefas, testes, skills e checkpoints. Não usar hospedagem Replit como requisito do APK Kellvyn.
- **Lovable:** estudar como transforma requisitos e contexto em protótipos/full-stack; manter o layout já aprovado intocado. Não usar como runtime final Android.
- **ChatGPT / Codex:** estudar sandbox, aprovações, execução de ferramentas, observabilidade e revisão de código.
- **Claude Code:** estudar MCP, hooks, skills e regras de projeto.
- **Gemini / Android Studio:** estudar Agent Mode, busca de arquivos, execução de ações e reparo iterativo.
- **Nano Banana:** estudar geração/edição multimodal; tratar API como remota, sujeita a custo/termos.
- **Dola:** a busca identificou um produto chamado Dola AI descrito como gerador de vídeos no navegador, mas ainda não foi verificada documentação pública suficiente de API, licença, modelos subjacentes ou estabilidade. Classificação: **observar e validar antes de qualquer integração**, não assumir que seja plataforma aberta.
- **Cursor, Jules, OpenHands, SWE-agent, Aider, ComfyUI, LTX, Kling, Runway, MusicGen, sherpa-onnx, whisper.cpp, n8n e SIMA 2:** referências por domínio; adoção dependerá de licença, compatibilidade, custo, recursos e prova de utilidade.

## 12. Priorização para a próxima etapa de engenharia

**P0 — Decisão sobre a inteligência-base:** fechar a discrepância entre o KN-003 experimental e a meta de cérebro geral; medir opções locais e definir o caminho sem esconder o custo/limite de treinamento do zero.

**P1 — Contrato cognitivo do KN:** definir formato de plano/ação/observação/avaliação/conclusão, memória com proveniência, catálogo de tools, policy, limites e traces.

**P2 — Centro de Estudos:** navegação nativa sem WebView, ingestão de fontes, deduplicação, qualidade, validade temporal, cursos e registros de experimento.

**P3 — Laboratório de engenharia:** workspace isolado, Git/checkpoints, compilação, testes, Logcat, comparação de patch, rollback e avaliação independente.

**P4 — Ferramentas multimodais:** áudio local primeiro; edição/renderização; geração de imagem/vídeo/música através de conectores opcionais e somente com aviso de dependência/custo.

**P5 — Bots:** prova de conceito Telegram long polling; Discord Gateway; análise formal da impossibilidade de WhatsApp Business inbound oficial sem webhook público; telemetria e limites Android.

**P6 — Otimização Poco F6:** medir RAM/PSS, CPU/GPU, armazenamento, energia, temperatura e tempo de resposta; não inferir compatibilidade a partir de outro Snapdragon.

## 13. Restrições e critérios de honestidade

- Nenhum agente novo ou sistema multiagente será criado.
- Agentes antigos não serão apagados nesta etapa; código e histórico serão preservados até auditoria de dependências.
- Não alterar layout aprovado da Kellvyn.
- Não depender de hospedagem externa, APIs pagas, contas pagas ou tokens para funcionamento básico offline.
- Integrações online devem ser opcionais, explícitas, auditáveis e substituíveis.
- Não copiar código proprietário, pesos privados ou identidade visual de terceiros; auditar licenças de bibliotecas e modelos.
- Não prometer uptime de bot que o Android ou a plataforma não garantem.
- Não declarar motor de imagem/vídeo/música local sem teste no aparelho.
- Não afirmar que memória de fatos equivale a treinar parâmetros.
- Nenhuma melhoria estará concluída até que existam testes e evidências registrados.

## Fontes web consultadas — 19/11/2026

- Anthropic, Building effective agents: https://www.anthropic.com/research/building-effective-agents
- Android Studio Agent Mode: https://developer.android.com/studio/gemini/agent-mode
- Android background work: https://developer.android.com/develop/background-work/background-tasks
- Android foreground services/timeouts: https://developer.android.com/develop/background-work/services/fgs
- Replit Agent: https://docs.replit.com/pt/references/agent/overview
- OpenAI sandbox: https://developers.openai.com/pt-BR/docs/sandboxing
- Cursor tools: https://docs.cursor.com/en/agent/tools
- Jules docs: https://jules.google/docs/
- SWE-bench Verified: https://www.swebench.com/verified
- Gemma on mobile: https://ai.google.dev/gemma/docs/integrations/mobile
- MLC LLM: https://github.com/mlc-ai/mlc-llm
- llama.cpp OpenCL backend: https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/OPENCL.md
- Discord Gateway: https://discord.com/developers/docs/topics/gateway
- Discord privileged intents: https://support-dev.discord.com/hc/pt-br/articles/6207308062871-O-que-s%C3%A3o-inten%C3%A7%C3%B5es-privilegiadas
- Telegram Bot API: https://core.telegram.org/bots/api
- Meta WhatsApp Business Platform: https://developers.facebook.com/documentation/business-messaging/whatsapp/overview/
- Google Nano Banana: https://deepmind.google/models/gemini-image/
- OpenAI GPT Image: https://developers.openai.com/api/docs/guides/image-generation
- LTX-2.5: https://docs.ltx.io/models/ltx-2-5
- Kling API: https://kling.ai/dev/api
- Runway API: https://docs.dev.runwayml.com/api/
- sherpa-onnx: https://github.com/k2-fsa/sherpa-onnx
- whisper.cpp Android: https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android
- MusicGen: https://github.com/facebookresearch/audiocraft/blob/main/docs/MUSICGEN.md
- Android courses: https://developer.android.com/courses
- Google ML Crash Course: https://developers.google.com/machine-learning/crash-course
- OpenAI Learn: https://developers.openai.com/learn
- DeepLearning.AI: https://www.deeplearning.ai/courses/
- SIMA 2: https://deepmind.google/blog/sima-2-an-agent-that-plays-reasons-and-learns-with-you-in-virtual-3d-worlds/
- Genie 4: https://deepmind.google/blog/genie-4-a-new-generation-of-world-simulation/
