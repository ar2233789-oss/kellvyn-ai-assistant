# Kellvyn — registro do laboratório

**Data:** 2026-11-16  
**Branch:** `lab/kellvyn-intelligence-core-20261116`  
**Base inicial:** `main` em `c85f2d94dea96c3d537aad81937f376ce36bf891`

## Estado de partida verificado

- O repositório tem uma aplicação Android nativa e uma implementação local em `LocalBrain.kt`.
- O motor atual usa `litertlm-android:0.18.0` e aponta para um modelo Gemma 4 E2B LiteRT-LM.
- O contexto configurado está em 4096 tokens; a saída normal está limitada a 768 tokens.
- Há workspace local privado e compilador Android Java restrito.
- Não havia ferramentas gerais de workspace registradas na configuração de conversa do `LocalBrain`.
- A branch `cerebro` é um experimento WebGPU/browser; não é a base correta para o APK nativo.
- O branch `kellvyn/native-local-brain` contém uma versão anterior/simplificada do motor; a implementação de `main` é a linha de base mais completa.

## Trabalho desta etapa

- Criar a branch isolada de inteligência.
- Documentar a arquitetura original Kellvyn e a origem das referências.
- Registrar a separação entre modelo, agente, ferramentas, memória e treinamento.
- Adicionar primeiro conjunto de ferramentas locais somente leitura e limites testáveis.
- Não alterar a interface protegida nem o repositório de distribuição.

## Não declarar como pronto

- Treinamento de pesos próprios;
- memória persistente avançada;
- agente de programação com edição geral;
- terminal/sandbox geral;
- execução autônoma de operações destrutivas;
- tool calling validado no Poco F6;
- APK desta branch validado.

Cada item acima depende de implementação e evidência próprias.
