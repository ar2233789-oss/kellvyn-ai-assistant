# KELLVYN — CONTROLE DE SESSÃO E CONTINUIDADE

**Este é o ponto único de entrada para qualquer nova conversa de trabalho.**
Leia este arquivo antes de alterar código. Depois confirme o HEAD real no GitHub. Não dependa da memória do chat.

**Última revisão:** 19/11/2026 — laboratório poliglota v2 em construção
**Branch experimental ativa:** `kellvyn-rnd/polyglot-lab-foundation-20261119`
**PR laboratório:** #25 (DRAFT, IMPLEMENTED_NOT_RUN) — `https://github.com/ar2233789-oss/kellvyn-ai-assistant/pull/25`
**HEAD-base desta atualização:** `b4096c30ec0b02c4ccbb5762758c74028ab757e8` (reconfirmar HEAD no GitHub antes de qualquer alteração)  
**Repositório:** ar2233789-oss/kellvyn-ai-assistant  
**Branch de trabalho:** kellvyn-rnd/agent-trace-duration-port-20261118  
**HEAD-base confirmado antes da integração da Workbench:** d19ac2a653ed75873ad03d70f9c85d3519f678db  
**Implementação da R&D Studio:** 4598cab4e831dd3e1f9d96c0c2158de8e77ff566  
**Últimos estudos:** auditoria KN-003 de gradientes e método de diferenças finitas, em `docs/kellvyn/rnd-studio/`.  
**CI observado:** run 44392690462 — `runner_id=0`, `steps=[]`; Gradle/JUnit/Android NOT RUN.

> Prioridade ativa: usar a Kellvyn R&D Studio externa para estudar e melhorar o cérebro próprio. Agent Lab Android e Agent Creation Studio são camadas distintas e não substituem essa bancada. Confirme sempre o HEAD real; o commit deste checkpoint é auto-referencial e será reconciliado pelo diário quando o runner voltar.
> Se o HEAD atual for diferente, leia os commits posteriores antes de editar. Este documento não autoriza sobrescrever trabalho posterior.

---

## 1. Objetivo ativo — não desviar

A prioridade ativa é a **Kellvyn R&D Studio externa**: uma bancada de engenharia para o assistente estudar a arquitetura neural própria, pesquisar métodos, formular hipóteses falsificáveis, isolar alterações, executar testes, comparar evidências e melhorar o cérebro sem perder continuidade. Não é uma tela do usuário nem um módulo do app.

Não confundir estas camadas:
- **Cérebro Kellvyn (KN):** modelo/tokenizer/pesos próprios e inferência neural; não está conectado ao chat de produção.
- **KellvynAgent:** loop operacional que usa uma interface de inferência injetada; não é o cérebro.
- **KellvynOrchestrator:** coordenador de delegações e especialistas; não substitui o cérebro.
- **Agent Creation Studio:** criação, compilação, versionamento e avaliação estrutural dos 14 perfis.
- **Agent Lab Android:** observa execuções reais, traces, benchmarks e recursos dos agentes dentro do app.
- **Kellvyn Engineering Lab:** inventário estático, mapa e diário do repositório.
- **Kellvyn R&D Studio externa:** bancada para estudar e desenvolver o cérebro, com worktrees, experimentos, pesquisas e gates de evidência. Este é o foco ativo.

O R&D Studio foi criado em `tools/kellvyn-rnd-studio/` e documentado em `docs/kellvyn/rnd-studio/`. Não recriar cérebro, orquestrador, 14 perfis, Agent Creation Studio nem Agent Lab Android. Não voltar para layout/interface nesta frente.

O aplicativo final continua nativo e offline-first para Poco F6 / HyperOS / 8 GB RAM. Não usar WebView final, não depender de hospedagem externa nem de serviços pagos.

## 2. Regras obrigatórias para não refazer nem estragar

1. **Não recriar o que já existe.** Antes de propor uma classe/ferramenta/módulo, pesquisar no repositório por implementação, testes, registro, integração no runtime e documentação. Uma classe existente não significa que esteja integrada; verificar cada camada.
2. **Não editar por suposição.** Toda alteração precisa apontar um defeito reproduzido, uma lacuna real ou um teste faltante que seja necessário ao próximo gate. Preferir a menor correção capaz de resolver.
3. **Não adicionar escopo para esconder falta de validação.** Enquanto o CI não executa, não empilhar funcionalidades novas. Primeiro obter compilação e testes reais.
4. **Não confundir estados:** “implementado”, “compilado”, “teste unitário aprovado”, “teste Android instrumentado aprovado”, “instalado em emulador” e “validado no Poco F6” são evidências diferentes.
5. **Não declarar sucesso por teste simulado.** Harness com stubs, fixtures, cópias equivalentes ou verificadores injetados prova somente o que realmente executou. Não prova competência do modelo nem funcionamento de ponta a ponta.
6. **Preservar a interface aprovada.** Não alterar `MainActivity.java`, `.github/interface-lock.sha`, layout, barras, menus, cores, chuva, raios, efeitos ou animações sem pedido explícito. Não mudar o hash de proteção para encobrir divergência.
7. **Trabalho móvel precisa ser limitado.** Preservar lazy loading, filas e limites de RAM/CPU/tempo/bytes. Não carregar todos os módulos ou executar 14 agentes em paralelo.
8. **Não expor mutações ao modelo.** Aprovação do proprietário, publicação/promoção de perfis e rollback precisam continuar em APIs confiáveis do app, não em ferramentas que o agente pode chamar por conta própria.
9. **Trabalhar em commits pequenos e rastreáveis**, mas não criar commits apenas para narrar progresso. Conferir o diff e os arquivos tocados antes de gravar.
10. Se a evidência não existe, registrar **NÃO TESTADO / BLOQUEADO**, sem preencher a lacuna com suposição.

## 3. Proteções verificadas nesta branch

- `MainActivity.java` tem blob SHA atual `8e1aea5406920ef273a50d5c91a5a8be6434c0b3`.
- `.github/interface-lock.sha` contém o hash protegido `bc7de61c36a3c46fc90b9962600cb48d6c3ed2c8` para `MainActivity.java`.
- A divergência já existia; não tentar “corrigi-la” editando a tela ou a lock. Manter ambos intocados até instrução explícita e um procedimento de comparação/aprovação visual.
- O commit HEAD `80c00fc` alterou somente `docs/kellvyn/CONTINUITY_HANDOFF.md` e `docs/kellvyn/STATUS.md`; não alterou Kotlin, Java, workflow, interface ou testes.

## 4. O que já está implementado — não duplicar

### Base dos agentes e laboratório
- 14 papéis especialistas declarados, com política central de ferramentas/capacidades por papel.
- Delegação com contexto isolado e limite de delegação aninhada; especialistas compartilham o mesmo backend de inferência, não são 14 modelos treinados separadamente.
- Gerenciador de módulos, runtime de recursos/governador, broker/fila de tarefas, skills sob demanda e ferramentas com política de risco.
- Seis módulos de ferramenta registrados no runtime. Isso **não** significa que os oito estúdios descritos no plano estejam completos.

### Agent Creation Studio
- Registro de perfis dos 14 papéis, com missão, instruções, formato de resposta e limites.
- Compiler de perfis com validação de cobertura/capabilities e SHA-256 determinístico.
- Catálogo de 28 cenários de benchmark, dois por papel.
- Harness de avaliação sequencial e fail-closed, com executor e verificador independentes injetáveis.
- Histórico versionado de perfis: eventos `STAGED`, `APPROVED`, `ROLLED_BACK`; AES-GCM com Android Keystore, AAD autenticado, hash chain e compare-and-swap.
- Limites móveis do histórico: 256 eventos globais, até 32 KiB por registro; histórico de leitura limitado a 12 eventos; ferramentas de consulta com estimativa STANDARD de 24 MiB e saída limitada.
- Ferramentas do modelo para histórico e diff pendente são somente leitura. A aprovação mutável não é exposta ao modelo.
- `KellvynRuntime.createAgent()` e `createProjectWorkspaceAgent()` passaram a ser suspend para carregar perfis ativos em I/O.

**Limite do que está pronto:** o fluxo confiável de confirmação/aprovação pelo proprietário ainda não tem uma UI/fluxo completo ligado ao usuário. O harness ainda não executa os especialistas contra uma inferência neural funcional. Perfil versionado e 28 cenários não significam competência comprovada.

### Fora do escopo concluído
- UI/UX Studio, Image Studio e 3D/CAD Studio não estão completos.
- Android Build Status é diagnóstico READ_ONLY, não um Android Studio/Gradle completo.
- O cérebro neural experimental não está conectado ao chat principal. Não afirmar que o agente cria aplicativos complexos pelo chat.

## 5. Validação real — estado em 18/11/2026, 15:30 UTC

### Testes
- Testes unitários e instrumentados do Profile Store, Studio, política e matriz de ferramentas foram **escritos**.
- Eles **não foram executados** por Gradle/JUnit/Android Runner nesta rodada.
- Houve smoke tests locais isolados com stubs/cópias equivalentes para partes do harness e do registro. Um conjunto sintético de 28 casos retornou 28/28 nesse harness. Isso não é compilação do app Android, não executa a inferência real e não prova competência dos 14 papéis.

### CI
- Run Agent Lab `44335158436`, job `134909733286`: terminou em failure sem steps e sem logs.
- Rerun do mesmo run, job `134910540861`: repetiu failure sem steps e sem logs.
- A execução parou antes de fornecer evidência de que Gradle começou. Classificação correta: **RUNNER/EXECUÇÃO BLOQUEADA — NOT RUN**. Não classificar como falha de código e não classificar como aprovação.
- O commit `80c00fc` é atualização documental após esse rerun; ele não representa uma nova execução de código/CI.
- A causa exata do bloqueio não foi revelada. Não afirmar que é billing, quota ou erro do código sem evidência.

### APK / dispositivo
- Não há APK desta rodada comprovadamente produzido a partir da branch atual.
- Não há prova de instalação/execução em emulador nesta rodada.
- Não há prova de teste físico no Poco F6 nesta rodada.
- Não alegar desempenho, RAM, calor, bateria ou funcionamento no aparelho sem medições.

### Diagnóstico adicional do runner — 18/11/2026, 15:41 UTC
- A página pública GitHub Status foi consultada: Actions aparece como Operational no momento da consulta. Isso afasta uma declaração de outage global ativa, mas não revela a causa particular desta conta/repositório.
- Foi criado um workflow isolado `.github/workflows/runner-probe.yml` para executar somente um `echo` num `ubuntu-latest`. Run `44337962674`, job `134919715612`: failure em aproximadamente 3 segundos, sem steps e sem logs_url. O arquivo isolado foi removido depois para não manter workflow redundante.
- O workflow Agent Lab recebeu um job mínimo `runner-allocation-probe` sem checkout, Gradle, Android SDK ou ações externas. Run `44338054696`: job de teste `134920052636` e probe `134920052769` terminaram em failure; ambos com `steps=null` e `logs_url=null`.
- Os arquivos alterados para esse diagnóstico foram somente `.github/workflows/agent-lab-tests.yml` e o workflow temporário de probe, que foi removido. O probe mínimo permanece como job separado no workflow Agent Lab.
- Classificação: **falha antes de iniciar qualquer step em dois tipos de workflow; Gradle/JUnit/Android não executaram.** Isto prova ausência de execução, não falha do código. A causa específica (quota/compute da conta, política da organização/repositório ou provisionamento do runner) ainda não está exposta pelo conector.
- Repositório é privado. A documentação oficial do GitHub informa que o compute incluído para Actions em repositórios privados depende do plano da conta; não foi possível ler a página privada de uso/cobrança nem as configurações de Actions por esta conexão. Não afirmar que quota/billing é a causa sem a tela/erro correspondente.
- Código do chat foi inspecionado: `KellvynRuntime.isReady()` retorna literalmente `false`; `KellvynRuntime.reply()` retorna indisponibilidade de inferência. `MainActivity.sendMessage()` verifica `isReady()` e não envia a solicitação ao agente quando o cérebro não está pronto. O modelo próprio experimental existe em código, mas ainda não está ligado ao chat com um checkpoint conversacional treinado/validado.
- Consequência: gerar agora um APK, mesmo se fosse possível, não entregaria uma IA conversacional funcionando. Não chamar esse estado de “IA pronta” nem prometer entrega integral hoje.

## 6. Estado após auditoria, pesquisa e próxima tarefa única

### Alterações documentais desta retomada
- Criado e executado um teste isolado de alocação de runner; o workflow temporário separado foi removido depois do teste.
- O probe foi repetido com `ubuntu-slim` em vez de `ubuntu-latest`; também não recebeu runner. O job completo do Android permaneceu em `ubuntu-latest`.
- Adicionado job `runner-allocation-probe` ao Agent Lab; run `44338054696`, jobs `134920052636` e `134920052769`.
- Atualizada `AGENT_TOOL_AVAILABILITY_2026-11-18.md`: o inventário correto tem seis módulos, incluindo `web-research-wikipedia` e `agent-creation`. A pesquisa Web disponível é somente `wikipedia_search`, limitada à Wikipédia PT e autorizada apenas ao Pesquisador com Acesso à Web ativado.
- Criado `AGENT_CAPABILITY_RESEARCH_ADDENDUM_2026-11-18.md`, com pesquisa de OpenAI Agents/evals/tracing, Cursor subagents/planning, Replit lifecycle/testing, Android Studio device testing, Nano Banana e LiteRT.
- Corrigido `LEARNING_AND_TRAINING.md`: não oferece mais a alternativa contraditória de usar pesos pré-treinados/LoRA externos. A regra de cérebro do zero prevalece.
- Atualizado `NEURAL_MODEL_DECISIONS.md` com uma síntese atual do estado KN-003 e seus limites quantitativos.
- Atualizado `LEARNING_AND_TRAINING.md` para remover a alternativa contraditória de modelo-base/LoRA de terceiros e formalizar a rota autoral do zero.
- Corrigidos `REFERENCE_STUDY.md` e `COGNITIVE_LAB.md`, que ainda tinham frases antigas sugerindo uso de pesos abertos/adapters externos; agora a regra de pesos próprios é consistente nesses documentos também.
- Nenhuma classe Kotlin/Java, perfil, política, teste ou arquivo visual foi alterado nessa retomada.

### Evidência exata do bloqueio GitHub Actions
- O endpoint detalhado de jobs para o run `44338054696` mostra, em ambos os jobs, `runner_id=0`, `runner_name=""`, `steps=[]`, falha em aproximadamente 2 segundos.
- A mesma ausência de atribuição ocorreu no run isolado `44337962674`, job `134919715612`.
- Para separar falha específica da imagem, o job `runner-allocation-probe` foi alterado para `ubuntu-slim` (1 vCPU/5 GB em repositórios privados, conforme a tabela oficial de runners). O Android/Gradle continuou em `ubuntu-latest`. Novo run `44344351674`: tentativa 1 falhou; a tentativa 2 foi repetida isoladamente no job `134942998352` e também terminou failure, `runner_id=0`, `runner_name=""`, `steps=[]`, em cerca de 3 segundos. Job Android `134943002530` continuou falhando sem runner.
- Isso confirma que o runner hospedado **não foi atribuído**; Gradle, Android SDK, checkout e qualquer teste não começaram. Não é evidência de erro Kotlin.
- A página pública do GitHub Status consultada para 18/11/2026 mostra Actions como Operational e nenhum incidente reportado nesse dia. O incidente de 16/11 terminou às 16:26 UTC; não explica diretamente os jobs de 18/11.
- A documentação oficial confirma que, em repositórios privados, compute incluído depende do plano e é compartilhado entre hosted runners privados e Cloud Sandbox compute/memory. Quando o compute incluído acaba, sem método de pagamento válido ou com um budget bloqueando o gasto, o uso pode ser bloqueado. Isso é agora a **hipótese mais forte a verificar**, mas não é causa confirmada porque não temos o estado privado de billing nem a mensagem de erro exibida ao proprietário.
- Ação do proprietário: abrir [run 44338054696](https://github.com/ar2233789-oss/kellvyn-ai-assistant/actions/runs/44338054696) e conferir [GitHub Billing](https://github.com/settings/billing) → Usage → Actions/compute e budgets; verificar método de pagamento e se o saldo incluído foi consumido. Não aumentar o limite nem adicionar cobrança sem decisão explícita do proprietário.
- Referências oficiais: https://docs.github.com/en/actions/concepts/billing-and-usage e https://docs.github.com/en/billing/concepts/product-billing/github-actions.

### Estado neural real
- `KellvynRuntime.isReady()` retorna `false`; `reply()` informa indisponibilidade de inferência. A conversa principal ainda não chama o agente com um modelo treinado.
- KN-003 é um Transformer autoral de um único bloco, com cache KV incremental para esse bloco. A configuração padrão do pipeline usa contexto 256, embedding 32, 4 cabeças, FF64, até 64 merges BPE, uma época e no máximo 100.000 passos. Com todos os 64 merges usados, o vocabulário é no máximo 321 e o modelo tem cerca de 31.233 parâmetros — um protótipo pequeno, não um modelo de linguagem geral.
- O pipeline de treino limita o corpus experimental a 500.000 bytes e exige amostras com TRAIN/VALIDATION/TEST e proveniência. A árvore do repositório não contém corpus independente para treinar o assistente; há fixtures de teste e documentação, não dataset de produção.
- O gate atual só admite o modelo a um benchmark de protocolo; não comprova português geral, raciocínio, programação ou geração de aplicativos.
- `NEURAL_MODEL_DECISIONS.md` e `LEARNING_AND_TRAINING.md` agora apresentam esse estado real e a regra de pesos próprios de modo consistente.

### LiteRT — decisão de pesquisa, não integração feita
- LiteRT CompiledModel pode acelerar modelos próprios em CPU/GPU e aceita conversão a partir de frameworks suportados. A implementação atual de KN-003 é Kotlin manual e não pode ser exportada automaticamente; exigiria portar o mesmo grafo/mapear os pesos e provar equivalência token/logit a token.
- Play Services LiteRT envia métricas de dispositivo/app/execução e pode contactar servidores Google para atualizações; por isso não foi adicionado ao runtime offline-first. Standalone LiteRT poderá ser avaliado depois, com comparativo de CPU/GPU, tamanho APK, latência, RAM, temperatura, bateria e equivalência matemática no Poco F6.
- A otimização não será introduzida antes de existir checkpoint treinado e benchmark Kotlin de referência.

### Próxima tarefa única
**Confirmar na tela privada de Actions/Billing se a conta/repositório está sem compute incluído ou bloqueado por budget/política e reativar um runner. O probe `ubuntu-slim` e o `ubuntu-latest` falharam antes dos steps.**

Depois:
1. executar `gradle testDebugUnitTest`;
2. executar `gradle connectedDebugAndroidTest`;
3. corrigir somente problemas reproduzidos;
4. completar a confirmação explícita do proprietário no Profile Store;
5. conectar o harness a inferência real só depois de checkpoint treinado e qualidade mínima;
6. construir APK, instalar em emulador e testar no Poco F6.

Um APK anterior a esses gates não é a IA completa.

### Pesquisa e docs novos
- [Pesquisa complementar de capacidades e ML móvel](AGENT_CAPABILITY_RESEARCH_ADDENDUM_2026-11-18.md)
- [Matriz corrigida de ferramentas por papel](AGENT_TOOL_AVAILABILITY_2026-11-18.md)
- [Política de treino alinhada à decisão de pesos próprios](LEARNING_AND_TRAINING.md)

## 6.1 Revisão de commits posteriores — 18/11/2026, 16:11–16:26 UTC

A branch avançou **12 commits** depois do HEAD registrado na revisão anterior (`d987d11`). A revisão direta do diff e dos arquivos atuais confirmou:

### Orquestrador explícito — implementado no código, ainda não validado por Gradle/CI

Commits relevantes:
- `20fd0c4` — criação de `KellvynOrchestrator.kt`.
- `8ed11dd` e `040415c` — transferência da coordenação dos especialistas para o orquestrador e roteamento de cada execução por uma sessão.
- `8ced235`, `8f2135a`, `5afe774` e `8ceb2bd` — testes e correções do orçamento de delegação por execução.

Verificado no código atual:
- `KellvynAgent` instancia um `KellvynOrchestrator`, cria uma sessão para cada execução e encaminha decisões `Delegate` para ela.
- O orquestrador valida a política/capabilities, restringe ferramentas ao subconjunto permitido, compila o perfil, cria o especialista com a mesma instância de inferência, proíbe delegação recursiva, aplica limites e timeout, propaga cancelamento e devolve o resultado como observação não confiável.
- O orçamento de delegações pertence à sessão; execuções separadas não compartilham esse contador.
- `KellvynOrchestratorTest.kt` foi adicionado com casos para isolamento, ferramentas autorizadas, limites por sessão e contexto excessivo.

**Interpretação correta:** o orquestrador independente agora existe e coordena a delegação. Isso não significa que há 14 modelos diferentes, que os especialistas tenham competência neural comprovada ou que a execução tenha passado em Gradle. Os testes foram escritos, mas não foram executados/aprovados em CI nesta revisão.

### Rastreamento do laboratório — estrutura criada, integração pendente

Commits relevantes:
- `b2ad787` — contrato de eventos de trace com metadados de privacidade.
- `ee7deaf` — armazenamento local cifrado e limitado.
- `1a86f72` — restauração da identidade do trace a partir do cabeçalho autenticado.
- `5233644` — suporte a spans/atores filhos no mesmo trace.

Verificado:
- `KellvynAgentLabTrace.kt` define eventos de início, chamadas de modelo, decisões, delegação, ferramentas e estados finais; inclui trace ID, horário, ator, papel, etapa, nome de ferramenta, hash de perfil, duração e códigos de estado.
- O schema não aceita prompt, resposta bruta do modelo, argumentos de ferramenta, conteúdo de memória/arquivo ou credenciais.
- `KellvynAgentLabTraceStore.kt` grava eventos em arquivos privados, cifra payloads com o cifrador AES-GCM existente, autentica o cabeçalho como AAD, encadeia registros por SHA-256, grava por arquivo temporário + rename e limita retenção/tamanho (64 traces, 128 eventos por trace, até 2 KiB por registro).
- `KellvynAgentLabTraceSession.forActor()` permite registrar atores filhos sob o mesmo trace ID.

**Lacunas confirmadas pela inspeção:** ainda não há referência a `KellvynAgentLabTrace`/Store/Session em `KellvynAgent.kt` nem em `KellvynRuntime.kt`; portanto, os eventos não estão ligados ao ciclo real do agente e não há evidência de traces efetivamente sendo gravados. A listagem atual de testes também não contém um `KellvynAgentLabTraceStoreTest`. O código de armazenamento existe, mas seus ciclos de escrita/leitura, corrupção, limite e pruning ainda não têm teste listado nem aprovação Gradle observada.

Risco específico a testar/corrigir antes de depender do histórico: `RUN_CANCELLED` existe no enum de eventos, mas não é classificado como terminal por `isTerminal()`; traces cancelados não serão tratados como concluídos pelo resumo/pruning atual.

### CI do HEAD auditado — continua bloqueado antes dos steps

- Run `44347211746`, associado ao HEAD `5233644`, falhou em 18/11/2026 às 16:26:16 UTC.
- Jobs `134952728080` (probe `ubuntu-slim`) e `134952728764` (testes Unit/Android) retornaram `runner_id=0`, `runner_name=""` e `steps=[]`.
- Run anterior `44347073611` repetiu o mesmo padrão: sem runner e sem steps.
- Classificação: **RUNNER NÃO ALOCADO / TESTES NOT RUN**. Não é evidência de falha Kotlin, mas também não é aprovação. Nenhum teste Gradle/JUnit/Android foi executado por esses jobs.

### Estado exato após esta revisão

- Orquestrador: **implementado + teste escrito + não validado por Gradle/CI**.
- Perfis dos 14 especialistas: **registrados e compiláveis pelo Profile Compiler + competência neural não comprovada**.
- Trace schema/store: **implementados no código + ainda não conectados ao runtime + sem teste próprio listado**.
- Agent Creation Studio: **registro, compilação, 28 cenários, harness e armazenamento versionado já existem**; não recriar esses módulos.
- Runner/CI: **bloqueio de alocação continua sendo o próximo gate**.
- Interface protegida e `MainActivity.java`: permanecem intocados pelos 12 commits comparados; não editar nem alterar o hash de proteção.
- Não há comprovação nova de APK, instalação em emulador ou teste físico no Poco F6.

### Próximo passo único

Confirmar na área privada de Actions/Billing se compute incluído, budget ou política da conta/repositório está bloqueando a alocação do runner e restabelecer uma execução mínima. Até existir runner, não empilhar novos módulos. Depois, executar primeiro a suíte Gradle existente; então adicionar os testes faltantes do trace store e só integrar o rastreamento ao ciclo real após validar os contratos, com I/O fora da thread principal e retenção/cancelamento corretos.

---

## 6.2 Agent Lab e continuidade automática — revisão 18/11/2026, 17:15 UTC

Esta seção **supersede as lacunas antigas descritas em 6.1** sempre que elas afirmem que o trace não está ligado ao agente ou que não existe módulo Agent Lab. A revisão atual inspecionou diretamente o código no HEAD `ca728f9`, os 33 commits posteriores a `b7e2270` e os jobs de CI mais recentes.

### Ambiente de trabalho já criado

1. **Observabilidade automática do agente**
   - `KellvynAgentLabTraceStoreRecorder` grava no armazenamento privado cifrado, em I/O.
   - `KellvynAgent.run()` gera um trace por execução e grava início/fim, decisões do modelo, chamadas/falhas de modelo, ferramentas, limites e cancelamentos.
   - `executePlannedStep()` também registra a execução e o resultado da etapa.
   - `KellvynOrchestrator` compartilha o trace ID com spans de delegação e atores filhos dos especialistas.
   - O schema não armazena prompts, respostas brutas, argumentos, conteúdo de arquivos/memória ou credenciais.
   - A cadeia continua autenticada por AES-GCM/AAD e encadeada por SHA-256. Eventos cancelados, planos persistidos e experimentos concluídos são estados terminais para retenção.

2. **Medição de recursos**
   - Eventos `RESOURCE_SNAPSHOT` registram snapshots sob demanda no início/fim do coordenador e dos especialistas.
   - Metadados compactos incluem memória disponível, percentual de heap, PSS, bateria e faixa térmica.
   - Não há thread de polling permanente. O laboratório não deve autorizar concorrência até existirem medições físicas do Poco F6.

3. **Módulo próprio do Agent Lab**
   - `KellvynAgentLabStudio.kt` está registrado no `KellvynRuntime` como módulo independente e carregado sob demanda.
   - Ferramentas READ_ONLY: status de ambiente/módulos, listagem de traces, inspeção de linha do tempo e comparação baseline/candidato.
   - A comparação inclui casos de benchmark, aprovação por caso, duração, hash de perfil e snapshots de recursos.
   - O módulo não promove perfis nem recebe capacidade de escrita.

4. **Execução e avaliação de perfis**
   - `KellvynSpecialistAgentBenchmarkExecutor.kt` executa um perfil compilado pelo loop real de `KellvynAgent`, usando a inferência fornecida e as ferramentas filtradas pela política central.
   - Captura ferramentas solicitadas versus executadas; escrita, delegação aninhada e ferramentas fora da matriz são recusadas/registradas.
   - `KellvynSpecialistEvaluationHarness` passa spans de cada caso ao executor e registra o resultado independente.
   - `KellvynRuntime.runAgentLabBenchmarkSuite()` aceita inferência real, verificador confiável e perfis candidatos; um candidato pode ser testado em memória sem ser ativado.
   - O verificador permanece separado e obrigatório. O executor nunca aprova o próprio resultado.

5. **Continuidade de desenvolvimento**
   - `scripts/append-agent-lab-activity.py` e `.github/workflows/agent-lab-activity-ledger.yml` foram criados para anexar automaticamente horário UTC, commit, autor, assunto e caminhos alterados.
   - `docs/kellvyn/DEVELOPMENT_ACTIVITY.md` recebeu backfill dos 37 commits da sessão até 17:15 UTC, verificados pelo histórico do GitHub.
   - O arquivo `SESSION_CONTROL.md` continua sendo o ponto único de entrada. Não criar diários paralelos.

### Testes acrescentados — escritos, ainda não executados

- `KellvynAgentLabTraceStoreTest.kt`: persistência/retomada de trace, ator filho, cancelamento/terminalidade, pruning explícito e adulteração da cadeia.
- `KellvynAgentTest.kt`: ciclo automático e snapshots de RAM/heap/PSS/bateria/temperatura.
- `KellvynSpecialistAgentBenchmarkExecutorTest.kt`: execução real do adaptador, captura de ferramenta, bloqueio de ferramenta fora da matriz e não delegação aninhada.
- `KellvynSpecialistEvaluationHarnessTest.kt`: trace por experimento e por caso.
- `KellvynSpecialistToolPolicyTest.kt`: matriz de capacidades atualizada para permitir que Arquiteto de Programação e Analista de Planejamento leiam ferramentas de avaliação.

### Limites e evidências — não exagerar

- **CI:** run Agent Lab `44355722423`, neural `44355722403` e activity ledger `44355722420`, criados por volta de 17:10:51 UTC, falharam antes de executar steps. Todos retornaram `runner_id=0`; os jobs tinham `steps=[]`. Classificação: **RUNNER NÃO ALOCADO / TESTES NOT RUN**. Não é aprovação e não é evidência de erro Kotlin.
- O workflow de activity ledger também não conseguiu executar; por isso o backfill atual foi feito manualmente a partir dos commits. A automação futura depende de recuperar o runner.
- `KellvynRuntime.isReady()` permanece `false`; o cérebro neural próprio ainda não está ligado ao chat. O executor está preparado para receber uma inferência aprovada, mas não há benchmark cognitivo de produção enquanto esse backend não estiver conectado.
- O verificador independente continua sendo fornecido por código confiável; ainda não foi demonstrado um grader válido para todos os 28 cenários.
- Nenhum Gradle/JUnit/Android Runner executou; não há build/APK desta revisão, instalação em emulador ou teste físico no Poco F6.
- Os arquivos da interface, `MainActivity.java` e `.github/interface-lock.sha` ficaram intocados nesta sessão. Não alterar sem pedido explícito.

### Próximo gate único

**Recuperar a alocação de runner no GitHub Actions e executar as suítes existentes.** Não adicionar outro grande subsistema antes de obter uma compilação/teste real. Depois corrigir apenas falhas reproduzidas, fortalecer o verificador independente e executar o benchmark com uma inferência neural aprovada. Só então medir recursos no Poco F6 e decidir se algum paralelismo é seguro.

---

## 6.3 Capacidade, eficiência e validação — revisão 18/11/2026, 17:43 UTC

Esta seção atualiza os limites e testes adicionados depois de 6.2. A implementação atual foi lida diretamente no HEAD `0151bec` e os consumidores foram conferidos nos commits `aacfa35` e `3fdac74`. O HEAD documental desta sessão é `0b4d583`.

### Armazenamento de traces

O primeiro limite de 128 eventos por trace era baixo para uma execução extensa e para o benchmark de 28 casos. Também havia custo O(n²): cada append relia e validava toda a cadeia anterior.

A implementação atual:
- até **1.536 eventos por trace**;
- até **16 traces**;
- no máximo **2 KiB por registro**; teto teórico de aproximadamente **48 MiB** para a capacidade máxima;
- append usa cursor em cache (contagem + hash do último registro) depois da primeira validação;
- cursor obsoleto de outra instância é detectado pela presença do próximo arquivo e reconstruído a partir da cadeia autenticada antes de gravar;
- `historyTail(traceId, maxEvents)` valida toda a cadeia, mas retém somente a janela final em memória;
- summaries mantêm primeiro/último registro e contagem; inspeção comum lê 48 eventos e comparação pode ler até 1.536 eventos por trace;
- a suíte de benchmark é limitada a **28 casos**, que correspondem ao catálogo atual.

**Trade-off:** append sequencial após a primeira validação mantém cursor em memória. Leituras por janela e summaries continuam percorrendo a cadeia completa para validar integridade; isso é O(n), mas n é limitado a 1.536. Não declarar a leitura por janela como O(1).

### Testes escritos nesta revisão

- `KellvynAgentLabTraceStoreTest.kt`: 140 eventos (além do antigo limite de 128), tail de 12 eventos, contagem/horários do summary, novo store retomando em sequência 141, duas instâncias intercalando append sem sobrescrita.
- `KellvynSpecialistEvaluationHarnessTest.kt`: suíte com 29 casos bloqueada antes de qualquer execução; o trace termina como BLOCKED.
- consumidores de histórico do Agent Lab e Runtime foram mudados para `historyTail`, evitando materializar todos os registros em ferramentas de leitura.

**Estado dos testes:** escritos, ainda **NÃO EXECUTADOS**. Não há execução local Gradle/JUnit ou Android instrumented aprovada.

### Estado mais recente do GitHub Actions

- Agent Lab: run `44361347765`, job `135003272287` (Unit/Android) e `135003272941` (probe).
- Neural: run `44361348005`, job `135003272866`.
- Activity ledger: run `44361347702`, job `135003273083`.
- Todos falharam antes do primeiro step: `runner_id=0`, `runner_name=""`, `steps=[]`. Classificação: **RUNNER NÃO ALOCADO / TESTES NOT RUN**. Não é falha Kotlin demonstrada, tampouco aprovação.
- Não foi possível confirmar, pela conexão atual, se a causa é quota, budget, política ou provisionamento da conta/repositório. Não atribuir causa sem evidência da área privada de Actions/Billing.
- O workflow do livro de atividades existe, mas também não executa sem runner. Os 58 registros de implementação/documentação foram conciliados manualmente com o histórico do GitHub.

### Limites de conclusão que continuam abertos

- `KellvynRuntime.isReady()` continua `false`; o cérebro neural próprio ainda não está conectado ao chat principal.
- O executor de benchmark aceita inferência real fornecida, mas ainda não existe evidência de benchmark cognitivo executado contra um backend de produção.
- O verificador independente é injetado por código confiável; a validade do grader para os 28 cenários não está demonstrada.
- Não há Gradle/JUnit/Android Runner aprovado, build/APK, instalação em emulador ou teste físico no Poco F6.
- `MainActivity.java`, `.github/interface-lock.sha`, layout e efeitos visuais permaneceram intocados.

### Próximo gate único

**Restaurar a alocação de GitHub Actions e executar a compilação/testes existentes.** Não acrescentar outro subsistema grande antes de a primeira execução real ocorrer. Corrigir somente falhas comprovadas; depois fortalecer o verificador independente e medir o laboratório no Poco F6.

---

## 6.4 Telemetria, resiliência e qualidade do benchmark — revisão 18/11/2026, 18:08 UTC

Esta seção **atualiza e substitui os estados temporais antigos** nas seções 6.1, 6.2 e 6.3. O código atual foi conferido no HEAD `a313537`; as pesquisas e especificações foram atualizadas nos commits `5ca3a4d` e `346d57d`.

### CPU e limites de atribuição

- `KellvynAndroidResourceMonitor` coleta `Process.getElapsedCpuTime()`, `SystemClock.elapsedRealtime()` e `Runtime.availableProcessors()`, além de RAM disponível, heap, PSS, bateria e estado térmico.
- A coleta ocorre sob demanda no início/fim da execução; não há loop de polling.
- A variação percentual estima CPU usada pelo **processo Android inteiro** durante a janela. Se outra corrotina/ferramenta trabalhar no mesmo período, ela também entra na medição. Benchmarks sequenciais reduzem o ruído, mas não produzem atribuição exclusiva por agente.
- Não é medição de energia em joules/watts. Bateria e térmica são amostras, não média contínua.
- Referências oficiais de API: https://developer.android.com/reference/android/os/Process e https://developer.android.com/reference/android/os/SystemClock.

### Resiliência do registro de traces

- Persistência de observabilidade é best-effort: erro de arquivo/Keystore não interrompe o ciclo real do agente.
- O recorder rastreia: ainda não testado, saudável, degradado e recuperado com falhas anteriores.
- Última falha inclui código, traceId e tipo do evento que não foi persistido; o conteúdo do evento não é guardado.
- Uma gravação posterior pode recuperar a escrita, mas **não recompõe retroativamente o evento perdido**. O painel mantém a falha histórica e identifica o trace potencialmente incompleto.
- Testes escritos: falha de armazenamento e recuperação; estado inicial não testado; identificação do evento/trace perdido; execução de `KellvynAgent.run()` concluída mesmo quando o recorder falha.
- Limpeza de retenção: `KellvynRuntime.pruneAgentLabTracesBefore(cutoffMillis, ownerConfirmed)` exige confirmação explícita em código confiável, executa em I/O e remove apenas traces terminais antigos cuja cadeia completa foi validada. Não existe ferramenta de agente para essa operação.

### Avaliação dos 28 cenários — lacuna deliberadamente aberta

- O executor real usa uma inferência fornecida, perfil compilado e ferramentas filtradas pela política; registra chamadas solicitadas/executadas e violações. Não se autoavalia.
- Os 28 cenários ainda têm `requiredEvidence` e `prohibitedBehaviors` como rubricas em linguagem natural.
- O harness requer um verificador separado, mas **ainda não existe grader semântico independente e validado para todos os 28 cenários**.
- Não criar aprovação por busca ingênua de palavras nem usar o próprio especialista para confirmar sua resposta.
- Próximo trabalho de avaliação: classificar cada critério por tipo de evidência; criar oráculos determinísticos somente para fatos objetivos (resultado de ferramenta, hash/diff/artefato, teste, ordem/dependências); deixar critérios sem oráculo como REVIEW_REQUIRED/BLOCKED e encaminhar revisão humana/semântica independente.

### Testes e CI — estado em 18:06 UTC

- Testes escritos nesta rodada: CPU delta, recorder que falha e recupera, agente que termina mesmo com erro de trace, e identificação do trace/evento perdido.
- Nenhum desses testes foi executado por Gradle/JUnit/Android Runner.
- CI HEAD `a313537`:
  - Agent Lab run `44366062435`: jobs `135019965925` e `135019966794`;
  - Neural run `44366062495`: job `135019965751`;
  - Activity ledger run `44366062453`: job `135019966140`.
- Todos retornaram `runner_id=0`, `runner_name=""`, `steps=[]` antes do primeiro step. Classificação: **RUNNER NÃO ALOCADO / TESTES NOT RUN**; não prova erro Kotlin nem aprovação.
- O workflow de atividade automática continua instalado, mas não executa sem runner. O diário foi conciliado manualmente até 18:08 UTC no commit `8b5f79a`.

### O que ainda não está provado

- `KellvynRuntime.isReady()` continua `false`; cérebro neural não ligado ao chat principal.
- Não há execução de benchmark cognitivo com backend de produção aprovado.
- Não há Gradle/JUnit/Android instrumented aprovado, APK desta rodada, instalação em emulador ou teste físico no Poco F6.
- Não alterar `MainActivity.java`, `.github/interface-lock.sha`, layout, cores, barras, menus ou efeitos; permaneceram intocados.

### Próximo gate único

**Restaurar a alocação do GitHub Actions e executar as suítes existentes.** Não acrescentar outro subsistema grande antes de compilar e rodar os testes. Depois, implementar verificadores por evidência objetiva e revisão pendente nos critérios não automatizáveis; só então testar inferência neural aprovada e medir no Poco F6.

---

## 6.5 Avaliação com revisão pendente — revisão 18/11/2026, 18:22 UTC

Esta seção atualiza a lacuna metodológica mencionada em 6.4. O protocolo de estado foi implementado; a qualidade do verificador continua sendo uma pendência separada.

### Estados agora presentes no código

- `PASSED`: todos os critérios obrigatórios foram marcados como verificados por um verificador confiável e há referências de evidência.
- `REVIEW_REQUIRED`: nenhum erro/falha de política foi encontrado, mas um ou mais critérios foram encaminhados explicitamente para revisão. Esses critérios não contam na cobertura aprovada.
- `FAILED`: há violação, prova faltante sem encaminhamento, critério desconhecido, ou o mesmo critério marcado como verificado e pendente.
- `BLOCKED`: o caso não foi executado por perfil/catálogo/política/limite.

O verificador retorna dois conjuntos: `verifiedEvidence` e `reviewRequiredEvidence`. O harness valida que ambos pertencem ao caso, que não se sobrepõem e que todo critério obrigatório esteja em um dos dois conjuntos. Ausência de classificação fecha como FAILED.

### Agregação e persistência

- `KellvynSpecialistBenchmarkReport` contabiliza separadamente aprovados, review-required, falhos e bloqueados.
- `KellvynAgentLabOutcome.REVIEW_REQUIRED` é gravado por caso e no fim do experimento.
- A comparação baseline/candidato mostra contagem de aprovação e de revisão pendente separadamente.
- O estado sobrevive ao armazenamento cifrado; teste round-trip foi escrito.
- Toda execução, inclusive suíte bloqueada antes do primeiro caso (ex.: 29 casos para limite de 28), agora recebe evento `EXPERIMENT_FINISHED`.

### Testes escritos

- Critério não verificável encaminhado à revisão retorna REVIEW_REQUIRED e não PASSED/FAILED.
- Critério inexistente ou marcado simultaneamente como verificado e pendente retorna FAILED.
- Critério omitido sem prova e sem revisão retorna FAILED.
- A suíte termina como REVIEW_REQUIRED se um caso exigir revisão.
- REVIEW_REQUIRED persiste no trace AES-GCM.
- A suíte padrão de 28 casos totalmente verificados continua com 28 PASSED e 0 REVIEW_REQUIRED.

**Execução real:** todos esses testes estão escritos, mas ainda não executados, pois GitHub Actions continua sem alocar runner.

### Limite restante — importante

O protocolo não criou um grader semântico. As rubricas dos 28 casos continuam em texto livre e não há oráculo validado para todos. O verificador é fornecido por código confiável; o executor não dá a própria nota. O próximo desenho deve classificar critérios em oráculos determinísticos (ferramenta/artefato/hash/build/teste/ordem), revisão humana e revisão semântica independente. Não implementar correspondência de palavras como prova de competência.

### CI no HEAD 1e79ec6 — 18:22 UTC

- Agent Lab run `44368982497`, jobs `135030295544` e `135030295646`.
- Neural run `44368982477`, job `135030294742`.
- Activity ledger run `44368982450`, job `135030294933`.
- Todos falharam antes dos steps: `runner_id=0`, `steps=[]`. Classificação: **RUNNER NÃO ALOCADO / TESTS NOT RUN**, não falha Kotlin comprovada e não aprovação.

### Próximo gate único

Restaurar a alocação dos runners e executar as suítes Gradle/JUnit/Android. Não construir mais subsistemas grandes antes desse resultado. Depois da compilação real, implementar oráculos para critérios objetivos e revisão pendente para os restantes. Só então conectar a benchmark a inferência neural de produção e medir no Poco F6.

---

## 6.6 Kellvyn Engineering Lab — criado em 18/11/2026

### Artefatos

- docs/kellvyn/ENGINEERING_LAB_BLUEPRINT_2026-11-18.md: finalidade, separação das quatro camadas, arquitetura de destino, gates e limites.
- scripts/kellvyn-engineering-lab.py: comandos audit e log; usa apenas biblioteca padrão do Python.
- .github/workflows/agent-lab-activity-ledger.yml: após push, chama o gerador de auditoria/mapa e publica snapshots junto com o diário. Os diretórios de snapshots são ignorados pelo próprio trigger para evitar loop.
- docs/kellvyn/AGENT_CAPABILITY_RESEARCH_ADDENDUM_2026-11-18.md, seção 8.11: pesquisa Aider Repository Map, SWE-agent ACI, OpenHands SDK e LiteRT.

### O que o script deve produzir quando executado

- docs/kellvyn/audits/agent-lab-audit-<UTC>.md e .json: branch, HEAD, horário, fontes Kotlin, testes, símbolos, runners e hash de cada arquivo protegido.
- docs/kellvyn/audits/REPOSITORY_MAP.md: índice estático de símbolos Kotlin.
- docs/kellvyn/audits/LATEST.md: ponteiro para o snapshot mais recente.
- Comando log: anota pesquisa, decisão, implementação, teste, incidente ou handoff no diário com hora UTC, status, evidência e próximo passo.

**Estado real:** script e workflow foram gravados no GitHub. O script não foi executado por nenhum runner nesta sessão; não existe snapshot gerado e não alegar sintaxe/execução validada. O inventário estático também não compila nem testa o app.

### Pesquisa e decisões arquiteturais

- Aider: mapa estrutural compacto antes de editar; adaptar como navegação e depois adicionar dependências fonte→teste.
- SWE-agent: interface de ferramentas curta, com leitura limitada, busca compacta e validação de patch antes de aceitar.
- OpenHands: separar núcleo do agente, ferramentas, workspace e eventos; usar workspace isolado para alterações mutáveis.
- LiteRT: apenas hipótese para medir aceleração do modelo autoral, nunca substituição automática do cérebro.

### Próximo gate

Restaurar runner e executar primeiro python3 scripts/kellvyn-engineering-lab.py audit; revisar os snapshots e corrigir qualquer erro real. Depois executar Gradle/JUnit e instrumentados. Não criar mais ferramentas até esse gate passar. Em seguida, implementar workspace isolado de patches e oráculos de benchmark. Nenhuma alteração de interface ou pesos neurais foi feita.

CI observado nesta etapa:
- Runs activity-ledger: 44384564919, 44384628285, 44384702736.
- Todos os jobs encerraram com runner_id=0 e zero steps; portanto o script nem chegou a iniciar.

---

## 7. Protocolo obrigatório ao iniciar uma nova conversa

- Abrir este arquivo primeiro.
- Consultar o HEAD da branch no GitHub; não presumir que o commit aqui listado ainda é o mais recente.
- Ler os commits posteriores a este registro.
- Conferir o estado do código diretamente nos arquivos relevantes, não somente nos documentos.
- Identificar o próximo gate aberto e trabalhar nele. Não refazer itens marcados como implementados sem um defeito demonstrado.
- Antes de editar, declarar internamente: qual falha/lacuna será corrigida, qual é a menor alteração e como será comprovada.

## 8. Protocolo obrigatório antes de encerrar cada conversa

Atualizar **este arquivo** com:
- data/hora UTC e HEAD final confirmado;
- arquivos e funcionalidades realmente alterados;
- commits correspondentes;
- testes escritos versus testes executados, com comando, resultado e logs;
- build/APK/emulador/Poco F6: sim ou não, com evidência;
- bloqueios concretos e o próximo passo único;
- lista explícita de arquivos/componentes que permaneceram intocados.

Não basta dizer “continue” em uma mensagem do chat. O estado deve ser gravado no repositório antes de encerrar. Atualizar `STATUS.md` e o histórico longo somente quando houver detalhe técnico novo que justifique; não manter três resumos redundantes competindo entre si.

## 9. Texto curto para retomar em outro chat

> Continue o trabalho no repositório ar2233789-oss/kellvyn-ai-assistant, branch lab/kellvyn-intelligence-core-20261116. Leia este arquivo e confirme o HEAD real. A bancada de P&D externa para o desenvolvimento do cérebro está em tools/kellvyn-rnd-studio/ e docs/kellvyn/rnd-studio/. Não confundir com Agent Lab Android, Agent Creation Studio, cérebro, Agent Loop ou Orquestrador. Use worktree isolado por hipótese; registre pesquisa, baseline, candidato, teste e log; nunca declare PASS com evidência ausente ou desatualizada. Primeira hipótese aberta: cobrir lacuna do checkGradients do KN-003, que só amostra parâmetros com gradiente analítico não nulo. Não mexer em pesos, interface, MainActivity ou integração com chat enquanto os testes e gates não sustentarem a mudança.

## 10. Kellvyn R&D Studio — bancada externa de P&D do cérebro (18/11/2026, 20:21 UTC)

**HEAD de implementação confirmado:** `4598cab4e831dd3e1f9d96c0c2158de8e77ff566` — `feat(kellvyn): establish brain research and development studio`. Este checkpoint documental acompanha a implementação; confirme HEAD novamente ao retomar.

### Finalidade e separação

- **Kellvyn R&D Studio:** ferramenta de engenharia no repositório para o assistente estudar código, registrar pesquisa, planejar experimentos falsificáveis, isolar mudanças e exigir evidência de testes.
- **Não é** tela do aplicativo, Agent Lab Android, Agent Creation Studio, novo cérebro, novo modelo ou substituto do orquestrador.
- O cérebro fornece a inferência; `KellvynAgent` é o loop operacional; `KellvynOrchestrator` coordena delegações. Continuam componentes distintos.
- Não recriar os 14 perfis, traces, benchmarks nem ferramentas preexistentes.

### Implementação gravada

- `tools/kellvyn-rnd-studio/studio.py`: CLI Python stdlib-only.
- `tools/kellvyn-rnd-studio/test_studio.py`: testes da ferramenta usando repositórios Git temporários.
- `README.md`: instalação/uso, limites e comandos.
- `docs/kellvyn/rnd-studio/BRAIN_BASELINE_2026-11-18.md`: mapa conceitual do cérebro e sequência inicial de estudo.
- `EXPERIMENT_PROTOCOL.md`, `research-ledger.jsonl` e catálogo de experimentos.

Operações da CLI:
- `map`: indexa fontes Kotlin, símbolos, imports, hashes e possíveis arquivos de teste; inventário estático, não prova integração.
- `task create`: cria branch `kellvyn-rnd/<id>` e Git worktree isolado fora da árvore do repositório.
- `task test`: executa somente uma das suítes allowlisted: `unit`, `assemble` ou `instrumented`, através do Gradle Wrapper, com timeout limitado.
- `task check`: verifica diff/whitespace, arquivos protegidos e evidência do teste. Evidência expira logicamente quando HEAD, fingerprint do código ou log mudam.
- `research` e `experiment create/record`: registram achados, hipóteses e métricas com timestamp UTC.

### Validação da própria bancada

- `python -m py_compile` no script: executado e aprovado no ambiente local de desenvolvimento.
- 6 testes Python da CLI: executados em repositórios Git temporários; 6/6 passaram (worktree isolado, arquivo protegido, whitespace, teste ausente, evidência obsoleta após mudança e allowlist de suítes).
- Isso valida controles da CLI em fixtures Git; não executa código Android/Kotlin.
- O run GitHub Actions `44390151347`, para o commit `4598cab`, terminou failure antes de qualquer step: `runner_id=0`, `steps=[]`, sem runner atribuído. Gradle/JUnit/Android continuam **NOT RUN** por CI.

### Primeiro estudo real do KN-003

A leitura de `KellvynTransformerLanguageModel.checkGradients()` e `KellvynTransformerLanguageModelTest.multiHeadTransformerGradientsMatchFiniteDifferences()` revelou uma lacuna de cobertura: as coordenadas candidatas são filtradas por `abs(gradient) > 1e-8`; grupos sem gradiente não nulo não recebem amostra. O teste atual usa uma configuração/seed, duas cabeças e no máximo duas coordenadas por grupo.

Isto **não prova erro na matemática da retropropagação**. Prova que o verificador numérico não testa coordenadas com gradiente analítico zero, então poderia deixar passar um caminho de gradiente inteiro omitido. A hipótese `brain-gradient-coverage-001` foi registrada como PLANNED. Proposta: amostrar deterministicamente coordenadas de todos os grupos, incluindo zero e não zero, abranger diferentes headCount/seeds/comprimentos de contexto e incluir EOS; medir erro absoluto/relativo por grupo. Nenhum código neural/peso foi alterado neste estudo.

### Próximo passo único

Abrir uma tarefa isolada `brain-gradient-coverage-001` no clone local, executar a baseline de testes neurais, construir a nova cobertura por diferenças finitas, validar com Gradle e só então aceitar ou rejeitar a alteração. Se runner continuar sem alocação, classificar como NOT RUN e não editar a implementação do cérebro às cegas.

### Proteções / estado

- Intocados nesta implementação: `MainActivity.java`, `.github/interface-lock.sha`, UI/layout/efeitos, modelos/pesos/checkpoints neurais, `KellvynRuntime`, `KellvynAgent`, `KellvynOrchestrator` e os módulos do Agent Lab Android.
- Não há APK novo, teste em emulador, execução neural de produção ou validação no Poco F6.
- A R&D Studio não faz commit/push/merge automático, não treina pesos por conta própria e não substitui isolamento de processo (Git worktree é isolamento de árvore Git, não sandbox de sistema operacional).

### Refinamento da hipótese KN-003 — 18/11/2026, 20:32 UTC

A pesquisa de métodos de gradcheck refinou `brain-gradient-coverage-001`: além de coordenadas selecionadas em cada um dos 14 arrays, testar uma direção determinística normalizada por array; usar diferenças centrais; calibrar epsilon em float32 por estabilidade; tratar explicitamente gradientes ambos-zero; incluir controle negativo que zera deliberadamente uma família. Referências e decisão estão no `research-ledger.jsonl` e no `GRADIENT_CHECK_AUDIT_2026-11-18.md`. Continua PLANNED / NOT RUN; nenhum código neural foi alterado.


## 11. R&D Studio — Brain + Agents + Integration (18/11/2026)

A bancada externa atende três frentes:
- Brain R&D: tokenizer, KN-001/002/003, treinamento, pesos, inferência e protocolo.
- Agent R&D: loop KellvynAgent, KellvynOrchestrator, 14 perfis, políticas, planner, traces e avaliação.
- Integration R&D: inferência, parser KELLVYN/1, loop do agente, ferramentas, delegação e verificador.

tools/kellvyn-rnd-studio/studio.py agora declara três tracks, mapeia fontes/testes e oferece suítes Gradle focadas brain, agents e integration. As suítes unit, assemble e instrumented continuam disponíveis. Cada tarefa registra scope e exige a suíte correspondente por padrão.

A suíte integration é um conjunto de testes de contrato/unitários; não prova conexão do chat ao cérebro real. KellvynRuntime.isReady() continua false.

Baselines:
- docs/kellvyn/rnd-studio/AGENT_BASELINE_2026-11-18.md
- docs/kellvyn/rnd-studio/BRAIN_AGENT_INTEGRATION_BASELINE_2026-11-18.md
- docs/kellvyn/rnd-studio/AGENT_EVALUATION_PROTOCOL.md

Hipóteses:
- agent-delegation-duration-001: o orquestrador registra DELEGATION_FINISHED com durationMillis nulo no caminho normal e não mede cancelamento.
- brain-agent-contract-001: validar KELLVYN/1 -> agent loop -> tool/delegation -> verifier, sem confundir engine fake com cérebro real.
- brain-gradient-coverage-001 permanece na track brain.

Pesquisa sobre AgentBench, tau-bench e avaliação de traces foi adicionada ao research ledger. As fontes orientam o método; nenhum código, peso ou serviço externo foi incorporado.

A árvore Git confirma 21 arquivos-fonte e 23 classes de teste planejados para Brain/Agents. O mapa ainda precisa ser executado no clone com Python; testes da CLI foram escritos, mas ainda não executados nesta sessão. Gradle/JUnit e inferência real seguem NOT RUN/BLOCKED pelo runner sem alocação.


## 14. Estado consolidado dos candidatos R&D (18/11/2026, 21:50 UTC)

A R&D Studio possui três tracks. Há dois candidatos técnicos em branches experimentais separadas; não os misturar nem fazer merge sem execução real:

### Brain — KN-003 gradient coverage
- Branch: kellvyn-rnd/brain-gradient-coverage-001
- Commit documentado: a580b6577ed27148decbeae3c0cd7a620a0c41c7
- Implementação: amostra coordenadas zero e não-zero nos 14 grupos nomeados, expõe contagens por grupo e restaura parâmetros em finally. O caminho quente arrays() permaneceu sem alocações novas.
- Testes escritos: todos os 14 grupos; headCount 1, 2, 4 com seeds diferentes.
- Run 44405313920: runner_id=0 e steps=[] nos dois jobs. IMPLEMENTED_NOT_RUN.
- Pendências: executar baseline/candidato, calibrar tolerância float32, directional check e negative control.

### Agents — specialist delegation duration
- Branch: kellvyn-rnd/agent-delegation-duration-001
- Commit documentado: 7a0696d76f35fdb1ae2ec2ad3bd144fab6173953
- Implementação: System.nanoTime injetável, duração terminal para sucesso/falha/bloqueio/cancelamento.
- Quatro testes determinísticos escritos; nenhum sleep.
- Run 44404198275: runner_id=0 e steps=[] nos dois jobs. IMPLEMENTED_NOT_RUN.
- Não fazer merge até executar a suíte focada agents.

### Regra de continuidade

A branch lab continua sendo a base de integração/documentação. As branches kellvyn-rnd/* são experimentos independentes. Ao retomar: confirmar cada HEAD, executar baseline/candidato quando houver runner, registrar evidência, revisar diff e somente então promover por cherry-pick/merge. Nunca tratar commit existente ou teste escrito como teste aprovado.

A frente Integration permanece como hipótese de contrato entre adapter, parser KELLVYN/1, loop, ferramenta, delegação e verificador. Não afirmar integração de chat: KellvynRuntime.isReady() continua false.


## 16. Estado consolidado Brain / Agents / Integration (18/11/2026, 22:05 UTC)

A R&D Studio externa possui três frentes implementadas no utilitário, e três candidatos em branches isoladas. A branch lab é a base de integração documental; não contém patches candidatos.

| Track | Branch candidata | Objetivo | Estado |
|---|---|---|---|
| Brain | kellvyn-rnd/brain-gradient-coverage-001 | cobertura numérica nos 14 grupos KN-003 | IMPLEMENTED_NOT_RUN |
| Agents | kellvyn-rnd/agent-delegation-duration-001 | duração monotônica de delegação em todos os caminhos | IMPLEMENTED_NOT_RUN |
| Integration | kellvyn-rnd/brain-agent-contract-001 | KELLVYN/1 -> Agent -> ferramenta -> especialista -> UNTRUSTED | IMPLEMENTED_NOT_RUN |

### Evidência e bloqueios
- Brain: run 44405313920 — runner_id=0, steps=[].
- Agents: run 44404198275 — runner_id=0, steps=[].
- Integration: run 44407125400 — runner_id=0, steps=[].
- Em nenhum caso o Gradle começou. Isso não demonstra falha nem aprovação do Kotlin.

### Limites não negociáveis
- Não fundir nem promover nenhum candidato sem executar a suite apropriada.
- Os testes de Integration usam ProtocolScriptedEngine; provam o contrato do parser/agent/orchestrator, não raciocínio do checkpoint neural.
- KellvynRuntime.isReady() continua false; chat ainda não ligado ao cérebro.
- Não alterar pesos, interface, MainActivity ou semântica de segurança durante estas experiências.
- Ao retomar: confirmar HEAD das três branches, rever diffs e evidência; executar os gates quando o runner voltar.


## 21. Consolidação do R&D Studio e do bloqueio GitHub Actions (18/11/2026, 22:35 UTC)

### Estado das quatro branches
- Base lab: 44c54ad41f60d5abeee07c8031b1f499bdd12306.
- Brain candidate: kellvyn-rnd/brain-gradient-coverage-001, HEAD db943167b82be2b8ca95674ecca81f612f8e3bb2.
- Agents candidate: kellvyn-rnd/agent-delegation-duration-001, HEAD 9181f31eb59b5f8c953410f22af89cbf6ba59ba5.
- Integration + Studio candidate: kellvyn-rnd/brain-agent-contract-001, HEAD 8630162bd7d6586663bcb386ad18ebef63e38e46.

Nenhum candidato foi fundido na branch lab.

### Últimas tentativas CI — 22:31 UTC
- lab run 44412348657;
- agents run 44412360596;
- brain run 44412372652;
- integration run 44412382401.
Todos os jobs, inclusive o probe ubuntu-slim e o job R&D Studio Python, apresentam runner_id=0, runner_name vazio e steps=[]. Não chegou a executar checkout, Python, Gradle ou Kotlin.

O workflow agora ignora alterações apenas em docs/kellvyn/rnd-studio/**. O job Python permanece ativo quando tools/kellvyn-rnd-studio/** muda, evitando CI acionado só por diário e relatórios.

### Estado da oficina Python
A versão anterior foi executada localmente em 22:11 UTC: py_compile aprovado e 13/13 testes aprovados (0,626 s). Depois disso foram adicionados três testes: executor de tarefa com wrapper simulado, registro correto de saída não zero e mapeamento de testes por símbolos. A versão atual soma 16 testes, mas os três novos ainda NOT RUN e o repo_map atualizado ainda não foi revalidado por py_compile. Não afirmar 16/16.

### Diagnóstico do runner
O repositório é privado. A página pública do GitHub indicava Actions operacional em 18/11; a degradação pública de início dos workflows em 16/11 foi resolvida às 16:26 UTC, antes das tentativas noturnas. O padrão runner_id=0/steps=[] acontece antes do workflow iniciar.
Há hipótese prioritária de bloqueio de conta/orçamento Actions. A documentação oficial explica que, em repositórios privados, runners hospedados consomem compute incluído e que budgets com Stop usage when budget limit is reached podem bloquear novos runners. Relatos comunitários descrevem assinatura igual, incluindo budget Actions de US$ 0 com Usage blocked. Isso não confirma que seja a configuração desta conta.
Ver docs/kellvyn/rnd-studio/GITHUB_ACTIONS_RUNNER_DIAGNOSTIC_2026-11-18.md. A conta precisa ser inspecionada pelo proprietário em Settings → Billing → Budgets and alerts. Não adicionar cartão, aumentar orçamento ou habilitar gasto pago sem autorização expressa.

### Limites de integração
- Brain: candidato de cobertura numérica em 14 grupos KN-003, ainda sem Gradle.
- Agents: duração monotônica nos caminhos de sucesso/falha/bloqueio/cancelamento, quatro testes determinísticos escritos, ainda sem Gradle.
- Integration: teste atravessa parser KELLVYN/1, agente, orquestrador, ferramenta READ_ONLY e handoff UNTRUSTED usando ProtocolScriptedEngine. Não usa pesos treinados e ainda não chama KellvynIndependentVerifier.
- KellvynRuntime.isReady() permanece false; o chat ainda não está integrado ao cérebro real.

## 22. Retomada e endurecimento da R&D Studio (18/11/2026, 23:40 UTC)

### HEADs confirmados
- Branch lab: `d8784b02401889d409a2831c27013ff186c2a765`
- Brain candidate: `kellvyn-rnd/brain-gradient-coverage-001` — `db943167b82be2b8ca95674ecca81f612f8e3bb2`
- Agents candidate: `kellvyn-rnd/agent-delegation-duration-001` — `9181f31eb59b5f8c953410f22af89cbf6ba59ba5`
- Integration + Studio candidate: `kellvyn-rnd/brain-agent-contract-001` — `9da1f906e4d147dfac81f449b6f921ff6d798270`

### Alteração concreta na Studio
A revisão estática identificou que `task_test` confiava em qualquer `android/gradlew` executável existente na worktree. Um wrapper substituído poderia retornar zero sem executar Gradle e gerar evidência falsa de aprovação.

Commit `9da1f906e4d147dfac81f449b6f921ff6d798270` corrige isso na branch experimental:
- registra hashes do `gradlew`, `gradlew.bat` (quando presente), `gradle-wrapper.jar` e `gradle-wrapper.properties` no commit-base da tarefa;
- recusa iniciar testes se esses arquivos divergirem;
- verifica novamente após o processo e marca `INTEGRITY_FAILURE` se o wrapper mudar;
- inclui integridade do wrapper no gate de `task_check`;
- adiciona teste de regressão para wrapper adulterado.

A suíte `test_studio.py` agora contém 17 testes escritos. **Os 17 estão NOT RUN** nesta versão. A versão antiga com 13 testes foi aprovada localmente anteriormente; essa aprovação não cobre o código atual. `py_compile` também precisa ser repetido.

### CI observado após as alterações
- Lab run `44413219991`, SHA `d8784b0`: três jobs falharam antes dos steps; sem logs e sem execução Python/Gradle.
- Integration/Studio run `44422679919`, SHA `9da1f90`: jobs `135219431722`, `135219432080` e `135219432199` terminaram em failure com `steps=null` e `logs_url=null`. A suíte Python de 17 testes não executou.
- Brain run `44412372652` e Agents run `44412360596`: continuam sem execução de steps.
- Classificação: bloqueio de alocação/execução do runner, causa específica não confirmada. Não reexecutar workflows repetidamente; aguardar inspeção de billing/Actions pelo proprietário ou evidência nova do GitHub Support.

### Próximos gates
1. Recuperar execução Python da Studio e rodar `py_compile` + 17 testes.
2. Rodar baseline/candidato Brain com gradcheck por grupo; ainda faltam teste direcional seeded e negative control.
3. Rodar suíte Agents, incluindo duração monotônica nos quatro caminhos.
4. Executar Integration; o teste atual usa `ProtocolScriptedEngine`, não checkpoint neural treinado, e não chama o verificador independente.
5. Só depois revisar diffs e promover candidatos. Nenhuma branch candidata foi mesclada.

### Limites
`KellvynRuntime.isReady()` continua `false`; cérebro neural não está ligado ao chat de produção. Não há APK, validação em emulador ou teste no Poco F6 desta rodada. Interface, `MainActivity`, pesos e checkpoints seguem intocados.


---

## 11. Workbench externa do assistente — 19/11/2026

**Esta seção atualiza a prioridade operacional anterior.** O foco imediato é equipar e usar a bancada externa que permite ao assistente pesquisar, inspecionar, experimentar, depurar, testar e produzir evidências para o trabalho dos agentes. Não confundir essa bancada com o Agent Lab dentro do APK, nem iniciar agora alterações no cérebro, nos perfis dos agentes ou na interface.

- Entrada única: `tools/kellvyn-workbench/workbench.py`.
- Blueprint: `docs/kellvyn/WORKBENCH_BLUEPRINT_2026-11-19.md`.
- Diário append-only com cadeia SHA-256: `docs/kellvyn/workbench-ledger.jsonl`.
- Checkpoint humano/JSON gerado pelo comando `checkpoint`: `docs/kellvyn/WORKBENCH_STATE.md` e `WORKBENCH_STATE.json`.
- Saídas ignoradas pelo Git: `artifacts/workbench/` (logs, runs, renders, snapshots e dashboard).
- Ferramentas integradas: auditoria de repositório e toolchain, sessões, checkpoints, execução com timeout e logs, triagem de debugger, worktrees do R&D Studio existente, visual diff, Playwright web audit, ADB/Android smoke e pipeline Blender com luzes/sombras/material/câmera/render/export/progresso.
- A workflow `.github/workflows/kellvyn-workbench.yml` valida Python, testes, scripts Android/Web e integridade do ledger; o render Blender é acionado manualmente.

**Evidência local antes da integração:** 13/13 testes Python da Workbench aprovados; `py_compile` aprovado para workbench, visual diff e pipeline Blender. O container tinha Git, Python 3.13.5, Java 21, Node 22, FFmpeg, Pillow e Playwright; não tinha Gradle, ADB ou Blender. Portanto Android smoke, build Gradle e render real permanecem NÃO EXECUTADOS/BLOCKED até que um host/runner com essas ferramentas esteja disponível.

**Regra de sessão:** iniciar com `doctor`, `verify-ledger` e confirmação do HEAD; registrar pesquisa/decisões/execuções; criar worktree antes de mudanças experimentais; preservar stdout/stderr, hashes e manifests; nunca transformar NOT_RUN/BLOCKED em PASS; gerar checkpoint antes de encerrar. Não recriar a lógica do R&D Studio — a Workbench o chama.

**Próximo passo:** confirmar no GitHub o commit desta integração; executar a workflow quando Actions alocar runner; corrigir apenas falhas reproduzidas. Não alterar `MainActivity.java`, `.github/interface-lock.sha`, layout, cérebro, agentes do APK ou modelos como parte desta bancada.


## 11.1. Checkpoint operacional da Workbench — 19/11/2026

**HEAD confirmado:** `da26e3ed1d692fa775ef03181a63ee71820f49b2` na branch `kellvyn-rnd/agent-trace-duration-port-20261118`.

### Commits incorporados
- `fb7928c34e7335e376f5df694adf24d5f5ce7583` — bancada externa com auditoria, workspaces, execução com timeout/logs, debugger, diff visual, web/Android e pipeline criativo.
- `2e3c332301937640f3ae4ab3f845c97474f0a228` — detecção Playwright Node corrigida.
- `da26e3ed1d692fa775ef03181a63ee71820f49b2` — teste de regressão da dependência Node Playwright.

### Evidência
- 14/14 testes Python locais aprovados.
- `py_compile` aprovado para `workbench.py`, `visual_diff.py` e `creative/blender_pipeline.py`.
- Workbench e seus testes existem no branch.
- Gradle, ADB e Blender não existem neste host. Build Android, teste AVD e render Blender real permanecem NÃO EXECUTADOS/BLOQUEADOS.
- O Playwright Python e o Playwright Node são detectados separadamente; o auditor web usa o pacote Node.

### Próximo passo
Executar a workflow `.github/workflows/kellvyn-workbench.yml` em runner que aloque. Ler os logs/artifacts, corrigir falhas reais e expandir a bancada somente depois de validar a execução. Não alterar cérebro, agentes do APK ou interface nesta frente.


## 12. Continuidade — laboratório poliglota v2 (19/11/2026)

**Foco atual:** preparar primeiro o ambiente completo de pesquisa e engenharia. Não desviar para implementar funcionalidades do cérebro, agentes ou interface até que as ferramentas e os gates de desenvolvimento necessários estejam disponíveis.

- Especificação da arquitetura-alvo em `docs/kellvyn/LABORATORY_ARCHITECTURE_V2_2026-11-19.md`.
- Branch isolada `kellvyn-rnd/polyglot-lab-foundation-20261119`, baseada no commit `ce0d7170c576e50fcb852d27aedfd4cae3d45caa`, PR #25.
- Implementado na Workbench: matriz de toolchains por domínio; suites TypeScript/TSX para typecheck, Vitest, ESLint e Vite; mapa semântico TS/TSX via TypeScript Compiler API; testes do mapa com fixtures; higiene básica de ambiente de subprocesso, redaction e encerramento de process group em timeout POSIX.
- Workflow Workbench usa Bun 1.4.5 e instalação congelada por `bun.lock`; testes do mapa e quatro gates TS/TSX entram no CI.
- Manifesto da R&D Studio corrigido: o arquivo atual tem 20 métodos `test_*`; o resultado anterior 13/13 pertence a snapshot mais antigo. Os 20 do snapshot atual ainda precisam ser executados.
- Último CI observado nesta branch: run `44554753701`, job `135667679375`, terminou em failure com `steps=[]`; classificar como runner/execução NOT RUN, não como falha de compilação.
- O mapa semântico TS/TSX é implementado, mas não foi executado; análise semântica Kotlin/Java, Tree-sitter multilinguagem, DAG durável, sandbox descartável, tracing de agentes, animation/performance lab Android e benchmark cognitivo ainda não estão implementados.
- Não mesclar PR #25. Não instalar runner no Poco F6, não adicionar serviços pagos e não modificar MainActivity/interface.


---

## 18. Continuidade — laboratório poliglota v2 (19/11/2026)

**Branch ativa:** `kellvyn-rnd/polyglot-lab-foundation-20261119`  
**PR:** #25 — DRAFT, IMPLEMENTED_NOT_RUN  
**HEAD-base desta atualização:** `b4096c30ec0b02c4ccbb5762758c74028ab757e8`  
**Arquitetura:** `docs/kellvyn/LABORATORY_ARCHITECTURE_V2_2026-11-19.md`

### O que entrou na bancada
- Doctor/Toolchain matrix por domínio: Kotlin/Java/Android SDK, TS/TSX, Python, shell/workflows, ADB/dispositivo, visual/mídia e candidatos de sandbox.
- Suites de projeto TS/TSX (typecheck, Vitest, ESLint, Vite build) selecionando Bun pelo `bun.lock`; CI fixa Bun 1.4.5 e usa instalação frozen.
- Mapa semântico TS/TSX pela TypeScript Compiler API: exports/declarations, imports locais/externos/não resolvidos, chamadas, referências e arquivos de teste.
- Executor com allowlist de ambiente filho, rejeição de nomes de variáveis sensíveis, redaction de padrões comuns e encerramento de grupos de processos POSIX em timeout.
- Kotlin LSP preflight: launcher via PATH ou `KOTLIN_LSP_EXECUTABLE`; runtime JDK25 via `KOTLIN_LSP_JAVA_HOME`; não altera JDK17 do Gradle. Detecção não equivale a importação Android ou análise semântica aprovada.
- Laboratório Android `android-animation-audit`: captura screenrecord do app já em primeiro plano por serial ADB explícito; recolhe gfxinfo/framestats, PSS, bateria, estado térmico e display; extrai quadros com FFmpeg; gera SHA-256 do vídeo e de cada frame; rotula cenário, confere foreground ao final e salva checkpoint mínimo em falha pós-preflight. Não instala APK, não abre o app, não toca na UI nem muda configurações.
- O relatório e o diário da Workbench usam `CAPTURED` apenas para coleta completa; `quality_assessment=NOT_EVALUATED`. Isso não declara fluidez ou fidelidade visual. O rótulo do cenário fica gravado; o alvo é comparado como token de pacote exato, e o foco é verificado antes/depois (sem monitoramento contínuo entre verificações). `PARTIAL` e `BLOCKED` continuam distintos.

### Contagem de testes e evidência
- Workbench principal: 31 métodos Python.
- Parser/contratos do coletor Android: 16 métodos Python.
- R&D Studio: 20 métodos Python.
- Mapa TS: 2 casos Node.
- Total inventariado: 67 métodos Python + 2 casos Node. Todos são testes escritos, não resultados de execução.
- Probes isolados antigos: mapa TS 2/2 e helpers de executor 4/4 em harness separado. Não equivalem às suites do checkout e não validam Android.
- Último CI para o HEAD-base desta atualização: run `44575908967`, job `135743226910`. O job terminou em failure com `steps=[]`, sem logs. Classificação correta: **RUNNER_ALLOCATION_BLOCKED / NOT_RUN**, não falha comprovada de compilação.
- A captura Android também permanece NOT_RUN: nenhum ADB/AVD autorizado foi usado nesta sessão.

### Limites que permanecem
- Sem sandbox descartável com isolamento real de filesystem/rede e limites de CPU/RAM/PIDs. Git worktree e detecção de Docker/Podman não equivalem a sandbox.
- Análise semântica Kotlin ainda não implementada; só preflight do Kotlin LSP. Experimento `kotlin-semantic-index-001` permanece PLANNED, exigindo JDK25 separado para LSP e JDK17 no build do app.
- Sem DAG durável integrado entre Workbench e R&D Studio, tracing OpenTelemetry completo ou grafo semântico multi-linguagem unificado.
- O coletor de animação ainda não automatiza cenário de chuva/raios/combinações; não integra Perfetto FrameTimeline nem Macrobenchmark; não substitui revisão visual com baseline.
- O cérebro neural continua experimental/desconectado do chat de produção; não existe checkpoint conversacional aprovado nem avaliação cognitiva independente concluída.

### Próximas tarefas, em ordem
1. Manter PR #25 em draft; executar suites Python/Node/TS no SHA exato assim que o runner iniciar steps e preservar logs/artefatos.
2. Revisar a implementação do coletor Android; validar parsers em testes locais confiáveis e realizar captura em AVD autorizado, depois separar medição física do Poco F6.
3. Implementar o contrato comum de adaptadores e vincular Workbench ↔ R&D Studio por taskId, commit/diff hash, runId e evidência.
4. Executar experimento Kotlin semântico comparando Analysis API Standalone e Kotlin LSP, com JDK25 do LSP separado do JDK17 do Gradle; medir precisão, cold start, warm latency e RSS.
5. Desenvolver sandbox descartável e só então executar código experimental não confiável.
6. Avançar para Perfetto/Macrobenchmark, replay de cenários de animação, tracing OTel e DAG de engenharia durável.
7. Preservar `MainActivity.java`, `.github/interface-lock.sha`, layout e efeitos visuais. Nenhuma alteração de interface nesta frente.

**Regra de continuidade:** antes de qualquer alteração, confirmar o HEAD atual no GitHub e ler commits posteriores. Não reiniciar a auditoria nem voltar ao PR #24 como foco; a frente ativa permanece PR #25.
