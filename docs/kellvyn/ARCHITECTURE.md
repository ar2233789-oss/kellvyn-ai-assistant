# Kellvyn — arquitetura da inteligência (v0.1)

**Data:** 2026-11-16  
**Branch de laboratório:** `lab/kellvyn-intelligence-core-20261116`  
**Escopo deste documento:** inteligência e agente. Não é uma especificação de redesign do aplicativo.

## Objetivo

Kellvyn é o nome da inteligência. O objetivo é desenvolver uma inteligência original capaz de compreender pedidos, planejar tarefas, consultar contexto, utilizar ferramentas autorizadas, observar resultados, corrigir erros e melhorar através de avaliação e treinamento legítimos.

A arquitetura não consiste em ligar várias IAs como se fossem cérebros independentes. Os projetos externos são referências de engenharia. O aplicativo continua apresentando uma única Kellvyn.

## Separação que não pode ser confundida

1. **Fundação neural:** um modelo aberto licenciado pode fornecer a capacidade linguística; a versão e a origem dos pesos serão declaradas. A arquitetura cognitiva e a integração em torno dele serão próprias da Kellvyn. KBN-001 permanece como pesquisa neural autoral, não como cérebro final.
2. **Agente Kellvyn:** controla o ciclo de tarefa: interpretar o objetivo, planejar, escolher uma ação autorizada pelo proprietário, observar seu resultado e decidir o próximo passo.
3. **Ferramentas:** funções Kotlin delimitadas que consultam ou, futuramente, alteram recursos concretos. O modelo não recebe acesso irrestrito ao Android.
4. **Memória:** dados persistentes e selecionados sobre usuário, tarefas e projetos. Memória não é treinamento dos pesos do modelo.
5. **Aprendizado do modelo:** treinamento ou adaptação efetiva dos pesos a partir de dados e avaliação. Só poderá ser anunciado quando o processo, os dados, os pesos resultantes e os testes estiverem documentados.

## Linhagem e autoria

- Pesos-base abertos podem ser aproveitados sob licença compatível.
- Fine-tuning/adapters podem gerar uma versão derivada com proveniência preservada.
- Código aberto de runtimes e ferramentas pode ser integrado seletivamente, com avisos e auditoria.
- Não atribuir à Kellvyn pesos ou código alheios; não copiar sistemas proprietários.
- API remota proprietária não será requisito para a execução offline.

O agente, o planejador, a memória, as skills, o registro de ferramentas, as políticas, o workspace, o verificador e a integração Android são componentes do sistema Kellvyn e serão desenvolvidos e mantidos neste projeto.

## Fluxo planejado

```text
Pedido do usuário
      |
      v
Kellvyn / interpretação do objetivo
      |
      v
Plano de tarefa e limites
      |
      v
Modelo neural local Kellvyn (inferência própria)
      |
      v
Seleção de ferramenta registrada
      |
      v
Política de permissão e orçamento
      |
      v
Execução isolada da ferramenta
      |
      v
Resultado observado e validado
      |
      +----> próxima ação, dentro dos limites
      |
      v
Resposta final + evidências do que ocorreu
```

Os runtimes serão comparados separadamente do modelo neural. A seleção de um runtime não define o cérebro nem escolhe pesos. Os contratos do agente Kellvyn serão próprios e independentes do executor selecionado.

## Fronteira inicial planejada

Ferramentas iniciais propostas para o primeiro ciclo de execução:

- `listLocalProjects`: listar projetos guardados no workspace privado;
- `listProjectFiles`: listar caminhos relativos de um projeto selecionado;
- `readProjectFile`: ler conteúdo de um arquivo textual suportado;
- `searchProjectFiles`: procurar texto em arquivos textuais de um projeto.

Restrições ativas:

- Sem terminal ou shell genérico;
- sem acesso arbitrário ao sistema de arquivos;
- sem escrita, exclusão ou renomeação;
- sem execução de código gerado;
- sem Git push, publicação, instalação ou operação de bot;
- sem acesso à rede por essas ferramentas;
- quantidade de chamadas e tamanho de saídas limitados;
- caminho relativo validado pelo workspace privado já existente.

As ações com efeitos colaterais só entram numa etapa futura, com confirmação real do usuário e política executada no código, não apenas instruções no prompt.

## Como as referências influenciam a arquitetura

- Claude Code: instruções em camadas, skills, hooks, subagentes isolados e compactação.
- ChatGPT/OpenAI Agents: function calling, ferramentas de Web/arquivos/código/computer use e MCP.
- Replit: Plan/Build, tarefas ordenadas, testes visuais, autocorreção e checkpoints.
- Cursor: coordenador, delegação, mapa de repositório, terminal, diff e revisão.
- Android Studio/Gemini: ciclo objetivo → plano → ferramentas → edição/build → dispositivo → screenshots/Logcat → correção.
- OpenHands/Aider/SWE-agent/OpenCode: sessões, workspace, busca de código, diffs, loops de ação, permissões e avaliação.

Esses conceitos orientam o projeto; código aberto pode ser integrado seletivamente quando a licença permitir. A origem e as alterações devem ficar registradas. As referências estão em [REFERENCE_STUDY.md](REFERENCE_STUDY.md) e [CAPABILITY_RESEARCH_2026-11-16.md](CAPABILITY_RESEARCH_2026-11-16.md).

## Estado real em 2026-11-16

- A implementação anterior `LocalBrain.kt` baseada em Gemma foi removida da branch de laboratório; ela não será recuperada.
- `MainActivity.java` e `BotRuntimeService.kt` já apontam para a fronteira temporária `KellvynRuntime`; ela ainda não contém inferência neural.
- O Gradle desta branch não declara LiteRT-LM nem outro runtime de modelo. A integração de um runtime licenciado será avaliada junto com a fundação neural e o benchmark do Poco F6.
- O workspace privado, o compilador Android restrito, o chat e a interface aprovada existem no código-base original.
- A alteração de `MainActivity.java` diverge do hash protegido em `.github/interface-lock.sha`, embora o objetivo tenha sido trocar apenas a fronteira de runtime e mensagens de estado. O gate de APK deve permanecer bloqueado até resolver essa divergência sem redesenhar a interface.

## Próximas etapas

1. Validar build Android e testes unitários desta branch.
2. Criar o primeiro protótipo neural próprio e testar tokenizer, passagem direta, retropropagação, loss e checkpoint.
3. Adicionar contexto de tarefa e resumo de sessão sem exceder a memória disponível.
4. Projetar memória persistente com consentimento, origem, edição e exclusão.
5. Adicionar ferramentas de escrita somente após um fluxo explícito de aprovação.
6. Criar avaliações repetíveis de qualidade e segurança do agente.
7. Benchmark de fundações abertas e integração do modelo aprovado com o agente próprio; manter KBN-001 como rota de treinamento experimental independente.

## Referência técnica primária

A lista de runtimes e referências está em `RUNTIME_COMPARISON.md`. O runtime será selecionado só depois de o formato e a linhagem dos pesos serem definidos.
