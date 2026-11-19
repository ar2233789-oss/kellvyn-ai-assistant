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

