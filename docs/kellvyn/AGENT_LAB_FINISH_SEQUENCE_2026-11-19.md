# Plano de conclusão — Agentes, laboratórios e integração Kellvyn

**Data:** 19/11/2026  
**Branch de trabalho:** `kellvyn-rnd/agent-trace-duration-port-20261118`  
**Prioridade solicitada:** terminar agentes + Agent Creation Studio + Agent Lab + integração + validação antes de retomar as demais áreas do aplicativo.

## 1. Decisões que não mudam

- Uma única IA/modelo central; os 14 papéis são especialistas com instruções, contexto, limites e ferramentas diferentes, não 14 modelos independentes.
- O Orquestrador continua separado do cérebro. O cérebro entende e propõe decisões; o Orquestrador distribui trabalho, verifica política, controla recursos, timeout, cancelamento e reúne resultados.
- Agent Creation Studio define/versiona perfis. Agent Lab executa experimentos, preserva evidências e compara resultados. Não são o mesmo módulo.
- Offline-first, dados e projetos privados locais, serviços remotos opcionais e explícitos.
- Não incorporar SDK, chave, assinatura, hospedagem, modelo ou runtime pago de Copilot, Claude, Replit, Lovable ou Gemini como dependência obrigatória.
- Não modificar a interface/layout já aprovado durante esta fase de engenharia dos agentes.
- Não ativar o cérebro/chat enquanto o modelo e os gates de qualidade não passarem.

## 2. O que já existe na branch e o que isso realmente prova

### Agentes e orquestração

- 14 perfis especialistas versionados e compilados pelo Profile Compiler.
- Política central de ferramentas; perfis/skills não podem conceder permissões por conta própria.
- Delegação com contexto isolado, sem delegação aninhada e ferramentas READ_ONLY nesta fase.
- Orquestrador separado, com limites, cancelamento, timeout e duração monotônica da delegação.
- Profile Store com eventos STAGED/APPROVED/ROLLED_BACK, criptografia, hash encadeado e APIs confiáveis de aprovação/rollback.
- Limite atual: os papéis ainda não recebem escrita/edição irrestrita. Mudanças devem avançar via plano, workspace isolado, diff e autorização explícita.

### Agent Creation Studio

- Catálogo, inspeção, compilação, validação e consulta de benchmarks dos 14 perfis.
- Histórico/diff e preview de revisão de perfil sem ativar a mudança.
- Ainda falta completar o fluxo confiável de revisão do proprietário até staging/aprovação/rollback acessível no produto, sem entregar a autoridade ao modelo.
- A validação de perfil prova estrutura e contrato, não competência do agente.

### Agent Lab

- Fila/checkpoints, traces criptografados e encadeados, snapshots de recursos, métricas de delegação, comparação de traces e auditoria de prontidão.
- Catálogo atual de 28 casos de especialistas (mínimo de dois por papel).
- Executor real de benchmark existe como API do Runtime, usando a mesma inferência e ferramentas autorizadas; exige verificador independente.
- O relatório de prontidão não executa o benchmark nem declara competência. A execução cognitiva continua bloqueada enquanto não houver motor treinado e verificador confiável.
- Falta validar persistência/recuperação, replay, resultados e métricas no Gradle/Android e aumentar a cobertura comportamental com oráculos objetivos.

### Construção de aplicativos

- Há workspace local e compilador Android limitado a projetos Java simples, sem Kotlin/Compose, bibliotecas externas, recursos XML complexos ou assets adicionais.
- O bridge consegue validar estrutura, compilar, assinar e verificar APK; isso não comprova instalação, abertura, interação ou funcionamento.
- A escada `APP-01..APP-10` especifica testes externos obrigatórios, mas o chat principal ainda não gera apps por uma inferência aprovada e o fluxo nativo end-to-end não foi demonstrado.
- O benchmark do agente construtor não deve pontuar fixture determinística como sucesso neural.

### Bots

- `BotRuntimeService` mantém adaptadores Telegram/Discord habilitados em foreground service, com reconexão, tokens cifrados no Android Keystore e heartbeat do Discord.
- Foi acrescentado diagnóstico READ_ONLY `bot_runtime_health`: estado habilitado, presença de credencial e idade do último sinal de polling/heartbeat.
- O cérebro ainda não está pronto; por isso, a conexão do bot não significa que ele consiga responder com a IA.
- WhatsApp continua explicitamente indisponível. A Cloud API oficial recebe mensagens por webhook HTTPS publicamente acessível. Sem endpoint público/túnel/hosting, o APK local não consegue receber eventos de entrada por esse fluxo suportado. Não substituir por automação não oficial frágil nem afirmar compatibilidade.

### Cérebro e validação

- KN-003 ainda é protótipo pequeno: 43.648 parâmetros estimados na configuração default, um bloco Transformer, contexto 256 e vocabulário default 512.
- Não existe bundle de pesos treinado versionado nem corpus autorizado TRAIN/VALIDATION/TEST concluído.
- `KellvynRuntime.isReady() == false`; o chat principal permanece desligado corretamente.
- GitHub Actions ainda encerra jobs sem steps/logs e sem runner alocado; testes Python/Gradle/Android não podem ser marcados como aprovados. A varredura estática de delimitadores não substitui compilação.

## 3. Ordem de implementação

### Gate A — recuperar e provar a base de execução

1. Obter um executor real para a suíte: GitHub Actions quando houver runner alocado, ou PoC manual isolada no ambiente ARM64 compatível.
2. Rodar primeiro `py_compile` e os 20 testes da CLI R&D Studio.
3. Rodar Gradle JVM unit tests; só depois Android instrumented tests e AVD.
4. Corrigir apenas defeitos reproduzidos, atualizar evidência e repetir os testes de regressão.
5. Não mesclar a branch nem alterar o runtime de produção antes dos gates obrigatórios.

**Critério:** run com steps efetivamente iniciados, logs, relatórios JUnit e artefatos associados ao SHA exato.

### Gate B — fechar contratos dos 14 agentes

1. Para cada papel, validar missão, entradas, saída estruturada, limites, skills e ferramentas permitidas.
2. Cruzar perfil → política → manifesto → ToolSpec → implementação real → teste.
3. Diferenciar ferramenta declarada, implementada, registrada, permitida e comprovadamente executada.
4. Corrigir capabilities sem ferramentas reais; não anunciar imagem/3D/CAD como disponíveis apenas porque o enum existe.
5. Completar revisão de perfil, diff, staging, aprovação explícita do proprietário e rollback.
6. Evoluir indexação de código do modo lexical para símbolos/imports/referências incrementais e AST quando houver parser compatível.

**Critério:** nenhum perfil com permissão fantasma, nenhuma escalada via prompt/skill, testes de isolamento e revisão aprovados.

### Gate C — transformar o Agent Lab em laboratório executável

1. Expor de forma controlada execução de suítes com motor real e verificador independente; manter benchmark bloqueado sem motor/verificador.
2. Separar casos determinísticos de contrato, ferramenta real, competência cognitiva e revisão humana.
3. Cada caso grava runId/caseId, perfil/hash, commit, configuração do modelo, orçamento, ferramentas solicitadas/permitidas/executadas, evidências, estado e falha.
4. Implementar replay somente para ações seguras/read-only; replay de mutações exige nova autorização.
5. Comparar baseline/candidato com mesmo ambiente e fingerprints equivalentes; mostrar PASSED, REVIEW_REQUIRED, FAILED, BLOCKED e NOT_RUN separadamente.
6. Medir RAM, heap, PSS, CPU, armazenamento, bateria e temperatura nos limites das tarefas; execução sequencial por padrão no Poco F6.
7. Expandir benchmarks depois que o runner real executar a base de 28 casos; não aumentar a carga antes de comprovar memória/tempo.

**Critério:** nenhuma aprovação por autodeclaração do agente; todos os resultados têm evidência independente e trilha reproduzível.

### Gate D — Software Factory e testes avançados de apps

Inspirado nos comportamentos públicos de Android Studio, Claude Code, GitHub Copilot, Replit e Lovable, sem copiar implementação proprietária:

1. Planejar → revisar critérios → criar workspace isolado → explorar arquivos → produzir diff → pedir autorização → aplicar.
2. Construir com toolchain identificada e hash/versionamento; manter caminho simples leve e só admitir motores pesados por orçamento.
3. Build real → verificar APK/assinatura → instalar em AVD → abrir → interagir via UIAutomator/Espresso/ADB quando disponível → capturar screenshot/Logcat.
4. Verificar função por estado observado, não por texto do modelo.
5. Comparação visual por alinhamento, geometria, cores, clipping, hierarquia de acessibilidade e estados.
6. Robustez: rede desligada/instável, permissões negadas, processo encerrado/reaberto, rotação, armazenamento baixo e regressões.
7. Performance: latência, CPU, PSS/heap, armazenamento, aquecimento e queda de bateria.
8. Executar os dez casos APP-01..APP-10 em workspace limpo, pelo menos três tentativas independentes quando o backend existir.

**Critério:** compilar não equivale a funcionar; APK assinado não equivale a instalado; teste escrito não equivale a executado.

### Gate E — Bot Operations

1. Manter Telegram/Discord com reconexão limitada, last successful poll/heartbeat ACK, status stale e erros sem exposição de token.
2. Adicionar métricas de sessão/uptime e tentativas, sem polling agressivo ou wake locks permanentes.
3. Ações de restart/reconcile devem exigir política/consentimento separado; o especialista não pode desligar/alterar bots livremente.
4. Testar sobrevivência a troca de rede, Wi-Fi/dados móveis, falha de token, perda de heartbeat, serviço recriado e restrições de bateria do Android.
5. Documentar limitação de inicialização após reboot e políticas OEM/Android; não prometer 24/7 garantido num aparelho que pode ser encerrado pelo sistema.
6. WhatsApp somente com uma arquitetura de webhook HTTPS público explicitamente autorizada; enquanto a exigência for sem hosting/túnel, manter o adaptador indisponível.

**Critério:** distinguir serviço ativo, plataforma acessível, heartbeat recente e resposta gerada pelo cérebro.

### Gate F — integração com cérebro principal

1. Manter o cérebro e o Orquestrador como componentes separados.
2. Ligar perfil, ferramentas, contexto, memória, checkpoint, broker e verificador numa única execução completa.
3. Testar delegação útil, não delegar tarefas simples, resolver resultados conflitantes e retomar tarefas após reinício.
4. Treinar apenas com corpus autorizado e proveniência; separar treino/validação/teste e testar generalização.
5. Só ativar o chat quando o checkpoint passar protocolo, português, ferramenta, segurança, benchmark comportamental e estabilidade offline.

**Critério:** nenhuma simulação/fake-engine conta como prova de competência do modelo.

## 4. Etapa seguinte, somente depois de A–F

- Image Studio: análise, geração/edição local, referências múltiplas, edição localizada e preservação de regiões; Nano Banana é benchmark visual, não dependência.
- Vector Studio: criação/edição/exportação SVG e validação de geometria.
- 3D/CAD: parser/renderer, geometria, cena, exportação e validação de malha.
- Animation/Simulation: timeline, keyframes, interpolação, estados intermediários, colisão/movimento e replay.
- Data/Research Studio ampliado e biblioteca versionada de recursos/SDKs/skills.

Esses módulos devem carregar sob demanda e liberar buffers/caches ao terminar. Não carregar ferramentas visuais pesadas durante conversas comuns.

## 5. Regra de conclusão

Uma etapa só é marcada como concluída quando possui implementação, teste, execução no ambiente correto, evidência vinculada ao SHA, política/autorização testada e medição de recursos quando relevante. Sem runner, build ou evidência, o estado continua NOT_RUN/BLOCKED — nunca “pronto”.
