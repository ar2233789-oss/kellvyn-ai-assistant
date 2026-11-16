# Kellvyn — comparação de runtimes Android

**Estado:** nenhuma dependência externa de runtime neural foi aprovada. A primeira inferência será escrita no próprio projeto para executar exclusivamente pesos Kellvyn treinados do zero. Este documento fica como mapa de pesquisa técnica, não como lista de dependências autorizadas.

## Critérios obrigatórios

- Android ARM64 no Poco F6;
- CPU funcional mesmo sem GPU/NPU;
- opção de aceleração no hardware disponível;
- formato de checkpoint definido e produzido pelo treinamento próprio da Kellvyn;
- quantização;
- cache de contexto/KV;
- streaming, cancelamento e gestão de ciclo de vida;
- integração Java/Kotlin/JNI;
- licença e dependências transitivas;
- funcionamento offline;
- RAM, aquecimento e desempenho medidos em aparelho real.

## Candidatos

| Runtime | Licença do projeto principal | Vantagens | Riscos/perguntas |
|---|---|---|---|
| LiteRT-LM | Apache 2.0 | API Kotlin Android/JVM, GPU/NPU, multimodalidade e ferramentas na API | não será integrado nesta fase; tool calling depende de modelos compatíveis |
| ExecuTorch | BSD 3-Clause | fluxo PyTorch → exportação `.pte`, runtime C++ e bindings Android | APIs LLM Android são descritas como experimentais; validar o export de arquitetura própria e backend Snapdragon |
| llama.cpp | MIT | suporte Android ARM64, GGUF, kernels CPU e ferramentas maduras de quantização | arquitetura precisa ser suportada pelo loader/conversor; validar aceleração GPU disponível no Poco F6 |
| MLC-LLM | Apache 2.0 no projeto principal | runtime compilado com foco em GPU móvel, cadeia de conversão/empacotamento | setup de compilação exige NDK, Rust, CMake e toolchain; confirmar se isso é sustentável no fluxo de desenvolvimento do usuário |

## Observações técnicas confirmadas

### LiteRT-LM
Estudado apenas para compreender o papel separado de um runtime. Não será incorporado agora, porque o primeiro motor neural será implementado no projeto Kellvyn e não dependerá de um runtime que possa introduzir código ou modelo de terceiros.

### ExecuTorch
A documentação mostra exportação de LLM para `.pte` e execução Android por APIs Java/C++. A documentação marca a API LLM Android como experimental. É um apenas referência de pesquisa; não está aprovado para integrar o cérebro.

### llama.cpp
A documentação oficial traz build Android `arm64-v8a`, projeto Android de exemplo, leitura de GGUF e runtime CPU com detecção de kernels. É uma boa referência para inferência local e formato quantizado, se a arquitetura e conversor forem compatíveis.

### MLC-LLM
A documentação Android exige toolchain Android NDK, CMake e Rust, compila bibliotecas de modelo e runtime e ressalta que a aceleração real depende de GPU física. A cadeia deve ser comparada com a restrição de desenvolvimento pelo telefone.

## Plano de benchmark

1. Fixar um único artefato de teste pequeno, sem dados privados.
2. Compilar cada runtime candidato com a mesma configuração de build e ABI.
3. Carregar o mesmo modelo/quantização quando o formato permitir.
4. Executar o mesmo conjunto de prompts.
5. Medir latência de carregamento, prefill, tokens/s, memória máxima, temperatura, bateria, falhas e qualidade.
6. repetir após fechar/reabrir o app.
7. comparar integração com streaming, cancelamento, Tool Calling e liberação de memória.
8. armazenar logs e resultados brutos.

Não escolher runtime por uma única execução ou por benchmark de outro aparelho.

## Fontes técnicas

- LiteRT-LM Android: https://developers.google.com/edge/litert-lm/android
- ExecuTorch LLM: https://pytorch.org/executorch/stable/llm/getting-started.html
- ExecuTorch Android: https://pytorch.org/executorch/stable/llm/run-on-android.html
- llama.cpp Android: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- MLC-LLM Android: https://llm.mlc.ai/docs/deploy/android.html
