# Kellvyn — estudo das referências

**Princípio:** estudar capacidades e engenharia de sistemas existentes, aproveitando código aberto, bibliotecas, runtimes e pesos quando a licença permitir. Kellvyn não será uma cópia: a identidade, o sistema cognitivo, a integração, as políticas, os fluxos e a verificação serão próprios; toda contribuição externa terá origem e autoria declaradas.

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



## 8. Claude Code

**Papel:** referência para manter contexto e procedimentos em tarefas longas.

O que estudar e transformar em implementação Kellvyn:
- regras persistentes e regras específicas por diretório;
- skills sob demanda para tarefas repetíveis;
- subagentes em contextos isolados para pesquisa, revisão, logs e auditoria;
- hooks determinísticos para bloquear comandos, rodar testes e validar alterações;
- política de compactação que preserve objetivo e restrições.

Fontes:
- Artigo oficial sobre CLAUDE.md, rules, skills, subagents e hooks: https://claude.com/resources/articles/steering-claude-code-skills-hooks-rules-subagents-and-more
- Documentação: https://code.claude.com/docs

A implementação interna e os pesos do Claude são proprietários. A referência é o comportamento documentado, não código privado.

## 9. ChatGPT / OpenAI Agents

**Papel:** referência para capacidades gerais de agentes e ferramentas.

O que estudar:
- function calling para executar funções próprias;
- busca Web e busca de arquivos;
- execução de código em ambiente restrito;
- computer use com ciclo ação → observação → próxima ação;
- MCP para conectores;
- seleção dinâmica de ferramentas e compactação de contexto.

Fontes:
- Ferramentas oficiais: https://developers.openai.com/api/docs/guides/tools
- Construção de agentes: https://developers.openai.com/tracks/building-agents

As ferramentas hospedadas pela OpenAI/API não serão dependência obrigatória de Kellvyn offline. As capacidades equivalentes serão implementadas localmente quando possível e com conectores opcionais quando autorizado.

## 10. Replit Agent

**Papel:** referência para transformar um pedido em aplicativo funcional, e não apenas gerar código.

O que estudar:
- Plan antes de Build;
- decomposição em tarefas ordenadas;
- iteração do código com testes;
- simulação visual de uso real;
- autocorreção de falhas;
- checkpoints e retorno a um estado anterior.

Fontes:
- Replit Agent: https://docs.replit.com/references/agent/overview
- Plan mode: https://docs.replit.com/references/agent/plan-mode
- App testing: https://docs.replit.com/references/agent/app-testing

Replit é uma referência de fluxo. O sistema nativo final continua sendo Android local e APK, sem depender da hospedagem do Replit.

## 11. Cursor Agent

**Papel:** referência para engenharia de software em repositórios grandes.

O que estudar:
- agente coordenador que planeja e delega;
- regras e skills por projeto;
- indexação e busca de código;
- leitura/escrita em vários arquivos;
- terminal, diffs, revisão, debugging;
- agentes especializados e isolamento de contexto.

Fontes:
- Visão geral oficial: https://cursor.com/docs/agent/overview
- Agent mode: https://cursor.com/help/ai-features/agent

Os mecanismos públicos inspiram requisitos; não copiar marca, interface ou código proprietário.

## 12. Como aproveitar sem plagiar

- Quando o componente for uma ideia ou padrão público, estudar, documentar a fonte e implementar o comportamento de forma compatível com a arquitetura Kellvyn.
- Quando uma biblioteca ou trecho de código aberto for realmente vantajoso, verificar licença e avisos no commit exato, incluir atribuição e documentar mudanças.
- Pesos abertos podem ser utilizados como fundação neural; registrar modelo-base, versão, autores, licença, hashes, tokenizer, quantização e restrições.
- Pesos pré-treinados de terceiros e adapters derivados não serão usados como cérebro da Kellvyn. A fundação neural deve ser inicializada e treinada pelo próprio projeto; bibliotecas auxiliares só entram após auditoria de licença.
- Não copiar código interno ou pesos proprietários de Claude, ChatGPT, Gemini ou Replit.
- Não atribuir à Kellvyn a autoria original de uma biblioteca, modelo, peso, dataset ou implementação externa.
- Não usar conversas privadas do proprietário como treino sem consentimento específico.
- Manter inventário de fontes, dependências e modelos dentro do projeto.

## Fontes principais já consultadas

[1] Android Studio Gemini Agent Mode: https://developer.android.com/studio/gemini/agent-mode
[2] OpenHands SDK: https://github.com/OpenHands/software-agent-sdk
[3] Aider: https://github.com/Aider-AI/aider
[4] SWE-agent: https://github.com/SWE-agent/SWE-agent
[5] OpenCode: https://github.com/anomalyco/opencode
[6] nanochat: https://github.com/karpathy/nanochat
[7] OLMo-core: https://github.com/allenai/OLMo-core
