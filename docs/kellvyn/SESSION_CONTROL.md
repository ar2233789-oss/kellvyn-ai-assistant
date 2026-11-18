# KELLVYN — CONTROLE DE SESSÃO E CONTINUIDADE

**Este é o ponto único de entrada para qualquer nova conversa de trabalho.**
Leia este arquivo antes de alterar código. Depois confirme o HEAD real no GitHub. Não dependa da memória do chat.

**Última conferência:** 18/11/2026, 15:58 UTC  
**Repositório:** `ar2233789-oss/kellvyn-ai-assistant`  
**Branch de laboratório:** `lab/kellvyn-intelligence-core-20261116`  
**HEAD confirmado antes desta atualização:** `b14f58193b5105479be5d3c34c8ada17d1610ae6`  
**Commit:** [b14f581 — state current KN-003 limits and readiness](https://github.com/ar2233789-oss/kellvyn-ai-assistant/commit/b14f58193b5105479be5d3c34c8ada17d1610ae6)

> Se o HEAD atual for diferente, primeiro leia os commits posteriores e atualize este estado. Este documento não autoriza sobrescrever trabalho posterior.

---

## 1. Objetivo ativo — não desviar

O foco de trabalho solicitado mais recentemente é o **Laboratório dos Agentes / Agent Creation Studio**: deixar a oficina interna avançada, funcional, verificável e eficiente em memória, CPU e bateria.

Não trocar esse objetivo pelo desenvolvimento genérico do cérebro, nem voltar para a interface, nem ampliar módulos só para produzir mais commits. O cérebro e o laboratório dos agentes são frentes diferentes. O usuário corrigiu explicitamente essa confusão.

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
- Adicionado job `runner-allocation-probe` ao Agent Lab; run `44338054696`, jobs `134920052636` e `134920052769`.
- Atualizada `AGENT_TOOL_AVAILABILITY_2026-11-18.md`: o inventário correto tem seis módulos, incluindo `web-research-wikipedia` e `agent-creation`. A pesquisa Web disponível é somente `wikipedia_search`, limitada à Wikipédia PT e autorizada apenas ao Pesquisador com Acesso à Web ativado.
- Criado `AGENT_CAPABILITY_RESEARCH_ADDENDUM_2026-11-18.md`, com pesquisa de OpenAI Agents/evals/tracing, Cursor subagents/planning, Replit lifecycle/testing, Android Studio device testing, Nano Banana e LiteRT.
- Corrigido `LEARNING_AND_TRAINING.md`: não oferece mais a alternativa contraditória de usar pesos pré-treinados/LoRA externos. A regra de cérebro do zero prevalece.
- Atualizado `NEURAL_MODEL_DECISIONS.md` com uma síntese atual do estado KN-003 e seus limites quantitativos.
- Nenhuma classe Kotlin/Java, perfil, política, teste ou arquivo visual foi alterado nessa retomada.

### Evidência exata do bloqueio GitHub Actions
- O endpoint detalhado de jobs para o run `44338054696` mostra, em ambos os jobs, `runner_id=0`, `runner_name=""`, `steps=[]`, falha em aproximadamente 2 segundos.
- A mesma ausência de atribuição ocorreu no run isolado `44337962674`, job `134919715612`.
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
**Obter o motivo exato da falta de compute/atribuição do runner pela tela privada de Billing/Actions e restabelecer uma execução mínima com logs.**

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

> Continue o trabalho da Kellvyn no repositório `ar2233789-oss/kellvyn-ai-assistant`, branch `lab/kellvyn-intelligence-core-20261116`. Antes de qualquer alteração, leia `docs/kellvyn/SESSION_CONTROL.md`, confirme o HEAD real e confira os commits posteriores. O foco ativo é o Laboratório dos Agentes / Agent Creation Studio. Não refaça o que já existe, não mexa na interface protegida e não acrescente funcionalidades enquanto o CI estiver bloqueado, salvo correção necessária e comprovável. Continue do próximo gate descrito no registro e atualize esse mesmo arquivo antes de encerrar.
