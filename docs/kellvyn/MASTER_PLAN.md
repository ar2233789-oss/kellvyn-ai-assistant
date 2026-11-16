# KELLVYN — plano mestre de engenharia

**Versão:** 1.0  
**Data:** 16/11/2026  
**Nome oficial da inteligência:** Kellvyn  
**Branch de trabalho:** `lab/kellvyn-intelligence-core-20261116`  
**Repositório de laboratório:** `ar2233789-oss/kellvyn-ai-assistant`  
**Regra de distribuição:** o repositório antigo continua reservado para releases; não publicar nele mudanças experimentais.

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
- administrar automações e bots dentro das permissões e limitações legais do Android;
- executar tarefas no Poco F6 com funcionamento local e sem exigir hospedagem permanente.

A interface aprovada do aplicativo é uma superfície de interação, não o cérebro. A sua estrutura visual, animações e controles protegidos não são alvo deste trabalho. As capacidades devem ser acionadas pela conversa principal e pelos controles que já existem, sem criar uma segunda IA ou um segundo chat.

## 2. O que significa “legítima” neste projeto

A Kellvyn pode utilizar conhecimento, bibliotecas, ferramentas, modelos ou trechos de código de terceiros quando a licença e os termos aplicáveis autorizarem esse uso. Isso não é plágio por si só. O que não será permitido:

- retirar avisos de copyright ou licenças;
- copiar código protegido sem autorização;
- esconder a origem de um componente;
- atribuir à Kellvyn o treinamento, os pesos ou a implementação de terceiros;
- chamar um modelo baixado de “modelo treinado pela Kellvyn”;
- usar o nome, a marca, a interface ou os recursos visuais de outro produto de forma confusa;
- incorporar pesos ou datasets antes de conferir separadamente seus termos de uso e redistribuição.

A documentação de cada dependência, modelo, dataset, tokenizer, ferramenta e recurso visual será registrada. O pacote de lançamento deverá conter os avisos e as licenças exigidos.

**Originalidade não exige reescrever toda a ciência da computação. Exige autoria verdadeira, proveniência e cumprimento das licenças.**

## 3. Restrições que orientam todas as decisões

1. Uma única inteligência chamada Kellvyn; não montar uma coleção de IAs com identidades concorrentes.
2. Não usar Gemma como cérebro da Kellvyn. A antiga integração Gemma não será recuperada nesta linha de desenvolvimento.
3. Não alegar treinamento, raciocínio, execução, pesquisa, compilação, instalação ou teste que não tenha ocorrido e sido verificado.
4. Funcionamento local como objetivo principal; Web opcional pela conexão do telefone.
5. Alvo de execução: Poco F6, Snapdragon 8s Gen 3, 8 GB de RAM.
6. Não presumir que o telefone pode pré-treinar um LLM avançado. Inferência, treinamento de modelo e treinamento de agentes são problemas diferentes.
7. Preservar a interface aprovada. Alterações de inteligência devem ficar separadas dos arquivos de interface bloqueados.
8. Não adicionar WebView à versão Android final.
9. Não colocar terminal irrestrito, shell genérico ou acesso arbitrário ao armazenamento à disposição do modelo.
10. Ações com efeitos colaterais exigem políticas executadas no código e confirmação quando o risco exigir.
11. Nenhum modelo, dataset, runtime ou código será escolhido somente pela popularidade. A decisão precisa de licença, compatibilidade, benchmark e justificativa.
12. O laboratório não é o repositório de distribuição. Não publicar branches experimentais como APK final.

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

### 4.1 Modelo neural

É a parte que representa linguagem, interpreta contexto e gera respostas ou chamadas de ferramenta. Sua linhagem precisa ser declarada:

- **Pesos próprios:** arquitetura implementada por nós e pesos iniciados aleatoriamente e treinados por nós.
- **Pesos derivados de uma base licenciada:** modelo de terceiros cuja licença permite o uso pretendido, adaptado por nós e identificado como derivado dessa base.

As duas opções podem resultar em um produto legítimo, mas não são a mesma coisa. A decisão ainda não está tomada. Nenhum peso de terceiros será tratado como peso original da Kellvyn.

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
- futura compreensão de tela, desde que os mecanismos Android e as políticas de distribuição permitam.

### F. Bots e automações
- Telegram e Discord com tokens protegidos e runtime verificável;
- estado “ativo” somente quando a conexão e o processamento estiverem confirmados;
- WhatsApp somente quando existir uma integração oficial/compatível com infraestrutura exigida e termos verificados;
- manter controle de ativação e interrupção pelo usuário.

### G. Jogos e controle de dispositivo
Esta é uma área de risco técnico e de política. O uso de AccessibilityService pelo Google Play não pode alimentar agentes que autonomamente iniciem, planejem e executem ações, exceto a exceção estreita para ferramentas realmente destinadas a acessibilidade. Portanto, não será implementada uma promessa de agente autônomo que controle qualquer app por AccessibilityService. Cada caso terá que ser projetado com APIs Android permitidas, escopo estreito, ação iniciada pelo usuário e análise de política antes de ser incluído na versão de Play Store.

## 6. Como estudar e aproveitar cada referência

| Referência | Aprendizado pretendido | Tipo de aproveitamento |
|---|---|---|
| Android Studio + Gemini | planejamento multi-etapas, navegação por projeto, ferramentas, permissões, Logcat, emulador/dispositivo, correção iterativa | padrões de fluxo; o código e o serviço Gemini são proprietários |
| OpenHands / Agent SDK | sessões, eventos, workspaces, estado de execução, agentes e ferramentas | arquitetura e, quando fizer sentido, código MIT com avisos |
| Aider | mapa do repositório, seleção de contexto, diffs, integração Git, lint e testes | algoritmos/padrões; código Apache 2.0 se integrado |
| SWE-agent | interface agente-computador, trajetórias observáveis, benchmark de tarefas reais | métodos e testes; MIT |
| mini-SWE-agent | ciclo compacto de observação/ação e desenho simples de agente | referência atual adicional; MIT |
| OpenCode | planejamento separado de execução, políticas de permissão, subagentes/skills e compactação | conceitos e código MIT selecionado |
| nanochat | tokenizer, pré-treinamento, SFT, avaliação, RL e inferência em uma sequência didática | referência e componentes MIT, especialmente na área de treino |
| OLMo-core | data pipeline, distribuição, otimizadores, checkpoints, métricas e avaliação | infraestrutura Apache 2.0, sobretudo em eventual ambiente de treino |
| LiteRT-LM | inferência Android, GPU/NPU, multimodalidade e chamada de ferramenta | candidato a runtime, não é modelo nem cérebro |
| ExecuTorch | exportar modelo PyTorch próprio e executar no Android | candidato a runtime BSD; API LLM Android ainda experimental |
| llama.cpp | inferência ARM64, GGUF, kernels e quantização | candidato a runtime MIT; requer arquitetura/modelo compatível |
| MLC-LLM | compilação de modelo, runtime GPU e pacote Android | candidato experimental; cadeia de build mais complexa |

A pesquisa não termina na leitura do README. Cada referência será examinada em código, documentação, testes, arquitetura e licença; as conclusões serão registradas com commit ou versão.

## 7. Fases do programa

### Fase 0 — estabilizar o laboratório
**Saídas:** inventário do estado real; branches e arquivos protegidos; separação de produção e laboratório; branch de transição sem Gemma documentada; matriz do que compila e do que ainda está quebrado.

**Gate:** não alterar interface nem publicar o estado experimental; todo bloqueio deve estar declarado.

### Fase 1 — auditoria e estudo de referências
**Saídas:** mapa de cada repositório; leitura de código central; análise de testes; comparação de arquiteturas; registro de licenças e avisos.

**Gate:** cada componente proposto tem origem, versão, licença e justificativa. “Vi no README” não é investigação suficiente.

### Fase 2 — decisão do modelo neural
**Saídas:** comparação de pesos próprios versus base aberta licenciada; tokenizer; arquitetura; dados; custo/limite de treinamento; formato de inferência.

**Gate:** decisão documentada e aprovada antes de baixar, treinar ou integrar pesos.

### Fase 3 — prova neural mínima
**Saídas:** tokenizador treinável; modelo pequeno inicializado por nós ou derivado conforme a decisão; treinamento reproduzível; checkpoint; inferência; benchmarks.

**Gate:** loss, perplexidade/qualidade, memória, latência e testes registrados. Um modelo que apenas emite texto não é automaticamente conversacional ou inteligente.

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

### Fase 9 — ferramentas adicionais
**Saídas:** Web, anexos, multimodalidade, bots, interface flutuante e outras capacidades já previstas.

**Gate:** cada funcionalidade com permissão, política, teste e estado real. Itens proibidos pelo Play Store não entram na distribuição.

### Fase 10 — release Play Store
**Saídas:** inventário de terceiros, avisos, licenças, SBOM, privacidade, Data Safety, permissões, screenshots originais, política de dados e pacote final assinado.

**Gate:** auditoria jurídica/técnica e cumprimento das políticas vigentes do Google Play. A aprovação da loja não pode ser prometida de antemão.

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
- Alterações na branch de laboratório nunca são copiadas para produção sem gate de release.
- Não reescrever história compartilhada nem force-push.
- Registrar o motivo de uma decisão recusada para não a reintroduzir depois.

## 10. Estado de partida

A branch atual é um rascunho de remoção/transição do runtime Gemma. Foram removidos dela `LocalBrain.kt`, `NativeModelPolicy.java`, o teste desse downloader e a primeira ferramenta experimental que estava acoplada à API LiteRT-LM. As chamadas antigas em `MainActivity.java` e `BotRuntimeService.kt` ainda precisam ser substituídas; o Gradle ainda inclui a biblioteca LiteRT-LM. Portanto, a branch não está pronta para build, não deve ser mesclada e não deve gerar APK de distribuição. O branch `main` de produção não foi modificado.

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
- Google Play IP policy: https://support.google.com/googleplay/android-developer/answer/9888072
- Google Play AccessibilityService policy: https://support.google.com/googleplay/android-developer/answer/10964491
