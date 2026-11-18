# Arquitetura do Agent Creation Studio invisível da Kellvyn

**Data:** 18/11/2026  
**Estado:** fases A/B e parte da C implementadas; revisão persistente E parcial escrita. Testes ainda aguardam execução Gradle/Android. Aprovação continua fora das ferramentas do modelo.  
**Superfície:** ferramentas internas headless, nunca uma tela de laboratório ou segundo chat.

## 1. Objetivo

O Agent Creation Studio define, configura, avalia e versiona os perfis especialistas usados pela única IA Kellvyn. Não cria 14 modelos neurais independentes. Cria contratos de trabalho especializados que compartilham a mesma inferência central e usam ferramentas conforme a política de menor privilégio.

Esta oficina é transversal aos oito estúdios de execução. Ela equipa e avalia os perfis que usam esses motores; não substitui Software, Android Build & Test, UI/UX, Image, 3D/CAD, Neural, Data/Architecture ou Automation Studio.

## 2. Base encontrada antes da implementação desta fase

Já existiam:
- enum dos 14 papéis em KellvynAgent;
- delegação interna com contexto separado e sem delegação recursiva;
- matriz central de capacidades em KellvynSpecialistToolPolicy;
- filtragem de ferramentas e bloqueio de mutação para especialistas;
- skills carregadas sob demanda;
- teste de contrato cobrindo os 14 papéis;
- KellvynStudioModuleManager, com manifests, leases, carregamento sob demanda e controle de recursos;
- fila/broker de recursos, workspace e checkpoints locais.

Lacuna encontrada: a missão e as instruções dos 14 papéis estavam codificadas num bloco when dentro do loop de delegação. Não havia catálogo versionado único, contrato uniforme de saída nem módulo invisível para listar, inspecionar e validar perfis.

## 3. Arquitetura de destino

Pedido do coordenador
    -> Agent Creation Studio
    -> Profile Registry
    -> Capability Mapper (política central; sem autoelevação)
    -> Skill Binder (não concede permissões)
    -> Profile Compiler
    -> Contract/Security Gate
    -> Isolated Evaluation Harness
    -> Independent Audit
    -> Versioned Profile Store
    -> Explicit promotion / rollback
    -> KellvynAgent specialist factory

## 4. Subsistemas necessários

1. Profile Registry: papel, versão, missão, limites, instrução e contrato de saída.
2. Capability Mapper: deriva ferramentas da matriz central; um perfil não pode conceder ferramentas a si próprio.
3. Skill Binder: associa procedimentos relevantes sob demanda, sem alterar permissões.
4. Profile Compiler: valida papel, versão, orçamento, risco, seções de saída e hash do perfil.
5. Evaluation Harness: executa casos fixos e registra prompt, contexto permitido, ferramentas solicitadas, observações e métricas.
6. Promotion/Audit: promove somente perfil que passou pelos gates; preserva diff, versão anterior, hash, aprovador e rollback.

## 5. Contrato mínimo de perfil

Cada perfil publicado deve possuir:
- papel estável e versão do esquema;
- versão do perfil e hash do conteúdo;
- missão e escopo explícitos;
- instrução especializada;
- capacidades resolvidas pela política central;
- skills recomendadas, com gatilhos e orçamento;
- ferramentas solicitadas, permitidas e recusadas registradas por execução;
- risco máximo e política de mutação;
- limites de passos, chamadas, contexto e tempo;
- formato de resposta: conclusão, evidência, incerteza e próxima ação;
- casos de competência e regressão;
- data, autoria, justificativa e estado de aprovação.

## 6. Segurança e autoridade

- O perfil não concede a si próprio capacidades.
- KellvynSpecialistToolPolicy permanece a autoridade final de ferramenta e risco.
- Especialistas não fazem delegação aninhada.
- Escrita, exclusão, instalação, acessibilidade e rede usam gates separados.
- Contexto de outro agente e resultados de ferramentas são dados não confiáveis.
- Perfil não pode alterar prompt confiável, política global ou limites do agente.
- Revisão experimental não substitui perfil estável até os testes passarem.
- Falta de versão, hash, evidência ou aprovação impede promoção.
- Auditor recebe os artefatos e as provas originais, não apenas o resumo do executor.

## 7. Testes do Studio

Catálogo: exatamente os 14 papéis, sem duplicidade, com versões válidas.

Compilador: recusar papel desconhecido, missão vazia, versão inválida, duplicidade, limites acima do orçamento e contrato de saída incompleto.

Permissões: tentar conceder escrita, rede indevida ou capacidade fora da matriz; todas devem falhar.

Isolamento: especialista recebe somente subtarefa e contexto explicitamente selecionados, não todo o histórico privado do coordenador.

Não recursão: qualquer tentativa de delegação aninhada é rejeitada.

Competência: avaliar separadamente pesquisa, exploração de código, revisão, teste, raciocínio, aprendizado, arquitetura, memória, segurança, planejamento, depuração, auditoria, operações e análise visual.

Regressão: toda revisão repete os casos anteriores; a versão estável permanece recuperável.

## 8. Recursos do Poco F6

Registro e compilador de perfis são LIGHT. Harness executa um papel por vez por padrão. Paralelismo só é permitido se Resource Governor comprovar margem de RAM, heap, temperatura e bateria. Testes Android, imagem e 3D usam motores STANDARD/HEAVY carregados sob demanda e liberados ao terminar.

## 9. Fases de implementação

- Fase A: registro de perfis versionados, instruções derivadas, módulo READ_ONLY de catálogo/inspeção/validação.
- Fase B inicial: Profile Compiler determinístico, resolução de capabilities pela política central e SHA-256 com campos UTF-8 prefixados por tamanho.
- Fase B restante: binding de skills recomendado, diff/versionamento de revisões e validação de compatibilidade.
- Fase C: harness isolado de contrato, ferramentas e regressão.
- Fase D: benchmark de competência cognitiva após conectar o backend neural aprovado.
- Fase E: armazenamento versionado, promoção explícita, auditoria e rollback.

A Fase A não significa que o Agent Creation Studio inteiro esteja concluído. Validação estrutural não é competência cognitiva demonstrada.

## 10. Estado atualizado em 18/11/2026

Implementado nesta sequência:
- KellvynSpecialistProfileRegistry com 14 perfis versionados por código;
- contrato uniforme de saída e limites por perfil;
- KellvynSpecialistProfileCompiler valida os 14 perfis, resolve capabilities pela política central e calcula SHA-256 determinístico com encoding por tamanho;
- KellvynAgentCreationStudio com catálogo, inspeção e validação somente leitura; lista e inspeciona hashes e bloqueia catálogo inválido;
- capacidades AGENT_DESIGN e AGENT_EVALUATION na matriz de capabilities;
- integração do módulo no runtime compartilhado e do perfil compilado no loop de delegação; hash do perfil usado acompanha a observação retornada;
- testes unitários e instrumentais escritos para catálogo, limites, hash, acesso por papel e matriz real de ferramentas.

Ainda pendente:
- execução Gradle/CI dos novos testes;
- persistência de revisões e revisão de diff de perfis;
- criação/edição de versões experimentais e fluxo de promoção com aprovação;
- harness de competência cognitiva ligado a inferência real;
- evidência de execução no Poco F6.



## 11. Atualização — persistência, auditoria e rollback — 18/11/2026
- KellvynSpecialistProfileStore grava eventos imutáveis STAGED, APPROVED e ROLLED_BACK em arquivos privados sequenciados.
- Payloads são criptografados com AES-GCM e alias dedicado no Android Keystore; o cabeçalho autenticado inclui sequência, horário, papel, versão, tipo de evento e hash anterior.
- A cadeia SHA-256 detecta alteração, lacuna e ordenação inválida; compare-and-swap exige o hash atual do ledger e do perfil ativo.
- Staging não ativa; aprovação exige ownerConfirmed no código confiável e o hash exato do candidato. Rollback só pode retornar a uma versão aprovada ou ao perfil-base original.
- KellvynRuntime carrega as versões aprovadas ao criar agentes por factories suspend, usando Dispatchers.IO. A aprovação e o rollback atualizam a snapshot usada em novas factories.
- O Studio agora oferece ferramentas READ_ONLY para histórico e diff de revisão, incluindo valores antes/depois limitados. Não há ferramenta de agente para aprovar ou promover.
- Testes unitários e instrumentados foram escritos; não há resultado CI/Gradle aprovado nesta etapa. O teste instrumentado pretende validar persistência entre instâncias e autenticação AAD com Android Keystore real.
- O escopo atual ainda não inclui um editor de perfis na interface, criação automática de draft por chamada do modelo ou aprovação em UI. A próxima peça confiável é a ação explícita de revisão do proprietário, preservando o layout aprovado.


## 12. Limites de recursos e validação — 18/11/2026
- O ledger append-only tem limite global de 256 eventos e máximo de 32 KiB por evento. Isso mantém o tamanho teórico dos arquivos numa faixa limitada para o alvo móvel; não é um banco de histórico ilimitado.
- As ferramentas de catálogo/inspeção/validação/histórico/diff têm estimativa de 24 MiB e classe STANDARD quando precisam ler o ledger; a listagem de benchmarks permanece LIGHT.
- O histórico devolvido ao agente mostra no máximo 12 eventos; o diff apresenta antes/depois com truncamento total de 6.500 caracteres.
- Testes unitários escritos cobrem ciclo stage/approve/rollback, CAS, corrupção, isolamento e diff. O teste instrumentado verifica persistência entre instâncias e autenticação AAD com Android Keystore.
- Estado de execução: ainda sem Gradle/JUnit/Android instrumented aprovado. Os jobs de CI recentes falharam antes de fornecer steps e logs_url; competência cognitiva dos papéis não foi avaliada.
- Fase E segue parcial: APIs internas de stage/approve/rollback existem; o caminho da confirmação do proprietário em código/app ainda precisa ser integrado. Não há edição de UI autorizada.


## 13. Última revisão de recursos e execução — 18/11/2026, 15:24 UTC
- As ferramentas de leitura com acesso ao ledger passaram a reservar 24 MiB e usar STANDARD; benchmark catalog permanece LIGHT. O teto físico configurado segue em 256 registros globais e 32 KiB por registro.
- Run 44335158436 / job 134909733286 falhou sem steps e logs_url. Isso não prova falha de compilação Kotlin/Gradle; nenhuma etapa foi exibida.
- Nenhum teste Gradle/JUnit/Android instrumented foi observado como iniciado ou aprovado.


## 14. Relação com o Agent Lab — atualização 18/11/2026

O Agent Creation Studio continua responsável pelos perfis: catálogo, compilação, revisão, versionamento, aprovação e rollback. Ele não deve ser tratado como a bancada completa de experimentação.

O **Agent Lab** é a camada complementar de execução e pesquisa:
- registra traces de coordenador, especialistas, modelo e ferramentas;
- inspeciona a integridade e compara execuções;
- executa cenários com um adaptador real sobre a inferência recebida;
- permite avaliar perfis candidatos sem ativá-los;
- mede snapshots de RAM disponível, heap, PSS, bateria e faixa térmica;
- mantém testes sequenciais e menor privilégio.

Arquivos centrais adicionados:
- `KellvynAgentLabTrace.kt` e `KellvynAgentLabTraceStore.kt`;
- `KellvynAgentLabStudio.kt`;
- `KellvynSpecialistAgentBenchmarkExecutor.kt`.

**Limite de conclusão:** o executor e os contratos estão escritos, mas o cérebro neural ainda não está conectado ao chat e a suíte não passou por Gradle/Android Runner. Portanto, o Agent Lab é uma infraestrutura de desenvolvimento em construção, não um ambiente já certificado. Não confundir módulo registrado, execução simulada, benchmark executado com inferência real e competência validada.



## 15. Capacidade do Agent Lab e proteção de recursos — 18/11/2026

O armazenamento dos traces foi ajustado para comportar um benchmark completo sem leitura integral repetida a cada gravação:

- 1.536 eventos por trace, 16 traces no total e registros limitados a 2 KiB; teto teórico aproximado de 48 MiB para os registros.
- Append usa cursor de sequência/hash em cache e reconcilia cursores obsoletos de outras instâncias antes de gravar.
- Consultas de histórico usam janela final limitada e ainda validam a cadeia completa; não carregar o trace inteiro para exibir apenas as últimas 48 etapas.
- O comparador limita cada trace a 1.536 eventos.
- O harness aceita no máximo 28 casos por suíte, correspondendo ao catálogo oficial atual.

Testes escritos:
- trace maior que 128 eventos;
- leitura tail de 12 eventos e resumo com contagem/primeiro/último horário;
- retomada do cursor após recriação do store;
- dois stores concorrentes em uma mesma raiz sem sobrescrita;
- bloqueio de uma suíte com 29 casos antes de qualquer execução.

Nenhum desses testes está aprovado por Gradle/CI até a infraestrutura alocar runner.


## 16. Operação do Agent Lab e limites de avaliação — atualização 18/11/2026

### Métricas de processo
- Snapshots medem CPU acumulada do processo, tempo monotônico e número de processadores, permitindo calcular uma aproximação de CPU% por janela de execução.
- A métrica é do processo Android inteiro, não de um agente isolado se houver trabalho concorrente.
- Não é medição de energia em watts/joules; o percentual de bateria e a faixa térmica são apenas amostras pontuais.
- Não existe polling permanente: as leituras acontecem nos limites das execuções.

### Persistência e auditoria
- A persistência de traces é best-effort e não interrompe a tarefa se arquivo/Keystore falhar.
- O painel distingue estado não testado, saudável, degradado e recuperado com falhas anteriores.
- Última falha identifica trace e tipo do evento, sem registrar prompt, conteúdo ou argumentos.
- Recuperar a escrita não recupera eventos perdidos; a lacuna permanece reportada.
- A limpeza exige confirmação do proprietário e só remove traces antigos, terminais e integralmente verificados. Não é ferramenta do modelo.

### Avaliação de competência
- As rubricas atuais dos 28 casos são texto livre. Ainda não existe verificador semântico objetivo e validado para todos os requisitos.
- O executor registra comportamento e evidências operacionais; não produz uma nota semântica nem aprova a própria resposta.
- Não transformar rubricas textuais em aprovação por correspondência de palavras.
- Próxima evolução precisa separar critérios determinísticos (estado de ferramenta, artefato, hash, saída de teste, dependência/ordem) de critérios que exigem revisão humana ou avaliação semântica independente.
- Critério sem oráculo válido deve permanecer REVIEW_REQUIRED/BLOCKED, nunca PASSED por autodeclaração.

### Estado de execução
As novas provas de persistência resiliente, saúde do gravador e telemetria foram escritas. Ainda não foram executadas pelo Gradle/JUnit/Android Runner; GitHub Actions continua falhando antes da alocação de runner. Não há build APK ou validação no Poco F6 nesta etapa.
