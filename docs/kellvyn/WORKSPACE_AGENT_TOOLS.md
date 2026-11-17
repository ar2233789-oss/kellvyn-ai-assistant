# Ferramentas locais do agente Kellvyn

Atualizado em 17/11/2026.

## Ferramentas registradas

| Ferramenta | Risco | Operação |
|---|---|---|
| `workspace_list_projects` | READ_ONLY | Lista projetos locais do workspace privado |
| `workspace_list_files` | READ_ONLY | Lista caminhos relativos de um projeto |
| `workspace_read_file` | READ_ONLY | Lê trecho UTF-8 limitado de um arquivo |
| `workspace_write_file` | REVERSIBLE_WRITE | Cria/substitui um arquivo |
| `workspace_delete_file` | DESTRUCTIVE | Remove um arquivo comum |

As ferramentas operam somente sobre `ProjectWorkspaceStore`. Não invocam shell, não executam código gerado e não acessam diretórios externos ao workspace.

## Autorização de escrita

- O app pode emitir uma autorização com `KellvynRuntime.approveProjectFileWrite(projectId, path, content)`.
- O token fica vinculado ao projeto, caminho normalizado e SHA-256 do conteúdo exato.
- O token expira em 120 segundos por padrão, aceita uma única utilização e não sobrevive ao encerramento do processo.
- O app deve chamar a emissão do token somente depois de mostrar a alteração proposta e receber confirmação explícita do proprietário.
- Uma mudança de caminho, projeto ou conteúdo invalida a autorização.
- Remoção exige autorização separada por `approveProjectFileDeletion(projectId, path)`.
- A autorização é verificada pelo coordenador e consumida dentro da ferramenta imediatamente antes da mutação.

## Especialistas

`KellvynAgent` delega usando `KellvynToolRegistry.readOnlySubset`. Os especialistas recebem somente ferramentas classificadas como `READ_ONLY`; não recebem gravação nem exclusão, mesmo que o modelo peça esses nomes na delegação.

## Integração

`KellvynRuntime.createProjectWorkspaceToolRegistry()` cria o catálogo local. `createProjectWorkspaceAgent(inference, activeProjectId, config)` cria o coordenador com a política de autorização exata.

**Limite importante:** essa integração está disponível na camada do runtime, mas o chat principal ainda não a utiliza. `KellvynRuntime.isReady()` permanece falso até haver inferência própria treinada e aprovada. Não confundir ferramentas implementadas com agentes cognitivamente validados.

## Testes escritos

- Unitários: expiração, replay, adulteração de conteúdo, projeto/caminho divergentes, separação entre autorização de escrita e exclusão, e bloqueio de rede/controle de dispositivo.
- Instrumentais Android: leitura real no workspace, gravação aprovada, recusa sem token, exclusão aprovada e filtro READ_ONLY.
- Instrumentais do agente: especialista de código lendo arquivo real sem receber ferramentas de mutação; coordenador gravando com aprovação exata.
- Esses testes estão escritos no repositório. A execução Gradle/Android está pendente porque os jobs recentes do GitHub Actions encerram com `runner_id=0` e zero etapas; não registrar como aprovados até um runner executar os passos.

