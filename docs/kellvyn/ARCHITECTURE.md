# Kellvyn — arquitetura da inteligência (v0.1)

**Data:** 2026-11-16  
**Branch de laboratório:** `lab/kellvyn-intelligence-core-20261116`  
**Escopo deste documento:** inteligência e agente. Não é uma especificação de redesign do aplicativo.

## Objetivo

Kellvyn é o nome da inteligência. O objetivo é desenvolver uma inteligência original capaz de compreender pedidos, planejar tarefas, consultar contexto, utilizar ferramentas autorizadas, observar resultados, corrigir erros e melhorar através de avaliação e treinamento legítimos.

A arquitetura não consiste em ligar várias IAs como se fossem cérebros independentes. Os projetos externos são referências de engenharia. O aplicativo continua apresentando uma única Kellvyn.

## Separação que não pode ser confundida

1. **Modelo neural Kellvyn:** será criado do zero, com arquitetura e código próprios, tokenizer próprio, pesos inicializados aleatoriamente e treinamento próprio. Nenhum modelo-base, peso, adaptador ou fallback de terceiros será usado.
2. **Agente Kellvyn:** controla o ciclo de tarefa: interpretar o objetivo, planejar, escolher uma ação autorizada pelo proprietário, observar seu resultado e decidir o próximo passo.
3. **Ferramentas:** funções Kotlin delimitadas que consultam ou, futuramente, alteram recursos concretos. O modelo não recebe acesso irrestrito ao Android.
4. **Memória:** dados persistentes e selecionados sobre usuário, tarefas e projetos. Memória não é treinamento dos pesos do modelo.
5. **Aprendizado do modelo:** treinamento ou adaptação efetiva dos pesos a partir de dados e avaliação. Só poderá ser anunciado quando o processo, os dados, os pesos resultantes e os testes estiverem documentados.

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

- Android Studio/Gemini: ciclo objetivo → plano → ferramentas → edição/testes → leitura dos resultados → correção; aprovações de operações sensíveis.
- OpenHands: agente desacoplado da interface, sessões, eventos, workspace e execução em ambiente controlado.
- Aider: mapa de repositório, seleção consciente de contexto, separação entre investigar e editar, diffs e validação.
- SWE-agent: ciclos explícitos de observação/ação em tarefas de engenharia e tratamento de histórico.
- OpenCode: distinção entre planejar e executar, permissões por ferramenta e compactação de contexto.
- nanochat: decomposição do caminho de treinamento neural, do tokenizer até inferência e avaliação.
- OLMo-core: engenharia de treinamento, checkpoints, otimização e avaliação reproduzível.

Nenhum código desses projetos é copiado nesta primeira implementação. Os detalhes de origem e as páginas de referência ficam em [REFERENCE_STUDY.md](REFERENCE_STUDY.md).

## Estado real em 2026-11-16

- A implementação anterior `LocalBrain.kt` baseada em Gemma foi removida da branch de laboratório; ela não será recuperada.
- `MainActivity.java` e `BotRuntimeService.kt` já apontam para a fronteira temporária `KellvynRuntime`; ela ainda não contém inferência neural.
- O Gradle desta branch não declara LiteRT-LM nem outro runtime de modelo externo.
- O workspace privado, o compilador Android restrito, o chat e a interface aprovada existem no código-base original.
- A alteração de `MainActivity.java` diverge do hash protegido em `.github/interface-lock.sha`, embora o objetivo tenha sido trocar apenas a fronteira de runtime e mensagens de estado. O gate de APK deve permanecer bloqueado até resolver essa divergência sem redesenhar a interface.

## Próximas etapas

1. Validar build Android e testes unitários desta branch.
2. Criar o primeiro protótipo neural próprio e testar tokenizer, passagem direta, retropropagação, loss e checkpoint.
3. Adicionar contexto de tarefa e resumo de sessão sem exceder a memória disponível.
4. Projetar memória persistente com consentimento, origem, edição e exclusão.
5. Adicionar ferramentas de escrita somente após um fluxo explícito de aprovação.
6. Criar avaliações repetíveis de qualidade e segurança do agente.
7. Expandir o treinamento integral do zero e medir a qualidade antes de conectar o modelo ao agente.

## Referência técnica primária

A lista de runtimes e referências está em `RUNTIME_COMPARISON.md`. O runtime será selecionado só depois de o formato e a linhagem dos pesos serem definidos.
