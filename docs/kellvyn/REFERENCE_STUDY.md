# Kellvyn — estudo das referências

**Princípio:** estudar publicações e projetos para compreender problemas e resultados, mas escrever a implementação Kellvyn do zero. Nenhum código, peso, tokenizer, dataset, adaptador ou arquitetura de modelo de terceiros será copiado ou incorporado ao cérebro.

## 1. Android Studio + Gemini

**Papel:** referência principal de engenharia de agente para desenvolvimento Android.

O que estudar:
- transformar uma meta grande em etapas;
- construir um plano antes de editar;
- navegar por vários arquivos e manter coerência entre eles;
- escolher ferramentas adequadas ao passo atual;
- compilar, ler erros, fazer correções e validar novamente;
- instalar/testar em dispositivo, observar captura de tela e logs;
- pedir aprovação antes de ações sensíveis.

A documentação pública do Agent Mode descreve tarefas complexas em várias etapas, planejamento, uso de ferramentas, alterações em vários arquivos e ciclos iterativos de reparo [1]. É uma referência comportamental/documental; a implementação interna e os pesos do Gemini não são código aberto e não serão copiados.

## 2. OpenHands

**Papel:** arquitetura de agente de engenharia e ambiente de execução.

O que estudar:
- separação entre SDK/agente e interface;
- sessão, eventos e estado de execução;
- ferramentas explícitas e workspaces;
- execução em ambiente controlado;
- avaliação de risco e contenção de ações;
- condensação de contexto.

Fontes:
- Repositório: https://github.com/All-Hands-AI/OpenHands
- SDK: https://github.com/All-Hands-AI/agent-sdk

## 3. Aider

**Papel:** compreensão de repositórios e disciplina de edição.

O que estudar:
- mapa estrutural de repositório;
- seleção de arquivos relevantes para o contexto;
- modo de investigação sem edição versus modo de alteração;
- separação arquiteto/editor;
- diffs, lint e integração com Git.

Fontes:
- Repositório: https://github.com/Aider-AI/aider
- Comandos: https://aider.chat/docs/usage/commands.html
- Modos: https://aider.chat/docs/usage/modes.html

## 4. SWE-agent

**Papel:** resolver tarefas de engenharia através de ciclos observáveis.

O que estudar:
- interpretar issue/requisito;
- usar ambiente e ferramentas específicas;
- manter registro de ações e observações;
- processar/compactar histórico;
- reproduzir, corrigir e validar falhas;
- medir sucesso em benchmarks.

Fontes:
- Repositório: https://github.com/SWE-agent/SWE-agent
- Documentação: https://swe-agent.com/

## 5. OpenCode

**Papel:** sessões, permissões e organização operacional.

O que estudar:
- modo de planejamento versus modo de execução;
- agentes auxiliares especializados;
- permissões individuais por ferramenta;
- política de aprovação/bloqueio;
- compactação de contexto e resumos de sessão;
- ferramentas tipadas para arquivos e terminal.

Fontes:
- Repositório: https://github.com/anomalyco/opencode
- Documentação: https://opencode.ai/docs/

## 6. nanochat

**Papel:** estudar o ciclo didático completo de criação e treinamento de um modelo de linguagem.

O que estudar:
- tokenizer e representação dos dados;
- pré-treinamento;
- ajuste supervisionado;
- avaliação;
- aprendizado por preferência/reforço;
- inferência e medição de desempenho.

Fontes:
- Repositório: https://github.com/karpathy/nanochat
- Não tratar o treinamento mínimo como prova de que um modelo pequeno alcança capacidades de fronteira.

## 7. OLMo-core

**Papel:** engenharia de infraestrutura de treinamento.

O que estudar:
- pipelines de dados e batching;
- arquitetura/modelo e otimização;
- distribuição e paralelismo;
- checkpoints e recuperação;
- métricas, avaliação e reprodutibilidade.

Fontes:
- Repositório: https://github.com/allenai/OLMo-core
- Documentação: https://olmo-core.readthedocs.io/

## 8. Como aproveitar sem plagiar

- Implementar em Kotlin os componentes de rede neural, tokenização, treinamento, checkpoint e inferência, com autoria e testes próprios.
- Usar os repositórios apenas como material de estudo conceitual; não copiar arquivos, blocos de código, pesos, tokenizer ou pipeline de treino.
- Não utilizar qualquer modelo-base, pesos pré-treinados, fine-tuning, LoRA ou fallback de outra IA.
- Não alegar que o código interno do Gemini ou de outros sistemas proprietários foi estudado.
- Registrar as fontes consultadas e explicar o que foi aprendido, separando a ideia pública da implementação escrita para Kellvyn.
- Dependências genéricas do aplicativo não podem introduzir um modelo ou pesos externos.

## Fontes adicionais

[1] Android Studio Gemini Agent Mode: https://developer.android.com/studio/gemini/agent-mode

As fontes listadas são referências de estudo. Nenhuma delas fornece código, pesos ou componentes neurais incorporados ao cérebro da Kellvyn.
