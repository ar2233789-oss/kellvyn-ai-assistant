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

- O app registra uma autorização com `KellvynRuntime.approveProjectFileWrite(projectId, path, content)` somente depois de mostrar a alteração proposta e receber confirmação explícita do proprietário.
- A autorização fica vinculada ao projeto, caminho normalizado e SHA-256 do conteúdo exato.
- Expira em 120 segundos por padrão, aceita uma única utilização e não sobrevive ao encerramento do processo.
- **O modelo não precisa conhecer nem gerar o token aleatório.** A ferramenta procura uma autorização pendente que corresponda exatamente ao projeto, caminho e conteúdo propostos. O argumento opcional `approval_id` existe apenas para uma chamada confiável que já tenha o ID.
- Qualquer mudança de caminho, projeto, operação ou conteúdo invalida a autorização.
- Remoção exige autorização separada por `approveProjectFileDeletion(projectId, path)`, também sem exigir token produzido pelo modelo.
- A autorização é verificada pelo coordenador e consumida dentro da ferramenta imediatamente antes da mutação.

## Especialistas

`KellvynAgent` delega usando `KellvynToolRegistry.readOnlySubset`. Os especialistas recebem somente ferramentas classificadas como `READ_ONLY`; não recebem gravação nem exclusão, mesmo que o modelo peça esses nomes na delegação.

## Integração

`KellvynRuntime.createProjectWorkspaceToolRegistry()` cria o catálogo local. `createProjectWorkspaceAgent(inference, activeProjectId, config)` cria o coordenador com a política de autorização exata.

**Limite importante:** essa integração está disponível na camada do runtime, mas o chat principal ainda não a utiliza. `KellvynRuntime.isReady()` permanece falso até haver inferência própria treinada e aprovada. Não confundir ferramentas implementadas com agentes cognitivamente validados.

## Testes escritos

- Unitários: expiração, replay, adulteração de conteúdo, projeto/caminho divergentes, separação entre autorização de escrita e exclusão, e bloqueio de rede/controle de dispositivo.
- Instrumentais Android: leitura real no workspace, gravação aprovada sem token no prompt, recusa sem autorização, exclusão aprovada sem token no prompt e filtro READ_ONLY.
- Instrumentais do agente: especialista de código lendo arquivo real sem receber ferramentas de mutação; coordenador gravando com aprovação exata.
- Esses testes estão escritos no repositório. A execução Gradle/Android está pendente porque os jobs recentes do GitHub Actions encerram com `runner_id=0` e zero etapas; não registrar como aprovados até um runner executar os passos.

