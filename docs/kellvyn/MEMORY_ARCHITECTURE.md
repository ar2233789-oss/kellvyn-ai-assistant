# Memória local da Kellvyn — KLM-001

**Estado:** domínio e adaptador Android implementados no laboratório; integração com interface/runtime ainda pendente de validação no aparelho.  
**Privacidade:** dados locais, escopos isolados, conteúdo criptografado no banco SQLite.

## Objetivo

Dar continuidade entre tarefas sem transformar toda conversa em memória automática. A memória só registra informação quando a origem é elegível e existe aprovação explícita do proprietário.

## 1. Tipos e proveniência

Cada registro possui:
- identificador estável;
- título, conteúdo e tags;
- tipo: preferência, fato do projeto, decisão, episódio de tarefa ou resumo de sessão;
- origem: declaração explícita do proprietário, ou resultado verificado de ferramenta;
- proveniência em texto;
- nível de confiança;
- data de criação e expiração opcional;
- classificação de privacidade e consentimentos;
- escopo de acesso.

Inferências do modelo, conteúdo externo e páginas Web não podem ser gravados como memória duradoura automaticamente. Uma tarefa só pode se tornar lembrança permanente depois da confirmação do proprietário. A aprovação não torna credenciais elegíveis: segredos e chaves são sempre recusados.

## 2. Isolamento de escopo

Escopos separados:
- global;
- projeto;
- conversa;
- tarefa.

A recuperação exige uma lista explícita de endereços autorizados. Um projeto não consegue ler memórias de outro apenas porque há palavras iguais. O agente recebe os registros como contexto não confiável — não como instruções que possam substituir políticas e permissões.

## 3. Recuperação

O ranker inicial funciona inteiramente local:
1. normaliza acentos e caixa;
2. tokeniza consulta, título, conteúdo e tags;
3. calcula sobreposição lexical;
4. considera correspondência no título e tags;
5. usa confiança e recência como fatores menores;
6. ordena por pontuação, data e ID;
7. limita o número de resultados.

Esse mecanismo é determinístico e auditável. Não é busca semântica por embeddings, nem substitui um índice vetorial futuro. A vantagem inicial é não depender de rede, pesos adicionais ou bibliotecas externas.

## 4. Persistência e segurança Android

Arquivo principal: `KellvynEncryptedMemoryRepository.kt`.

- SQLite para registros estruturados e operações parciais;
- AES-GCM com chave de 256 bits;
- chave gerada e mantida pelo Android Keystore;
- conteúdo serializado fica dentro do payload autenticado e criptografado;
- IDs de escopo são armazenados no índice como hash SHA-256;
- leitura/escrita corre em `Dispatchers.IO`;
- até 5.000 registros por escopo; consultas leem até 500 candidatos;
- o limite de capacidade não apaga memórias antigas silenciosamente;
- migração e downgrade ainda não implementados; o código bloqueia uma atualização destrutiva.

O banco `kellvyn_memory.db` foi excluído do Auto Backup e das transferências do Android. A chave Android Keystore é vinculada ao dispositivo; portanto, o banco criptografado não deve ser restaurado em outro aparelho sem um mecanismo de exportação e migração próprio. Essa exclusão evita restaurar um ciphertext sem a chave correspondente, mas também significa que uma troca de telefone exige uma futura ferramenta de migração segura.

## 5. Limites atuais

- KLM-001 ainda precisa de teste de integração Android/Keystore/SQLite em aparelho ou emulador.
- Não existe exportação/importação criptografada entre aparelhos.
- A classificação de privacidade ainda precisa de interface e confirmação real na conversa principal.
- Não existe ferramenta de “lembrar”/“esquecer” conectada ao chat.
- O agente já aceita memória por injeção de dependência, mas o runtime principal ainda não instancia o repositório.
- A busca é lexical; embeddings e busca híbrida ficam para uma fase posterior, após benchmark de memória e RAM.
- Não há aprendizado automático dos pesos a partir das conversas.

## 6. Critérios de conclusão do módulo

- negar gravação sem consentimento;
- negar conteúdo de origem inferida ou externa;
- negar credenciais;
- exigir autorização separada para dados pessoais sensíveis;
- isolar consultas entre projetos;
- filtrar memórias expiradas;
- apagar registro, escopo e expiração;
- criptografar payload e validar integridade;
- excluir banco vinculado à chave local de backup/transferência;
- testar recuperação e operações no Poco F6 antes de ativar no runtime.

## 7. Referências documentais

- Replit Agent Plan Mode: planejamento sem alterar código ou dados, aprovação de tarefas e transição para execução: https://docs.replit.com/pt/references/agent/plan-mode
- Replit App Testing: execução de testes, controle humano em etapas autenticadas e replay da sessão: https://docs.replit.com/pt/references/agent/app-testing
- Replit Checkpoints and Rollbacks: snapshots de arquivos, contexto de conversa e memória do Agent; rollback e continuidade: https://docs.replit.com/pt/references/version-control/checkpoints-and-rollbacks
- Android SQLite: https://developer.android.com/training/data-storage/sqlite
- Android Keystore / cryptography: https://developer.android.com/privacy-and-security/cryptography
- Android Auto Backup and data-extraction rules: https://developer.android.com/identity/data/autobackup

O projeto aplica padrões próprios com base em documentação pública. Não incorpora o código ou os mecanismos internos do Replit.
