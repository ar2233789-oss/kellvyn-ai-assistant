# Pesquisa de capacidades para os agentes e Studios Kellvyn

**Data:** 18/11/2026  
**Branch:** lab/kellvyn-intelligence-core-20261116  
**Objetivo:** converter capacidades públicas observáveis em requisitos próprios, sem copiar código proprietário, prompts privados, pesos, marca ou interface.

## 1. Lovable — planejamento, execução longa, conhecimento e teste

Fontes oficiais:
- https://docs.lovable.dev/features/agent-mode
- https://docs.lovable.dev/features/plan-mode
- https://docs.lovable.dev/features/browser-testing
- https://docs.lovable.dev/features/knowledge
- https://lovable.dev/en/a-smarter-lovable
- https://academy.lovable.app/academy/video-catalog

Capacidades observadas:
- produzir um plano detalhado e revisável antes de alterar código;
- decompor trabalhos grandes em tarefas e executar sequências longas;
- manter fila de pedidos sem perder o objetivo principal;
- explorar o codebase, editar arquivos e executar comandos;
- testar aplicações em sessão real de navegador, com screenshots, cliques e verificação de fluxos;
- manter conhecimento de projeto e skills reutilizáveis;
- executar testes e iterar sobre falhas observadas.

Adaptação própria:
1. O planejador existente deve exigir critérios de aceite mensuráveis e estimativa de recursos antes de executar.
2. A fila persistente deve preservar prioridade, dependências, checkpoint, cancelamento e retomada.
3. Cada Studio publica contratos tipados; skills ensinam procedimentos, mas nunca concedem permissões.
4. O laboratório Android deve produzir evidência de interação nativa por UIAutomator/Espresso quando disponível.
5. Conhecimento por projeto deve ter proveniência, versão e carregamento seletivo.

Limite: browser testing do Lovable não prova qualidade de aplicativo Android nativo. Não importar hospedagem ou serviços do Lovable como dependência da Kellvyn local.

## 2. Lovable Academy — cursos e trilhas

Catálogo oficial: https://academy.lovable.app/academy/video-catalog

Conteúdos relevantes no catálogo consultado:
- visão geral e workflow do Lovable;
- Plan Mode;
- reverts e recuperação;
- Custom Knowledge;
- segurança e infraestrutura;
- Skills;
- Exec Tools;
- design e debugging;
- atualização do agente com testes automáticos e conectores.

Uso na Kellvyn: transformar cada tópico em skill autoral, caso prático e checklist verificável. Não copiar texto de curso nem tratar assistir a aula como competência demonstrada. A competência exige tarefa executada e avaliada.

## 3. Replit Agent — Plan, Skills, checkpoints e App Testing

Fontes oficiais:
- https://docs.replit.com/pt/references/agent/overview
- https://docs.replit.com/pt/references/agent/plan-mode
- https://docs.replit.com/pt/references/agent/skills
- https://docs.replit.com/pt/references/agent/app-testing
- https://docs.replit.com/pt/references/version-control/checkpoints-and-rollbacks

Capacidades observadas:
- planejar tarefas complexas antes de construir;
- reutilizar skills, inspecionando o conteúdo e carregando instruções completas apenas quando relevantes;
- criar checkpoints e recuperar o estado do projeto;
- testar em navegador real, registrar replay e corrigir problemas;
- preservar contexto de desenvolvimento como parte da recuperação.

Limite essencial: a documentação consultada do App Testing o restringe a aplicações web JavaScript full-stack e Python Streamlit. Não é um testador de APK nativo.

Adaptação própria:
- ampliar as skills seletivas já existentes com domínio, versão e critérios de ativação;
- anexar evidências e hashes dos artefatos a cada checkpoint;
- guardar contexto mínimo e eventos de execução, sem persistir segredos;
- criar replay de ações Android via registros, screenshots, árvore de acessibilidade e Logcat;
- fazer rollback do workspace sem apagar silenciosamente a trilha de auditoria.

## 4. Android Studio com Gemini Agent Mode

Fontes oficiais:
- https://developer.android.com/studio/gemini/agent-mode
- https://developer.android.com/studio/gemini/features
- https://developer.android.com/studio/run
- https://developer.android.com/studio/run/device.html

Capacidades observadas:
- receber meta ampla, criar plano multi-etapas e operar sobre vários arquivos;
- revisar alterações e executar build;
- implantar em dispositivo, inspecionar tela, capturar screenshot, consultar Logcat e interagir com o app;
- criar testes, investigar erros e iterar.

Adaptação própria:
- distinguir diagnóstico do SDK de compilação real;
- separar contratos de build, assinatura, instalação, inicialização, crash/logcat, screenshot, acessibilidade e interação;
- anexar cada evidência ao caso de teste e ao hash do APK;
- testar em AVD antes de qualificação física no Poco F6.

Limite: a documentação descreve recursos do Android Studio desktop. Não comprova que ferramentas host do SDK possam rodar dentro de Android arm64; isso exige teste independente.

## 5. Cursor Agent — exploração e coordenação

Fontes oficiais:
- https://cursor.com/docs/agent/overview
- https://cursor.com/help/ai-features/agent

Capacidades observadas:
- busca de arquivos/símbolos, edição multi-arquivo, terminal e testes;
- ciclos de diagnóstico e correção;
- coordenação de agentes com contexto isolado;
- diff revisável, checkpoints e recuperação.

Adaptação própria:
- evoluir o indexador lexical para símbolos/imports/referências incrementais;
- usar perfis especialistas sob a mesma inferência, com escopo isolado e privilégio mínimo;
- separar workspaces/branches para mudanças concorrentes;
- exigir revisão de diff antes da integração.

## 6. OpenAI Agents SDK e LangGraph — orquestração e persistência

Fontes oficiais:
- https://developers.openai.com/tracks/building-agents
- https://developers.openai.com/api/docs/guides/agents/integrations-observability
- https://docs.langchain.com/oss/python/langgraph/persistence

Padrões observados:
- loop explícito de modelo, ferramenta, observação e próxima decisão;
- handoffs, sessões, guardrails e tracing;
- checkpointers para restaurar estado de execução e stores para dados duráveis.

Adaptação própria:
- trace local com taskId, stepId, toolCallId e evidenceId;
- redaction antes de persistir eventos;
- separar estado transitório da tarefa da memória durável;
- combinar checkpoints com hash e Git.

Não adicionar SDKs de servidor como dependência obrigatória do Android; implementar contratos Kotlin enxutos.

## 7. Nano Banana / Gemini Image — referência para o Image Studio

Fontes oficiais:
- https://deepmind.google/models/gemini-image/
- https://ai.google.dev/gemini-api/docs/image-generation?hl=pt-BR
- https://ai.google.dev/gemini-api/docs/pricing

A documentação consultada descreve Nano Banana como família de modelos de geração e edição de imagens, com interação conversacional e imagens de referência. A tabela oficial de preços consultada distingue modelos e variantes e não indica um nível gratuito para a geração de imagens nos modelos atuais consultados.

Decisão:
- Nano Banana é referência de benchmark visual, não cérebro de agente.
- Não integrar API paga, chave obrigatória ou dependência remota ao runtime privado.
- O Image Studio autoral deve medir consistência entre referências, edição localizada, preservação de regiões protegidas, legibilidade de texto, dimensões, transparência, variações e comparação visual.
- Adaptadores futuros devem ser opcionais e substituíveis; o fluxo local não pode depender deles.
- Não atribuir ao motor local uma imagem criada por serviço externo.

## 8. Cursos técnicos Android

Curso oficial: https://developer.android.com/courses/android-basics-compose/course

Tópicos úteis: Kotlin, estrutura Android, layouts adaptáveis, arquitetura, ViewModel/StateFlow, navegação, testes unitários e execução em dispositivo/emulador. O curso pressupõe computador com Android Studio para as atividades práticas; não significa que a IDE desktop possa ser instalada ou executada integralmente no Poco F6.

Uso na Kellvyn: organizar uma trilha de competência para Arquiteto, Analista de Testes, Depurador e Analista Visual; cada módulo de estudo deve terminar em um exercício que possa ser compilado/testado no ambiente compatível.

## 9. Matriz de impacto

| Área | Aprendizado externo | Trabalho próprio exigido |
|---|---|---|
| Planejador | Plano revisável e execução faseada | Critérios mensuráveis, dependências e custo de recursos |
| Agentes | Skills seletivas e papéis isolados | Perfis versionados, catálogo, validação e benchmark |
| Memória | Checkpoints, sessões e stores | Eventos redigidos, recuperação e proveniência |
| Software Studio | Busca estrutural, diff e execução | AST/símbolos, workspace isolado e testes repetíveis |
| Android Studio interno | Deploy, screenshots, Logcat e interação | Toolchain real, ADB, UIAutomator e evidência por APK |
| UI/UX Studio | Teste visual e replay | Hierarquia de acessibilidade e regressão visual |
| Image Studio | Referências múltiplas e edição iterativa | Pipeline de assets e benchmark de preservação |
| 3D/CAD Studio | Inspeção/edição geométrica | Parser/renderer enxuto, export e validação de malha |
| Neural Studio | Tracing, evals e recuperação | Corpus, métricas de qualidade e inferência própria |
| Segurança | Guardrails e rastreabilidade | Menor privilégio, redaction e verificador independente |

## 10. Regras de interpretação

1. Capacidade anunciada por produto não prova que ela exista no repositório.
2. Documentação de produto web não prova compatibilidade Android nativa.
3. Skill é conhecimento procedural, não permissão.
4. Checkpoint precisa de hash, escopo e recuperação testada.
5. Screenshot/replay prova somente o fluxo específico observado.
6. Benchmark exige tarefas fixas, repetíveis e avaliação independente.
7. Nenhum serviço pago, chave, hospedagem ou modelo externo será dependência do cérebro Kellvyn.
8. Nenhuma interface visual aprovada será alterada para implementar essas capacidades.

## 11. Próxima sequência

1. Recuperar a delegação e a matriz central existentes sem duplicá-las.
2. Implementar catálogo versionado e validação estrutural no Agent Creation Studio invisível.
3. Conectar os perfis ao ponto único de criação dos especialistas dentro de KellvynAgent.
4. Ampliar testes de contrato e benchmark de competência quando a inferência conversacional estiver ligada.
5. Completar Android Build & Test antes de abrir os motores visuais e 3D.
