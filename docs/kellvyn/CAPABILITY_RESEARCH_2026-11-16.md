# Kellvyn — pesquisa comparativa de capacidades e fundamentos

**Data da pesquisa:** 16/11/2026  
**Objetivo:** transformar capacidades públicas de agentes modernos em requisitos de engenharia para Kellvyn, sem clonar produtos nem ocultar a origem de componentes aproveitados.

## 1. Decisão de engenharia

Kellvyn será construída como um sistema próprio. Isso não significa rejeitar pesos, bibliotecas, runtimes ou componentes de terceiros. Podemos aproveitar recursos legalmente licenciados quando eles aumentarem a capacidade real do projeto.

A regra de autoria é:
- o produto integrado, a identidade, o orquestrador, as políticas, os fluxos, a memória, a arquitetura de ferramentas, as verificações, a interface e as integrações específicas de Kellvyn serão desenvolvidos neste projeto;
- pesos pré-treinados de terceiros e adapters derivados não serão usados como cérebro da Kellvyn; os pesos conversacionais serão inicializados e treinados pelo próprio projeto, conforme a decisão vinculante em NEURAL_MODEL_DECISIONS.md;
- bibliotecas, runtimes e ferramentas auxiliares podem ser avaliados sob licenças compatíveis, sem importar pesos nem substituir o treinamento original;
- bibliotecas e código de terceiros podem ser integrados quando a licença permitir, com avisos, atribuição, registro de alterações e auditoria de dependências;
- não copiar um sistema inteiro e apenas trocar seu nome, marca ou interface;
- não alegar que pesos derivados foram inicializados e treinados integralmente do zero;
- modelos proprietários sem pesos/código publicamente licenciados podem ser estudados pela documentação pública. Acesso por API só poderá ser considerado se o proprietário autorizar custo, rede e dependência externa; não será a base do funcionamento offline.

## 2. Capacidades observáveis nos sistemas de referência

| Referência | Capacidade documentada | O que Kellvyn deve aprender a fazer |
|---|---|---|
| Claude Code | instruções persistentes, regras por caminho, skills procedurais, subagentes isolados, hooks determinísticos e compactação de contexto | separar regras, procedimentos e delegação; não carregar tudo no contexto; automatizar validações sem depender da vontade do modelo |
| ChatGPT / OpenAI Agents | chamadas de função, busca Web, busca de arquivos, execução de código, computer use, geração de imagens, MCP e orquestração de ferramentas | ter ferramentas tipadas, descobertas sob demanda, execução real, resultados observáveis e conectores com permissões |
| Replit Agent | Plan antes de Build, decomposição em tarefas ordenadas, construção, testes visuais como usuário, autocorreção e checkpoints | separar planejamento da execução, testar fluxos reais, fazer rollback e só finalizar com verificação |
| Cursor Agent | coordenador que planeja e delega, busca no código, leitura/escrita de arquivos, Web, terminal, revisão e debugging | mapa de repositório, contexto seletivo, subagentes especializados, diffs, execução de comandos e revisão |
| Android Studio Gemini Agent Mode | tarefas multi-etapas, plano, alterações em vários arquivos, build, correção iterativa, deploy em dispositivo, captura de tela, Logcat e interação ADB | ciclo de engenharia completo: requisito → plano → edição → build → dispositivo → observação → correção |
| OpenHands SDK | sessões, eventos, workspaces, agentes, ferramentas, API e ambientes de execução | manter estado de execução explícito e separar agente, workspace, executor e interface |

### Lições que se tornam requisitos

1. **Planejamento estruturado:** cada tarefa longa terá objetivo, subtarefas, dependências, riscos, critério de aceite e evidência.
2. **Contexto seletivo:** índice do projeto, busca por símbolos, leitura de arquivos relevantes e resumo de sessão; não despejar o repositório inteiro no modelo.
3. **Skills:** procedimentos versionados e invocados por necessidade, como criar APK, diagnosticar build, testar interface ou revisar segurança.
4. **Subagentes internos:** tarefas isoladas como pesquisa, auditoria de licenças, revisão de código, análise de logs e testes. Todos reportam ao mesmo Kellvyn; não são identidades independentes para o usuário.
5. **Hooks e verificações determinísticas:** executar formatador, testes, bloqueio de arquivo protegido e validações após alterações, independentemente do texto que o modelo produza.
6. **Ferramentas reais:** leitura/escrita confinada a workspace, terminal limitado, Git, build, instalação autorizada e inspeção do dispositivo.
7. **Computer/device use:** operar telas por mecanismos Android disponíveis e permissões reais, com observação da tela e capacidade de interrupção.
8. **Compaction e continuidade:** preservar objetivo, restrições, estado, decisões, tentativas e resultados ao reduzir histórico.
9. **Checkpoints e rollback:** nunca depender de uma única alteração irreversível.
10. **Avaliação por tarefa:** medir se o objetivo foi concluído e verificado, não se a resposta parece convincente.

## 3. Fundação neural: decisão em aberto

Nenhum modelo de terceiros foi escolhido, baixado ou integrado como cérebro da Kellvyn. A identidade da inteligência, a arquitetura cognitiva, o agente, a memória, as políticas, a integração com Android e o sistema de validação pertencem ao projeto Kellvyn.

A pesquisa de componentes externos permanece separada e só poderá avançar mediante avaliação técnica e jurídica específica. Não haverá seleção automática de um modelo externo apenas porque possui uma versão móvel ou uma licença permissiva.

Qualquer eventual aproveitamento de pesos ou código deverá:
- estar oficialmente disponível para o uso pretendido;
- ter licença, termos, autores, versão e hashes verificados;
- preservar atribuições e avisos exigidos;
- manter claro o que é componente externo e o que foi desenvolvido no projeto;
- passar por testes de português, raciocínio, código, ferramentas, consumo de memória e estabilidade;
- poder ser substituído sem reescrever a identidade ou a arquitetura cognitiva da Kellvyn.

Não copiar produtos, mecanismos privados, prompts internos ou pesos não autorizados. Não apresentar componente de terceiros como criação integral da Kellvyn.

## 4. Como será feita a escolha do cérebro local

Não há modelo final escolhido nesta pesquisa. O processo de avaliação será:

1. validar licença, model card, versão e hashes;
2. obter o formato oficial indicado para mobile;
3. testar carregamento e estabilidade no Poco F6;
4. medir RAM de pico, armazenamento, tempo inicial, velocidade de geração, temperatura e bateria;
5. medir português brasileiro, instruções longas, raciocínio, código, visão, extração de documentos e chamadas de ferramentas;
6. executar a mesma suíte de tarefas no mesmo runtime e condições sempre que tecnicamente possível;
7. comparar qualidade e latência com/sem quantização e contexto curto/long;
8. registrar erros, limites e regressões;
9. selecionar uma fundação principal; não expor um seletor de provedores ao usuário.

## 5. Arquitetura de Kellvyn proposta

### Camada neural
Um modelo principal de pesos abertos, sujeito à validação. Ele interpreta linguagem, raciocina dentro de seus limites, gera texto e solicita ações estruturadas. Os pesos e sua autoria permanecem claramente identificados.

### Camada de identidade e comportamento Kellvyn
Instruções, regras, contexto, formato de resposta, perfis de tarefa, políticas e comportamento coerente. O usuário conversa com uma única Kellvyn.

### Camada cognitiva própria
Planejador, decomposição de objetivos, estado da tarefa, seleção de skills, memória, busca de contexto, subagentes internos, avaliação de progresso, compactação e recuperação de falhas.

### Camada de ferramentas
Workspace, leitura/escrita protegida, busca de projeto, Git, shell restrito, compiladores, testes, Web opcional, arquivos, documentos, imagem, inspeção da tela e integrações.

### Camada de verificação
Testes, hashes, logs, capturas de tela, diff, build, assinatura, validação do APK e confirmação de resultado. O modelo não pode autodeclarar sucesso sem evidência.

### Camada de evolução
Memória selecionada, feedback autorizado, conjuntos de avaliação, ajuste supervisionado e adapters derivados quando aprovados. Treinamento de pesos não será confundido com memória ou ferramentas.

## 6. Limite de propriedade intelectual

Estudar Claude, ChatGPT, Replit, Cursor ou Gemini não dá acesso ao código interno ou aos pesos proprietários deles. Usaremos os comportamentos documentados e, quando existirem, componentes de código aberto com licença verificável. Não imitaremos identidade visual nem apresentaremos mecanismos proprietários como se tivéssemos acesso às suas implementações internas.

## 7. Fontes oficiais consultadas

- Claude Code — instruções, skills, subagentes e hooks: https://claude.com/resources/articles/steering-claude-code-skills-hooks-rules-subagents-and-more
- OpenAI — ferramentas, function calling, Web, file search, computer use e MCP: https://developers.openai.com/api/docs/guides/tools
- Replit Agent — Plan, Build e construção iterativa: https://docs.replit.com/references/agent/overview
- Replit App Testing: https://docs.replit.com/references/agent/app-testing
- Cursor Agent: https://cursor.com/docs/agent/overview
- Android Studio Agent Mode: https://developer.android.com/studio/gemini/agent-mode
- OpenHands SDK: https://github.com/OpenHands/software-agent-sdk

Este documento registra capacidades observáveis e hipóteses de engenharia. Não afirma acesso aos sistemas internos desses produtos nem que suas capacidades já foram implementadas em Kellvyn.
