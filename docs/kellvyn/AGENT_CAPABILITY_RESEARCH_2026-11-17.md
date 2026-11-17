# Pesquisa complementar — agentes de engenharia (17/11/2026)

Esta pesquisa compara recursos de agentes por documentação pública oficial. O objetivo é identificar comportamentos verificáveis a reimplementar na Kellvyn, não copiar código proprietário, prompts internos, pesos ou marcas.

## 1. Android Studio — Gemini Agent Mode

Fontes oficiais:
- Agent Mode: https://developer.android.com/studio/gemini/agent-mode
- Recursos de IA no Android Studio: https://developer.android.com/studio/gemini/features
- Build, deploy e execução assistidos: https://developer.android.com/studio/run

Capacidades descritas na documentação:
- recebe objetivos de alto nível e decompõe trabalho em plano multi-etapas;
- pesquisa arquivos, lê conteúdo e procura texto no projeto;
- invoca ferramentas e pede permissões quando necessário;
- executa alterações em vários arquivos e itera sobre resultados;
- constrói o projeto e corrige erros de build;
- instala em emulador/aparelho conectado, inspeciona tela, tira screenshots, consulta Logcat e interage com o app usando ferramentas/ADB;
- permite conversas paralelas no painel do agente.

Aplicação Kellvyn:
- manter plano persistente e checkpoints antes/depois de ações;
- separar ferramentas de leitura, escrita, build, instalação e interação;
- tratar aprovação do proprietário como autorização de execução, não como mera confirmação textual;
- criar um ciclo real: editar → compilar → instalar → interagir → capturar logs → verificar critérios → corrigir;
- anexar evidências de build/device/screenshot/log ao checkpoint da tarefa.

Limite: a documentação descreve o fluxo do produto Android Studio com Gemini; não autoriza copiar o agente ou suas ferramentas privadas. A implementação Kellvyn será própria e executada em ambiente local.

## 2. Replit Agent

Fontes oficiais:
- Visão geral: https://docs.replit.com/pt/references/agent/overview
- Modo Plan: https://docs.replit.com/pt/references/agent/plan-mode
- Testes de aplicativo: https://docs.replit.com/pt/references/agent/app-testing
- Checkpoints e rollback: https://docs.replit.com/pt/references/version-control/checkpoints-and-rollbacks

Capacidades descritas:
- modo Plan para discutir abordagem e decompor objetivos em tarefas antes da alteração;
- revisão e aprovação do plano antes de transitar para Build;
- testes de aplicativo em navegador real, interação como usuário, replay da sessão e autocorreção;
- checkpoints automáticos nos marcos, incluindo estado de projeto/contexto no ambiente Replit.

Limites documentados importantes:
- a página de App Testing informa que, no momento da documentação consultada, o teste automatizado é para aplicações web JavaScript full-stack e Python Streamlit; não afirmar que esse recurso cobre apps Android nativos;
- checkpoints Replit abrangem estado do ambiente Replit, mas o formato e a infraestrutura são próprios do serviço e não serão copiados.

Aplicação Kellvyn:
- plano com tarefas e critérios de aceitação antes de alterações;
- checkpoints locais versionados + histórico Git;
- smoke tests que interagem com aplicação Android nativa via ADB/UIAutomator;
- replays de passos por logs e screenshots, em vez de depender de um serviço em nuvem.

## 3. Cursor Agent

Fontes oficiais:
- Agent Mode: https://cursor.com/help/ai-features/agent
- Visão geral e checkpoints: https://cursor.com/docs/agent/overview

Capacidades descritas:
- busca automática no codebase;
- edição de vários arquivos;
- execução de comandos/terminal;
- testes e correção de erros;
- modos distintos para construir, perguntar, planejar e depurar;
- checkpoints que permitem revisar e restaurar alterações.

Aplicação Kellvyn:
- o indexador deve combinar texto, símbolos, imports, referências e caminho do projeto;
- o agente deve mostrar diff e justificativa por arquivo;
- criar checkpoint antes de alterações multi-arquivo;
- usar modo de análise somente-leitura antes de autorizar escrita;
- usar execução de build/teste e evidência de runtime para depurar, não inferência textual sobre sucesso.

## 4. OpenAI Agents SDK — padrões gerais de orquestração

Fontes oficiais:
- Construção de agentes: https://developers.openai.com/tracks/building-agents
- SDK Python: https://openai.github.io/openai-agents-python/
- Observabilidade e tracing: https://developers.openai.com/api/docs/guides/agents/integrations-observability

Padrões descritos:
- agente como modelo + instruções + ferramentas;
- loop que continua após chamadas de ferramenta;
- handoffs para especialistas;
- guardrails para validar entradas e saídas;
- sessões para preservar contexto;
- tracing de chamadas de modelo, ferramentas, handoffs e guardrails.

Aplicação Kellvyn:
- as ferramentas tipadas, memória, plano e delegação já são módulos separados;
- adicionar tracing local estruturado para cada etapa: entrada, decisão parseada, ferramenta solicitada, autorização, execução, verificação, checkpoint e resultado;
- manter os registros sem segredos e com limites de tamanho/tempo;
- o agente não deve inventar sucesso: marcar ferramenta executada, resultado observado e critério verificado como estados distintos.

## 5. Lacunas identificadas na Kellvyn

1. Não há ainda tracing por execução do agente com correlação de modelo → ferramenta → verificador → checkpoint.
2. Os checkpoints salvam plano/estado, mas ainda precisam anexar referências de evidência produzida pelo executor e replay determinístico das ações.
3. A ferramenta de build precisa retornar artefatos e resultados verificáveis (compilação, APK, assinatura, instalação, crash log, interação).
4. O indexador do projeto ainda é sobretudo lexical; precisa de símbolos/imports e atualização incremental.
5. A inferência KN-003 acabou de ganhar adaptador estrito, mas continua sem dados de qualidade suficientes e não está ligada ao chat principal.
6. A UI não deve mudar durante esse trabalho cognitivo.

## 6. Ordem recomendada de implementação

1. Teste e estabilização do adaptador KN-003; garantir que peso aleatório falha fechado.
2. Tracing local com eventos tipados e redaction, ligado a checkpoints de tarefas.
3. Ferramentas locais de build, instalação, UIAutomator, screenshot e Logcat com contratos de saída.
4. Replay/verificação independente de critérios por etapa.
5. Indexação AST/símbolos/imports e busca de referências.
6. Só após o modelo aprender saídas estruturadas em corpus próprio, conectar o motor de inferência ao loop e depois ao chat.

## 7. Princípio de prova

Um teste só demonstra o que observou:
- teste unitário valida uma unidade e suas entradas/saídas;
- build valida compilação;
- instalação/launch no AVD valida aquele dispositivo virtual;
- interação UIAutomator valida o fluxo automatizado específico;
- teste físico mede o comportamento no Poco F6 real.

Nenhuma dessas categorias pode ser usada como substituta silenciosa de outra.
