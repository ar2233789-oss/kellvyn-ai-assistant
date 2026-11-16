# Kellvyn — registro de procedência do modelo local de avaliação

**Estado:** candidato prioritário para benchmark; ainda não instalado, nem selecionado como fundação final.  
**Modelo de origem:** Google DeepMind — Gemma 4 E2B Instruct.  
**Pacote móvel:** LiteRT Community — \`gemma-4-E2B-it.litertlm\`.

## Identificação e licença

- Repositório do pacote: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
- Revisão de repositório consultada: \`a4a831c060880f3733135ad22f10e0e9f758f45d\`
- Arquivo: \`gemma-4-E2B-it.litertlm\`
- Licença declarada do pacote: Apache License 2.0.
- Modelo-base declarado no cartão: \`google/gemma-4-E2B-it\`.
- Relação declarada: quantized.
- Tamanho publicado: 2.583.085.056 bytes (aprox. 2,59 GB).
- SHA-256 publicado para o arquivo: \`181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c\`.
- URL fixada na revisão consultada:
  https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/a4a831c060880f3733135ad22f10e0e9f758f45d/gemma-4-E2B-it.litertlm

A atribuição continuará explícita: os pesos-base pertencem à linhagem Gemma 4 e o pacote é uma conversão/quantização publicada pela LiteRT Community. Não serão renomeados como pesos treinados do zero pela Kellvyn.

## Formato e capacidade publicadas

- Formato de implantação: \`.litertlm\`.
- O cartão descreve texto e componentes multimodais, incluindo visão e áudio sob demanda.
- O pacote indica contexto de até 32K tokens, diferente do contexto maior descrito para outras variantes/formas do modelo.
- O stack LiteRT-LM acrescenta execução, cache KV, templates e suporte a function calling.
- Para uso somente texto, o cartão informa aproximadamente 0,79 GB de pesos do decoder e 1,12 GB de parâmetros de embeddings mapeados em memória.
- Visão e áudio são carregados quando necessários.

## Runtime de avaliação

- LiteRT-LM Android Kotlin API.
- Versão inicialmente fixada no laboratório: 0.19.1.
- Licença do projeto/runtime: Apache 2.0.
- Primeira configuração de teste: CPU, 4 threads.
- GPU só será testada como configuração separada, depois de confirmar bibliotecas nativas e estabilidade.
- NPU não será presumida compatível com o Snapdragon 8s Gen 3; nenhum artefato Qualcomm para outra plataforma será tratado como suporte ao Poco F6.

## Limite dos benchmarks publicados

O cartão do pacote publica medidas de Android em Samsung S26 Ultra (CPU e GPU), não no Poco F6. Esses resultados ajudam a formular hipóteses, mas não predizem desempenho, temperatura, memória de pico ou estabilidade no dispositivo alvo.

O Poco F6 tem 8 GB de RAM. O sistema operacional, o app, o KV cache, as imagens/áudio e outros processos competem por essa memória. O modelo só será aprovado após teste real no aparelho.

## Segurança das ferramentas

A conversa será criada com \`automaticToolCalling = false\`. O modelo poderá sugerir chamadas, mas a execução será encaminhada ao registro de ferramentas e à política da Kellvyn. Não entregar shell genérico, acesso irrestrito a arquivos ou controle destrutivo ao runtime.

## Download e armazenamento

O arquivo não será embutido no APK. O instalador futuro deverá:
1. apresentar licença, autoria, tamanho e espaço necessário;
2. aguardar ação explícita do proprietário;
3. baixar a revisão fixa para diretório privado do app;
4. permitir retomada por HTTP Range;
5. validar tamanho e SHA-256;
6. renomear o arquivo parcial apenas depois da validação;
7. permitir remover o pacote e liberar espaço.

**Estado operacional:** nenhuma cópia de 2,59 GB foi baixada nesta etapa. O registro de procedência e o código de instalação serão preparados antes do uso.
