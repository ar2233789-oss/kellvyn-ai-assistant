# Kellvyn — registro do laboratório

**Data:** 16/11/2026  
**Branch:** `lab/kellvyn-intelligence-core-20261116`  
**Repositório:** `ar2233789-oss/kellvyn-ai-assistant`

## Decisão irrevogável sobre o cérebro

O cérebro será criado do zero neste projeto:
- arquitetura e código neural próprios;
- tokenizer desenvolvido no projeto;
- pesos inicializados aleatoriamente;
- treinamento e checkpoints próprios;
- nenhuma base pré-treinada, pesos externos, fine-tuning, LoRA, adaptador ou fallback de outra IA;
- projetos públicos podem ser estudados como referências conceituais, sem copiar código, pesos, tokenizers ou datasets.

O uso privado não torna plágio aceitável. Toda origem dos dados e toda etapa de treino serão documentadas.

## Trabalho executado nesta branch

- Criada a fronteira temporária `KellvynRuntime`, sem provedor ou peso externo.
- Criado `KellvynAgent` como controlador do ciclo de ferramentas; não é o cérebro neural.
- Removida a implementação anterior de cérebro baseada em Gemma da branch de laboratório.
- Removida a dependência LiteRT-LM do Gradle desta branch.
- Criado o primeiro protótipo neural autoral `KellvynByteBrain.kt`: tokenizer UTF-8 por bytes, inicialização aleatória, embeddings treináveis, camada oculta, softmax, loss de próximo byte, retropropagação, atualização de pesos e checkpoint próprio.
- Criados testes de tokenizer, redução de loss em sequência sintética, alteração de pesos e persistência exata de checkpoint.
- Corrigido um teste preexistente do agente que tentava chamar `toList()` no resultado de `repeat()`.
- Criado workflow isolado de testes neurais, sem gerar APK nem publicar release.
- Documentada a especificação KBN-001.

## Validação atual

- Uma execução anterior de `testDebugUnitTest` concluiu com sucesso, cobrindo o protótipo inicial e os testes do agente.
- Foi adicionada agora uma verificação numérica da retropropagação por diferenças finitas. A execução dessa nova verificação está em andamento.
- O teste inicial só mede o aprendizado sobre sequência sintética repetitiva. Não demonstra compreensão geral, diálogo ou raciocínio.
- Nenhum APK desta branch foi validado.
- Nenhum teste físico no Poco F6 foi executado nesta etapa.

## Bloqueios conhecidos

- `MainActivity.java` diverge do hash fixado em `.github/interface-lock.sha`. O arquivo teve alterações apenas na ligação de runtime e no texto de estado, mas o mecanismo de proteção continua exigindo correspondência exata. O bloqueio não será enfraquecido nem o hash será alterado sem autorização explícita.
- A integração definitiva do cérebro com o chat ainda não foi feita.
- O agente não possui backend neural e não deve ser apresentado como pronto.
- O protótipo KBN-001 é uma pequena rede preditora de bytes; não é ainda uma arquitetura conversacional completa.

## Próximas etapas técnicas

1. Aprovar os gradientes analíticos por comparação numérica.
2. Fortalecer formato de checkpoint, limites de memória e testes com múltiplas seeds.
3. Construir avaliação separada de treino, validação e teste.
4. Especificar uma arquitetura sequencial mais capaz, escrita e testada no projeto, sem reproduzir um modelo público.
5. Desenvolver um pipeline de corpus com origem e autorização verificadas.
6. Medir o que o Poco F6 consegue treinar e executar; só então definir escala e otimizações.
7. Integrar o cérebro ao agente e à interface somente após os gates neurais.

## Não declarar como concluído

- IA conversacional Kellvyn;
- raciocínio geral ou programação autônoma;
- memória inteligente treinada;
- agente geral com ferramentas;
- controle do Android;
- criador universal de APKs;
- build final ou teste físico no Poco F6.

Cada capacidade depende de implementação e evidência próprias.
