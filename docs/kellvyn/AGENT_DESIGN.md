# Kellvyn — desenho do agente

## 1. Princípio

A Kellvyn é uma inteligência central. Planejador, leitor, programador, compilador, pesquisador e ferramentas são responsabilidades do mesmo sistema e do mesmo modelo neural. Não criar um enxame de modelos independentes nem um seletor de identidades.

## 2. Ciclo de execução

```text
Pedido
  ↓
Interpretar objetivo e restrições
  ↓
Buscar contexto relevante
  ↓
Criar plano estruturado
  ↓
Checar permissões e orçamento
  ↓
Selecionar uma ferramenta registrada
  ↓
Executar ação delimitada
  ↓
Observar saída real
  ↓
Validar saída e atualizar plano
  ├── precisa de outra etapa → repetir dentro dos limites
  ├── precisa de aprovação → parar e pedir aprovação
  ├── falhou → diagnosticar/recuperar dentro do orçamento
  └── passou → resposta final com evidência
```

## 3. Estrutura de estado da tarefa

Toda tarefa multi-etapas deve persistir:
- `taskId`, pedido original e data;
- objetivo final e critérios verificáveis;
- requisitos e restrições;
- plano com dependências;
- etapa ativa e etapa concluída;
- chamadas de ferramenta, parâmetros e resultado;
- artefatos gerados e hashes;
- permissões/consentimentos obtidos;
- falhas, tentativas e rollback;
- resumo final e evidências.

## 4. Planejamento e execução

A referência Android Studio/Gemini demonstra o fluxo de meta de alto nível → plano → ferramentas → revisão/aprovação → alterações em vários arquivos → build e correção. A Kellvyn deverá implementar o mesmo rigor sem depender do modelo ou serviço Gemini.

O padrão do OpenCode de separar planejamento sem escrita da execução com permissões é útil. Na Kellvyn, isso deve ser um estado interno do trabalho, não um segundo chat ou um novo seletor de modo na interface.

## 5. Camada de ferramentas

Cada ferramenta declara:
- nome e descrição;
- schema de entrada;
- schema de saída;
- categoria de risco;
- escopo de caminhos e recursos;
- timeout e limite de resultado;
- se é somente leitura ou mutável;
- tipo de aprovação exigido;
- maneira de cancelar;
- teste unitário e integração.

Categorias:
- **Leitura:** listar, pesquisar, ler, inspecionar.
- **Escrita local:** criar/editar arquivos no workspace.
- **Build:** compilar, assinar, gerar artefatos.
- **Instalação:** instalar/atualizar app após confirmação.
- **Rede:** pesquisar/obter conteúdo público quando Web habilitada.
- **Comunicação:** enviar mensagens por bots ou outros canais.
- **Dispositivo:** abrir app, capturar tela, interagir com controles por APIs autorizadas.

O modelo jamais ganha acesso irrestrito ao sistema de arquivos, terminal, contatos, notificações ou credenciais.

## 6. Orçamento e contenção

Controles obrigatórios:
- limite de chamadas por tarefa;
- máximo de ciclos do agente;
- timeout por ferramenta;
- cancelamento imediato;
- limite de bytes/tokens lidos;
- limite de arquivos indexados por ciclo;
- limites de saída;
- detecção de repetição de ferramenta;
- política de caminhos canônicos;
- workspace isolado;
- logs de auditoria;
- rollback antes de aplicar diffs perigosos.

Os limites devem existir no código. Instruções de prompt não são segurança suficiente.

## 7. Prompt injection e dados não confiáveis

Resultados da Web, arquivos, imagens, texto de issue e conteúdo de projeto são dados, não instruções confiáveis. O agente:
- separa system/developer/user de dados externos;
- nunca deixa arquivo ou página mudar identidade e permissões;
- não mostra segredos ou arquivos `.env`;
- não executa comandos extraídos de README, issues ou páginas sem uma tarefa autorizada;
- marca incerteza e origem de cada observação.

## 8. Edição de código

Inspirado no mapa de repositório do Aider e na engenharia de tarefas do SWE-agent:
1. identificar linguagem e projeto;
2. gerar mapa por diretórios, símbolos e dependências;
3. localizar poucos arquivos relevantes;
4. ler contexto suficiente e rastrear imports/usos;
5. formular plano e diff;
6. validar que diff não toca área protegida;
7. aplicar no workspace;
8. rodar format/lint/test;
9. analisar falhas;
10. aplicar correção mínima;
11. confirmar com testes e estado Git.

A arquitetura deve ter operações de leitura e escrita separadas. Escrita precisa de diff que o usuário possa revisar quando o risco exigir.

## 9. Criação de APK

O gerador de APK será uma skill do agente:
- requisitos e arquitetura;
- estrutura e conteúdo de arquivos;
- validação de caminhos e manifest;
- resolução de dependências;
- compilação;
- teste;
- assinatura e integridade;
- instalação e execução;
- captura de evidências.

O estado “APK concluído” precisa de arquivo existente, verificação de assinatura e teste correspondente. Build verde sem execução não prova que o app abre ou funciona.

## 10. Memória e compactação de contexto

A memória deve guardar fatos úteis com:
- origem (usuário, ferramenta, inferência);
- data e nível de confiança;
- escopo do projeto;
- consentimento;
- capacidade de edição e exclusão.

A compactação deve reter objetivo, restrições, decisões, arquivos alterados, estado de testes, erros e próximos passos. Não resumir de forma que apague uma restrição crítica.

## 11. Avaliação do agente

Conjunto de tarefas sintéticas e reais, sem dados secretos:
- encontrar arquivo;
- explicar fluxo de chamada;
- alterar uma função com teste;
- resolver build quebrado;
- gerar app pequeno;
- corrigir regressão de interface;
- recusar acesso fora do workspace;
- ignorar prompt injection em arquivo;
- parar quando falta permissão;
- não declarar ação sem evidência.

Métricas: sucesso verificado, regressão, custo de ferramentas, falhas, chamadas inválidas, tempo e RAM.
