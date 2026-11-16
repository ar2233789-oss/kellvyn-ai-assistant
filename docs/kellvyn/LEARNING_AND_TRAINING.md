# Kellvyn — aprendizado, memória e treinamento

## Três coisas diferentes

### 1. Memória operacional da Kellvyn

Informações explicitamente guardadas sobre preferências, projetos e tarefas. Deve ter:
- origem e data;
- consentimento/razão para guardar;
- limite de tamanho e política de retenção;
- consulta, edição e exclusão;
- prevenção de exposição de segredos;
- distinção entre fato confirmado e inferência.

Guardar uma memória não altera os pesos do modelo.

### 2. Adaptação do comportamento

Ajustar o modelo com exemplos selecionados, por exemplo:
- SFT (supervised fine-tuning);
- LoRA/adapters quando o runtime/modelo suportar;
- preferência/feedback estruturado;
- avaliação antes/depois para medir regressões.

É preciso guardar a origem e licença dos dados, filtrar informações privadas, remover segredos e avaliar memorizações indevidas.

### 3. Treinamento de um modelo-base

Pré-treinar um modelo do zero exige grandes conjuntos de dados, processamento acelerado, armazenamento e uma infraestrutura muito além da memória de um celular. O Poco F6 pode hospedar inferência local e testes; não deve ser apresentado como infraestrutura para pré-treinar um LLM de grande porte.

## Referências de treinamento

- nanochat permite estudar o percurso de um LLM pequeno/educacional, desde tokenizer até pré-treinamento, ajuste e inferência: https://github.com/karpathy/nanochat
- OLMo-core permite estudar a infraestrutura de treinamento mais ampla: https://github.com/allenai/OLMo-core

Essas referências não significam que vamos executar o treinamento inteiro no Poco F6. A estratégia realista é:

1. preservar inicialmente o modelo local atual como base neural operacional;
2. construir o agente e as ferramentas próprias da Kellvyn;
3. criar um conjunto próprio de tarefas e avaliações;
4. recolher exemplos/feedback apenas com consentimento e proveniência;
5. experimentar adaptação em ambiente de treinamento apropriado, se houver infraestrutura;
6. portar para o Android somente pesos e formatos compatíveis, depois de testes de memória, latência, temperatura e qualidade.

## Gate para afirmar que houve aprendizado

Só declarar uma melhoria de modelo quando houver:
- dataset e versão identificados;
- licença/consentimento verificados;
- método de treinamento documentado;
- pesos/adapters de saída identificados por hash;
- conjunto de avaliação fixo;
- resultado comparável antes/depois;
- análise de regressões, privacidade e segurança.

Uma alteração de prompt, uma memória salva ou uma ferramenta nova melhora o sistema, mas não é treinamento dos pesos.
