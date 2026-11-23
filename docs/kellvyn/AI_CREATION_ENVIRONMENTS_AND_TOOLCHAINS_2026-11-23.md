# Ambientes de criação de IAs e ferramentas usadas no desenvolvimento — 23/11/2026

**Escopo:** estudo complementar ao AI_CAPABILITY_STUDY_2026-11-20.md para distinguir criação de aplicativos, treinamento/ajuste de modelos, avaliação e execução Android.  
**Decisão de arquitetura:** este estudo não altera o KN, seus pesos, tokenizer, inferência ou treinamento. A Kellvyn mantém um único cérebro central; ferramentas são executores determinísticos subordinados a ele.  
**Uso pretendido:** orientar experimentos isolados e melhorias de ferramentas sem transformar serviços pagos, modelos externos ou subagentes em dependências do produto.

## 1. Quatro camadas que costumam ser confundidas

| Camada | O que ela faz | Exemplos |
|---|---|---|
| Desenvolvimento do aplicativo | Gera, edita, compila e testa o Android | Android Studio, Google AI Studio Build Mode |
| Treinamento de modelo | Prepara dados, treina ou ajusta pesos | Python, PyTorch, Hugging Face Transformers/TRL/PEFT, Colab, Kaggle |
| Avaliação e observabilidade | Mede qualidade, regressões, latência, erros e recursos | Testes locais, MLflow, Langfuse, LangSmith, Sentry, W&B |
| Runtime de inferência | Carrega o modelo e executa geração no aparelho | Runtime KN próprio, LiteRT-LM, ExecuTorch, llama.cpp |

Uma ferramenta que cria um aplicativo Android a partir de texto não é, por isso, uma plataforma para treinar um cérebro neural próprio. Um notebook que treina um modelo também não substitui Gradle, Android Studio nem o runtime que executa esse modelo no Poco F6.

## 2. O que desenvolvedores realmente usam

A Stack Overflow Developer Survey 2026 oferece uma amostra de uso, não uma medição de todo o mercado. Cada porcentagem pertence ao grupo respondente daquela pergunta:

- **Assistentes de programação** — entre 12.255 respondentes: Claude Code 65,5%, GitHub Copilot 58,7%, OpenAI Codex 29,5% e Cursor 25,5%.
- **Orquestração e frameworks** — entre 4.400 respondentes: LangChain 30,2%, OpenAI Agents SDK 23,8%, uso direto do terminal 18,9% e LangGraph 18,8%.
- **Observabilidade e avaliação** — entre 2.277 respondentes: Sentry 26%, LangSmith 17,3%, MLflow 15,2%, Langfuse 15,2% e Weights & Biases 10,7%.
- **Uso de IA no trabalho** — entre 17.464 respondentes: assistentes/agentes de programação 65,9%, chatbots gerais 62,5% e agentes/workflows 26,2%.

**Interpretação para a Kellvyn:** os números não indicam que devemos copiar a arquitetura multiagente desses frameworks. Eles mostram que o desenvolvimento sério combina editor/agente de código, execução em ambiente real, testes, rastreamento de falhas e avaliação. A Kellvyn pode adotar essas práticas com um único cérebro central e ferramentas próprias.

Fonte: https://survey.stackoverflow.co/2026/ai/data

## 3. Ambientes para construir o aplicativo Android

### Google AI Studio — Build Mode para Android

O anúncio oficial de 19/05/2026 descreve geração de aplicativos Android nativos em Kotlin/Jetpack Compose, emulador Android integrado no navegador, instalação no aparelho por USB/ADB e encaminhamento do projeto ao Android Studio por ZIP ou GitHub. Também descreve publicação para teste interno no Google Play.

**Onde ajuda:** criar protótipos, telas novas isoladas, exemplos mínimos reproduzíveis e testar rapidamente uma ideia de interface.

**Onde não resolve o problema completo:** não é um ambiente de treinamento do KN. A documentação de Build Mode também descreve integração com Gemini/Firebase para experiências de IA hospedadas. Isso é diferente de treinar e executar o cérebro próprio offline. A documentação técnica e o anúncio de produto não detalham todas as mesmas limitações da mesma forma; portanto, qualquer alegação sobre exportação ou suporte a código nativo deve ser confirmada na versão efetivamente disponível antes de depender dela.

**Decisão:** ambiente auxiliar de prototipagem, não substituto do repositório, do Gradle, do laboratório Android nem do KN.

Fontes:
- https://android-developers.googleblog.com/2026/05/build-android-apps-google-ai-studio.html
- https://ai.google.dev/gemini-api/docs/aistudio-build-mode

### Android Studio e GitHub

É a base de engenharia para o aplicativo real: projeto Android existente, Gradle, Kotlin/Java, SDK/NDK, ADB, emulador, Logcat, instrumented tests e instalação no aparelho. A automação GitHub Actions deve provar que esses comandos passaram; job encerrado antes de alocar runner não é uma compilação reprovada nem aprovada.

**Decisão:** manter Android Studio/Gradle/GitHub como trilha principal de construção e validação da Kellvyn. Não reconstruir o projeto aprovado em outro gerador.

## 4. Ambientes usados para treinar e ajustar modelos

### Python + PyTorch + Hugging Face

É um fluxo comum para modelos compatíveis com a arquitetura e os formatos do ecossistema Hugging Face:

1. Python/PyTorch define e executa o modelo.
2. Datasets prepara e versiona os exemplos.
3. Transformers/Trainer executa o ciclo de treino.
4. TRL SFTTrainer ajusta modelos com exemplos de instrução, conversas ou prompt-completion.
5. PEFT/LoRA/QLoRA treina adaptadores menores enquanto mantém a maior parte do modelo-base congelada.

Isso reduz memória e tamanho dos parâmetros treináveis, mas não torna qualquer arquitetura automaticamente compatível. A integração PEFT depende dos módulos e nomes de camadas do modelo; a documentação do Transformers mostra alvos específicos para arquiteturas como Llama, Gemma e Qwen.

**Aplicação ao KN:** não presumir que o checkpoint ou tokenizer próprio da Kellvyn pode ser aberto diretamente pelo SFTTrainer. Antes de usar essa trilha, seria necessário demonstrar compatibilidade da arquitetura, formato de pesos, tokenizer, máscara de treino, função de perda e exportação. Sem essa prova, o ambiente pode treinar outro modelo, não o KN.

Fontes:
- https://huggingface.co/docs/trl/sft_trainer
- https://huggingface.co/docs/transformers/peft
- https://huggingface.co/docs/trl/peft_integration

### Google Colab

Notebook Jupyter hospedado, útil para experimentos Python, preparação de conjuntos de dados e testes de treino com GPU/TPU quando o recurso está disponível.

**Limite que importa:** a própria Google diz que recursos gratuitos não são garantidos, disponibilidade e limites variam, e os runtimes podem expirar. O FAQ informa sessões gratuitas de até 12 horas dependendo de disponibilidade e uso. Portanto, não é uma máquina de treino garantida, nem deve ser a única cópia de dados, checkpoints ou resultados.

Fonte: https://research.google.com/colaboratory/faq.html

### Kaggle Notebooks

Outra opção de notebook hospedado com ambiente pré-configurado e acesso gratuito a GPU/TPU conforme disponibilidade. A documentação avisa que, em períodos de procura alta, o acesso à GPU pode entrar em fila.

**Uso indicado:** experimentos curtos, reprodutíveis e portáveis. Não depender de uma sessão gratuita para treino longo nem guardar o único checkpoint dentro da VM.

Fonte: https://www.kaggle.com/docs/notebooks

### Unsloth

É uma opção de otimização para fine-tuning em hardware compatível, com suporte a fluxos LoRA/QLoRA. A tabela de requisitos publicada pela equipe mostra que o consumo depende do tamanho do modelo e da técnica, e que a compatibilidade CUDA/GPU é requisito importante.

**Uso indicado:** comparar eficiência de fine-tuning em um ambiente GPU compatível. Não é uma solução para treinar diretamente o KN sem compatibilidade comprovada, nem elimina a necessidade de avaliação.

Fonte: https://unsloth.ai/docs/get-started/fine-tuning-for-beginners/unsloth-requirements

## 5. Runtimes de IA no Android — a parte mais relevante para o destino da Kellvyn

### LiteRT-LM (Google AI Edge)

A documentação oficial oferece API Kotlin para Android e JVM. O runtime usa modelos no formato .litertlm, suporta streaming, conversa multi-turno e backends CPU/GPU/NPU. Também oferece chamadas de ferramentas definidas por funções Kotlin ou OpenAPI — mas isso depende de o modelo utilizado ter suporte a tool calling (a documentação cita FunctionGemma como exemplo).

**Ponto de interesse:** é um caminho moderno para executar modelos abertos e ferramentas diretamente no Android sem exigir um servidor remoto.

**Limite decisivo:** não é um carregador universal de qualquer arquivo de pesos. O formato .litertlm, a arquitetura suportada, a conversão do modelo, o tokenizer e o backend precisam coincidir. Não há motivo para presumir que possa carregar o checkpoint KN atual sem uma cadeia de conversão e validação.

Fontes:
- https://developers.google.com/edge/litert-lm/android
- https://developers.google.com/edge/litert/overview

### ExecuTorch (PyTorch)

Oferece AAR Android com APIs Java/Kotlin e bibliotecas JNI. A documentação lista backends Android para CPU (XNNPACK), NPU (Qualcomm AI Engine e MediaTek NeuroPilot) e GPU (Vulkan). Modelos passam por exportação para o formato ExecuTorch, como .pte.

**Ponto de interesse:** integração Android nativa, opções de backend e ferramentas de profiling/inspeção.

**Limite decisivo:** a exportação do modelo é parte do trabalho. Um .pte não é intercambiável com o checkpoint KN nem com .litertlm.

Fonte: https://docs.pytorch.org/executorch/stable/using-executorch-android.html

### llama.cpp

O projeto oficial tem documentação específica para Android, uma aplicação de exemplo importável no Android Studio e compilação cruzada por Android NDK. A aplicação de exemplo lê modelos GGUF do armazenamento privado, cria um motor de inferência e transmite tokens por Kotlin Flow. O projeto documenta também detecção de recursos do processador Arm para selecionar kernels compatíveis.

**Ponto de interesse:** alternativa madura para testar modelos distribuídos em GGUF e execução local sem depender de API remota.

**Limite decisivo:** é uma base C/C++ com cadeia própria de conversão/exportação e integração JNI. Um checkpoint KN que não esteja no formato e arquitetura compatíveis não pode ser carregado só por adicionar a biblioteca. O caminho Android usa NDK e deve ser medido em memória, temperatura e desempenho no aparelho real.

Fonte: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md

### Como comparar no Poco F6

Nenhum benchmark de Pixel, Samsung ou outro aparelho deve ser tratado como resultado do Poco F6. Uma comparação justa precisa usar exatamente o mesmo conjunto de prompts e medir, no aparelho real:

- modelo, formato, tokenizer e quantização utilizados;
- memória RAM máxima e memória reservada ao modelo/KV cache;
- tempo de carregamento, primeiro token e tokens por segundo;
- temperatura, throttling, consumo e estabilidade em conversas longas;
- qualidade de resposta e aderência a chamadas de ferramenta;
- funcionamento offline e recuperação após fechamento/reabertura do processo.

**Decisão atual:** estudar LiteRT-LM e ExecuTorch como alternativas de runtime em uma prova isolada, sem substituir o KN nem alterar a arquitetura neural existente. Primeiro precisa existir um benchmark reproduzível e uma conversão fiel; depois, comparar. Não adicionar dependências ao app principal ainda.

## 6. Avaliação e rastreamento: a prática que mais vale copiar

A documentação do MLflow mostra como rastrear etapas internas, entradas, saídas e metadados de aplicações LLM, e depois avaliar traces com respostas esperadas, métricas e avaliadores. Isso permite reutilizar traces já coletados em avaliações offline.

**Para a Kellvyn, a versão mais segura e barata é começar localmente:**

1. Criar um conjunto fixo de casos de teste sintéticos, versionado no Git, com entrada, resultado esperado, ferramenta que deveria ser selecionada e critérios de segurança.
2. Registrar apenas metadados necessários: ferramenta chamada, duração, status, quantidade de resultados, origem e versão; não armazenar segredos, conteúdo privado ou conversas completas por padrão.
3. Reexecutar o mesmo conjunto depois de cada mudança e comparar precisão da seleção, recuperação, falsos positivos, falsos negativos, falhas e latência.
4. Manter as traces privadas e criptografadas dentro do app; usar MLflow local apenas como opção de análise em uma máquina própria, não como serviço remoto obrigatório.

A Kellvyn já possui um armazenamento local de eventos do Agent Lab com encadeamento de hashes e proteção criptográfica, sem aceitar prompts, conclusões, argumentos ou conteúdo de arquivos no esquema de eventos. Essa base é mais compatível com a política local-first do que enviar dados privados a um painel hospedado.

Fontes:
- https://mlflow.org/docs/latest/genai/tracing
- https://mlflow.org/docs/latest/genai/eval-monitor/running-evaluation/traces/

## 7. O que deve ser aproveitado — e o que não deve ser copiado

| Padrão observado em outras ferramentas | Tradução segura para Kellvyn |
|---|---|
| Assistente de programação conectado a IDE, arquivos e terminal | Ferramentas locais tipadas, com permissões e validação explícitas |
| Frameworks de orquestração e fluxo | Estado explícito e sequências determinísticas controladas pelo KN; sem subagentes |
| Bancos vetoriais e memória de agentes | Recuperação local rastreável, com origem, expiração, limite e política de privacidade |
| MLflow/Langfuse/LangSmith para observabilidade | Casos de regressão e traces locais, criptografados e sem prompts privados por padrão |
| PEFT/LoRA para ajuste de modelos conhecidos | Só considerar depois de demonstrar que a arquitetura do modelo-alvo é compatível |
| Runtimes móveis com GPU/NPU | Provar formato, conversão, memória, temperatura e qualidade no Poco F6 antes de adotar |

## 8. n8n: automação visual, MCP e o que pode ser aproveitado

### O que ele é — e o que não é

n8n é uma plataforma fair-code de automação de processos. Conecta aplicações, APIs, bancos, triggers, filas, transformações e execuções por meio de workflows visuais, com possibilidade de código e nós customizados. Também oferece recursos de IA, mas a existência de um AI Agent node não significa que a plataforma seja um runtime neural local ou um substituto para o cérebro KN.

### Capacidades relevantes observadas em 23/11/2026

1. **n8n Assistant:** chat capaz de planejar, criar, editar, testar e depurar workflows. O resultado é um workflow normal que pode ser inspecionado e publicado. O próprio n8n alerta que o Assistant pode errar e que a saída deve ser revista antes de produção. O recurso também pode conectar MCP servers, navegar com Browser Use e consultar trabalhos/execuções recentes. Em self-hosted Community o Assistant está listado como disponível, mas precisa de um modelo configurado para funcionar; o AI Agent interno do n8n não deve ser usado como cérebro da Kellvyn.
2. **MCP em nível de instância:** uma conexão autenticada pode expor workflows selecionados; clientes podem localizar, executar e testar workflows, e desde n8n 2.13.0 também criar/editar workflows. A exposição não é automática: MCP precisa ser habilitado e cada workflow liberado individualmente. Um detalhe de segurança importante: as permissões não são isoladas por cliente MCP; os clientes conectados podem ver os mesmos workflows expostos, respeitando o acesso do usuário. Para a Kellvyn, isso exigiria uma allowlist estrita e autenticação própria.
3. **MCP protegido:** a documentação informa que, desde 2.47.0, uma instância configurada como protegida/read-only bloqueia ferramentas MCP sem readOnlyHint: true, com exceção de execução de workflows publicados em modo production. É um modelo útil para reduzir o risco de uma integração.
4. **Aprovação humana (HITL):** um workflow pode pausar antes de uma ferramenta sensível, mostrar os parâmetros e aguardar aprovação ou rejeição. Serve como referência de UX e política para ações externas; não substitui as aprovações já existentes no workspace da Kellvyn.
5. **Avaliações:** o n8n suporta conjuntos de exemplos de teste, resultados esperados e comparações para detectar regressões em workflows com IA. A prática é valiosa mesmo que a Kellvyn mantenha sua avaliação inteiramente local.
6. **Integrações e extensibilidade:** nodes de aplicativos, HTTP Request, Code, triggers, sub-workflows, MCP Client/Server Trigger e nodes customizados permitem conectar sistemas sem implementar cada conector de raiz.

Fontes oficiais:
- https://docs.n8n.io/build/ways-of-building-workflows/n8n-assistant.md
- https://docs.n8n.io/connect/connect-to-n8n-mcp-server.md
- https://docs.n8n.io/connect/connect-to-n8n-mcp-server/mcp-server-tools-reference.md
- https://docs.n8n.io/build/integrate-ai/ai-examples/human-in-the-loop-for-tools.md
- https://docs.n8n.io/build/integrate-ai/test-and-improve-ai-workflows/understand-why-to-test.md

### Novidades de versão verificadas nesta data

- **2.48.8** aparece como a versão estável mais recente publicada em 23/11/2026. Seu changelog inclui explicação do último estado de execução no Migration Report e correções para e2e, permissões e tratamento de memória do motor de expressões.
- **2.49.5** aparece como pré-lançamento, não deve ser confundida com a versão estável.
- **3.0.0 Beta** foi anunciada em 27/10/2026; o anúncio previa a versão estável para o início de dezembro. A versão 3.0 reúne mudanças potencialmente incompatíveis: self-hosting passa a ser Docker-based, remove instalação npm/npx, muda execução manual para workers em queue mode, remove modo de dados binários em memória, substitui Cron/Interval pelo Schedule Trigger, reduz o timeout padrão do Code node e endurece defaults de segurança. Não atualizar para 3.0 sem ler o Migration Report e testar os workflows.

Fontes:
- https://github.com/n8n-io/n8n/releases
- https://blog.n8n.io/introducing-n8n-v3/

### Custo, hospedagem, privacidade e licença

- **Community self-hosted:** pode ser gratuita para uso pessoal ou interno da organização sob a Sustainable Use License. O n8n Cloud é um serviço separado, com assinatura e créditos.
- **Uso dentro de um produto:** se a Kellvyn permitir que usuários externos construam ou configurem workflows via editor, API, MCP ou agente de IA, a FAQ do n8n indica a necessidade de licença comercial. Não assumir que a Community Edition pode ser embutida gratuitamente num produto distribuído.
- **Hospedagem:** a documentação atual orienta self-hosting com Docker, Docker Compose ou ambiente compatível. A versão 3.0 deixa npm/npx sem suporte para novas instalações. Os requisitos de produção recomendam host Linux 64-bit; o guia de Docker Compose para o stack do n8n Assistant pede Docker Engine, Compose, pelo menos 4 GB de RAM e 2 vCPUs. O sandbox de desenvolvimento desse guia usa Docker-in-Docker privilegiado, com isolamento fraco, e não deve ser instalado junto a dados pessoais num aparelho principal.
- **Poco F6:** Android/HyperOS não aparece como alvo de instalação suportado nas instruções oficiais. Portanto, não tratar o telefone como host n8n oficial. Os 8 GB de RAM do aparelho, isoladamente, não resolvem o requisito Docker/host nem os limites de execução em background do Android.
- **Dados:** n8n mantém workflows, credenciais e histórico numa base de dados. Ele tem controles para criptografia, auditoria, ocultação de dados de execução, bloqueio de nodes e proteção SSRF; isso exige configuração e manutenção do servidor. O tráfego MCP também deve ser protegido e limitado a workflows explicitamente publicados.

Fontes:
- https://support.n8n.io/article/can-i-use-your-license-for-your-use-case
- https://docs.n8n.io/deploy/host-n8n.md
- https://docs.n8n.io/deploy/host-n8n/install-options/production-requirements.md
- https://docs.n8n.io/deploy/host-n8n/install-options/install-using-docker-compose.md
- https://docs.n8n.io/deploy/host-n8n/configure-n8n/security.md

### Veredito para Kellvyn

**Aproveitar:** o padrão de workflow versionado, triggers, nós reutilizáveis, retry/backoff, logs de execução, testes com dataset conhecido, gates de aprovação, MCP autenticado e allowlist de operações.

**Não copiar para o runtime principal:** AI Agent do n8n, Chat Hub multi-modelo, ou delegação de raciocínio. Isso criaria outro agente decisor e contrariaria a arquitetura de cérebro único da Kellvyn.

**Não integrar como dependência agora:** n8n exige uma instância/host separado, e o caminho oficial não é instalar no APK. O modelo Cloud adiciona custos; self-hosted exige infraestrutura e manutenção; embutir criação/configuração de workflows dentro do produto pode exigir licença comercial.

**Melhor uso imediato:** usar o n8n como referência para desenhar e testar fluxos determinísticos fora do app, e extrair os padrões comprovados para ferramentas Kotlin locais. Caso no futuro exista um host próprio e uma autorização explícita para integração externa, o KN poderá chamar apenas workflows publicados e allowlisted por meio de um adaptador MCP/HTTP — continuando sendo a única inteligência que decide quando e por que chamar a ferramenta.

## 9. Plano de decisão para o laboratório

1. **Agora:** estabilizar catálogo de ferramentas, pesquisa local bilíngue, pesquisa acadêmica e pesquisa de programação; ampliar testes de seleção e segurança.
2. **Em paralelo:** construir um conjunto de avaliação offline com entradas representativas e resultados esperados. Isso permite saber se a Kellvyn melhorou, em vez de medir progresso pelo número de ferramentas.
3. **Pesquisa isolada, sem tocar no KN:** testar compatibilidade teórica de LiteRT-LM e ExecuTorch com formatos e arquitetura do projeto; não baixar nem trocar pesos no app principal.
4. **Só com evidência:** comparar runtimes no Poco F6 com a mesma carga, os mesmos prompts e as mesmas métricas.
5. **Somente depois de autorização específica:** qualquer proposta que altere KN, pesos, tokenizer, pipeline de treino ou inferência.

**Resultado deste estudo:** Google AI Studio é interessante para prototipagem Android; Colab/Kaggle são ambientes de experimento, não capacidade de treino garantida; Hugging Face/PEFT/TRL é um fluxo de ajuste para modelos compatíveis; LiteRT-LM e ExecuTorch são candidatos reais de execução Android, mas exigem formatos/exportação compatíveis; e avaliação/traces locais devem orientar cada mudança. Nenhuma dessas referências justifica substituir o cérebro KN agora.
