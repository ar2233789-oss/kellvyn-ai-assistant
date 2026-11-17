# Kellvyn — contrato do agente executável

## Fronteira entre agente e modelo

`KellvynAgent` é o ciclo de controle. Ele não contém pesos nem finge ser um modelo neural. O modelo escolhido futuramente implementará `KellvynInferenceEngine` e retornará uma decisão estruturada:
- resposta final;
- chamada de ferramenta com argumentos.

Isso mantém o orquestrador independente do formato/engine neural e impede que um runtime defina a identidade do projeto.

## Ciclo implementado nesta primeira prova

1. Montar contexto estruturado com instrução do sistema e pedido do usuário.
2. Solicitar uma decisão ao backend neural.
3. Se houver resposta final, concluir.
4. Se houver ferramenta, confirmar que está registrada.
5. Validar parâmetros obrigatórios e rejeitar parâmetros desconhecidos.
6. Aplicar política de autorização baseada no risco.
7. Executar com timeout.
8. Inserir a observação como conteúdo não confiável.
9. Repetir até conclusão ou limite.

## Proteções implementadas no núcleo

- número máximo de etapas e chamadas por tarefa;
- timeout de inferência e ferramentas;
- bloqueio de ferramenta não registrada;
- validação de argumentos;
- prevenção de chamada idêntica repetida;
- autorização padrão que permite somente ferramenta local de leitura;
- resultados de ferramentas explicitamente marcados como não confiáveis;
- exceções de cancelamento propagadas para permitir interromper o agente.

## Não implementado ainda

- backend neural real;
- parser do formato de tool calls do modelo;
- memória persistente;
- aprovação interativa com o proprietário;
- sandbox/terminal;
- ferramentas de escrita/build;
- integração no Poco F6.

A aplicação usa temporariamente `KellvynRuntime` como fronteira que informa de modo honesto que o backend neural não está pronto. Não existe fallback de terceiros nem backend neural conversacional conectado.

## Evolução posterior do agente — skills, navegação, plano e especialistas

A branch de laboratório também implementa:

- **Skills sob demanda:** `KellvynSkillRegistry` escolhe no máximo duas instruções procedurais relevantes, com orçamento de caracteres. Skills não concedem permissões.
- **Navegação Kotlin/Java:** `KellvynProjectCodeIndexer` identifica pacotes, imports, declarações e chamadas em trechos limitados. É lexical, não AST, e os achados precisam ser confirmados no fonte.
- **Plano persistente:** `KellvynModelDecision.Plan` carrega plano estruturado; o agente valida ferramentas, riscos, dependências e critérios e só retorna o plano depois de gravá-lo no checkpoint criptografado.
- **Aprovação e execução:** `approveTaskStep` grava aprovação como novo checkpoint; `executePlannedStep` salva RUNNING antes da ação e só grava SUCCEEDED após verificação independente; `executeReadySteps` percorre etapas prontas até o próximo gate.
- **Especialistas internos:** `KellvynModelDecision.Delegate` cria 14 papéis especializados — pesquisador, explorador de código, revisor, analista de testes, analista de raciocínio, analista de aprendizado, arquiteto de programação, analista de memória, analista de segurança, analista de planejamento, depurador, auditor independente, analista de operações e analista visual — usando a mesma instância de inferência e histórico isolado. Ferramentas são restritas a READ_ONLY; não há recursão nem paralelismo. Os papéis de programação/aprendizado/operações/visual continuam em modo de análise, sem escrita, treino de pesos ou controle de dispositivo.

Arquivos de teste foram escritos para as skills, indexador, plano/executor e delegação. **Não considerar esses testes aprovados sem execução verificável do Gradle/CI.** O runtime de inferência continua não implementado, o chat principal ainda não chama o agente e o teste físico no Poco F6 não ocorreu.


## Atualização — ampliação do catálogo interno (17/11/2026)

O enum `KellvynSpecialistRole` agora declara 14 papéis com instruções próprias. O coordenador continua sendo o próprio `KellvynAgent`: ele decide quando delegar, limita o número de delegações e reconcilia resultados. Não existe um segundo modelo nem múltiplas identidades visíveis.

O teste `KellvynAgentSpecialistEvaluationTest` percorre cada papel e verifica que:
- recebe sua instrução e subtarefa correspondentes;
- não recebe o pedido privado integral do coordenador;
- só recebe ferramentas READ_ONLY mesmo quando o coordenador tenta autorizar uma ferramenta de escrita;
- o retorno volta ao coordenador classificado como dado não confiável.

Esta atualização amplia o contrato de software; não prova ainda competência do modelo, uso real de cada ferramenta ou integração na conversa Android. A execução Gradle/CI da nova matriz é o próximo gate obrigatório.
