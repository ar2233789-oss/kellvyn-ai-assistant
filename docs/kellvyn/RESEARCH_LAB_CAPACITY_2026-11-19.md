# Pesquisa de capacidade do laboratório — 19/11/2026

**Branch de trabalho:** `kellvyn-rnd/agent-trace-duration-port-20261118`  
**Escopo:** meios de execução, recursos, temperatura, segurança, reprodutibilidade e avaliação dos agentes.  
**Regra:** não ativar inferência neural nem mesclar candidatos enquanto os gates não produzirem evidência reproduzível.

## 1. Achado prioritário: runner próprio em Android ARM64

Foi encontrado o projeto externo **DroidRunner**: https://github.com/m96-chan/DroidRunner

A proposta é executar o runner oficial Linux ARM64 do GitHub Actions dentro de um ambiente Ubuntu/PRoot, controlado por um APK Android. O projeto declara funcionamento sem root e sem Termux. O README informa suporte a ARM64 e Android 9/API 28+, rede estável, alguns GB livres, carregador e resfriamento para tarefas longas. O runner pode adiar trabalhos quando bateria, temperatura ou espaço estão fora dos limites configurados. O app mostra CPU, memória, disco, bateria, temperatura, rede e estado do runner; usa Keystore para credencial e permite modo efêmero.

### Evidência e maturidade observada

- Repositório público consultado em 19/11/2026: criado em setembro de 2026, 8 estrelas, 2 forks e 7 issues abertas no momento da consulta.
- Release estável publicada: v1.0.0 em 12/09/2026.
- A release informa CI Android aprovado e testes de debug em aparelhos físicos Android API 36, incluindo um Xiaomi. Isso é evidência do projeto DroidRunner, não prova de compatibilidade específica com o Poco F6.
- Código licenciado GPL-2.0-only; PRoot, rootfs e demais componentes têm licenças próprias. Não copiar nem incorporar o código ao Kellvyn sem análise de licença e decisão específica.
- O runner é Linux ARM64 em PRoot. O README lista Git, Node, Python, Rust, Go e Gradle; tarefas que dependem de APIs Android/NPU usam um Device Agent separado no APK.

### Limites e riscos que impedem adoção imediata no Poco F6 pessoal

1. Um self-hosted runner executa o código definido nos workflows diretamente no dispositivo. O projeto alerta expressamente que PRoot **não é uma fronteira forte de segurança** como Docker ou VM.
2. A documentação recomenda um dispositivo dedicado à CI, não o aparelho pessoal usado diariamente; também recomenda evitar PRs de forks, reduzir permissões e usar runners efêmeros.
3. Docker, service containers, KVM e virtualização aninhada não são suportados. Portanto, não presumir que o AVD Android 15 usado no workflow atual possa ser iniciado dentro desse ambiente.
4. HyperOS e mecanismos de economia de bateria podem encerrar processos; o projeto implementa recuperação, mas reconhece que algumas ROMs podem impedir execução contínua.
5. Mesmo que o runner e Gradle iniciem, ainda é necessário provar que todas as ferramentas host do Android SDK/build-tools usadas por este repositório funcionam em ARM64/PRoot. A documentação externa não prova esse conjunto.
6. O Poco F6 é o aparelho pessoal do proprietário. Não instalar nem registrar um runner que execute código remoto nele sem uma avaliação e autorização explícitas.

**Decisão atual: candidato para PoC em aparelho Android secundário/dedicado; NO-GO para instalar no Poco F6 pessoal nesta fase.** Não instalar APK, registrar runner nem criar token como parte desta pesquisa.

## 2. Comparação das vias de execução

| Via | Vantagem | Limite / custo | Decisão |
|---|---|---|---|
| GitHub-hosted runner privado atual | ambiente Linux x64 padronizado; já existe workflow completo | consome compute incluído; os jobs atuais falham antes do primeiro step | manter como caminho principal quando voltar a alocar |
| Self-hosted runner ARM64 dedicado | uso de runner self-hosted não consome compute hospedado; hardware real | exige aparelho dedicado, operação, segurança e prova do toolchain Android | PoC separado, condicionado |
| DroidRunner no Poco F6 de uso diário | pode executar jobs no hardware Android sem root/Termux | workflow remoto executa no telefone; PRoot não é sandbox forte; bateria/temperatura/HyperOS; sem KVM/Docker | não instalar no telefone pessoal |
| Tornar o repositório público para obter runners públicos | GitHub informa uso gratuito dos runners padrão em repositórios públicos | expõe fonte, histórico, arquitetura e possivelmente informação privada | rejeitado |
| Aumentar orçamento/adicionar cobrança | pode liberar hosted compute se a causa for budget | gasto financeiro e causa ainda não confirmada | não alterar sem decisão explícita |

GitHub documenta que runners self-hosted não consomem compute incluído e que runners padrão hospedados em repositórios públicos são gratuitos; em repositórios privados, runners hospedados consomem compute incluído e depois podem gerar cobrança. ARM64 Linux self-hosted aparece como suporte em public preview.

## 3. Melhoria já aplicada no CI do Agent Lab

Arquivo: `.github/workflows/agent-lab-tests.yml`

Antes, cada push podia tentar alocar três runners em paralelo:
- `ubuntu-slim` para probe;
- `ubuntu-latest` para Python;
- `ubuntu-latest` para Gradle/AVD.

Agora existe **um único job sequencial**:
1. checkout e captura inicial de ambiente;
2. Python compile e 17 testes da CLI R&D Studio;
3. JDK 17 e Android SDK 35;
4. Gradle 8.13 e testes JVM;
5. KVM e testes instrumentados API 35;
6. captura final de memória/disco e artefatos.

O artefato `kellvyn-agent-lab-evidence` preserva o relatório de preflight, APK debug quando gerado, relatórios JUnit e resultados instrumentados. O preflight registra SHA/ref, OS/arquitetura/nome do runner, CPU, RAM, disco e versões de Python, Java, Gradle e ADB.

Isso reduz três alocações para uma por rodada e fornece diagnóstico de ambiente. Não reduz a necessidade de um runner válido nem muda o escopo dos testes.

### Resultado real após a mudança

- Run: `44426067190`
- Job: `135231121117`
- Conclusão: failure antes da primeira etapa.
- `runner_id=0`, `runner_name` vazio, `steps=[]`.
- Classificação: **runner não alocado / NOT RUN**. A mudança de workflow ainda não foi exercitada pelo runner; não inferir erro de YAML, Python, Gradle ou Kotlin a partir deste resultado.

## 4. Protocolo científico para agentes e módulos no aparelho

A revisão de literatura sobre avaliação de agentes recomenda não usar apenas sucesso final: é necessário observar a trajetória, segurança, custo, latência, recursos e recuperação de falhas. O Agent Lab já possui parte dessa base: traces, resultados com estados diferenciados, snapshots de RAM/heap/PSS, CPU de processo, temperatura e bateria, execução sequencial e módulo sob demanda.

### Condições de cada experimento

Registrar antes de executar:
- commit/branch, hash do perfil e fingerprint da suíte;
- aparelho, versão Android/HyperOS, API, arquitetura e estado da rede;
- bateria %, carregamento, modo economia, banda térmica e headroom quando disponível;
- RAM livre, heap, PSS, espaço livre, versão do modelo/checkpoint e módulos carregados.

Registrar durante e depois:
- início/fim monotônicos, latência total e duração por etapa/delegação;
- quantidade de passos, ferramentas autorizadas/chamadas/bloqueadas e tentativas;
- estado do verificador: PASSED, REVIEW_REQUIRED, FAILED ou BLOCKED;
- pico/instantâneo de heap/PSS, CPU do processo, bateria e temperatura;
- retries, cancelamentos, pressão de memória, falha/recovery do recorder e integridade da cadeia do trace.

### Sequência de teste recomendada

1. Baseline ocioso: três snapshots em repouso, sem inferência concorrente.
2. LIGHT: três execuções idênticas de uma ferramenta READ_ONLY curta.
3. STANDARD: três execuções do mesmo caso, com uma única especialidade em execução.
4. HEAVY: somente em primeiro plano, após verificar folga térmica, RAM e bateria; executar uma vez, avaliar e descarregar o módulo.
5. Falhas controladas: bateria baixa, pressão de memória, temperatura elevada, espaço insuficiente, cancelamento e falha de persistência. A resposta correta pode ser BLOCKED/WAITING_RESOURCES; não tratar bloqueio de proteção como defeito.
6. Repetir a baseline, comparar o before/after e rejeitar qualquer melhoria que aumente uso de RAM/CPU/temperatura sem ganho comprovado de qualidade.

Não abrir 14 inferências paralelas no Poco F6. A avaliação dos 14 perfis deve permanecer sequencial por padrão; paralelismo só depois que medições reais demonstrarem margem e o Resource Governor conceder permissões.

### Gates de qualidade

- PASSED só com critérios objetivos, evidência rastreável e verificador independente.
- Critério subjetivo ou sem oráculo: REVIEW_REQUIRED, nunca aprovação automática.
- Perfil que solicita ferramenta fora da allowlist: bloqueio e trace sem armazenar argumentos sensíveis.
- Trace corrompido/incompleto: impedir promoção do resultado.
- Build verde não prova instalação/execução física; AVD não comprova bateria, temperatura ou performance do Poco F6 real.

## 5. Próximas tarefas — ordem que evita retrabalho

1. Resolver a alocação do GitHub Actions: inspecionar compute incluído, budgets/Usage blocked e política de Actions; se não houver bloqueio, escalar ao GitHub Support com os run IDs.
2. Executar a CLI R&D Studio e a suíte Gradle em runner real; conferir artefatos e relatórios.
3. Validar a fila persistente/handler/WorkManager e matriz de 14 perfis em Android instrumented tests.
4. Executar separadamente os três candidatos Brain, Agents e Integration. Nenhum merge até cada suíte específica passar.
5. Integrar benchmarks ao checkpoint neural real somente depois do gate de integridade/qualidade; o Runtime segue desligado até então.
6. Avaliar DroidRunner apenas num aparelho dedicado e descartável, primeiro para tarefa inofensiva e sem segredos; medir temperatura, bateria, armazenamento, restart e isolamento antes de confiar-lhe workflows da Kellvyn.

## Fontes consultadas

- GitHub Actions billing: https://docs.github.com/en/actions/concepts/billing-and-usage
- GitHub Actions self-hosted runners e arquiteturas: https://docs.github.com/en/actions/reference/runners/self-hosted-runners
- GitHub Actions billing detalhado: https://docs.github.com/en/billing/concepts/product-billing/github-actions
- Android build-managed devices: https://developer.android.com/studio/test/managed-devices
- DroidRunner README, limitações, segurança, release e licença: https://github.com/m96-chan/DroidRunner
- Review de avaliação de agentes, publicada em 24/04/2026: https://link.springer.com/article/10.1007/s10462-026-11571-0


## 6. Alternativa local mais promissora: Code on the Go (CoGo)

Consulta atualizada em 19/11/2026:

- CoGo Release 26.47 R2 foi atualizado em 17/11/2026; o projeto declara IDE offline-capable para desenvolvimento e build de aplicativos Android completos Kotlin/Java diretamente em ARM64, sem PC.
- A página oficial declara suporte ARM64, AGP 7.3.0–9.3.1, JDK 21 e Kotlin 2.3.21 recomendado. A necessidade declarada é aproximadamente 3,5 GB de armazenamento; memória livre mínima estimada 1,5 GB, com 4 GB recomendados.
- Artefatos de build (APK/AAB) ficam no armazenamento privado do CoGo e podem ser exportados pela interface. Portanto, não é um backend headless do app Kellvyn nem substitui automaticamente GitHub Actions, mas pode realizar uma compilação manual de referência no próprio celular.
- O wiki do CoGo contém instruções de migração para uma combinação Gradle 9.6.1 / AGP 9.3.1 / Kotlin 2.3.21. A página de compatibilidade atual afirma o conjunto de versões, mas as notas de 26.47 não descrevem explicitamente a troca de Gradle. Não assumir o Gradle efetivamente instalado sem consultar a versão dentro do app.

### Cruzamento com o projeto Kellvyn

Estado dos arquivos da branch de inteligência:
- AGP: 8.13.2 (android/build.gradle).
- Kotlin Gradle Plugin: 2.3.20 (android/build.gradle).
- Java source/target e Kotlin JVM target: 17 (android/app/build.gradle).
- compileSdk: 35.
- O CI atual instala Gradle 8.13.
- O repo não contém android/gradlew, gradlew.bat, gradle-wrapper.jar nem gradle-wrapper.properties.

A matriz Kotlin atual declara KGP 2.3.20–2.3.21 como totalmente compatível com Gradle 7.6.3–9.3.0 e AGP 8.2.2–9.0.0. A documentação Android declara que AGP 8.13 requer no mínimo Gradle 8.13; a tabela de R8/Kotlin 2.3 cita AGP 8.2.2–8.13 e R8 8.13.19 como mínimo. A configuração CI atual (Gradle 8.13 + AGP 8.13.x + KGP 2.3.20) é coerente com essas faixas publicadas. A combinação com o Gradle interno atual do CoGo ainda precisa ser medida; o CoGo pode usar uma versão diferente daquela usada no CI.

**Decisão:** CoGo é o candidato mais forte para uma PoC de build manual local, mas ainda não é um runner automático para os workflows. Não mudar AGP/Kotlin/Gradle do projeto estável para acomodar CoGo. Primeiro fazer uma prova separada, sem alterar a branch principal, e capturar versão, saída do build, APK, hash, logs e dependências resolvidas.

### Protocolo seguro da PoC no Poco F6

1. Conferir memória realmente livre, armazenamento e temperatura antes de importar o projeto. O requisito de 4 GB é RAM livre, não RAM total; o dispositivo nominal de 8 GB não garante essa folga.
2. Importar uma cópia de trabalho isolada; não editar a pasta/branch de produção.
3. Começar com sync e tarefa tasks, depois testDebugUnitTest, então assembleDebug. Não iniciar AVD ou testes instrumentados até confirmar o que o CoGo realmente suporta.
4. Comparar os resultados com o Gradle 8.13 do CI; guardar log integral, versão das ferramentas, commit exato e SHA-256 do APK.
5. Interromper o teste se ocorrer pressão térmica, queda acentuada de bateria, encerramento do app ou pressão de memória.
6. Só considerar CoGo um caminho repetível após duas compilações limpas do mesmo commit e uma compilação com alteração controlada.

Fontes oficiais:
- CoGo 26.47 R2, recursos, offline e exportação: https://appdevforall.org/code-on-the-go/
- CoGo compatibilidade, RAM/armazenamento/JDK/Kotlin: https://appdevforall.org/code-on-the-go/
- CoGo migração de toolchain: https://github.com/appdevforall/CodeOnTheGo/wiki/IMPORTANT:-Fix-project-breaking-changes-after-the-Gradle-AGP-Kotlin-toolchain-upgrade
- Kotlin Gradle Plugin compatibility: https://kotlinlang.org/docs/gradle-configure-project.html
- AGP → Gradle compatibility: https://developer.android.com/build/releases/about-agp
- Kotlin 2.3 / D8 / R8 requirements: https://developer.android.com/build/kotlin-support

## 7. Correção identificada no R&D Studio: Gradle sem Wrapper

A inspeção da árvore da branch de inteligência confirmou que o projeto não contém Gradle Wrapper. O workflow, porém, instala Gradle 8.13 diretamente. A implementação anterior do R&D Studio exigia gradlew e o JAR/properties do wrapper para criar uma tarefa, divergindo do ambiente real e impedindo o início de experimentos nessa branch.

Correção experimental aplicada na branch kellvyn-rnd/agent-trace-duration-port-20261118:
- O Studio prefere um wrapper completo e íntegro quando ele existe.
- Sem wrapper, aceita um Gradle externo de confiança, fora do repositório/worktree, com versão fixada (por padrão 8.13) e SHA-256 registrados no manifesto da tarefa.
- Verifica o executável antes e depois da execução; versão/hash ausente ou alterada bloqueia a tarefa.
- Os manifests novos registram o modo wrapper ou system; manifests antigos com hash do wrapper continuam sendo lidos.
- Três testes adicionais cobrem Gradle externo 8.13, recusa de binário substituído e bloqueio quando não há nenhuma rota de execução confiável.
- O conjunto Python passou a ter **20 testes escritos**. Estado atual: NOT RUN na versão de trabalho; o runner do GitHub continua indisponível, portanto não existe evidência de aprovação desses 20 testes.

A solução exige que o host configure KELLVYN_GRADLE_EXECUTABLE e KELLVYN_GRADLE_VERSION quando gradle não estiver no PATH. A localização e a integridade do Gradle são estabelecidas pelo host confiável; não aceitar caminho de executável fornecido por conteúdo de tarefa/agente.

## 8. Ajuste adaptativo de telemetria térmica — 19/11/2026

A leitura do código existente revelou que o monitor usava cache de 1 segundo para todas as respostas de getThermalHeadroom. A API Android diz que não há benefício em consultar mais de aproximadamente uma vez por segundo; o guia de Thermal API também orienta espaçar tentativas em 10 segundos quando o sensor devolve NaN. O código anterior repetia a tentativa após 1 segundo mesmo quando a leitura estava indisponível.

Correção isolada:
- Criado KellvynThermalHeadroomSampler, cache sem timer/polling.
- Amostras válidas mantêm intervalo de 1 segundo.
- NaN, valor negativo, infinito ou exceção são classificados como indisponíveis e passam a usar intervalo de repetição de 10 segundos.
- Quando volta uma amostra válida, o intervalo retorna a 1 segundo.
- O monitor Android agora delega a leitura ao sampler e remove o cache duplicado/constante antigo.

Validação local executada:
- Ambiente: kotlinc-jvm 1.9.0, JRE 21.
- O código isolado do sampler compilou localmente e quatro cenários determinísticos passaram (4/4): cache de leitura válida, recuo/recovery após NaN, recuo/recovery após exceção e rejeição de valores negativos/infinito.
- Isso valida a classe pura Kotlin reproduzida do fonte do branch. Não equivale ao Gradle/JUnit da aplicação, integração Android/PowerManager, AVD, nem teste físico Poco F6.
- Testes JUnit correspondentes foram adicionados ao repositório; Gradle/CI ainda NOT RUN.

Referências:
- Android PowerManager getThermalHeadroom: https://developer.android.com/reference/android/os/PowerManager
- Android ADPF Thermal API: https://developer.android.com/games/optimize/adpf/thermal

### Estado CI da rodada térmica
- Run 44428910941 / job 135240881720: runner_id=0, runner_name vazio, steps=[]; execução encerrada antes do primeiro step.
- Classificação: NOT RUN / runner não alocado. Não houve compilação Gradle nem JUnit/Android.


## 9. Medição de armazenamento e proteção do Worker — 19/11/2026

A bancada precisava enxergar não só RAM e temperatura, mas também o espaço de dados disponível para arquivos locais, checkpoints, caches e APKs. Foi acrescentada uma leitura sob demanda do volume privado do app com Android StatFs:
- bytes disponíveis para aplicações e capacidade total do volume onde fica filesDir;
- tempo de CPU acumulado do processo e número de processadores disponíveis no snapshot;
- relatório READ_ONLY passa a expor valores em MiB e "unknown" quando a API não oferece leitura.

A fila WorkManager agora exige duas condições de sistema antes de drenar tarefas em segundo plano:
- bateria não baixa (já existia);
- armazenamento não baixo (nova condição nativa requiresStorageNotLow).

O snapshot continua sendo observação sob demanda; não foi criado polling de espaço, bateria, CPU ou temperatura. O armazenamento observado é o volume privado de dados do app, não uma promessa de que todo o armazenamento nominal do aparelho esteja livre.

Testes escritos:
- teste do relatório verifica os bytes convertidos em MiB, CPU e processadores;
- teste instrumentado verifica que o WorkRequest publicado inclui as restrições battery-not-low e storage-not-low.
- Ambos aguardam runner Android; não foram executados por Gradle/JUnit.

Referências oficiais:
- https://developer.android.com/reference/android/os/StatFs#getAvailableBytes()
- https://developer.android.com/reference/androidx/work/Constraints.Builder#setRequiresStorageNotLow(boolean)
- https://developer.android.com/reference/androidx/work/Constraints#requiresStorageNotLow()

### CI depois das melhorias de armazenamento
- Run 44430446144, branch atual: job 135246159400, runner_id=0, steps=[].
- Classificação: runner não alocado / NOT RUN. O job não chegou a executar Python, Gradle, JUnit ou instrumented tests.

### Evidência local delimitada
- Sampler térmico puro Kotlin: 4 cenários determinísticos passaram localmente com kotlinc/JRE.
- Política de limiares térmicos/OEM: harness puro local da lógica de decisão passou 5 cenários; após acrescentar invariantes de armazenamento ao snapshot, a cópia isolada da regra passou 6/6. Esses harnesses não são o Gradle da aplicação nem compilação do monitor Android.


## 2. Limites mensuráveis do cérebro atual

A configuração padrão em `KellvynTransformerLanguageModel.Config` é:
- Vocabulário: 512 tokens (valor padrão, não o tamanho garantido de qualquer checkpoint treinado).
- Contexto: 256 tokens.
- Embedding: 32 dimensões.
- Cabeças: 4.
- Feed-forward: 64.
- Profundidade: um único bloco Transformer.
- Estimativa de parâmetros nessa configuração: 43.648 parâmetros, calculada a partir das matrizes de embedding, projeções de atenção, LayerNorm, SwiGLU e saída.

Limites da pipeline:
- No máximo 1.000 exemplos por treino.
- No máximo 500.000 bytes no corpus total.
- No máximo 100.000 passos planejados por chamada.
- Contexto permitido atualmente: até 256 tokens.
- O bundle é gerado no aparelho depois de treino autorizado; não há pacote de pesos pré-treinado embutido no APK/repositório.

Isso é uma prova de conceito de modelo pequeno, não um LLM geral. Um checkpoint que reduza a loss ou passe o quality gate fica apenas **elegível para benchmark de protocolo**; não prova conversa natural, raciocínio, código, planejamento ou competência geral.
