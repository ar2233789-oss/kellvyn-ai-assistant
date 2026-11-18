# KELLVYN — CONTROLE DE SESSÃO E CONTINUIDADE

**Este é o ponto único de entrada para qualquer nova conversa de trabalho.**
Leia este arquivo antes de alterar código. Depois confirme o HEAD real no GitHub. Não dependa da memória do chat.

**Última conferência:** 18/11/2026, 15:41 UTC  
**Repositório:** `ar2233789-oss/kellvyn-ai-assistant`  
**Branch de laboratório:** `lab/kellvyn-intelligence-core-20261116`  
**HEAD confirmado antes desta atualização:** `e453b98ef859992d12c766830564676c4ebbbe7b`  
**Commit:** [e453b98 — keep runner probe inside agent lab workflow](https://github.com/ar2233789-oss/kellvyn-ai-assistant/commit/e453b98ef859992d12c766830564676c4ebbbe7b)

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

## 6. Próxima tarefa única — continuar daqui

**Obter diagnóstico da falha de alocação do runner da conta/repositório, sem alterar código funcional até haver execução real.**

A prova mínima já falhou antes dos steps. A ação que exige o proprietário da conta é abrir o run [44338054696](https://github.com/ar2233789-oss/kellvyn-ai-assistant/actions/runs/44338054696) e verificar a mensagem exibida no resumo dos jobs; conferir também a utilização incluída de GitHub Actions e o limite de gasto da conta em Billing and licensing → Usage. O conector atual não expõe essas configurações privadas nem a mensagem de falha pré-runner. Registrar o erro exato, sem pressupor que seja billing.

Quando o runner executar:
1. executar primeiro os unit tests;
2. executar depois os instrumented tests Android;
3. corrigir apenas falhas concretas dos logs;
4. só então completar a confirmação do proprietário no fluxo do Profile Store e ligar o harness de benchmark à inferência real, com verificador independente.

**Bloqueio de produto:** o chat ainda não é uma IA funcional. Antes de declarar a IA pronta, o próprio modelo precisa ser treinado, carregar um checkpoint validado e responder por `KellvynRuntime.reply()`; hoje `isReady() == false`. Nenhum APK atual pode ser apresentado como a IA completa.


Ordem:
1. Confirmar o HEAD e os workflows associados à branch.
2. Inspecionar configuração de GitHub Actions/runner e a disponibilidade de compute da conta, sem assumir a causa. Obter uma execução que apresente steps e logs.
3. Quando o runner funcionar, executar primeiro os unit tests; depois os instrumented tests Android.
4. Corrigir apenas falhas reais que apareçam nos logs. Reexecutar e registrar os resultados por commit.
5. Só após os gates passarem, completar o fluxo confiável de revisão/aprovação pelo proprietário e, em etapa separada, ligar o executor de benchmark à inferência real com verificador independente.
6. Depois validar em emulador e, por último, no Poco F6 físico.

Não criar novos agentes, módulos ou estúdios antes de fechar esse gate, salvo correção objetiva necessária para permitir a execução.

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
