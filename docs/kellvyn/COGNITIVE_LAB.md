# Laboratório cognitivo da Kellvyn

**Identificador:** KCL-001  
**Branch:** \`lab/kellvyn-intelligence-core-20261116\`  
**Escopo:** pesquisa, prototipagem, comparação e validação da inteligência. Não altera a interface protegida nem a branch de produção.

## 1. Objetivo

Criar um ambiente de engenharia para construir a inteligência própria da Kellvyn com qualidade comparável às capacidades públicas observadas em agentes modernos, sem copiar produtos, código proprietário, prompts privados, pesos não autorizados ou identidade visual de terceiros.

O laboratório é dividido em quatro áreas:

1. **Pesquisa de capacidades:** estudar documentação e demonstrações públicas.
2. **Implementação original:** escrever componentes específicos de Kellvyn.
3. **Integração auditada:** incorporar dependências, código ou pesos externos somente após aprovação de origem, licença, versão e compatibilidade.
4. **Avaliação:** comparar resultados com tarefas reproduzíveis, testes, logs e evidências.

## 2. Referências estudadas e tradução para projeto próprio

| Referência | Capacidade pública observada | Tradução original na Kellvyn |
|---|---|---|
| Claude Code | Instruções persistentes, regras, skills, hooks e subagentes | Regras do projeto, procedimentos versionados, eventos internos e tarefas especializadas sob o mesmo agente |
| ChatGPT / OpenAI Agents SDK | Ferramentas, sessões, guardrails, tracing, handoffs e estado de execução documentados publicamente | Registro tipado de ferramentas, memória de sessão, trilha de eventos e validação independente; sem dependência obrigatória dos serviços OpenAI |
| Replit Agent | Separação entre planejamento e construção, teste e correção iterativa | Plano estruturado antes de executar; execução em workspace; ciclos de teste e recuperação |
| Cursor | Navegação de codebase, edição multi-arquivo, terminal, projetos coordenados e subagentes especializados | Índice do projeto, seleção de contexto, diffs, verificação e responsabilidades internas focadas |
| Android Studio Gemini Agent Mode | Planejamento de tarefas, ações sobre projeto, build, testes, Logcat e revisão | Pipeline Android com gates separados para alteração, compilação, instalação e teste real |
| OpenHands SDK | SDK modular, ferramentas, workspaces, eventos e coordenação | Interfaces próprias desacopladas, trilha de eventos e executor confinado |
| Aider | Mapa de repositório e seleção de contexto para mudanças de código | Indexador próprio de símbolos/dependências e busca seletiva; componentes licenciados podem ser avaliados para incorporação |
| SWE-agent / mini-SWE-agent | Ciclo agente-ambiente para tarefas de software e avaliação reproduzível | Executor com ações tipadas, workspace e benchmark de tarefas |
| OpenCode | Regras de permissão, sessões e ferramentas em agente de programação | Política de permissões e skills locais com formatos próprios |

Estas referências são guias de capacidade, não autorização para copiar implementação privada. Onde houver código público com licença permissiva, a integração será analisada por commit e arquivo antes de qualquer uso.

## 3. Arquitetura original em construção

\`\`\`text
Pedido do proprietário
        |
        v
Interpretador de objetivo e restrições
        |
        v
Recuperador de contexto e memória
        |
        v
Planejador estruturado KCL
        |
        v
Validador de plano + grafo de dependências
        |
        v
Política de risco e aprovação
        |
        v
Executor isolado / registro de ferramentas
        |
        v
Verificador independente
        |
        v
Memória de evidências + resposta
\`\`\`

A interface neural permanece desacoplada: o modelo pode propor planos e chamadas, mas não pode ignorar validação, permissões ou critérios de conclusão.

## 4. Primeiro módulo autoral: planejador estruturado

Código: \`KellvynTaskPlanner.kt\`.

O módulo implementa:
- planos com objetivo, restrições, etapas, dependências, ferramentas, risco, pré-condições e critérios de sucesso;
- conferência de risco declarado contra o catálogo confiável de ferramentas, recusando ferramentas ausentes ou risco subestimado;
- prova separada das pré-condições antes de iniciar uma etapa;
- validação de IDs, limites, dependências inexistentes, ciclos e metadados;
- estados explícitos: aguardando, pronto, aguardando aprovação, executando, concluído, falhou, bloqueado e cancelado;
- aprovação obrigatória para tarefas não somente de leitura;
- bloqueio de etapas dependentes quando uma pré-condição falha;
- exigência de prova independente que cubra todos os critérios antes de marcar uma etapa como concluída;
- contagem de tentativas e trilha de evidências.

O módulo não gera planos em linguagem natural nem executa ferramentas ainda. Ele valida e governa planos estruturados fornecidos por uma futura interface neural.

## 5. Política de não plágio e origem

### Permitido após auditoria
- estudar documentação, artigos, APIs e comportamento publicamente documentado;
- usar algoritmos e padrões conhecidos, implementados de forma própria;
- incorporar código de repositórios públicos com licença compatível e atribuição;
- incorporar bibliotecas e runtimes licenciados;
- utilizar pesos de modelos sob licença e termos compatíveis;
- adaptar pesos por métodos autorizados, preservando lineage, autores e hashes.

### Bloqueado
- copiar código proprietário, prompts privados, pesos privados ou engenharia reversa proibida;
- remover cabeçalhos, créditos, avisos ou atribuições;
- copiar um produto inteiro e trocar marca/nome;
- chamar pesos derivados de treinamento integralmente do zero;
- usar dataset sem origem e permissão claras;
- ocultar dependências, versões ou alterações.

### Gate obrigatório antes de incorporação

Nenhum artefato externo entra no produto sem:
1. URL oficial e repositório de origem;
2. versão, commit e hash do arquivo/peso;
3. licença do código e licença/termos dos pesos separados;
4. arquivos LICENSE, NOTICE e atribuições exigidas;
5. auditoria de dependências transitivas e componentes incorporados;
6. objetivo técnico e justificativa de uso;
7. lista dos arquivos alterados/adaptados;
8. testes de segurança, regressão e desempenho;
9. registro da proveniência no repositório.

A licença precisa permitir o uso pretendido. Uma licença de código não cobre automaticamente pesos, datasets, marcas, documentação, serviços ou outros ativos.

## 6. Protocolo de avaliação

Cada experimento precisa declarar:
- hipótese e capacidade testada;
- código/commit e configuração;
- conjunto de tarefas e critérios de sucesso;
- restrições e riscos;
- resultados brutos, logs e artefatos;
- RAM, latência, armazenamento, temperatura e bateria quando rodar no Poco F6;
- falhas, regressões e limitações;
- decisão: aceitar, revisar ou descartar.

Não confundir:
- benchmark de modelo com benchmark de agente;
- resultado declarado pelo modelo com evidência do executor;
- teste unitário com build APK;
- build APK com instalação;
- instalação com funcionamento real no Poco F6.

## 7. Próximas camadas em ordem

1. Planejador estruturado KCL-001 e testes unitários — implementado e testado.
2. Verificador de evidências independente do modelo — contrato e gates iniciais implementados no planejador; verificador real por executor ainda pendente.
3. Memória local KLM-001 — domínio, política de consentimento, recuperação lexical, armazenamento AES-GCM/Android Keystore e exclusão de backup implementados; integração real e teste físico ainda pendentes.
4. Indexador de projetos e busca contextual.
5. Registro tipado de ferramentas com esquemas reais e permissões.
6. Workspace isolado, diffs e rollback.
7. Avaliação de tarefas multi-etapas e testes de regressão.
8. Auditoria de dependências e SBOM.
9. Benchmark de arquiteturas neurais e runtimes, sem selecionar base externa como cérebro da Kellvyn nesta etapa.
10. Integração Android e medição no Poco F6.

### Aprendizados adicionais do Replit

A documentação pública do Replit acrescenta quatro padrões úteis:
- Plan Mode discute e decompõe o trabalho sem alterar o app; a construção ocorre depois da aprovação.
- App Testing executa testes autônomos e permite assumir o controle quando uma etapa exige autenticação humana; mantém replay da sessão para inspeção.
- Checkpoints podem guardar arquivos, configuração, contexto de conversa e memória do Agent; rollback recupera um estado anterior.
- A documentação para criar agentes sugere persistência de estado entre execuções por banco de dados e habilidades versionadas em arquivos de instrução.

Na Kellvyn, isso foi traduzido para planejamento estruturado, aprovação de operações, evidência de teste, memória local criptografada e recuperação por checkpoints/estado Git. Não foram copiados código, prompts privados ou mecanismo interno do Replit.

## 8. Estado de execução

- A implementação KCL-001 é código original escrito para este repositório.
- O agente experimental existente ainda não está ligado a um modelo neural funcional.
- KBN-001 é um protótipo didático de previsão de bytes, não uma IA conversacional pronta.
- Nenhum teste físico no Poco F6 foi realizado nesta etapa.
- A interface permanece fora do escopo.
- Nenhum código ou peso dos sistemas citados foi copiado ou incorporado nesta etapa.

## 9. Referências públicas usadas na pesquisa

- Cursor Agent overview: https://cursor.com/docs/agent/overview
- Cursor subagents: https://cursor.com/docs/subagents
- Claude Code — instruções, skills, hooks e subagentes: https://claude.com/resources/articles/steering-claude-code-skills-hooks-rules-subagents-and-more
- Replit Agent: https://docs.replit.com/pt/references/agent/overview
- Replit Plan Mode: https://docs.replit.com/pt/references/agent/plan-mode
- Replit App Testing: https://docs.replit.com/pt/references/agent/app-testing
- Replit Checkpoints and Rollbacks: https://docs.replit.com/pt/references/version-control/checkpoints-and-rollbacks
- Replit Build an Agent: https://docs.replit.com/pt/build/build-an-agent
- OpenAI Agents SDK: https://developers.openai.com/api/docs/guides/agents/sdk
- OpenHands Software Agent SDK: https://github.com/OpenHands/software-agent-sdk
- Aider: https://github.com/Aider-AI/aider
- SWE-agent: https://github.com/SWE-agent/SWE-agent
- mini-SWE-agent: https://github.com/SWE-agent/mini-swe-agent
- OpenCode: https://github.com/anomalyco/opencode
- Android Studio Agent Mode: https://developer.android.com/studio/gemini/agent-mode
- Android Studio build/test/device agent tools: https://developer.android.com/studio/run
- Android SQLite: https://developer.android.com/training/data-storage/sqlite
- Android cryptography / Keystore: https://developer.android.com/privacy-and-security/cryptography
- Android Auto Backup: https://developer.android.com/identity/data/autobackup
- Android DataStore: https://developer.android.com/topic/libraries/architecture/datastore
- Android Keystore: https://developer.android.com/privacy-and-security/keystore
- REUSE compliance: https://reuse.software/tutorial/
