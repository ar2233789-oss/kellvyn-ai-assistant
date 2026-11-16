# Kellvyn — registro do laboratório

**Data:** 16/11/2026  
**Branch:** `lab/kellvyn-intelligence-core-20261116`  
**Objetivo atual:** desenvolver uma inteligência e um agente privados, de uso exclusivo do proprietário.

## Decisão de produto

- Não há publicação planejada na Play Store.
- Não há distribuição pública planejada.
- O objetivo é um agente privado, independente e integrado ao Poco F6.
- A conexão do telefone pode ser usada para pesquisa e acesso Web opcionais; o cérebro não deve depender de uma API de IA em nuvem para conversar.
- O uso privado não autoriza plágio: código, modelos, pesos, dados e bibliotecas continuam exigindo proveniência e termos de uso compatíveis.

## Estado de partida verificado

- O repositório contém app Android nativo, chat, histórico, anexos, workspace local e um compilador Android limitado a Java.
- A interface existente é referência protegida e não deve ser redesenhada durante o desenvolvimento do cérebro.
- A antiga implementação `LocalBrain.kt` baseada em Gemma foi removida desta branch.
- As referências antigas a `LocalBrain` permanecem em `MainActivity.java` e `BotRuntimeService.kt`; o branch está em transição e não deve ser compilado ou instalado.
- A dependência LiteRT-LM permanece declarada no Gradle, mas não está aprovada como runtime final.
- A branch `cerebro` contém um experimento Web/browser e não deve ser tomada como base para a inteligência Android nativa.

## Pesquisa já realizada

- Foram inspecionadas estruturas e arquivos centrais de OpenHands SDK, Aider, SWE-agent, mini-SWE-agent, OpenCode, nanochat e OLMo-core.
- Foram consultadas licenças raiz de projetos de referência; isso é apenas triagem, não valida cada dependência transitiva.
- Foram registrados candidatos de runtime e uma estratégia de linhagem de modelo, ainda sem decisão final.
- Os documentos mestres do projeto ficam em `docs/kellvyn/`.

## Próxima sequência — sem saltar etapas

1. Finalizar o mapa dos acoplamentos antigos no app e nos bots.
2. Definir contratos próprios `KellvynEngine`, `KellvynSession` e `KellvynAgent`.
3. Comparar runtime de inferência sem escolher pesos por conveniência.
4. Implementar o primeiro núcleo neural experimental com pesos inicializados por nós, acompanhado de tokenizer, teste de forward/backward e checkpoint.
5. Definir caminho realista de treino e benchmark para o Poco F6 e, se necessário, um ambiente de treino separado.
6. Só depois conectar o runtime selecionado ao agente e ao chat.

## Não declarar como concluído

- Modelo conversacional Kellvyn treinado;
- memória inteligente;
- agente geral com uso de ferramentas;
- edição e compilação autônomas de qualquer app;
- controle geral do dispositivo;
- benchmark no Poco F6;
- APK desta branch validado.

Cada capacidade exige teste real e evidência.
