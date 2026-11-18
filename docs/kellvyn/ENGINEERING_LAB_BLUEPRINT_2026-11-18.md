# Kellvyn Engineering Lab — bancada de pesquisa, desenvolvimento e auditoria

**Data:** 18/11/2026  
**Branch:** `lab/kellvyn-intelligence-core-20261116`  
**Objetivo:** dar continuidade técnica entre conversas e criar um processo reproduzível para evoluir o cérebro, os agentes e suas ferramentas sem retrabalho.

## 1. Três ambientes diferentes — não misturar

1. **Cérebro Kellvyn:** inferência neural principal, memória, planejamento e raciocínio. Não é substituído pelo orquestrador nem por SDK de terceiros.
2. **Agent Creation Studio + Agent Lab do aplicativo:** criação/versionamento dos 14 perfis, execução, traces, benchmark, recursos e comparação. Já existem componentes headless no código; não são uma IDE completa.
3. **Kellvyn Engineering Lab (este documento e scripts):** bancada de desenvolvimento do repositório. Faz mapa do código, inventário, auditoria estática, registro de pesquisa/decisões e snapshots com horário. Não é tela do app e não altera a interface aprovada.

## 2. Descobertas da pesquisa externa

### Aider — mapa estrutural antes de editar

Aider mantém um mapa conciso do repositório com símbolos, tipos e assinaturas, selecionando o contexto relevante por relações entre arquivos. Isso reduz edições sem conhecer abstrações já existentes.

**Adaptação Kellvyn:** o Engineering Lab gera `audits/REPOSITORY_MAP.md`, com arquivos e símbolos Kotlin, e uma auditoria JSON/Markdown datada. A primeira versão é um índice estático por regex; não é AST nem análise semântica. Evolução futura: mapa de dependências Kotlin/Gradle e seleção de contexto por tarefa.

Referências:
- https://aider.chat/docs/repomap.html
- https://aider.chat/docs/

### SWE-agent — interface de ferramentas desenhada para agentes

A pesquisa SWE-agent conclui que uma interface específica para agentes pode melhorar a navegação, edição e execução de testes. A documentação ACI destaca linter antes de aceitar edições, visualizador de arquivos com janelas limitadas e busca que retorna resultados compactos.

**Adaptação Kellvyn:** antes de uma alteração, localizar símbolos e dependências; limitar leituras por janela; validar sintaxe/diff antes de aceitar patch; executar teste específico e regressão. Uma edição futura deve ocorrer em workspace/branch isolada, nunca diretamente no código estável.

Referências:
- https://papers.nips.cc/paper/2024/hash/5a7c947568c1b1328ccc5230172e1e7c-Abstract-Conference.html
- https://github.com/SWE-agent/SWE-agent/blob/main/docs/background/aci.md

### OpenHands — separar agente, ferramentas, workspace e eventos

A arquitetura atual do SDK OpenHands separa o núcleo do agente, ferramentas, workspace/sandbox e servidor/API; usa componentes composáveis e eventos tipados. A interface de controle não executa ações diretamente: comunica-se com o servidor que possui o runtime.

**Adaptação Kellvyn:** preservar separação entre cérebro, orquestrador, política de ferramentas, executor/workspace e auditoria. Não importar o SDK nem mudar o modelo; aproveitar somente padrões arquiteturais. Em futuras tarefas mutáveis, cada especialista deve produzir patch num workspace isolado e o coordenador integra apenas após auditoria, testes e aprovação do proprietário.

Referências:
- https://github.com/OpenHands/software-agent-sdk
- https://docs.openhands.dev/sdk/arch/overview
- https://github.com/OpenHands/OpenHands/blob/main/docs/architecture.md

### LiteRT — caminho experimental de inferência Android

A documentação Google descreve LiteRT como runtime on-device e recomenda a API CompiledModel nas versões 2.x para aceleração. Isso é uma hipótese de otimização, não motivo para substituir o cérebro autoral nem prova de compatibilidade com o Poco F6.

**Adaptação Kellvyn:** primeiro documentar o grafo e os operadores do modelo próprio; depois comparar saída numérica, latência, RAM/PSS, temperatura e bateria com a mesma carga. Nenhuma troca de runtime até teste de equivalência e benchmark no aparelho-alvo.

Referência:
- https://developers.google.com/edge/litert/overview

## 3. Ferramentas entregues nesta etapa

Arquivo: `scripts/kellvyn-engineering-lab.py`

Comandos:
- `python3 scripts/kellvyn-engineering-lab.py audit`: cria snapshot JSON e Markdown com timestamp UTC, branch, HEAD, inventário de fontes/testes, runners declarados e hashes da interface protegida.
- `python3 scripts/kellvyn-engineering-lab.py log --kind research|decision|implementation|test|incident|handoff --summary "..." --status implemented|not-run|passed|failed|blocked|decision --evidence "arquivo ou URL" --next-step "..."`: registra um evento manual de trabalho no diário.
- A auditoria também atualiza `audits/REPOSITORY_MAP.md` e `audits/LATEST.md`.

O script é intencionalmente stdlib-only. Não instala frameworks, não baixa pesos, não envia código para serviços externos e não altera os hashes protegidos. O status da auditoria é sempre `NOT_RUN_BY_STATIC_AUDIT`; inventário não é compilação.

## 4. Arquitetura de destino da bancada

```text
Research notebook + fontes citadas
              |
              v
Repository map / arquitetura / dependências
              |
              v
Task plan + risco + critérios de aceite
              |
              v
Workspace isolado / patch / checkpoint
              |
              v
Static checks -> unit tests -> Android build -> emulator/device tests
              |
              v
Independent verifier + security audit
              |
              v
Evidence bundle + UTC activity log + decision record
              |
              v
Owner approval -> integration -> rollback-ready checkpoint
```

O Engineering Lab atual implementa o mapa estático e o registro/auditoria documental. O Agent Lab Android já contém traces, telemetria e benchmark. Workspace isolado por tarefa, executor completo de comandos/build e verificação cognitiva de produção continuam como lacunas; não devem ser descritos como prontos.

## 5. Auditoria de cada mudança

Toda alteração precisa registrar:
- horário UTC e horário local quando conhecido;
- commit/branch/HEAD;
- objetivo e razão técnica;
- arquivos alterados;
- risco e áreas protegidas;
- testes tentados e resultado literal;
- evidências (logs, relatórios, hashes, traces);
- estado inequívoco: planejado, implementado, não executado, aprovado, falhou ou bloqueado;
- próximo passo e dependência externa.

O script de atividade já existente registra commits e caminhos automaticamente quando GitHub Actions executa. O novo comando `log` registra pesquisa, decisões, incidentes, testes e handoffs que não são commits. Nenhum mecanismo consegue registrar raciocínio privado não materializado; registrar decisões técnicas, não conversas sensíveis.

## 6. Portões de qualidade

| Gate | Evidência necessária | Estado atual |
|---|---|---|
| Mapa e inventário | snapshot versionado | ferramenta escrita; execução pendente |
| Proteção visual | hash do arquivo comparado ao lock | auditoria será gerada pelo script |
| Compilação Kotlin/Android | log Gradle completo | bloqueado por runner sem steps |
| Testes unitários | relatório JUnit | escritos, não executados |
| Testes instrumentados | relatório emulator/ADB | não executados |
| Cérebro real | inferência com pesos e traces reais | não comprovado |
| Agentes reais | tarefa executada por perfil e prova externa | não comprovado |
| Poco F6 | build instalado, ações reproduzidas e medições | não testado |

## 7. Limites móveis e segurança

- Um modelo e uma inferência por vez; especialistas são perfis e não modelos paralelos.
- Execução sequencial como padrão; paralelismo só com margem medida.
- Não carregar todos os módulos no início; lazy loading e descarte continuam obrigatórios.
- Nenhum agente recebe permissão de autoaprovação, promoção de perfil ou alteração de política.
- Tarefas mutáveis devem usar workspace isolado, diff revisável e checkpoint reversível.
- Traces não devem conter prompts privados, segredos, argumentos sensíveis ou arquivos completos.
- Não instalar dependências pesadas ou frameworks de agentes de terceiros no APK.

## 8. Próximas etapas priorizadas

1. Restaurar execução de GitHub Actions. Os jobs observados falham com `runner_id=0` e zero steps; isso é pré-execução, não falha comprovada do Kotlin.
2. Executar o script de auditoria em runner real e verificar os snapshots gerados.
3. Fazer compilação/testes Gradle e instrumentados; corrigir falhas reais, não especulativas.
4. Criar mapa de dependências Kotlin e relação fonte→teste, sem duplicar o inventário existente.
5. Implementar workspace isolado e pipeline de patch/diff/rollback para tarefas de engenharia dos agentes.
6. Transformar os 28 critérios em oráculos determinísticos ou revisão independente; manter `REVIEW_REQUIRED` para critérios sem prova automática.
7. Só então conectar benchmark à inferência neural de produção e medir no Poco F6.

**Regra de encerramento:** nenhuma sessão termina apenas com “continue”. Deve atualizar diário, snapshot de auditoria, SESSION_CONTROL e deixar o próximo passo específico. Se o runner estiver bloqueado, não fingir que os testes passaram.
