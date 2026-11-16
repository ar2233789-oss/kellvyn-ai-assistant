# Kellvyn — comparação de agentes e melhorias aplicadas

**Data da revisão:** 16/11/2026  
**Repositório:** `ar2233789-oss/kellvyn-ai-assistant`  
**Branch:** `lab/kellvyn-intelligence-core-20261116`

## Objetivo

Transformar recursos publicamente documentados de agentes de programação em requisitos concretos para o agente próprio Kellvyn. Isto não significa copiar produtos, interfaces, prompts privados ou código proprietário. A interface aprovada da Kellvyn não faz parte desta revisão.

## Pesquisa em documentação oficial

### Replit Agent

Fontes oficiais:
- Visão geral e fluxo Plan/Build: https://docs.replit.com/pt/references/agent/overview
- Modo Plan: https://docs.replit.com/pt/references/agent/plan-mode
- Skills: https://docs.replit.com/pt/references/agent/skills
- Modos e testes de app: https://docs.replit.com/pt/references/agent/agent-modes
- Code Optimizations: https://docs.replit.com/references/agent/code-optimizations
- Checkpoints e rollback: https://docs.replit.com/pt/references/version-control/checkpoints-and-rollbacks

Capacidades relevantes observadas:
- Planejar uma tarefa complexa antes de modificar código ou dados; decompor em tarefas ordenadas e revisar antes de construir.
- Skills persistentes por projeto: metadados curtos são conhecidos pelo agente e o conteúdo detalhado é carregado quando a skill é relevante.
- Testes automáticos do aplicativo e revisão/otimização de código após a construção.
- Checkpoints em marcos, estados estáveis e antes de recuperação; rollback para um estado anterior.
- Sistema de tarefas e trabalho em segundo plano.

Adaptação para Kellvyn:
- Planejamento e aprovação permanecem internos ao único Kellvyn; não adicionar modos visíveis ao usuário.
- Skills são instruções procedurais próprias, carregadas sob demanda e sem ampliar permissões.
- Checkpoints devem ser conectados ao ciclo real de execução, não apenas existir como armazenamento isolado.
- O verificador deve executar testes observáveis e registrar evidências, sem confundir o relato do agente com aprovação.

### Cursor Agent

Fontes oficiais:
- Agent Mode: https://cursor.com/help/ai-features/agent
- Subagents: https://cursor.com/docs/subagents

Capacidades relevantes observadas:
- Explorar a base de código, selecionar contexto, editar múltiplos arquivos, executar comandos e testes e corrigir erros.
- Revisar alterações por diff e recuperar um checkpoint anterior.
- Subagentes especializados com contextos isolados; podem explorar/revisar em paralelo. Cópias de trabalho separadas evitam que edições concorrentes sobrescrevam umas às outras.

Adaptação para Kellvyn:
- Evoluir recuperação lexical para índice de símbolos, imports, referências e dependências.
- Criar papéis internos especializados (pesquisador, implementador, revisor, executor de testes) usando o mesmo modelo Kellvyn, sem múltiplas identidades ou seletor de modelos.
- Delegação que altera código exige cópia/branch isolada e revisão antes de integrar.
- O agente deve apresentar e inspecionar diff antes de declarar uma alteração concluída.

### Gemini Agent Mode no Android Studio

Fontes oficiais:
- Agent Mode: https://developer.android.com/studio/gemini/agent-mode
- Recursos do Gemini no Android Studio: https://developer.android.com/studio/gemini/features

Capacidades relevantes observadas:
- Receber um objetivo amplo, elaborar plano e fazer alterações multi-arquivo em etapas.
- Gerar testes unitários e ajudar a atualizar dependências/resolver erros de build.
- Implantar o app no dispositivo conectado, observar a tela, tirar screenshots, verificar Logcat e interagir com o aplicativo por ações de dispositivo/ADB.
- Revisar alterações e manter visibilidade dos arquivos modificados.

Adaptação para Kellvyn:
- Criar uma skill Android que diferencie compilação, testes, APK, assinatura, instalação, inicialização e teste real de tela.
- Integrar screenshots, árvore de acessibilidade e Logcat como evidência independente quando a ferramenta de dispositivo existir.
- Não afirmar que o app funciona só porque o Gradle terminou.
- O laboratório atual de fixture do construtor é uma prova do pipeline, não prova de criação autônoma de apps pelo chat.

## Estado real do agente Kellvyn antes desta atualização

Já existiam:
- Ciclo controlado de chamada de ferramentas com timeout, limite de etapas/chamadas, bloqueio de ferramenta não registrada, validação de argumentos e detecção de repetição.
- Política padrão que só autoriza leitura local sem fluxo de aprovação.
- Marcação de resultados de ferramentas, memória e arquivos como dados não confiáveis.
- Recuperação lexical de memória e trechos do projeto ativo.
- Planejador com dependências, risco, aprovação, pré-condições, provas independentes e rollback descrito.
- Armazenamento local criptografado de memória e de checkpoints encadeados.

Ainda não existiam ou não estavam ligados ao ciclo executável:
- Registro de skills com carregamento seletivo.
- Integração automática de checkpoints em cada transição do agente.
- Delegação interna com contexto isolado e revisão de diff.
- Índice de símbolos e referências do projeto.
- Ferramentas de terminal/sandbox, build e controle de dispositivo integradas ao ciclo de inferência.
- Backend neural conversacional e integração do agente ao chat principal.

## Melhoria implementada nesta revisão

Arquivos:
- `android/app/src/main/java/com/kellvyn/assistant/KellvynSkillRegistry.kt`
- `android/app/src/main/java/com/kellvyn/assistant/KellvynAgent.kt`
- `android/app/src/test/java/com/kellvyn/assistant/KellvynSkillRegistryTest.kt`
- `.github/workflows/neural-core-tests.yml`

Comportamento:
1. Cada skill tem ID, descrição, termos de ativação e procedimento.
2. O registro calcula relevância lexical de forma determinística.
3. São carregadas no máximo duas skills por tarefa, respeitando orçamento de caracteres.
4. O texto completo só entra no contexto quando há correspondência; tarefas sem relação não recebem instruções extras.
5. As skills são adicionadas como instruções internas confiáveis, separadas de observações externas não confiáveis.
6. Skills não criam ferramentas, não executam ações e não alteram a política de autorização.

Skills iniciais:
- `android-build-verification`
- `failure-diagnosis`
- `code-review`
- `web-research`
- `app-functional-testing`

Testes escritos cobrem seleção relevante, exclusão de skills não relacionadas, determinismo, orçamento de contexto e injeção no contexto confiável do agente. **Os testes ainda precisam ser executados e aprovados no CI; escrever os testes não é prova de que passaram.**

## Próximas melhorias prioritárias

1. Executar e corrigir o CI do registro de skills antes de ampliar o agente.
2. Conectar `KellvynTaskCheckpointStore` ao ciclo de execução: salvar plano, etapa, tentativa, resultado, evidência, erro, cancelamento e recuperação.
3. Emitir eventos de progresso estruturados para que uma tarefa longa possa ser retomada e auditada.
4. Evoluir a busca lexical para índice incremental de símbolos, imports, chamadas e dependências, preservando filtros de segredos.
5. Criar revisão independente de diffs e execução repetível de testes, com relatório de aprovação/falha.
6. Integrar build, instalação, screenshots, acessibilidade e Logcat por ferramentas autorizadas do laboratório.
7. Só conectar o agente ao chat principal após o backend de inferência neural próprio passar pelos gates; nenhuma mudança visual é necessária para esta etapa.

## Restrições permanentes

- Um único modelo/identidade Kellvyn; papéis internos usam o mesmo cérebro.
- Offline/local primeiro; sem dependência obrigatória de hospedagem ou APIs pagas.
- Sem seletor de provedores ou modos leve/pesado na interface.
- Sem alterações em aparência, menus, animações ou componentes já aprovados.
- Sem pesos pré-treinados de terceiros como cérebro da Kellvyn.
- Não declarar pesquisa, código, build, teste, instalação ou funcionamento como concluídos sem a evidência correspondente.

## Implementação adicional após a comparação — 16/11/2026

### 1. Skills próprias sob demanda

Arquivos: `KellvynSkillRegistry.kt`, `KellvynAgent.kt`, `KellvynSkillRegistryTest.kt`.

O agente agora tem um registro interno de seis skills:
- Android build e verificação;
- diagnóstico de falhas;
- revisão de código;
- navegação estrutural Kotlin/Java;
- pesquisa técnica;
- teste funcional de aplicativos.

A seleção é determinística por termos relevantes, com limite padrão de duas skills e orçamento de 6.000 caracteres. O texto completo só entra no contexto SYSTEM confiável quando selecionado. Skills não registram ferramentas, não autorizam operações e não executam código.

### 2. Navegação estrutural Kotlin/Java

Arquivos: `KellvynProjectCodeIndexer.kt`, `KellvynProjectContextRetriever.kt`, `KellvynAgent.kt`, `KellvynProjectCodeIndexerTest.kt`.

O índice limitado identifica pacote, imports, classes, funções/propriedades, declarações de métodos Java e nomes de chamadas. A busca do projeto considera esses identificadores além do caminho e do texto. O agente recebe os identificadores reconhecidos no contexto, mas deve confirmar a informação no trecho-fonte original.

Limites: não é AST, não resolve sobrecargas, tipos genéricos ou fluxo de controle e não substitui compilador. O recorte máximo por arquivo continua limitado. A implementação filtra comentários e strings para reduzir falsos símbolos.

### 3. Plan antes de Build e checkpoints

Arquivos: `KellvynTaskCheckpointRepository.kt`, `KellvynAgent.kt`, `KellvynRuntime.kt`, `KellvynAgentPlanTest.kt`.

O protocolo do agente agora aceita `KellvynModelDecision.Plan`. Um plano só é aceito quando:
- respeita o esquema e dependências;
- usa ferramentas registradas;
- declara risco no mínimo igual ao risco real da ferramenta;
- exige aprovação para etapas mutáveis e possui plano de recuperação;
- pode ser salvo no armazenamento local de checkpoints.

O plano inicial é persistido antes de qualquer ferramenta executar. `approveTaskStep` registra uma aprovação como novo checkpoint. `restoreTaskPlan` recupera a cadeia validada. `executePlannedStep` executa uma etapa pronta, persiste o estado RUNNING antes da ferramenta e só grava SUCCEEDED após um `KellvynIndependentVerifier` comprovar todos os critérios. Uma resposta de sucesso da própria ferramenta, sem prova independente, resulta em FAILED. Nesta alpha, cada etapa executável deve declarar exatamente uma ferramenta.

A aprovação do proprietário e a autorização técnica são gates separados. Aprovar uma etapa não concede acesso automaticamente: a política de ferramentas ainda precisa autorizar a execução. Timeout, erro e cancelamento têm caminhos distintos.

### 4. Como isso se relaciona com as referências

Replit: a skill registry segue a ideia de disponibilizar metadados pequenos e carregar instruções completas apenas quando relevantes. O protocolo Plan/Build agora tem validação e persistência iniciais. Code Optimizations e testes autônomos integrados ainda não foram implementados.

Cursor: o indexador dá o primeiro passo para navegação por símbolos/referências. Subagentes com contexto realmente isolado, execução paralela e branches/worktrees não foram implementados. A referência do Cursor explica que subagentes próprios podem pesquisar, delegar e validar em janelas separadas; não vamos fingir essa capacidade usando apenas chamadas sequenciais ao mesmo agente.

Android Studio: o protocolo de etapas, autorização e prova aproxima o ciclo plano → ação → validação. Deploy em device, screenshots, árvore de acessibilidade, ADB e Logcat ainda não estão conectados ao agente Kellvyn.

### 5. Validação realizada e pendências

Foi compilado e executado um harness Kotlin isolado para o indexador estrutural, cobrindo pacote, imports, declarações, referências, comentários, strings e limites. Um smoke test independente também exercitou a lógica de seleção de skills em português. Isso não equivale a compilar o repositório nem a executar os testes JUnit reais.

O status combinado consultado para os commits recentes não trouxe checks. A ferramenta de consulta de runs disponível só retorna execuções associadas a pull requests e também não retornou runs. Portanto:
- Gradle/JUnit do repositório: pendente de confirmação;
- integração Android/Keystore no dispositivo: pendente;
- backend neural que produza decisões Plan/CallTool: não existe ainda;
- integração com o chat principal: não realizada;
- teste no Poco F6: não realizado.

Não declarar o agente operacional no aplicativo até esses gates serem executados.
