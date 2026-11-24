# Matriz de runtimes de IA no Android

**Estado: pesquisa técnica; nenhuma biblioteca foi instalada nem integrada ao aplicativo.**

O objetivo é escolher um runtime para experimentos locais sem comprometer a arquitetura: uma única inteligência principal decide; runtimes executam inferência; ferramentas auxiliares apenas inspecionam, medem e validam. Nenhum runtime ganha autonomia para planejar o trabalho do Kellvyn.

## Comparação

| Runtime | Formato de modelo | Caminhos Android | Ponto forte | Limitação para este projeto |
|---|---|---|---|---|
| llama.cpp | GGUF | CPU ARM64; builds específicos podem incluir Adreno/Hexagon; binding Kotlin Android | Caminho direto para modelos GGUF e inferência local; documentação inclui build Android e Termux | Build completo continua pesado; aceleração depende do build, kernel, SoC e versão; não presumir NPU no Poco sem teste |
| LiteRT-LM | .litertlm | Kotlin/Android; CPU, GPU e NPU conforme dispositivo/runtime | API Android nativa, streaming, multimodalidade e uso de ferramentas; modelos Gemma, Llama, Phi e Qwen | Modelos precisam estar no formato compatível; aceleração NPU/GPU precisa ser verificada no aparelho |
| ExecuTorch | .pte + recursos/tokenizador necessários | XNNPACK CPU, Vulkan GPU, Qualcomm QNN | Backend modular e caminho Qualcomm; o enum oficial inclui SM8635 | Exportação/AOT, NDK e SDK QNN exigem toolchain; a inclusão do SoC no enum não prova que o Poco F6 foi validado |
| NNAPI | Depende do modelo/API | API Android antiga | Histórico de aceleração no Android | Descontinuada no Android 15; não deve ser base de uma nova arquitetura |

## Evidências e interpretação

### llama.cpp — candidato inicial para GGUF

A documentação oficial inclui um binding Android em Kotlin, leitura de metadados GGUF, seleção de modelo local, geração em fluxo e um caminho de build para Android ARM64. Também documenta build via Termux no próprio Android e compilação cruzada com Android NDK. O projeto mantém artefatos Android ARM64; algumas distribuições recentes separam builds CPU e Snapdragon com CPU/Adreno/Hexagon.

**Decisão provisória:** primeiro candidato para um teste controlado de inferência GGUF. Começar com CPU ARM64 para estabelecer uma linha de base; só comparar GPU/NPU quando houver build explicitamente compatível com o aparelho. Não assumir que qualquer APK/binário publicado serve ao Poco.

### LiteRT-LM — candidato para integração Kotlin nativa

O LiteRT-LM oferece API Kotlin para Android, streaming por coroutines, conversas multimodais e backends GPU/NPU quando suportados. A documentação também permite tool calling automático ou manual. Para manter o cérebro principal como autoridade única, qualquer integração futura deve usar chamada manual de ferramentas (automaticToolCalling = false): o runtime entrega a solicitação; a camada principal valida, decide e executa a ferramenta autorizada.

O formato de modelo documentado é .litertlm; não se deve tratar um GGUF ou Safetensors como intercambiável com esse formato. A conversão/empacotamento do modelo precisa ser uma etapa explícita e reproduzível.

**Decisão provisória:** candidato forte para uma integração Android mais nativa, depois de confirmar modelo, conversão, tamanho, RAM e backend no Poco F6.

### ExecuTorch + Qualcomm QNN — candidato de pesquisa, não presumir validação

ExecuTorch documenta backends Android XNNPACK (CPU), Vulkan (GPU), MediaTek e Qualcomm AI Engine (QNN). O enum oficial QcomChipset inclui SM8635, identificado como HTP v73. O Poco F6 usa Snapdragon 8s Gen 3; essa correspondência torna o caminho tecnicamente relevante, mas não prova compatibilidade de todas as operações, modelo, versão do SDK ou bibliotecas do aparelho.

O tutorial Qualcomm do ExecuTorch declara verificação em Android/Linux com SM8550 e SM8450. Portanto, a presença de SM8635 na enumeração não deve ser apresentada como teste concluído no Poco F6. A exportação AOT e o build documentado usam ambiente de desenvolvimento, Android NDK e SDK QNN; isso é uma barreira real para uma rotina exclusivamente no telefone.

**Decisão provisória:** manter como trilha de aceleração futura. Não escolhê-lo como primeira implementação até comprovar o caminho de build sem PC e o suporte real ao SM8635.

### NNAPI — não iniciar por aqui

O Android declara NNAPI descontinuada a partir do Android 15 e direciona desenvolvedores para runtimes mais atuais. Não iniciar uma integração nova dependente de NNAPI.

## Sequência de validação quando houver autorização para executar

1. Confirmar no aparelho ABI, versão Android, RAM disponível e bibliotecas nativas acessíveis.
2. Selecionar um modelo pequeno e licenciado, com arquivo local e hash conhecido.
3. Executar primeiro CPU-only e medir carregamento, memória de pico, TTFT, tokens/s, temperatura e estabilidade.
4. Repetir com GPU/NPU somente quando o backend declarar suporte ao SoC e ao modelo.
5. Rodar a mesma suíte fixa de qualidade e o mesmo perfilador para comparar os caminhos.
6. Rejeitar qualquer ganho de velocidade que venha com regressão de qualidade, falha de cobertura ou instabilidade térmica.
7. Manter o runtime substituível por adaptador; o cérebro principal e o histórico do produto não devem depender de uma biblioteca específica.

## Fontes oficiais

- llama.cpp Android: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- llama.cpp builds e opções Android ARM64: https://github.com/ggml-org/llama.cpp/blob/master/docs/build.md
- LiteRT-LM Android/Kotlin: https://developers.google.com/edge/litert-lm/android
- LiteRT-LM visão geral e formatos/modelos: https://developers.google.com/edge/litert-lm/overview
- ExecuTorch backends Android: https://docs.pytorch.org/executorch/stable/using-executorch-android.html
- ExecuTorch Qualcomm QNN: https://docs.pytorch.org/executorch/stable/backends-qualcomm.html
- Enumeração oficial de SoCs Qualcomm: https://github.com/pytorch/executorch/blob/main/backends/qualcomm/serialization/qc_schema.py
- Migração da NNAPI: https://developer.android.com/ndk/guides/neuralnetworks/migration-guide
