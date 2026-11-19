# Cérebro Kellvyn — capacidades finais e critérios de entrega

Data: 19 de novembro de 2026  
Branch: `kellvyn-rnd/reasoning-alternatives-20261119`  
Status: especificação de destino; não representa funcionalidades já entregues.

## 1. Princípio central

A Kellvyn terá uma única identidade, uma conversa principal e uma inteligência central. Ferramentas, módulos e, caso sejam reativados, especialistas internos serão subordinados a essa inteligência. Eles não serão provedores independentes, personalidades concorrentes nem substitutos do cérebro.

O núcleo neural será desenvolvido pela própria Kellvyn, com arquitetura, tokenizer, pesos, treinamento e avaliação próprios. Não serão incorporados Gemma, checkpoints, pesos ou adaptadores de terceiros. Pesquisar produtos existentes serve para aprender práticas de engenharia, não para copiar seus modelos ou código.

A aplicação será offline-first. Acesso à internet só ocorrerá quando a opção de conexão estiver habilitada pelo usuário. Dados, memória, projetos e modelos locais permanecerão no armazenamento privado do aplicativo, com limites e controles explícitos.

## 2. O que o cérebro deverá fazer quando concluído

### A. Conversa e raciocínio

- Entender solicitações em português natural e manter o contexto da conversa.
- Distinguir pedido, pergunta, hipótese, instrução, restrição e autorização.
- Dividir tarefas grandes em etapas, estimar dependências, escolher a próxima ação e explicar decisões importantes.
- Comparar alternativas, procurar contradições, identificar lacunas, revisar o próprio resultado e corrigir erros.
- Dizer quando não sabe, quando faltam dados e quando uma tarefa não foi executada.
- Transformar respostas em entregas concretas: arquivos, alterações de projeto, relatórios, planos e resultados verificáveis.

### B. Memória e continuidade

- Manter contexto de curto prazo da conversa e memória persistente de longo prazo controlada pelo usuário.
- Separar memória global, memória por projeto, histórico de tarefas e registros técnicos.
- Recuperar informações relevantes sem misturar projetos ou inventar lembranças.
- Permitir revisão, correção e exclusão de registros pelo usuário.
- Registrar origem, data e integridade de dados importantes; evitar guardar credenciais e segredos.

### C. Pesquisa e conhecimento

- Pesquisar na web quando a conexão estiver autorizada.
- Comparar fontes, datas e evidências; distinguir fato, inferência e opinião.
- Entregar sínteses com referências verificáveis.
- Pesquisar documentação técnica, bibliotecas, ferramentas, padrões e soluções alternativas.
- Continuar útil offline usando conhecimento local, arquivos autorizados e cache válido, sem fingir que uma pesquisa online foi feita.

### D. Arquivos, documentos e projetos

- Ler, resumir, comparar, organizar e transformar documentos e arquivos compatíveis.
- Criar e editar projetos em workspace privado, com histórico e recuperação.
- Gerar código, explicar código, encontrar defeitos e propor mudanças rastreáveis.
- Exigir autorização do proprietário para gravações ou exclusões sensíveis; apresentar o diff antes de aplicar mudanças relevantes.
- Preservar arquivos e partes da interface que o usuário marcou como corretos.

### E. Desenvolvimento Android e software

- Planejar implementação, editar código Kotlin/Java e recursos Android, identificar dependências e preparar builds.
- Executar testes unitários, instrumentados e de interface quando o ambiente de execução estiver disponível.
- Diagnosticar falhas de compilação, crashes, ANRs, permissões, consumo de memória e incompatibilidades.
- Instalar e validar builds em dispositivo/emulador autorizado, comparar screenshots e consultar logs.
- Medir desempenho antes e depois de otimizações; não declarar ganho sem benchmark.
- Para o aplicativo Kellvyn, preservar o layout original já aprovado e alterar apenas o que estiver explicitamente autorizado.

### F. Ferramentas, automações e ações no Android

- Acionar ferramentas locais registradas, com escopo, validação de argumentos, limites e permissões.
- Criar fluxos de automação, tarefas recorrentes e operações condicionais quando o Android e as permissões do usuário permitirem.
- Pausar, cancelar, retomar e recuperar tarefas com checkpoints.
- Gerenciar concorrência e prioridade para evitar que várias tarefas pesadas travem o chat ou disputem memória.
- Não executar operações destrutivas, enviar mensagens, publicar conteúdo, comprar ou alterar contas sem autorização apropriada.

### G. Orquestração interna e laboratório

O laboratório de especialistas permanece fora do foco de implementação imediato, mas o destino arquitetural prevê:
- um coordenador central que decompõe a tarefa e distribui subtarefas;
- especialistas internos com escopo limitado (por exemplo, pesquisa, código, segurança, dados e testes);
- execução paralela apenas para tarefas independentes, com escrita serializada quando houver risco de conflito;
- um verificador independente do executor, que avalia evidências, testes e critérios de aceitação;
- trilhas de auditoria, limites de recursos, cancelamento e recuperação;
- consolidação de todos os resultados em uma única resposta da Kellvyn.

Especialistas não poderão ampliar suas próprias permissões, aprovar alterações do proprietário nem declarar sucesso sem evidência.

### H. Imagem, áudio, vídeo e outros conteúdos multimídia

A meta é que a Kellvyn compreenda e produza conteúdo multimídia dentro do próprio ecossistema, incluindo análise de imagens/documentos visuais, voz, áudio e criação/edição de mídia. Isso exige componentes multimodais originais, dados com direitos de uso e avaliações específicas. O pequeno Transformer textual atual, sozinho, não executa essas tarefas.

Não serão usados serviços ou pesos de terceiros como atalho disfarçado. Cada capacidade multimídia só será anunciada como pronta depois de existir uma implementação própria, um fluxo funcional e testes de qualidade no hardware suportado. Caso uma tarefa exceda os recursos do aparelho, a Kellvyn deverá explicar o limite, e não simular que a concluiu.

### I. Jogos e interação visual

- Analisar regras, telas, estados e estratégias de jogos.
- Ajudar o usuário a planejar ações e interpretar o que está acontecendo.
- Quando houver integração Android autorizada e tecnicamente compatível, interagir por APIs/acessibilidade permitidas.
- Não prometer controle universal de qualquer jogo, nem burlar proteções ou mecanismos antitrapaça.

### J. Desempenho, privacidade e segurança

- Operar offline por padrão e respeitar a chave de acesso à rede.
- Monitorar RAM, CPU, bateria, aquecimento e tarefas em execução.
- Aplicar limites de contexto, tamanho de arquivo, número de chamadas e duração de tarefas.
- Tratar texto externo, páginas, arquivos e saídas de ferramentas como dados não confiáveis, nunca como autorização para ignorar regras.
- Validar completamente uma proposta neural antes de executá-la como chamada de ferramenta.
- Manter rastreabilidade suficiente para diagnosticar erros sem registrar segredos desnecessários.

## 3. O que não pode ser declarado pronto só por existir código

Uma função, classe, tela, agente ou benchmark de protocolo não prova competência geral. Para cada capacidade será necessário comprovar:
1. implementação integrada ao caminho real do usuário;
2. testes reproduzíveis aprovados;
3. comportamento seguro em entradas inválidas e casos adversos;
4. desempenho e estabilidade medidos no dispositivo-alvo;
5. documentação do que funciona e do que ainda não funciona.

Em especial, passar pela avaliação de loss do KN-003 ou pelo protocolo KELLVYN/1 não prova conversa natural, raciocínio geral, programação competente ou compreensão multimodal.

## 4. Situação técnica verificada nesta data

### Implementado no código da branch de pesquisa
- Tokenizer BPE próprio com fallback UTF-8 e token EOS dedicado.
- Transformer autorregressivo KN-003 experimental, com atenção causal, RoPE, Pre-LayerNorm, conexões residuais, SwiGLU e KV-cache.
- Otimizações estruturais de alocações e buffers na inferência/treinamento.
- Pipeline de pré-treinamento por documentos, com separação TRAIN/VALIDATION/TEST, proveniência e limites de corpus.
- Pipeline de ajuste fino supervisionado que continua os pesos e estado AdamW do bundle pré-treinado sem alterar a base antes de sucesso.
- Persistência do bundle com verificação de integridade.
- Adaptador de inferência que gera decisões estruturadas KELLVYN/1.
- Infraestrutura de memória, workspace, ferramentas e recursos já presente em outras partes do aplicativo.

### Bloqueios atuais
- O chat principal não está conectado ao KN-003.
- `KellvynRuntime.isReady()` ainda retorna `false`; `reply()` ainda informa que o cérebro está em construção.
- O adaptador de inferência é um caminho experimental de decisão estruturada, não um gerador de conversa livre validado.
- O modelo permanece minúsculo para a ambição final: a configuração registrada é vocabulário 512, contexto 256, embedding 32, 4 cabeças e feed-forward 64, aproximadamente 43,6 mil parâmetros.
- Não existe corpus autorizado do proprietário treinado nesta entrega, nem bundle de conversa competente demonstrado.
- Os testes novos existem no código, mas o Gradle/JUnit ainda não foi executado com sucesso nesta branch. Os jobs de CI observados falharam sem etapas/logs utilizáveis; isso não prova sucesso nem falha de compilação.
- Não há APK final, benchmark real de latência/RAM/bateria ou validação física no Poco F6 nesta entrega.

## 5. Ordem de conclusão — portões de qualidade

### Fase 0 — Recuperar validação confiável
- Corrigir ou substituir a execução de CI que está falhando antes de iniciar as etapas.
- Obter build Gradle real e executar todos os testes existentes.
- Corrigir falhas comprovadas; não empilhar mudanças grandes sobre código sem compilação.

**Portão:** build reproduzível e testes da base aprovados.

### Fase 1 — Tornar o KN-003 tecnicamente escalável
- Revisar arquitetura para permitir mais blocos/camadas e configurações controladas.
- Adicionar testes de forward, backward, checkpoint, geração, cancelamento e determinismo.
- Medir custo de treinamento e inferência; estabelecer limites compatíveis com o alvo.
- Criar avaliações de linguagem e instrução que não dependam apenas de loss.

**Portão:** checkpoint reproduzível, arquitetura dimensionável, benchmark e qualidade mensurados.

### Fase 2 — Dados e treinamento próprio
- Definir formato, licença, proveniência, deduplicação, limpeza e divisão de corpus.
- Treinar somente com dados autorizados.
- Fazer pré-treinamento e ajuste fino encadeados; avaliar validação e teste sem vazamento.
- Comparar o modelo com baselines simples e registrar as limitações.

**Portão:** bundle próprio reproduzível e ganho verificável em tarefas reservadas.

### Fase 3 — Inferência ligada ao chat
- Definir um formato de conversa adequado, sem obrigar texto natural a ser emitido como um protocolo hexadecimal excessivamente caro.
- Preservar um canal tipado e estritamente validado para ferramentas.
- Implementar streaming, cancelamento, histórico limitado, contexto, EOS e recuperação de erro.
- Conectar o chat principal sem habilitar falsamente `isReady()`.

**Portão:** conversa local ponta a ponta, persistente e testada, com ferramentas bloqueadas por padrão até validação.

### Fase 4 — Memória, pesquisa e ferramentas
- Ligar memória e recuperação ao fluxo de conversa.
- Integrar ferramentas com permissões, validação e logs.
- Implementar pesquisa online opcional e respostas com fontes.
- Adicionar verificação independente dos resultados antes de afirmar conclusão.

**Portão:** tarefas completas com evidências, segurança e funcionamento offline/online conforme a preferência do usuário.

### Fase 5 — Orquestração e laboratório
- Reativar o laboratório somente depois de o núcleo conseguir conversar e chamar ferramentas de forma estável.
- Adicionar coordenador, especialistas restritos, execução paralela controlada, verificador e trilhas de auditoria.
- Testar conflitos, falhas parciais, cancelamento e recuperação.

**Portão:** uma tarefa complexa pode ser dividida e concluída sem perda de contexto, conflito de escrita ou falsa declaração de sucesso.

### Fase 6 — Multimodal e capacidades avançadas
- Construir e treinar capacidades originais de imagem, áudio/voz e demais formatos necessários.
- Integrar análise visual, criação de conteúdo e interação com jogos por etapas.
- Medir memória, latência, temperatura e bateria em cada fluxo.
- Não carregar simultaneamente componentes pesados sem um orçamento de recursos validado.

**Portão:** cada capacidade tem benchmark próprio e funciona no hardware anunciado.

### Fase 7 — Entrega no Poco F6
- Produzir APK instalável, assinado e reproduzível.
- Testar HyperOS, permissões, armazenamento, rede opcional, notificações, processos em segundo plano e retomada.
- Medir RAM, CPU, bateria, temperatura, latência, crashes e ANRs em uso real.
- Fazer regressão visual para preservar o layout original aprovado.

**Portão:** instalação e operação física estáveis, offline-first, com resultados medidos e lista transparente de limites.

## 6. Regra para acelerar sem degradar qualidade

- Pesquisas independentes podem ocorrer em paralelo.
- Alterações no mesmo arquivo ou em componentes acoplados são serializadas.
- Cada fatia termina com teste focado antes da próxima.
- Benchmarks orientam otimizações; não se atribui percentual de ganho sem medição.
- Mudanças grandes de arquitetura aguardam build/teste confiável da base.
- A cada entrega, registrar commit, arquivos alterados, testes executados e bloqueios restantes.

## 7. Critério final de conclusão

O cérebro só será chamado de concluído quando o caminho real for: receber uma solicitação no chat → compreender e planejar → consultar memória/contexto → escolher e validar ferramentas → executar ou responder → verificar resultado → persistir a continuidade → explicar o que foi efetivamente concluído. As capacidades multimodais, desenvolvimento Android, automação e interação com jogos terão seus próprios portões de qualidade.

Até esses portões serem aprovados, esta especificação é o destino técnico, não uma alegação de que tudo já funciona.
