# Kellvyn — registro atualizado do laboratório

**Data:** 16/11/2026  
**Branch:** `lab/kellvyn-intelligence-core-20261116`  
**Repositório:** `ar2233789-oss/kellvyn-ai-assistant`

## Decisão de autoria e fundação neural

Kellvyn será uma inteligência e um sistema próprios. Nenhum modelo de terceiros foi escolhido, baixado ou integrado como cérebro da Kellvyn. Sistemas proprietários são referências somente pelas capacidades publicamente documentadas. Código, bibliotecas, pesos e dados externos só poderão ser usados com origem, licença, termos e atribuições preservados. Nenhum componente externo será apresentado como criação integral do projeto.

## Pesquisa consolidada

A matriz `CAPABILITY_RESEARCH_2026-11-16.md` estuda:
- Claude Code: regras, skills, hooks, subagentes e gestão de contexto;
- ChatGPT / OpenAI Agents: ferramentas, tracing, guardrails e orquestração;
- Replit: planejamento, aprovação, testes autônomos, replay, checkpoints e continuidade;
- Cursor: navegação de codebase, edição multi-arquivo, delegação e revisão;
- Android Studio: plano multi-etapas, build, testes, dispositivo, screenshots e Logcat;
- OpenHands, Aider, SWE-agent, mini-SWE-agent e OpenCode: arquiteturas públicas de agentes, workspaces, ferramentas, permissões e avaliação.

A pesquisa foi transformada em módulos próprios. Não foram copiados código privado, prompts internos, pesos privados ou identidade visual de terceiros.

## Código implementado no laboratório

### Planejamento e execução
- `KellvynTaskPlanner.kt`: plano estruturado com dependências, critérios, riscos, permissões, rollback e prova independente;
- bloqueio de ferramentas não registradas e de subestimação de risco;
- comprovação separada de pré-condições antes da execução;
- limite de chamadas, repetição e timeout no agente;
- o agente não chama retorno positivo de ferramenta de verificação independente.

### Memória local KLM-001
- `KellvynMemory.kt`: tipos de memória, origem, proveniência, confiança, escopo, consentimento, classificação de privacidade, expiração e ranker lexical em português;
- `KellvynEncryptedMemoryRepository.kt`: SQLite privado, payload AES-GCM, chave Android Keystore, leitura/escrita em thread de IO, limite de capacidade e isolamento por escopo;
- chave composta de banco por escopo e ID;
- inferência e conteúdo externo não entram automaticamente na memória duradoura;
- credenciais são recusadas; dados pessoais sensíveis exigem aprovação específica;
- backup e transferência excluem o banco cuja chave é vinculada ao aparelho;
- o agente consegue receber memórias explicitamente aprovadas como contexto não confiável e restrito ao projeto.

### Contexto e busca em projetos
- `KellvynContextAssembler.kt`: orçamento determinístico de contexto;
- mantém instruções de sistema e a última solicitação;
- preserva ordem cronológica e metadados de confiança;
- descarta mensagens antigas quando o orçamento não comporta tudo;
- `KellvynProjectContextRetriever.kt`: busca lexical no projeto explicitamente ativo, com limite de arquivos, trechos e resultados, filtros de caminhos/segredos e origem marcada como não confiável;
- `ProjectWorkspaceStore.readFileExcerpt()`: lê trechos limitados sem carregar o arquivo inteiro;
- `KellvynRuntime.createAgent()`: monta o agente com memória local e busca opcional do projeto ativo; a conversa principal ainda não chama essa fábrica porque não há backend neural conectado;
- o orçamento de contexto usa estimativa de caracteres, não contagem exata de tokens; o tokenizer real só poderá ser usado após integração neural.

### Protótipo neural
- `KellvynByteBrain.kt`: KBN-001, preditor experimental de próximo byte com pesos inicializados, gradientes, atualização de parâmetros e checkpoint próprios;
- KBN-001 demonstra mecânica de treino, não é um cérebro conversacional nem um modelo de linguagem avançado.

## Validação automatizada

As execuções concluídas do GitHub Actions passaram para:
- planejador e teste de risco/consentimento;
- política de memória, escopo e recuperação lexical;
- agente com memória injetada como contexto não confiável;
- montagem/limite de contexto;
- protótipo KBN-001 e verificação de gradientes em execuções anteriores.

A execução `43928133784` passou após corrigir a interpolação do trecho com número de linha. A execução mais recente `43928462023`, no commit `f9096480fd5ecc4427c7f4c17ec9d5d79fb48ea6`, também passou com a fábrica do runtime incluída. O workflow executa testes unitários e compila as fontes; não produz APK nem comprova execução no aparelho.

## Limites e bloqueios que permanecem

- `KellvynRuntime.kt` ainda não possui backend de inferência conversacional conectado;
- a fábrica do runtime prepara memória e busca de projeto para um agente, mas `MainActivity` ainda não invoca essa fábrica; o chat ainda não oferece operação de lembrar/apagar e não foi testado em aparelho;
- o armazenamento AES-GCM/Keystore e a leitura contextual do workspace ainda precisam de testes de integração Android em emulador/aparelho;
- armazenamento AES-GCM/Keystore precisa de teste de integração Android real ou emulador; exportação/migração segura entre telefones ainda não existe;
- a recuperação é lexical, não embeddings/busca semântica;
- nenhuma base neural está selecionada; nenhum benchmark físico de modelo foi feito no Poco F6;
- não há teste físico no Poco F6 desta etapa;
- `MainActivity.java` permanece divergente do hash de interface protegido em `.github/interface-lock.sha`; não editar a lock nem promover APK enquanto esse bloqueio não for resolvido sem alterar a interface aprovada;
- a interface protegida e a branch principal não foram modificadas pelo trabalho cognitivo desta etapa;
- a branch permanece experimental e nenhum APK dela está validado para instalação.

## Próximas etapas

1. terminar e inspecionar a execução CI atual;
2. adicionar testes de persistência/criptografia com Android test harness;
3. implementar armazenamento de checkpoints e histórico de tarefas com hashes;
4. ampliar o indexador de projetos com mapa de símbolos/imports e cache incremental, preservando limites de segurança;
5. melhorar esquemas de ferramentas e aprovação de ações mutáveis;
6. preparar benchmark de qualidade e execução offline sem pré-selecionar modelo;
7. desenhar interface de memória pelo chat principal sem tocar na aparência já aprovada;
8. integrar inferência neural somente depois de decisões documentadas de arquitetura, licenciamento e compatibilidade;
9. build, teste de instalação e execução real no Poco F6 em etapas independentes.

Cada capacidade só será declarada concluída com código, testes e evidência correspondente.
