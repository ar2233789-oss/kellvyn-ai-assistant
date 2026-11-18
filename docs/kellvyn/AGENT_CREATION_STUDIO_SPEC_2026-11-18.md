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
