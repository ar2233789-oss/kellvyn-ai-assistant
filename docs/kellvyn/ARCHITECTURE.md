# Kellvyn — arquitetura da inteligência (v0.1)

**Data:** 2026-11-16  
**Branch de laboratório:** `lab/kellvyn-intelligence-core-20261116`  
**Escopo deste documento:** inteligência e agente. Não é uma especificação de redesign do aplicativo.

## Objetivo

Kellvyn é o nome da inteligência. O objetivo é desenvolver uma inteligência original capaz de compreender pedidos, planejar tarefas, consultar contexto, utilizar ferramentas autorizadas, observar resultados, corrigir erros e melhorar através de avaliação e treinamento legítimos.

A arquitetura não consiste em ligar várias IAs como se fossem cérebros independentes. Os projetos externos são referências de engenharia. O aplicativo continua apresentando uma única Kellvyn.

## Separação que não pode ser confundida

1. **Modelo neural:** transforma contexto em linguagem e, quando treinado para isso, em chamadas estruturadas de ferramenta. No APK atual, o substrato neural é Gemma 4 E2B quantizado em LiteRT-LM.
2. **Agente Kellvyn:** controla o ciclo de tarefa: interpretar o objetivo, escolher uma ação autorizada, observar seu resultado e decidir o próximo passo.
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
Modelo neural local (LiteRT-LM)
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

O LiteRT-LM 0.18.0 oferece chamada automática ou manual de ferramentas. Nesta primeira implementação, as ferramentas registradas são somente de leitura; o runtime controla a repetição de tool calls e o código Kellvyn impõe um limite adicional por mensagem. A documentação oficial descreve o registro de `ToolSet`, `@Tool`, `@ToolParam` e a execução automática ou manual [1].

## Primeira fronteira funcional

Ferramentas liberadas nesta versão de laboratório:

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

- Já existe um modelo local Gemma 4 E2B no motor Android `LocalBrain.kt`.
- A configuração existente limita o contexto a 4.096 tokens e a resposta normal a 768 tokens.
- Já existe leitura de anexos, contexto Web opcional e pipeline específico para projetos Android/compilação.
- Antes desta alteração, o chat chamava o modelo e apresentava a resposta; não existia um conjunto geral de ferramentas de agente no `ConversationConfig`.
- Esta branch registra ferramentas locais de leitura no LiteRT-LM. A aprovação de compilação e o teste de tool call no Poco F6 ainda precisam ser verificados; a presença do código não prova funcionamento em dispositivo.

## Próximas etapas

1. Validar build Android e testes unitários desta branch.
2. Testar uma chamada de ferramenta do modelo Gemma 4 E2B no Poco F6 (GPU e fallback CPU).
3. Adicionar contexto de tarefa e resumo de sessão sem exceder a memória disponível.
4. Projetar memória persistente com consentimento, origem, edição e exclusão.
5. Adicionar ferramentas de escrita somente após um fluxo explícito de aprovação.
6. Criar avaliações repetíveis de qualidade e segurança do agente.
7. Planejar SFT/LoRA e, separadamente, qualquer treinamento de modelo-base.

## Referência técnica primária

[1] LiteRT-LM 0.18.0, código e documentação Kotlin na tag de lançamento: https://github.com/google-ai-edge/LiteRT-LM/tree/b2f686e2ed4718fb84ec398a61dd59ca0f0aff27
