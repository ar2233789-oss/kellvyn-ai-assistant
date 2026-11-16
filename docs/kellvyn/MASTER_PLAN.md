# KELLVYN — plano mestre de engenharia

**Versão:** 1.0  
**Data:** 16/11/2026  
**Nome oficial da inteligência:** Kellvyn  
**Branch de trabalho:** `lab/kellvyn-intelligence-core-20261116`  
**Repositório de laboratório:** `ar2233789-oss/kellvyn-ai-assistant`  
**Privacidade:** a Kellvyn será privada e de uso exclusivo do proprietário. Não há plano de publicação na Play Store nem de distribuição pública.

---

## 1. Objetivo do projeto

Desenvolver a inteligência Kellvyn como um sistema integrado capaz de:

- conversar em português com coerência e continuidade;
- compreender objetivos e transformar solicitações em planos executáveis;
- consultar memória e arquivos autorizados;
- pesquisar na Web pela conexão do próprio aparelho quando o usuário habilitar;
- programar, editar projetos, executar verificações, diagnosticar erros e iterar;
- criar aplicativos Android, compilar APKs reais e verificar os artefatos;
- trabalhar com documentos, imagens e outras modalidades na medida em que o modelo e o aparelho suportarem;
- administrar automações, bots e integração com o dispositivo, respeitando as permissões explícitas do proprietário e os limites técnicos do Android;
- executar tarefas no Poco F6 com funcionamento local e sem exigir hospedagem permanente.

A interface aprovada do aplicativo é uma superfície de interação, não o cérebro. A sua estrutura visual, animações e controles protegidos não são alvo deste trabalho. As capacidades devem ser acionadas pela conversa principal e pelos controles que já existem, sem criar uma segunda IA ou um segundo chat.

## 2. O que significa “legítima” neste projeto

A Kellvyn será um sistema original que pode aproveitar pesos, bibliotecas, runtimes, código aberto e técnicas de terceiros quando isso for legalmente permitido e tecnicamente vantajoso. Não é exigido reimplementar tudo que já existe.

Regras obrigatórias:
- pesos abertos podem ser utilizados como fundação neural após auditoria de licença, model card, versão, hashes, proveniência e compatibilidade;
- fine-tuning, LoRA e outros derivados são permitidos quando os termos do modelo-base autorizarem, mantendo a atribuição e identificando o resultado como derivado;
- código aberto pode ser integrado seletivamente com licença, avisos, atribuição, registro de alterações e auditoria de dependências;
- código, pesos ou componentes proprietários não serão copiados nem obtidos por meios não autorizados;
- Claude, ChatGPT, Replit, Cursor e Gemini/Android Studio serão estudados pelo comportamento documentado; seus mecanismos internos e pesos privados não serão presumidos acessíveis;
- APIs proprietárias remotas não serão requisito para o funcionamento local/offline e só poderão ser adotadas mediante decisão explícita sobre custos, privacidade e dependência de rede;
- não copiar um produto inteiro e apenas trocar seu nome, marca, interface ou prompts;
- não atribuir a Kellvyn código, pesos ou treinamento originalmente produzidos por terceiros;
- a arquitetura cognitiva, orquestração, memória, políticas, ferramentas, verificação, integração Android e identidade de Kellvyn serão desenvolvidas e controladas no projeto;
- não usar conversas privadas do proprietário no treino sem consentimento específico;
- manter proveniência de cada dado, dependência, peso e checkpoint.

**Kellvyn será própria pelo sistema, pela engenharia e pela integração que entregaremos. Componentes externos autorizados poderão acelerar e ampliar suas capacidades, sem esconder a autoria de cada parte.**

## 3. Restrições que orientam todas as decisões

1. Uma única inteligência chamada Kellvyn; não montar uma coleção de IAs com identidades concorrentes.
2. Não tratar modelos, pesos, códigos ou produtos de terceiros como se fossem criados por Kellvyn. Nenhuma fundação neural externa está selecionada; qualquer componente futuro exige auditoria, origem e atribuição preservadas.
3. Não alegar treinamento, raciocínio, execução, pesquisa, compilação, instalação ou teste que não tenha ocorrido e sido verificado.
4. Funcionamento local como objetivo principal; Web opcional pela conexão do telefone.
5. Alvo de execução: Poco F6, Snapdragon 8s Gen 3, 8 GB de RAM.
6. Não presumir que o telefone pode pré-treinar um LLM avançado. Inferência, treinamento de modelo e treinamento de agentes são problemas diferentes.
7. Preservar a interface aprovada. Alterações de inteligência devem ficar separadas dos arquivos de interface bloqueados.
8. Não adicionar WebView à versão Android final.
9. Não colocar terminal irrestrito, shell genérico ou acesso arbitrário ao armazenamento à disposição do modelo.
10. Ações com efeitos colaterais exigem políticas executadas no código e confirmação quando o risco exigir.
11. Nenhum modelo, dataset, runtime ou código será escolhido somente pela popularidade. A decisão precisa de licença, compatibilidade, benchmark e justificativa.
12. O laboratório é privado e experimental. Não publicar builds experimentais como versões estáveis; o proprietário decide quando gerar e instalar um APK pessoal.

## 4. Arquitetura geral pretendida

A arquitetura deve ser modular por responsabilidade, mas o usuário conversa sempre com a mesma Kellvyn.

```text
                     USUÁRIO
                        |
                 Conversa principal
                        |
               KELLVYN / ORQUESTRADOR
                        |
       +----------------+----------------+
       |                |                |
  Contexto e       Planejador       Política de
   memória         de tarefas        permissões
       |                |                |
       +----------------+----------------+
                        |
                MODELO NEURAL ÚNICO
                        |
               chamadas estruturadas
                        |
                 Registro de ferramentas
                        |
       +----------------+----------------+
       |                |                |
  Workspace        Android/Build      Internet
  e código         e dispositivos      opcional
       |                |                |
       +----------------+----------------+
                        |
               Observação de resultados
                        |
             Verificação independente
                        |
                Resposta comprovável
```

### 4.1 Fundação neural

Uma fundação principal de pesos abertos, sob licença verificada, poderá fornecer a capacidade linguística inicial. A escolha será feita por qualidade, português, raciocínio, código, visão, chamadas de ferramentas, consumo de RAM, latência e execução offline no Poco F6. Pesos e autores-base permanecem identificados.

KBN-001 é uma experiência própria de treinamento neural e continuará como laboratório. Ela não será confundida com uma fundação conversacional avançada nem usada para bloquear a escolha de um modelo aberto mais capaz.

Personalização de modelo-base por treino/adapters é permitida quando os termos autorizarem. Os pesos resultantes serão registrados como derivados. A rota de pré-treinamento integralmente do zero pode continuar como pesquisa de longo prazo, mas não é condição para entregar uma Kellvyn funcional.

### 4.2 Orquestrador

Mantém o objetivo da tarefa, plano, estado, tentativas, resultados e critério de conclusão. Impõe orçamento, cancelamento, timeout e limite de chamadas. O orquestrador não pode considerar uma afirmação do modelo como prova de que uma ação aconteceu.

### 4.3 Planejador

Divide pedidos complexos em tarefas com:
- objetivo e resultado esperado;
- pré-condições;
- arquivos ou recursos envolvidos;
- ferramentas necessárias;
- risco e permissão;
- validação de saída;
- dependências de outras tarefas;
- plano de recuperação.

O plano é armazenado como dados estruturados, não apenas como texto em uma mensagem.

### 4.4 Registro de ferramentas

As ferramentas são funções explícitas, tipadas, com esquemas e permissões. O modelo pode propor chamadas, mas o registro e a política decidem se a chamada existe, se os parâmetros são válidos e se a ação pode ocorrer.

### 4.5 Memória e contexto

Separar:
- contexto temporário da conversa;
- resumo da sessão;
- memória duradoura explicitamente salva;
- índice de projetos/arquivos;
- histórico de tarefas e resultados;
- preferências e configurações.

Memória não é treinamento de pesos. Segredos, tokens e credenciais não entram em memória conversacional comum.

### 4.6 Workspace e execução

Cada projeto tem pasta privada, metadados, arquivos, estado Git local e artefatos. Operações devem ficar confinadas ao workspace. Execução de código requer ambiente restrito, limites de CPU/RAM/tempo e proteção contra acesso a outros dados.

### 4.7 Verificação

Um resultado só é “concluído” quando uma evidência independente confirma o critério: arquivo existe e tem conteúdo; teste passou; APK foi assinado; pacote foi validado; app iniciou; tela ou log demonstrou o comportamento.

## 5. Capacidades pretendidas e ordem de maturidade

### A. Conversa e raciocínio
- português natural;
- instruções longas e múltiplas restrições;
- contexto de várias mensagens;
- memória selecionada;
- perguntas de esclarecimento somente quando essenciais;
- reconhecimento explícito de incerteza.

### B. Pesquisa e leitura
- acesso Web opcional, com fontes e trechos recuperados;
- leitura de repositórios;
- leitura de arquivos e documentos suportados;
- indexação de símbolos e dependências;
- distinção entre conteúdo externo e instruções confiáveis.

### C. Engenharia de software
- compreender estrutura de projeto;
- localizar código relevante;
- planejar mudanças em vários arquivos;
- produzir diff revisável;
- rodar lint, testes e builds;
- investigar logs e falhas;
- corrigir e repetir validações;
- manter checkpoints e histórico.

### D. Criação de aplicativos
Pipeline planejado:
1. transformar o pedido em requisitos verificáveis;
2. propor arquitetura e escopo;
3. gerar estrutura e arquivos;
4. validar caminhos, manifest, recursos e dependências;
5. compilar com ferramentas reais;
6. executar testes unitários e instrumentados;
7. verificar APK, assinatura, pacote e permissões;
8. instalar no Poco F6 com autorização;
9. executar fluxo real, guardar logs/capturas e comparar com o requisito;
10. corrigir falhas e repetir.

O compilador que já existe no projeto só aceita um subconjunto de projetos Android Java. Isso não equivale a suporte completo a Android Studio/Gradle, Kotlin, Compose ou dependências arbitrárias. A expansão será gradual e comprovada.

### E. Multimodalidade
- imagem e PDF, dentro dos limites de memória;
- documentos e código;
- áudio/ditado e síntese de voz;
- compreensão de tela, dentro das APIs e permissões realmente disponíveis no Android.

### F. Bots e automações
- Telegram e Discord com tokens protegidos e runtime verificável;
- estado “ativo” somente quando a conexão e o processamento estiverem confirmados;
- WhatsApp somente quando existir uma integração oficial/compatível com infraestrutura exigida e termos verificados;
- manter controle de ativação e interrupção pelo usuário.

### G. Controle do dispositivo e ambientes interativos

Como o projeto é privado, não vamos limitar a arquitetura por regras de publicação da Play Store. Podemos investigar integração por AccessibilityService, MediaProjection, intents, APIs de automação, ADB em ambiente de desenvolvimento e outras interfaces Android, sempre confirmando quais são tecnicamente disponíveis no Poco F6 e quais dependem de root, depuração ou permissões especiais.

O agente deve conseguir operar interfaces e ambientes que o proprietário autorizar. A implementação terá níveis de acesso, visualização das ações, limites de risco, botão de interrupção e proteção contra ações irreversíveis acidentais. Não presumir que o Android permite controle irrestrito sem root ou sem permissões explícitas.

## 6. Como estudar e aproveitar cada referência

| Referência | Aprendizado pretendido | Tipo de aproveitamento |
|---|---|---|
| Claude Code | regras, skills, hooks, subagentes isolados, compactação de contexto | reproduzir os conceitos documentados com implementação própria; código e pesos privados não são copiados |
| ChatGPT / OpenAI Agents | function calling, Web, file search, code interpreter, computer use, MCP | ferramentas equivalentes implementadas no ambiente local; APIs remotas são opcionais e não serão requisito offline |
| Replit Agent | Plan/Build, tarefas ordenadas, testes visuais, autocorreção e checkpoints | padrões de processo e verificação |
| Cursor Agent | coordenador, delegação, mapa de projeto, terminal, diff, review e debugging | arquitetura e fluxos; componentes abertos só após auditoria da licença |
| Android Studio + Gemini | planejamento, edição multi-arquivo, build, dispositivo, screenshots, Logcat e ADB | padrões documentados; código e modelo internos não são copiados |
| OpenHands / Agent SDK | sessões, eventos, workspaces, estado e ferramentas | código MIT pode ser integrado seletivamente após auditoria e atribuição |
| Aider | mapa de repositório, contexto, diffs e Git | código Apache 2.0 pode ser integrado seletivamente após auditoria e atribuição |
| SWE-agent / mini-SWE-agent | ciclos observáveis de ação, ambiente e avaliação | código MIT pode ser integrado seletivamente após auditoria e atribuição |
| OpenCode | permissões, skills, subagentes e contexto | código MIT pode ser integrado seletivamente após auditoria e atribuição |
| nanochat / OLMo-core | treinamento, dados, checkpoints e avaliação | estudar e eventualmente integrar componentes licenciados; nenhum peso/dataset é automaticamente autorizado |
| LiteRT-LM | inferência Android, GPU/NPU, multimodalidade e chamada de ferramenta | candidato a runtime, não é modelo nem cérebro |
| ExecuTorch | exportar modelo PyTorch próprio e executar no Android | candidato a runtime BSD; API LLM Android ainda experimental |
| llama.cpp | inferência ARM64, GGUF, kernels e quantização | candidato a runtime MIT; requer arquitetura/modelo compatível |
| MLC-LLM | compilação de modelo, runtime GPU e pacote Android | candidato experimental; cadeia de build mais complexa |

A pesquisa não termina na leitura do README. Cada referência será examinada em código, documentação, testes, arquitetura e licença; as conclusões serão registradas com commit ou versão.

## 7. Fases do programa

### Fase 0 — estabilizar o laboratório
**Saídas:** inventário do estado real; branches e arquivos protegidos; separação de produção e laboratório; transição do runtime antigo documentada; matriz do que compila e do que ainda está quebrado.

**Gate:** não alterar interface nem publicar o estado experimental; todo bloqueio deve estar declarado.

### Fase 1 — auditoria e estudo de referências
**Saídas:** mapa de cada repositório; leitura de código central; análise de testes; comparação de arquiteturas; registro de licenças e avisos.

**Gate:** cada componente proposto tem origem, versão, licença e justificativa. “Vi no README” não é investigação suficiente.

### Fase 2 — escolha da fundação neural e arquitetura cognitiva
**Saídas:** comparação de modelos de pesos abertos, auditoria legal, especificação da interface de inferência, plano de personalização e arquitetura própria de agente.

**Gate:** modelo candidato identificado com versão, licença, hash e formato; benchmark preliminar no Poco F6 ou justificativa explícita de incompatibilidade. Não declarar um vencedor antes dos testes.

### Fase 3 — núcleo cognitivo e adaptação
**Saídas:** planner estruturado, estado de tarefa, skills, subagentes internos, memória, registro de ferramentas, políticas, verificação e checkpoints. Conectar uma fundação neural validada pela interface própria.

**Gate:** tarefas multi-etapas executadas com evidência, limites e rollback. Se houver treino/adapters, registrar model-base, dataset, licença, configuração e hashes.

KBN-001 continua em paralelo como laboratório de treinamento próprio: tokenizer, passagem direta, gradientes e checkpoint são critérios de aprendizagem de engenharia, não o gate de entrega do assistente.

### Fase 4 — runtime no Poco F6
**Saídas:** uma única implementação de inferência Android; carregamento/descarga; quantização se aplicável; streaming; cancelamento; uso de RAM; recuperação após erro.

**Gate:** testes físicos no Poco F6. CI de build não substitui execução no telefone.

### Fase 5 — agente Kellvyn
**Saídas:** orquestrador, plano estruturado, registro de ferramentas, ciclo observar/agir, limites, aprovações, logs e avaliação.

**Gate:** tarefas multi-etapas concluídas com evidências, sem execução fora das permissões.

### Fase 6 — contexto, memória e proteção
**Saídas:** armazenamento local, busca de contexto, resumos, origem de dados, edição e exclusão, isolamento de segredos, testes contra prompt injection.

**Gate:** sobreviver a reinício sem perder estado necessário; não misturar conversas/projetos; dados externos não podem alterar as regras da IA.

### Fase 7 — agente de programação
**Saídas:** mapa de projeto, leitura, pesquisa, diff, escrita controlada, terminal/sandbox restrito, lint, testes, diagnóstico e rollback.

**Gate:** conjunto fixo de tarefas em repositórios de teste; qualidade medida por testes aprovados e regressões, não pela fluência da resposta.

### Fase 8 — criador de aplicativos
**Saídas:** gerador de projetos, editor, workspace, integração Gradle/SDK, compilador APK, assinatura, instalação e validação.

**Gate:** vários projetos Android de complexidade crescente construídos e executados no aparelho. APK válido não é sinônimo de app funcional.

### Fase 9 — ferramentas e controle do dispositivo
**Saídas:** Web, anexos, multimodalidade, bots, automação, integração com aplicativos e controle de dispositivo por mecanismos Android compatíveis.

**Gate:** cada funcionalidade passa por teste real no Poco F6, com permissões verificadas, interrupção funcional e logs.

### Fase 10 — consolidação privada e instalação no Poco F6

**Saídas:** build nativo pessoal, configuração local, gestão de dados, backup privado, recuperação, atualização controlada e documentação do proprietário.

**Gate:** APK pessoal compilado, assinado e verificado; instalação e execução reais no Poco F6; nenhuma dependência obrigatória de hospedagem ou serviço de IA externo; dados e credenciais permanecem sob controle do proprietário.

## 8. Métricas e critérios de sucesso

- taxa de tarefas concluídas com testes independentes aprovados;
- taxa de regressão após mudanças;
- precisão das chamadas de ferramenta;
- chamadas inválidas, loops e timeouts;
- qualidade de resposta em português avaliada em conjunto fixo;
- recuperação de contexto e memória;
- tempo para primeira resposta e tokens/segundo;
- pico de RAM, armazenamento, temperatura e consumo de bateria;
- sucesso de build, assinatura, instalação e abertura;
- taxa de falha em tarefas multimodais;
- percentagem de afirmações de conclusão sustentadas por evidência.

Nenhuma meta numérica será inventada. Primeiro coletaremos uma linha de base no Poco F6 e definiremos limites realistas.

## 9. Política de alterações

- Um objetivo por branch/commit lógico.
- Não empilhar refatoração, redesign e nova função no mesmo patch.
- Antes de editar: mapear os arquivos e os hashes que não podem mudar.
- Depois de editar: diff, lint, testes, build e comparação visual/funcional conforme o escopo.
- Falha de teste bloqueia a fase seguinte até ser entendida.
- Alterações da branch de laboratório só são promovidas para uma versão pessoal estável depois de passar pelos gates de teste.
- Não reescrever história compartilhada nem force-push.
- Registrar o motivo de uma decisão recusada para não a reintroduzir depois.

## 10. Estado de partida

A branch de laboratório mantém `KellvynRuntime.kt` sem inferência neural funcional. O agente experimental tem ciclo de ferramentas, planejamento estruturado, recuperação de contexto e memória local em implementação; ainda não está conectado a um modelo conversacional funcional. Nenhuma base neural externa está selecionada. A proteção da interface permanece divergente porque `MainActivity.java` difere do hash bloqueado; o workflow de APK continua bloqueado até resolver isso sem redesenho e sem enfraquecer a proteção. A branch `main` de produção não foi modificada.

A próxima etapa segura é fechar a auditoria e decidir o contrato de integração do novo modelo/agente antes de substituir as chamadas antigas.

---

## Fontes de referência

- Android Studio Agent Mode: https://developer.android.com/studio/gemini/agent-mode
- OpenHands SDK: https://github.com/OpenHands/software-agent-sdk
- Aider: https://github.com/Aider-AI/aider
- SWE-agent: https://github.com/SWE-agent/SWE-agent
- mini-SWE-agent: https://github.com/SWE-agent/mini-swe-agent
- OpenCode: https://github.com/anomalyco/opencode
- nanochat: https://github.com/karpathy/nanochat
- OLMo-core: https://github.com/allenai/OLMo-core
- LiteRT-LM: https://github.com/google-ai-edge/LiteRT-LM
- ExecuTorch: https://github.com/pytorch/executorch
- llama.cpp: https://github.com/ggml-org/llama.cpp
- MLC-LLM: https://github.com/mlc-ai/mlc-llm
