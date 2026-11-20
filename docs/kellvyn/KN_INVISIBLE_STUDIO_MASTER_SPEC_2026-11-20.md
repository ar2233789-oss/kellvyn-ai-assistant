# Kellvyn — especificação-mestra do laboratório invisível

**Data:** 20/11/2026  
**Branch de implementação:** `kellvyn-rnd/kn004-invisible-studio-foundation-20261120`  
**Base KN:** `kellvyn-rnd/kn004-multiblock-clean-20261120`  
**Alvo:** Poco F6 / Android / HyperOS / 8 GB RAM / 512 GB  
**Operação:** local e offline-first; rede somente quando o proprietário habilitar  
**Interface:** nenhuma interface de Studio. A conversa principal continua sendo a única superfície de comando.

## 1. Decisão que prevalece

Kellvyn terá **uma única inteligência central: o modelo neural KN**. Não criar redes decisoras separadas, conjuntos de inteligências, personalidades operacionais concorrentes ou roteadores de modelos.

O KN interpreta o pedido, raciocina, planeja, escolhe uma capacidade, observa o resultado, corrige o plano e decide quando a tarefa está concluída. Os motores do laboratório são executores determinísticos: compiladores, parsers, processadores de mídia, renderizadores, ferramentas de dados, automação e verificadores. Eles não conversam com o usuário, não definem objetivos próprios e não substituem o KN.

Este documento atualiza e prevalece sobre as passagens históricas das especificações anteriores que descreviam vários papéis decisores. A arquitetura e o histórico antigos são preservados para auditoria; não devem ser reativados como uma rede de inteligências.

## 2. O que significa laboratório invisível

O laboratório é uma oficina interna acionada por ferramentas. Não é uma nova tela, IDE visual, painel, aplicativo separado ou emulador permanente.

Fluxo obrigatório:

```text
Conversa principal
       |
Uma única inteligência KN
       |
Contexto + memória + plano + estado da tarefa
       |
Catálogo de capacidades e ferramentas
       |
Política de autorização + Task Broker + Resource Governor
       |
Workspace + armazenamento de artefatos
       |
Motores internos carregados sob demanda
       |
Observação + logs + hashes + testes + verificador
       |
Resultado, arquivos e evidências devolvidos ao chat
```

Nenhum motor pesado permanece carregado sem necessidade. O fim de uma operação deve liberar buffers, bitmaps, cenas, caches e recursos gráficos regeneráveis. A ausência de interface reduz a sobrecarga permanente, mas não elimina o custo computacional da tarefa solicitada.

## 3. Motores que compõem a oficina

Os módulos abaixo são capacidades do mesmo laboratório, não inteligências independentes.

| Motor | Trabalho que deverá realizar |
|---|---|
| Software Studio | Ler projetos, indexar símbolos e dependências, entender Kotlin/Java/TS/Python/JSON/XML/YAML/SQL, propor patches, revisar diffs, executar testes permitidos e recuperar versões |
| Android Build & Test | Validar projetos, resolver dependências autorizadas, compilar, empacotar, assinar, verificar APK, instalar quando autorizado, iniciar, capturar tela/Logcat e testar critérios |
| UI/UX Studio | Criar árvores de componentes, tokens de design, layouts responsivos, estados e navegação; detectar overflow, clipping, contraste e regressões visuais |
| Image Studio | Inspecionar, recortar, redimensionar, compor camadas, máscaras, vetores, texto, ajustes não destrutivos, comparar e exportar imagens |
| Image Generation | Criar/editar imagens por instrução usando representação e decodificação visual próprias; deve ser uma capacidade separada do editor raster e não pode ser anunciada antes de existir um motor treinado e validado |
| 3D/CAD Studio | Criar cenas, geometria, malhas, materiais, luzes, câmeras, transformações, modelos paramétricos e exportar GLB/glTF |
| Animation Studio | Criar timelines, keyframes, rigs, curvas, movimentos de câmera/objetos, sequências 2D/3D, renderizar previews e exportar artefatos com duração/FPS definidos |
| Video Studio | Montar/editar sequências, quadros, áudio, cortes, transições, timestamps e exportar vídeo, somente após validar codecs, memória e tempo de execução |
| Audio/Voice Studio | Capturar/transcrever áudio, analisar voz/sons e produzir áudio/voz com codecs próprios; consentimento e controles do Android são obrigatórios |
| Neural Studio | Configurar, treinar, avaliar, versionar e otimizar o próprio KN; controlar corpus, tokenizer, checkpoints, gradientes, memória, latência e regressões |
| Data & Architecture Studio | Inspecionar e criar SQLite, JSON/Schema, CSV, OpenAPI, diagramas e contratos; validar migrações e consistência entre código e documentação |
| Automation Studio | Criar fluxos autorizados, gatilhos, condições, agendamentos, retries, timeouts, pausa/cancelamento/retomada e relatórios |
| Device Interaction | Capturar estado de tela e executar ações autorizadas via APIs Android; acessibilidade e MediaProjection são opcionais e exigem habilitação explícita |
| Research Studio | Consultar fontes online com a chave de rede ligada, ler documentação, registrar proveniência, datas, licenças e evidências |

A existência de um contrato de módulo ou de um enum de capacidade não significa que o motor foi implementado. Cada módulo deve ter uma implementação real, testes, disponibilidade e limites declarados.

## 4. Serviços compartilhados obrigatórios

1. **Capability/Tool Catalog:** ID, versão, descrição, argumentos, risco, permissões, dependências, pré-condições, formatos, orçamento, timeout, cancelamento, saídas e verificador.
2. **Task Broker:** fila, prioridade, exclusão mútua de tarefas pesadas, dependências, cancelamento, retomada e recuperação.
3. **Resource Governor:** admissibilidade baseada em RAM disponível, heap, PSS, CPU, bateria, temperatura, espaço e classe de carga.
4. **Workspace:** projetos e arquivos privados com caminhos relativos, versionamento, diffs, locking e rollback.
5. **Artifact Store:** artefatos binários separados do contexto; referências pequenas no prompt, conteúdo no disco privado.
6. **Resource Catalog:** SDKs, toolchains, bibliotecas, templates e motores com versão, origem, licença, hash, compatibilidade e tamanho.
7. **Execution Broker:** executa somente operações registradas, com limites, timeout e ambiente apropriado.
8. **Approval Gate:** autorizações exatas, de uso único, verificadas por código confiável; aprovação de escrita não aprova exclusão, rede ou controle do dispositivo.
9. **Evidence Store:** logs sanitizados, hashes, screenshots, saídas de compilação/testes e relatórios.
10. **Checkpoint Service:** persistência autenticada do estado necessário à retomada.
11. **Telemetry:** duração, RAM/PSS/heap, CPU, bateria, estado térmico, bytes, erros, cancelamentos e motivo de bloqueio.

O KN recebe apenas descrições compactas e referências aos artefatos. Não carregar imagens grandes, projetos inteiros ou logs gigantes no contexto se uma referência e um resumo estruturado forem suficientes.

## 5. Estado real verificado na branch-base em 20/11/2026

### 5.1 Fundamentos internos encontrados

- `KellvynResourceGovernor`, `KellvynTaskBroker`, `KellvynAndroidResourceMonitor` e `KellvynResourceRuntime`: admissão de recursos, snapshots, gestão sob demanda e fila persistente.
- `KellvynPersistentTaskScheduler` e `KellvynAutomationDrainWorker`: fila durável e drenagem WorkManager de tarefas leves/padrão; o Worker não assume tarefas HEAVY.
- Ferramentas de automação desta branch: `automation_schedule_resource_snapshot` agenda somente o handler interno fixo (atraso máximo de 7 dias), `automation_task_status` consulta sem revelar payload e `automation_cancel_queued_task` cancela apenas tarefas QUEUED/WAITING_RESOURCES. Não aceita scripts, comandos ou handlers fornecidos pelo modelo.
- `KellvynStudioModuleManager`: manifestos, construção lazy de módulos, leases, timeout, admissão de memória e descarte de módulos ociosos.
- `KellvynArtifactStore` (fundação adicionada nesta branch): armazenamento privado endereçado por SHA-256, objetos binários deduplicados, registros vinculados a tarefa/produtor, gravação atômica e leitura/cópia verificadas.
- `KellvynImageStudio` (motor raster inicial adicionado nesta branch): inspeção de dimensões/formato e redimensionamento/exportação PNG/JPEG/WebP sobre artefatos privados, com limites de pixels, admissão HEAVY, autorização append-only e saída imutável verificada. Não é um gerador neural de imagens; ainda faltam recorte, composição, camadas, máscaras, edição vetorial, importação de anexos pelo chat e testes executados no aparelho.
- Ferramentas centrais `artifact_store_status`, `artifact_list_task` e `artifact_verify`: consulta de metadados e integridade sem despejar binários no contexto da inteligência.
- Ferramentas `image_inspect` e `image_resize`: disponíveis via registro central; edição raster somente após o fluxo fornecer um artefato de origem. A geração neural por prompt continua NOT_IMPLEMENTED.
- `studio_capability_inventory`: ferramenta somente leitura que distingue módulos lazy registrados, ferramentas centrais registradas e capacidades ausentes; não declara execução ou verificação sem evidência.
- `ProjectWorkspaceStore` e ferramentas de workspace: listar projetos/arquivos, ler trechos, escrever e excluir sob aprovação exata.
- Memória local cifrada, checkpoints de tarefa, traces com metadados e snapshots de recursos.
- Catálogo interno atual: navegação de Kotlin/Java, auditoria de segurança, inspeção de dados JSON/CSV, diagnóstico do ambiente de compilação, pesquisa restrita à Wikipédia em português e diagnóstico de bots.
- Compilador local Java experimental: ECJ → D8 → APK, assinatura e verificação. O construtor limita-se a projetos Java Android simples; não é um Gradle/Android Studio completo.
- KN-004: Transformer multi-bloco próprio, retropropagação por todas as camadas, cache KV por camada, tokenizer BPE, pipeline local e checkpoint independente.

### 5.2 Bancada externa que já existe

`tools/kellvyn-workbench` contém CLI de auditoria, execução de testes, triagem de logs, diff visual, auditoria web/Playwright, scripts ADB/Android smoke, mapa semântico TypeScript, pipeline Blender e executor Podman rootless separado.

Essa bancada é usada no ambiente de engenharia; **não está embutida no APK e não é automaticamente acessível ao KN no Poco F6**. Blender, Playwright, ADB, SDK e Podman dependem das ferramentas instaladas no host. O executor Podman exige imagem local pinada por SHA-256 e rootless comprovado; não é uma sandbox Android nem uma ponte pronta para o app.

### 5.3 Lacunas comprovadas

- `KellvynRuntime.isReady()` permanece falso e `reply()` ainda retorna indisponibilidade. O chat principal não está ligado ao KN-004.
- A branch não contém checkpoint/pesos treinados nem corpus conversacional autorizado suficiente. Os testes neurais usam fixtures sintéticas e não demonstram competência geral.
- O quality gate KN-004 libera benchmark de protocolo, não prontidão de conversa/produção.
- O catálogo de app não inclui motores internos de UI/UX, raster/vetor, geração de imagem, 3D/CAD, animação, vídeo, áudio ou jogos.
- A automação nativa exposta ao KN agora permite agendar apenas o probe interno de recursos, consultar o estado sem payload e cancelar tarefas ainda não iniciadas. Ainda faltam workflows gerais definidos por usuário, gatilhos, condições, dependências, pausa/retomada configurável e integrações com apps externos.
- O diagnóstico do compilador não compila; a compilação real ocorre por rota limitada do construtor nativo, ainda sem toolchain Android completo.
- Não há integração do Blender externo com o runtime Android.
- Não há sistema geral de interação com apps/jogos; permissões Android não podem ser presumidas.
- CI observado nesta branch não executou steps (runner_id=0, steps=[]); testes Gradle/JUnit e teste virtual não podem ser chamados de aprovados.
- Não há build APK nem teste físico Poco F6 comprovado nesta auditoria.

## 6. Regras de recursos e concorrência para o Poco F6

- LIGHT: leituras curtas e consultas de índice.
- STANDARD: parsing, análise, pesquisa e operações médias.
- HEAVY: treinamento KN, compilação, render 3D, exportação de vídeo e processamento de imagens grandes; uma tarefa HEAVY por vez por padrão.
- PERSISTENT: tarefas adiáveis/reiniciáveis via WorkManager quando a política Android permitir.
- Treinamento e inferência não compartilham pesos mutáveis. O KN ativo não pode ser alterado durante uma conversa.
- Verificar RAM, armazenamento, bateria e temperatura antes de admitir trabalho pesado; cancelar cooperativamente quando a política exigir.
- Carregar motores sob demanda; limpar caches regeneráveis; preservar artefatos e checkpoints duráveis.
- Não usar polling contínuo para métricas que podem ser consultadas sob demanda.
- Não prometer aceleração GPU/NPU sem equivalência numérica e benchmark medido no aparelho.
- O Poco F6 é alvo de inferência e experimentos móveis pequenos; não é uma plataforma realista para pré-treinar sozinho um modelo multimodal de fronteira.

## 7. Automação e segurança

- O modelo propõe uma ação estruturada; código confiável valida esquema, risco, escopo e permissão.
- Conteúdo externo, documentos, memória, páginas e resultados de ferramentas são dados não confiáveis; nunca substituem as regras confiáveis.
- Escrita/deleção usam workspace e autorização de uso único ligada ao conteúdo/caminho exato.
- Rede permanece desligada até a opção do proprietário habilitar; credenciais nunca entram no contexto nem nos logs.
- Controle de tela, instalação de APK, câmera, microfone e acessibilidade exigem permissões explícitas e interrupção pelo usuário.
- Nenhuma automação deve burlar controles de segurança, anti-cheat, paywall, autenticação ou regras de plataformas.
- Tarefas não concluídas recebem FAILED, BLOCKED, TIMED_OUT, PARTIAL ou NOT_RUN, nunca sucesso inventado.
- Operação com risco irreversível ou fora do escopo permitido exige confirmação explícita.

## 8. Estratégia de implementação

### P0 — conectar o que existe sem fingir prontidão
1. Manter KN-004 como único cérebro e verificar tokenizer, treinamento, checkpoints e inferência.
2. Fechar a ponte `KellvynRuntime → KN-004 → KellvynCognitiveKernel → Tool Registry → ferramentas → observações → chat`.
3. Criar gate de prontidão comportamental com casos held-out de português, continuidade, protocolo, planejamento, chamadas de ferramentas, erros e recusas seguras.
4. Garantir que o chat só seja habilitado após bundle íntegro, benchmark comportamental, testes e medição.
5. Resolver a execução de testes Gradle/Android real; runner sem steps significa NOT_RUN.

### P1 — consolidar o laboratório invisível
1. Completar catálogo de capacidades com estado explícito: REGISTERED_LAZY, AVAILABLE, BLOCKED, EXECUTED, VERIFIED ou NOT_IMPLEMENTED.
2. Completar Artifact Store e Resource Catalog com hash, licença, versão, quarentena e rollback.
3. Expandir Software Studio para patch/diff/rollback e execução controlada; expandir compilador Android para perfis suportados além de Java simples, sem anunciar Kotlin/Compose antes de testes.
4. Transformar scheduler persistente em automação controlável pelo KN com allowlist de handlers, criação/consulta/pausa/cancelamento/retomada e provas de execução.
5. Ligar os instrumentos da bancada externa ao runtime somente por adaptador explícito e somente nos hosts compatíveis; no Android, usar motores nativos próprios.

### P2 — motores criativos e multimodais
1. UI/UX estruturado.
2. Image Studio determinístico não destrutivo, seguido por geração neural visual somente com encoder/decoder próprios treinados e avaliados.
3. Scene Graph 3D/CAD e exportação GLB; preview/renderização sob demanda.
4. Timeline de animação, keyframes, curvas, câmera, rig e exportação de quadros/vídeo.
5. Áudio/voz e vídeo por codecs/transdutores próprios, quando o orçamento de memória, tempo e dados permitir.
6. Jogos e controle de apps somente por interfaces Android autorizadas, primeiro com cenários limitados e testáveis.

Nenhuma modalidade pode ser anunciada como concluída apenas porque existe uma enum, um manifesto, um protótipo de arquivo ou uma ferramenta externa no repositório.

## 9. Critérios universais de conclusão

Uma capacidade só passa a VERIFIED quando todos estes itens existirem:
1. contrato de entrada/saída tipado e versionado;
2. implementação real acessível pelo registry;
3. pré-condições, permissões e orçamento de recursos;
4. teste positivo representativo;
5. teste negativo/erro/timeout/cancelamento;
6. artefato ou resultado real com hash e origem;
7. verificador que não dependa da autodeclaração do executor;
8. medição de RAM/CPU/tempo/armazenamento e térmica quando relevante;
9. evidência CI ou execução local reproduzível;
10. teste no Poco F6 antes de prometer desempenho/compatibilidade nesse aparelho.

Estados de inventário não são intercambiáveis:
- DECLARED: especificado em documento ou enum.
- REGISTERED_LAZY: módulo e contratos registrados; motor não necessariamente carregado.
- AVAILABLE: dependências/permissões/recursos estão prontos agora.
- EXECUTED: uma operação real foi executada e produziu resultado.
- VERIFIED: resultado passou verificador independente e testes aplicáveis.
- BLOCKED / NOT_IMPLEMENTED / NOT_RUN: impedimentos explícitos, sem simular aprovação.

## 10. Proteção da interface e da identidade

A interface aprovada permanece intacta. O laboratório é invisível; não acrescentar abas, telas, painéis ou seletores de motores ao usuário. A única identidade conversacional é Kellvyn. Motores são recursos internos invocados pela mesma inteligência.

---

**Regra de continuidade:** antes de cada implementação, conferir branch/HEAD, diff, arquivos protegidos e dependências já existentes. Alterar uma lacuna por vez, adicionar testes e registrar o estado literal. Não apagar componentes históricos que estejam fora do escopo sem plano de migração.