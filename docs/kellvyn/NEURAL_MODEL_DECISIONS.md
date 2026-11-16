# Kellvyn — estratégia do modelo neural

**Estado:** decisão em aberto; nenhum modelo de terceiros aprovado como cérebro.

## 1. Uma única inteligência, duas linhagens possíveis

A identidade do produto é Kellvyn. A linhagem dos pesos precisa ser descrita de forma verdadeira.

### Caminho A — pesos originais da Kellvyn

- Implementar a arquitetura neural e o pipeline de treinamento.
- Inicializar os pesos aleatoriamente.
- Treinar com dados de proveniência e licença verificadas.
- Executar pré-treino, ajuste supervisionado e avaliação.
- Produzir checkpoints e pesos com hash e histórico reproduzível.
- Converter/quantizar para execução Android.

Vantagem: permite afirmar que os pesos foram treinados pela equipe da Kellvyn, quando isso for comprovado.

Limite: um modelo neural útil de conversação exige escala de dados, treino e avaliação. O Poco F6 não pode ser tratado como substituto de uma infraestrutura moderna de treinamento acelerado. Um experimento pequeno no aparelho não produzirá por mágica uma IA geral de alto nível.

### Caminho B — uma única base de pesos de terceiros, licenciada e adaptada

- Selecionar exatamente um modelo com licença/termos que permitam o uso pretendido.
- Adaptá-lo ao comportamento, idioma e tarefas da Kellvyn por SFT/LoRA ou método compatível.
- Identificar publicamente o modelo-base, seus autores e licença.
- Documentar o que foi alterado e o que continua pertencendo à base original.
- Não afirmar que os pesos-base foram criados do zero pela Kellvyn.

Vantagem: pode entregar uma qualidade de linguagem e raciocínio muito superior com menos treinamento próprio.

Limite: é um modelo derivado. É legítimo quando licença e obrigações são cumpridas, mas não é honesto chamá-lo de pesos inteiramente originais.

A possibilidade de aproveitar uma base aberta não autoriza ocultar sua origem. O projeto não usará Gemma.

## 2. Candidatos apenas para benchmark

Estes modelos foram localizados com licenças declaradas, mas não foram selecionados nem baixados. Eles servem para comparar capacidade, compatibilidade e direitos, não para substituir automaticamente a identidade da Kellvyn.

| Candidato | Licença indicada na página oficial consultada | Observação |
|---|---|---|
| Qwen/Qwen3-0.6B | Apache 2.0 | 0,6B; repositório de pesos safetensors listado em aproximadamente 1,52 GB não quantizados |
| Qwen/Qwen3-1.7B | Apache 2.0 | 1,7B; repositório listado em aproximadamente 4,08 GB não quantizados; exigiria avaliação cuidadosa de espaço e RAM |
| microsoft/Phi-4-mini-instruct | MIT | candidato de avaliação; conferir o tamanho/quantização exata e termos associados ao artefato que seria distribuído |

A presença de MIT/Apache não prova, sozinha, que o modelo é o melhor para o Poco F6 ou que todos os arquivos associados têm termos compatíveis. Conferir o LICENSE, model card, hashes, tokenizer, quantização e arquivos de cada revisão.

## 3. Por que não escolher um modelo agora

Escolher por nome, número de parâmetros ou popularidade repete o erro que queremos evitar. O benchmark precisa medir no Poco F6:
- instalação e espaço real de armazenamento;
- RAM de pico incluindo KV cache, tokenizer e app;
- tempo de carregamento;
- tokens por segundo;
- temperatura e throttling;
- consumo de bateria;
- português brasileiro;
- raciocínio e seguimento de instrução;
- programação;
- estruturação de chamadas de ferramentas;
- estabilidade em contexto longo;
- qualidade depois da quantização.

O modelo precisa funcionar sem rede após preparado, conforme o requisito local.

## 4. Arquitetura neural experimental

A primeira família a estudar é um Transformer decoder-only autoregressivo. Componentes em avaliação:
- tokenizer BPE ou SentencePiece;
- token embeddings;
- positional encoding/RoPE;
- atenção causal multi-head ou grouped-query;
- normalização RMSNorm;
- MLP/SwiGLU;
- residual connections;
- logits e sampling;
- KV cache;
- loss de next-token prediction.

A arquitetura é uma combinação de técnicas publicadas, não uma invenção isolada. A originalidade estará na implementação, seleção, configuração, treinamento, dados e avaliação, sem alegar autoria de conceitos científicos existentes.

## 5. Pipeline de treinamento

### Etapa 1 — dados
- corpus em português brasileiro;
- texto de domínio público ou licenças expressamente compatíveis;
- código com licença e proveniência compatíveis;
- dados de instruções elaborados pela equipe;
- exemplos sintéticos revisados;
- filtros de qualidade, duplicação, dados pessoais e segredos.

### Etapa 2 — tokenizer
Treinar ou selecionar tokenizer licenciado; medir taxa de compressão e cobertura do português, nomes de bibliotecas, símbolos e código.

### Etapa 3 — pré-treino
Treinar previsão do próximo token. Guardar configurações, seed, quantidade de tokens, loss, checkpoints e métricas.

### Etapa 4 — ajuste supervisionado
Exemplos de conversa, explicações, português, código, planejamento e chamadas de ferramenta.

### Etapa 5 — preferência e alinhamento
Somente depois de avaliação reproduzível. Preferências explícitas e dados revisados; nunca treinar indiscriminadamente em todo o histórico privado.

### Etapa 6 — quantização e exportação
Comparar precisão, qualidade, RAM e latência. Guardar versão não quantizada e derivada quantizada com seus hashes.

## 6. O que significa “aprender”

- Uma memória salva altera o contexto, não os pesos.
- Um prompt novo altera instruções, não os pesos.
- Uma ferramenta nova aumenta a capacidade do agente, não treina o modelo.
- SFT/LoRA altera os parâmetros de um modelo e precisa ser registrado como treino/adaptação.
- Pré-treino desde pesos aleatórios produz um modelo novo, mas a qualidade deve ser medida.

Nenhuma dessas etapas será anunciada como “aprendizado” sem especificar qual tipo ocorreu.

## 7. Decisão necessária antes de baixar ou treinar pesos

A decisão deverá comparar:
1. peso original treinado pela Kellvyn, com limite realista de qualidade;
2. uma base única licenciada, adaptada e reconhecida como derivada.

A decisão incluirá benchmark, orçamento de armazenamento/RAM, termos dos pesos e plano de execução privada. Até esse gate, nenhum modelo pré-treinado está aprovado.
