# Kellvyn — especificação do primeiro protótipo neural

**Identificador:** KN-001  
**Código:** `android/app/src/main/java/com/kellvyn/assistant/neural/KellvynByteModel.kt`  
**Estado:** primeira implementação experimental; ainda não conectada ao chat.

## Objetivo

Provar, com código escrito neste repositório, que a Kellvyn consegue inicializar parâmetros aleatórios, executar uma previsão neural, calcular gradientes, atualizar seus próprios pesos, medir a loss e persistir um checkpoint próprio.

## Estrutura matemática implementada

1. O texto é codificado em bytes UTF-8: IDs de 0 a 255. O ID 256 é reservado para preenchimento à esquerda.
2. Cada byte recebe um vetor de embedding treinável.
3. Uma janela fixa dos bytes anteriores é posicionada e projetada para uma camada oculta.
4. A camada oculta usa tangente hiperbólica.
5. A camada de saída calcula 256 logits e uma distribuição softmax.
6. A loss é entropia cruzada para previsão do próximo byte.
7. A retropropagação calcula gradientes de embeddings, pesos e vieses.
8. O gradiente global é limitado antes da atualização por descida do gradiente.

As operações matemáticas são princípios gerais conhecidos. O código, as estruturas, os parâmetros, o treinamento e o formato de checkpoint foram escritos especificamente para Kellvyn. Este protótipo não reivindica ter inventado embeddings, MLP, softmax ou retropropagação; também não reproduz um modelo público identificado.

## Origem dos parâmetros

A função `create` inicializa todos os parâmetros a partir de uma seed explícita usando o gerador da plataforma Java. Nenhum arquivo externo é carregado. `readCheckpoint` aceita somente o formato versionado KLVN, com assinatura e dimensões verificadas.

## Limites conhecidos

- Não possui memória recorrente; só vê a janela fixa de contexto.
- É um preditor de bytes, não um assistente conversacional.
- Não possui corpus de pré-treinamento de qualidade incluído.
- O teste com sequência repetitiva valida a mecânica de treino, não conhecimento de português.
- Não está conectado ao orquestrador, ao chat, aos bots ou às ferramentas.
- Não há benchmark físico no Poco F6 ainda.

## Próximos critérios técnicos

Antes de crescer o modelo: validar numericamente os gradientes por diferenças finitas, medir custo e memória, separar treino/validação/teste, registrar dataset autorizado e testar a estabilidade por múltiplas seeds. A próxima arquitetura será projetada a partir dos resultados deste protótipo; não será copiada de outro modelo.
