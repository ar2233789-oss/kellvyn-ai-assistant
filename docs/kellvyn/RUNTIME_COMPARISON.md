# Kellvyn — comparação de runtimes Android

**Status:** nenhum modelo ou runtime foi selecionado para a versão final. Este documento compara runtimes possíveis em caráter de pesquisa; não representa uma escolha de cérebro neural.

## Critérios obrigatórios

- Android ARM64, minSdk compatível com o app;
- CPU funcional antes de depender de GPU/NPU;
- uso offline depois de instalar os pesos;
- formato e modelo-base documentados;
- streaming e cancelamento;
- sessão e ciclo de vida sob um único executor de inferência;
- ferramentas em modo manual, com política de autorização Kellvyn;
- RAM, armazenamento, aquecimento e velocidade medidos no Poco F6;
- licença e dependências transitivas registradas.

## Candidato 1 — LiteRT-LM 0.19.1

- Licença do runtime: Apache 2.0.
- API Kotlin para Android/JVM.
- Backends CPU, GPU e NPU, sujeitos ao suporte do modelo e do aparelho.
- Engine, Conversation, streaming por Flow e suporte a multimodalidade.
- A API permite desabilitar execução automática de ferramentas (`automaticToolCalling=false`) e entregar a decisão ao código do app para execução manual.
- A versão 0.19.1 documenta recursos multimodais em backends específicos; disponibilidade de um backend ou arquitetura em geral não prova suporte a aceleração no Poco F6. A compatibilidade precisa ser demonstrada com o componente e o dispositivo exatos.

Dependência a testar e fixar, sem `latest.release`:
`com.google.ai.edge.litertlm:litertlm-android:0.19.1`.

A inicialização do Engine pode bloquear por vários segundos e deve ocorrer em thread/corrotina de fundo. Um executor serial deve ser proprietário de Engine e Conversation; cada recurso deve ser encerrado exatamente uma vez e na ordem correta.

### CPU e GPU

- Começar por CPU com 4 threads como baseline.
- GPU é uma segunda configuração de teste, não uma suposição de compatibilidade. O manifesto pode declarar `libvndksupport.so` e `libOpenCL.so` como bibliotecas nativas opcionais.
- NPU não será usada sem pacote explicitamente compatível com o Snapdragon 8s Gen 3 e confirmação do backend no Poco F6.

## ExecuTorch

BSD 3-Clause no repositório principal. Mantido como alternativa caso o modelo/fluxo de exportação escolhido seja melhor suportado. A API LLM Android tem estado experimental descrito nas páginas oficiais.

## llama.cpp

MIT no projeto principal. Alternativa de inferência ARM64/GGUF, se a arquitetura e a conversão do modelo forem compatíveis. O suporte CPU não garante aceleração GPU específica no Poco F6.

## MLC-LLM

Apache 2.0 no projeto principal. Candidato experimental para GPU móvel. A cadeia de compilação e os backends precisam ser avaliados; não selecionar sem benchmark e build reproduzível.

## Benchmark no Poco F6

1. Instalar o pacote de modelo em armazenamento privado, depois de consentimento.
2. Verificar SHA-256 e tamanho.
3. Iniciar pela CPU e registrar memória livre antes/depois.
4. Medir cold start, tempo até primeiro token, tokens/s, uso de RAM e erros.
5. Repetir com contexto curto e longo.
6. Medir temperatura e throttling após cargas repetidas.
7. Só então testar GPU e comparar a mesma bateria de prompts.
8. Testar cancelamento, encerramento e reinício do processo.
9. Testar function calling manual sem executar ferramentas fora da política Kellvyn.

## Fontes oficiais

- Kotlin Android API: https://developers.google.com/edge/litert-lm/android
- API overview e Maven: https://developers.google.com/edge/litert-lm/api_overview
- Release 0.19.1: https://github.com/google-ai-edge/LiteRT-LM/releases/tag/v0.19.1
- ExecuTorch LLM: https://pytorch.org/executorch/stable/llm/getting-started.html
- llama.cpp Android: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- MLC-LLM Android: https://llm.mlc.ai/docs/deploy/android.html

## Estado

Nenhum runtime foi selecionado para a versão final. Cada opção depende de auditoria de dependências, compatibilidade real, CI e teste físico no Poco F6 com o componente neural específico que vier a ser autorizado.
