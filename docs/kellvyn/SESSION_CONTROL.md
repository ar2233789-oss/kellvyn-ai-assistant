# KELLVYN — CONTROLE DE SESSÃO E CONTINUIDADE

**Este é o ponto único de entrada para qualquer nova conversa de trabalho.**
Leia este arquivo antes de alterar código. Depois confirme o HEAD real no GitHub. Não dependa da memória do chat.

**Última revisão:** 18/11/2026, 18:22 UTC  
**Repositório:** `ar2233789-oss/kellvyn-ai-assistant`  
**Branch de laboratório:** `lab/kellvyn-intelligence-core-20261116`  
**HEAD de código auditado:** `1e79ec6f72a0524bf53b5227c60c98b44886c9c9`  
**Documentação técnica:** pesquisa `559b8d7`; especificação `c435e8f`.  
**Livro de atividades:** `c44d824e91f2a991d92eccb6a946e004ca278c5c` — 92 entradas de implementação/documentação reconciliadas manualmente.

> Esta revisão compara dez commits posteriores ao estado das 18:08 UTC, confirma a classificação REVIEW_REQUIRED no harness, trace e comparador, e verifica os workflows às 18:22 UTC. Confirme o HEAD real antes de qualquer edição; esta atualização documental criará novo commit.

> Se o HEAD atual for diferente, primeiro leia os commits posteriores e atualize este estado. Este documento não autoriza sobrescrever trabalho posterior.

---

## 1. Objetivo ativo — não desviar

O foco ativo é o **Agent Lab / laboratório de engenharia de agentes**: ambiente para acompanhar execuções, executar benchmarks reais, comparar perfis candidatos, medir custo de recursos e investigar falhas.

Não confundir três camadas:
- **Cérebro Kellvyn:** backend neural próprio e raciocínio principal; permanece uma frente separada.
- **Agent Creation Studio:** catálogo, compilação, revisão, versionamento, aprovação e rollback dos 14 perfis.
- **Agent Lab:** execução, traces, testes, experimentos, comparação e telemetria da engenharia dos agentes.

Não voltar para a interface nem recriar os módulos de perfis já existentes. O objetivo é evoluir a bancada e validar o que já foi implementado, não acumular commits sem testes.

O aplicativo final continua sendo Android nativo, offline-first, para Poco F6 / HyperOS, 8 GB de RAM. Não usar WebView na versão final, não depender de hospedagem externa ou serviços pagos.

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

> Continue o Agent Lab no repositório `ar2233789-oss/kellvyn-ai-assistant`, branch `lab/kellvyn-intelligence-core-20261116`. Leia SESSION_CONTROL, confirme HEAD e commits posteriores. Não refaça perfis, Agent Creation Studio, orquestrador, trace, executor ou estado REVIEW_REQUIRED; já existem. Os testes cobrem traces, CPU/recorder, execução do agente apesar de falha de gravação, revisão pendente e comparação, mas nenhum foi executado. Próximo gate: destravar runner e executar Gradle/JUnit/Android; não empilhar módulos antes disso. Depois classificar rubricas e criar oráculos objetivos/verificação independente, sem autoavaliação nem busca ingênua de palavras. Não mexer na interface protegida; não alegar testes sem logs; reconciliar o diário e este arquivo ao encerrar.
