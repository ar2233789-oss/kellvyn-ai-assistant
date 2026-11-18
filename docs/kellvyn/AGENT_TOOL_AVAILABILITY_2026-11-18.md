# Kellvyn — disponibilidade real de ferramentas por papel

**Data:** 18/11/2026  
**Branch:** `lab/kellvyn-intelligence-core-20261116`  
**Fonte da verdade:** manifests e `ToolSpec` registrados no runtime, não só os nomes de capabilities no enum.

## 1. Regra de leitura

A matriz `KellvynSpecialistToolPolicy` declara que capacidades um papel pode solicitar em princípio. Isso **não** significa que exista uma ferramenta concreta para toda capacidade.

Uma ferramenta só conta como disponível quando:
1. existe implementação executável;
2. seu `ToolSpec` declara a capability correta;
3. um módulo/runtime a registra;
4. o papel consegue recebê-la pela filtragem READ_ONLY;
5. há teste do contrato e, antes de chamar operacional, evidência de execução real.

Todos os 14 papéis compartilham a mesma inteligência/inferência. A tabela descreve delegações internas do mesmo Kellvyn, não 14 modelos independentes.

## 2. Catálogo concreto encontrado na HEAD auditada

### Navegação e leitura do workspace
Ferramentas:
- `workspace_list_projects`
- `workspace_list_files`
- `workspace_read_file`
- `code_find_symbols`
- `code_find_references`
- `code_file_outline`

Capability principal: `CODE_NAVIGATION`. O indexador de código atual é lexical e limitado; não deve ser descrito como AST/compilação.

### Auditoria de segurança
Ferramenta:
- `security_scan_project`

Capabilities: `SECURITY_AUDIT` e `CODE_NAVIGATION`. A auditoria é estática, local, limitada a arquivos textuais e não executa código nem reproduz segredos.

### Arquitetura e inspeção de dados
Ferramenta:
- `data_inspect_structure`

Capability: `DATA_MODELING`. Inspeciona estrutura de JSON/CSV sem reproduzir valores de registros.

### Diagnóstico do aparelho
Ferramenta:
- `device_resource_snapshot`

Capability: `DEVICE_DIAGNOSTICS`. Lê snapshot de memória, heap, PSS, térmica e bateria sob demanda.

### Automação
Ferramenta:
- `automation_queue_status`

Capability: `WORKFLOW_AUTOMATION`. Consulta quantidade de tarefas pendentes; não enfileira, cancela ou executa ações solicitadas pelo modelo.

### Escrita no workspace
Ferramentas internas:
- `workspace_write_file`
- `workspace_delete_file`

Capability: `CODE_EDITING`, mas são REVERSIBLE_WRITE/DESTRUCTIVE e exigem aprovação curta e exata do proprietário. A política atual dos especialistas não lhes concede essas permissões; não são ferramentas dos 14 papéis.

## 3. Matriz por papel — estado efetivo

| Papel | Capacidade autorizada na política | Ferramenta concreta hoje | Limitação atual |
|---|---|---|---|
| Pesquisador | WEB_RESEARCH, DATA_MODELING | `data_inspect_structure` | Pesquisa Web não está registrada como ferramenta do especialista |
| Explorador de código | CODE_NAVIGATION, DATA_MODELING | `workspace_list_projects`, `workspace_list_files`, `workspace_read_file`, `code_find_symbols`, `code_find_references`, `code_file_outline`, `data_inspect_structure` | Indexação lexical, sem AST completo |
| Revisor | CODE_NAVIGATION, SECURITY_AUDIT, DATA_MODELING, UI_LAYOUT | navegação, `security_scan_project`, `data_inspect_structure` | Ferramentas de análise UI/visual ainda ausentes |
| Analista de testes | ANDROID_BUILD, ANDROID_DEVICE_TEST, DEVICE_DIAGNOSTICS | `android_build_environment_status`, `device_resource_snapshot` | Diagnóstico do compilador Java/D8 disponível; build, instalação, Logcat, screenshots e execução de testes no dispositivo ainda não são ferramentas do especialista |
| Analista de raciocínio | DATA_MODELING, DEVICE_DIAGNOSTICS | `data_inspect_structure`, `device_resource_snapshot` | Sem ferramentas específicas de avaliação de raciocínio |
| Analista de aprendizado | NEURAL_TRAINING, NEURAL_INFERENCE, DATA_MODELING | `data_inspect_structure` | Treino/inferência existem como APIs de runtime próprias, mas não como ferramentas especializadas do agente |
| Arquiteto de programação | CODE_NAVIGATION, UI_LAYOUT, DATA_MODELING | navegação e `data_inspect_structure` | UI_LAYOUT sem motor concreto; sem ferramenta de edição/build para especialista |
| Analista de memória | MEMORY_READ | `memory_search` | Consulta somente memórias não sensíveis nos escopos global e projeto ativo desta conversa; limite de 5 resultados e saída truncada; nunca escreve/apaga |
| Analista de segurança | SECURITY_AUDIT, CODE_NAVIGATION, ANDROID_BUILD | `security_scan_project`, navegação, `android_build_environment_status` | Status do compilador é somente leitura; não executa build |
| Analista de planejamento | WORKFLOW_AUTOMATION, DEVICE_DIAGNOSTICS, DATA_MODELING | `automation_queue_status`, `device_resource_snapshot`, `data_inspect_structure` | Automação é apenas consulta; não agenda nem altera tarefas |
| Depurador | CODE_NAVIGATION, ANDROID_BUILD, ANDROID_DEVICE_TEST, DEVICE_DIAGNOSTICS | navegação, `android_build_environment_status`, `device_resource_snapshot` | Sem leitura estruturada de Logcat, execução de teste ou inspeção de crash nativa |
| Auditor independente | CODE_NAVIGATION, ANDROID_BUILD, ANDROID_DEVICE_TEST, VISUAL_ANALYSIS, SECURITY_AUDIT, DATA_MODELING | navegação, `android_build_environment_status`, auditoria de segurança e inspeção de dados | Build/teste/visual completos ainda sem motores; o status do compilador não é evidência de build/teste |
| Analista de operações | DEVICE_DIAGNOSTICS, WORKFLOW_AUTOMATION | `device_resource_snapshot`, `automation_queue_status` | Sem controle operacional; não executa automações mutáveis |
| Analista visual | VISUAL_ANALYSIS, UI_LAYOUT, RASTER_IMAGE_EDIT, VECTOR_GRAPHICS, SCENE_3D, CAD_PARAMETRIC | nenhuma ferramenta visual concreta identificada no catálogo | É papel de análise com ferramentas ainda ausentes; sem edição raster/vetor, cena 3D ou CAD |

## 4. Módulos concretos registrados no runtime

1. `code-navigation` — navegação Kotlin/Java.
2. `security-audit` — auditoria estática de projeto.
3. `data-architecture` — inspeção de estrutura JSON/CSV.
4. `android-build-status` — diagnóstico READ_ONLY do compilador local Java/ECJ/D8 e do último APK encontrado.

Além deles, o runtime registra ferramentas compartilhadas de workspace, recursos, status de automação e `memory_search` (somente leitura, limitada aos escopos autorizados). O módulo Android registra apenas diagnóstico do ambiente; o builder local pré-existente continua sendo um backend restrito a Java simples (ECJ → D8 → APK assinado), não um Android Studio completo. **Não existem oito estúdios completos ainda.** As capacidades visuais, Android device test e Web research continuam lacunas de implementação.

## 5. Ordem correta para completar a cobertura

1. Fechar a Fase 1: provar handler real, fila, broker, resultado persistido e retomada no Android/Gradle.
2. Adicionar teste de integração papel → política → manifest → ferramenta real para cada capability existente.
3. Executar Memory Read no Android: validar escopo, proveniência, exclusão de registros sensíveis/desconhecidos, limites e isolamento por papel (a ferramenta `memory_search` já está implementada; harness Kotlin isolado passou, mas Android/Gradle segue pendente).
4. Criar Web Research read-only com fonte, data, URL, isolamento de prompt injection e rede opcional.
5. Completar Android Build/Test adapters: status do compilador já existe; build, AVD, captura, Logcat, UIAutomator e verificador de critérios continuam pendentes, com instalação e ações de dispositivo sujeitas a aprovação.
6. Criar motores UI/UX e Visual Analysis, começando por leitura/comparação antes de edição.
7. Criar Raster/Vector/3D/CAD somente com módulos sob demanda, formatos/limites definidos, auditoria de dependências e testes de desempenho.
8. Expor treinamento e inferência neural ao papel Learning somente após as APIs de runtime terem contratos de ferramenta, limites HEAVY, checkpoints e verificação independente.

Cada módulo deve trazer manifest, tool schemas, workload, orçamento estimado, política de licença, cancelamento, artefatos, evidência e testes. Não duplicar um motor por papel.

## 6. Limites de segurança atuais

- Os especialistas continuam filtrados para READ_ONLY.
- Capacidades previstas não conferem permissão automaticamente.
- Escrita/remoção exigem gate do proprietário e não são repassadas aos especialistas.
- A fila visível ao modelo oferece status somente leitura; não há ferramenta genérica para enfileirar comandos.
- O Worker de background não reivindica tarefas HEAVY.
- Dados externos e resultados de ferramentas continuam não confiáveis para o agente.

## 7. Como declarar progresso

Usar estados separados:
- **PAPEL REGISTRADO:** enum, instrução e política existem.
- **FERRAMENTA DEFINIDA:** ToolSpec e manifest existem.
- **MÓDULO REGISTRADO:** runtime oferece a ferramenta.
- **CONTRATO TESTADO:** teste executado e aprovado.
- **OPERAÇÃO COMPROVADA:** tarefa real executou e gerou evidência.
- **COMPETÊNCIA COMPROVADA:** o modelo treinado executou benchmark próprio com qualidade mensurada.

Não substituir um estado pelo outro. O fato de haver 14 papéis não significa que todos estejam equipados nem que tenham competência comprovada.


## 8. Implementação de Memory Read — 18/11/2026

- Nova ferramenta `memory_search` em `KellvynMemoryTools.kt`, incluída no registry do runtime.
- A ferramenta recebe somente `query` e `limit`; não aceita project_id nem qualquer argumento para escolher escopos.
- Os endereços são fornecidos pelo runtime: GLOBAL e, quando existente e validado, o projeto ativo da conversa.
- Só devolve registros `NON_SENSITIVE`; registros `SENSITIVE_PERSONAL`, `CREDENTIAL` e `UNKNOWN` são omitidos.
- Saída inclui ID de referência, tipo, origem, escopo, confiança, termos correspondentes e proveniência; conteúdo é explicitamente marcado como dado não confiável.
- Saída limitada a cinco memórias, 420 caracteres de conteúdo por item e 3.200 caracteres no total.
- Apenas a capability `MEMORY_READ` recebe a ferramenta através de `KellvynSpecialistToolPolicy`; demais papéis não a recebem.
- Testes unitários cobrem escopo, exclusão de sensíveis/desconhecidos, argumento de escopo forjado, limites e ausência de escopo.
- Teste instrumental da cobertura real atualiza expectativa do papel MEMORY_ANALYST para `memory_search`.
- Ainda não executado no Android/Gradle; contrato escrito não é aprovação.


## 8. Implementação de Memory Read — 18/11/2026

- Nova ferramenta `memory_search` em `KellvynMemoryTools.kt`, incluída no registry do runtime.
- A ferramenta recebe somente `query` e `limit`; não aceita project_id nem qualquer argumento para escolher escopos.
- Os endereços são fornecidos pelo runtime: GLOBAL e, quando existente e validado, o projeto ativo da conversa.
- Só devolve registros `NON_SENSITIVE`; registros `SENSITIVE_PERSONAL`, `CREDENTIAL` e `UNKNOWN` são omitidos.
- Saída inclui ID de referência, tipo, origem, escopo, confiança, termos correspondentes e proveniência; conteúdo é explicitamente marcado como dado não confiável.
- Saída limitada a cinco memórias, 420 caracteres de conteúdo por item e 3.200 caracteres no total.
- Apenas a capability `MEMORY_READ` recebe a ferramenta através de `KellvynSpecialistToolPolicy`; demais papéis não a recebem pela política de delegação.
- Testes unitários cobrem escopo, exclusão de sensíveis/desconhecidos/credenciais, argumento de escopo forjado, limites e ausência de escopo.
- Teste instrumental da cobertura real atualiza expectativa do papel MEMORY_ANALYST para `memory_search`.
- O fonte isolado passou por kotlinc com stubs mínimos; isso não é build Gradle nem validação Android.
- Ainda não executado no Android/Gradle; contrato escrito não é aprovação.

## Estado de verificação isolada — 18/11/2026
- `KellvynMemoryTools.kt` compilou com kotlinc num harness que stubbou apenas os contratos da camada de ferramentas; o algoritmo de ranking de memória e os casos de escopo/privacidade foram executados com repositório falso.
- Resultado: `KELLVYN_MEMORY_TOOL_INTEGRATION_TESTS_PASS`.
- Não prova compilação do app nem SQLite/Keystore real. A prova Android continua pendente do runner.


## Android Build status — 18/11/2026

- Novo módulo lazy `android-build-status` registra a ferramenta `android_build_environment_status`.
- A ferramenta é READ_ONLY e não tem argumentos; informa presença do `android.jar` cacheado, aceite da licença SDK, versão compileSdk e metadados limitados do último APK encontrado.
- Não revela caminho absoluto, não compila, não instala, não executa APK e declara explicitamente que não fornece estado de teste do dispositivo.
- Compartilha a instância do `ProjectWorkspaceStore` já mantida pelo runtime, evitando abrir uma segunda store.
- Teste instrumental cobre registro, capability, saída e rejeição de argumentos.
- A política entrega essa ferramenta somente a TEST_ANALYST, SECURITY_ANALYST, DEBUGGER e AUDITOR (papéis que possuem ANDROID_BUILD).
- Limite: o builder local disponível é perfil Java limitado ECJ → D8; Kotlin/Compose/Gradle completo e execução instrumentada no aparelho ainda não estão disponíveis como ferramentas dos especialistas.
- Teste ainda aguarda execução Gradle/Android real.
