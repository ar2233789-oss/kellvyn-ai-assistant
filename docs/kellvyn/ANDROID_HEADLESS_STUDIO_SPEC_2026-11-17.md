# Kellvyn — especificação do laboratório Android invisível

**Data:** 17/11/2026  
**Branch:** \`lab/kellvyn-intelligence-core-20261116\`  
**Dispositivo-alvo:** Poco F6 / HyperOS / 8 GB RAM  
**Modo de operação:** local e offline-first; rede opcional pela conexão do telefone  
**Interface:** nenhuma tela de Studio para o usuário; ferramentas internas chamadas pelos agentes da Kellvyn  
**Estado deste documento:** especificação de arquitetura e pesquisa; não representa implementação concluída

---

## 1. Decisão fundamental

O laboratório não será uma cópia dos programas desktop citados na pesquisa. Será um conjunto de **motores Android próprios, pequenos, invisíveis e acionados por ferramentas**, que reproduzem as operações importantes para o trabalho dos agentes.

A Kellvyn não precisa carregar um IDE visual, uma tela de edição, painéis, barras, animações, viewports permanentes ou janelas de ferramentas para que seus agentes possam programar, projetar, analisar imagens, montar cenas 3D ou treinar o cérebro.

O agente deve chamar uma operação com entradas estruturadas; o motor executa a operação; o resultado é devolvido com artefatos, diagnósticos, métricas e evidências.

A ausência de UI reduz custo de desenho, estado visual e renderização permanente. **Ela não torna gratuito o processamento da operação solicitada.** Por isso os motores devem ser carregados sob demanda, liberar memória ao terminar e obedecer ao gerenciador de recursos.

### Regras não negociáveis

1. Kellvyn continua sendo uma única inteligência, com os 14 papéis especialistas existentes.
2. O cérebro neural, tokenizer, pesos, treinamento e checkpoints de linguagem continuam autorais, construídos do zero.
3. Não baixar nem importar pesos pré-treinados de terceiros como cérebro ou substituto.
4. Não copiar programas proprietários nem transplantar IDEs desktop inteiros.
5. Bibliotecas auxiliares só entram após auditoria de origem, licença, tamanho, ABI, manutenção, dependências transitivas e compatibilidade real com Android.
6. Nenhuma UI de Studio será exposta ao usuário. A conversa principal permanece a superfície de trabalho.
7. Nenhum motor pesado fica inicializado sem uma tarefa que o necessite.
8. Uma tarefa só é concluída quando evidência independente valida seus critérios.
9. O alvo de desempenho é o Poco F6 real, não um desktop presumido.
10. Não alterar a interface aprovada durante a construção do laboratório.

---

## 2. Arquitetura: uma oficina compartilhada, vários motores sob demanda

\`\`\`text
                      CONVERSA PRINCIPAL
                             |
                       KELLVYN ÚNICA
                             |
                   Orquestrador / Planejador
                             |
              +--------------+--------------+
              |              |              |
           Agentes        Memória       Política
          existentes      e contexto   de segurança
              |              |              |
              +--------------+--------------+
                             |
                    CATÁLOGO DE FERRAMENTAS
                             |
                     GERENCIADOR DE TAREFAS
                             |
         +-------------------+-------------------+
         |                   |                   |
    Workspace           Recurso/CPU          Evidências
    e artefatos         RAM/temperatura      e checkpoints
         |                   |                   |
         +-------------------+-------------------+
                             |
                   ADAPTADORES ANDROID
                             |
      +-----------+----------+---------+-----------+
      |           |          |         |           |
   Código      Android     Visual    Neural     Dados/
   e linguagens Build      e imagem  e treino   arquitetura
      |           |          |         |           |
      +-----------+----------+---------+-----------+
                             |
                      RESULTADO VERIFICADO
\`\`\`

O catálogo de ferramentas descreve capacidades sem carregar seus motores. Um agente primeiro descobre quais ferramentas existem; o orquestrador autoriza a tarefa; o gerenciador carrega somente os módulos necessários.

### 2.1 Núcleo compartilhado

| Serviço | Responsabilidade |
|---|---|
| Tool Registry | Catálogo, esquemas de entrada/saída, versão, permissões e disponibilidade |
| Task Broker | Fila, prioridade, dependências, cancelamento, retomada e limite de concorrência |
| Workspace | Projetos isolados, arquivos, metadados, versões e artefatos |
| Artifact Store | Arquivos grandes no disco privado; referências pequenas no contexto |
| Project Index | Índice incremental de arquivos, símbolos, imports e dependências |
| Resource Governor | Orçamento dinâmico de memória, CPU, GPU, temperatura e bateria |
| Execution Broker | Invocação de motores e bibliotecas com limites e timeout |
| Approval Gate | Autorizações exatas, curtas, de uso único e verificadas no código |
| Evidence Store | Logs, hashes, screenshots, saídas de testes e resultados de build |
| Checkpoint Service | Retomada segura de tarefas e restauração de estado |
| Telemetry Local | Duração, picos de memória, falhas, cache hit, consumo estimado e causa de cancelamento |

### 2.2 Contrato obrigatório de ferramenta

Cada operação interna deve declarar:

- identificador estável e versão;
- agente/papéis autorizados;
- objetivo e descrição curta;
- esquema tipado de argumentos, limites e valores permitidos;
- risco: leitura, escrita reversível, destrutiva, rede ou controle de dispositivo;
- pré-condições e dependências;
- orçamento estimado de memória, CPU, armazenamento e tempo;
- timeout e política de cancelamento;
- se pode ser pausada e retomada;
- tipo e localização dos artefatos produzidos;
- critérios de sucesso;
- verificador independente;
- código de erro recuperável e recomendação de próxima ação.

As saídas devem ser estruturadas. Uma ferramenta não deve retornar um bloco enorme de código ou imagem ao contexto do agente quando pode retornar um resumo e um identificador de artefato.

---

## 3. Estúdios internos (módulos sem interface)

Os estúdios são capacidades lógicas; não são aplicativos separados e não precisam ficar ativos juntos.

### 3.1 Software Studio — código e linguagens

**Objetivo:** permitir que os agentes compreendam, alterem, testem e diagnostiquem projetos completos, sem depender de um editor visual.

**Operações necessárias:**

- listar e classificar projetos, módulos, arquivos e tipos;
- pesquisar texto, símbolos, referências e definições;
- interpretar árvores sintáticas (AST) e imports;
- detectar dependências e relações entre módulos;
- localizar erros e diagnósticos;
- aplicar patches pequenos e determinísticos;
- gerar diff revisável antes de gravar;
- formatar, validar e executar testes compatíveis;
- usar cache incremental para não reanalisar tudo após cada edição;
- registrar cada modificação e permitir rollback.

**Linguagens por prioridade:**

1. Kotlin e Java — núcleo Android e projetos Android;
2. JSON, XML, YAML, Markdown, SQL — configuração, dados e documentação;
3. HTML, CSS, JavaScript e TypeScript — aplicações e componentes web;
4. Python — dados, automação e experimentos;
5. C/C++ — bibliotecas nativas e kernels, somente depois de haver caso medido;
6. GDScript — projetos Godot quando a ferramenta correspondente estiver instalada.

A prioridade não significa que todos os compiladores estarão embutidos desde o primeiro APK. Cada linguagem terá uma capacidade declarada: leitura, parsing, análise, edição, compilação ou execução. Nunca anunciar “suporte” quando só existe realce de sintaxe.

**Pesquisa de implementação:**

- Tree-sitter / KTreeSitter é candidato para parsing incremental; o binding KTreeSitter documenta suporte Android/JVM, mas cada gramática precisa ser validada separadamente.
- Um índice próprio deve guardar símbolos, imports e referências; não depender apenas de busca lexical.
- LSP pode ser adaptado por linguagem quando o servidor e suas dependências funcionarem em Android. A adoção de um servidor completo não é requisito para as primeiras ferramentas.
- O editor visual não é necessário para o agente: o motor opera sobre buffers, AST, patches e arquivos.

**Limite conhecido:** o construtor atual valida e compila um perfil restrito de Android Java simples. Kotlin, Compose, dependências externas, assets e recursos avançados ainda precisam de uma estratégia própria e de gates separados.

### 3.2 Android Build & Test Studio

**Objetivo:** transformar uma especificação/projeto em APK verificável e provar o comportamento solicitado.

**Pipeline pretendido:**

1. validar estrutura, caminhos, manifesto e perfil de compatibilidade;
2. resolver dependências autorizadas e já disponíveis localmente;
3. compilar fontes e recursos;
4. gerar DEX e empacotar;
5. alinhar, assinar e verificar o APK;
6. registrar hashes e metadados do artefato;
7. instalar somente pelos fluxos permitidos pelo Android;
8. iniciar e testar o aplicativo quando houver executor compatível;
9. capturar logs, estados, hierarquia visual e screenshots;
10. executar critérios de aceitação e devolver evidências.

**Adaptado ao Android:** reutilizar o construtor Java local existente como primeiro backend; melhorar incrementalidade, cache de compilação, diagnóstico e saída de artefatos. Avaliar separadamente ECJ, D8/R8, AAPT2, zipalign, apksigner e ApkVerifier conforme arquitetura/ABI, licença e possibilidade de execução no próprio dispositivo.

**Importante:** a documentação oficial do Android explica o uso de AAPT2 e Build Tools, mas os binários host distribuídos no SDK não devem ser presumidos executáveis no Android arm64. Cada ferramenta precisa de build/ABI compatível e teste no aparelho.

**Teste de dispositivo:** instalação por PackageInstaller e execução de smoke tests no próprio aplicativo podem ser possíveis em escopos controlados; automação de outros apps, UIAutomator e ADB não são capacidades automáticas de um APK comum. Acessibilidade exige habilitação explícita e permissões; instalação pode exigir confirmação do sistema. O laboratório deve separar:
- validação estática;
- compilação e verificação de APK;
- instalação autorizada;
- teste instrumentado;
- teste de interação externa, quando legal e tecnicamente autorizado.

### 3.3 UI/UX Design Studio

**Objetivo:** dar aos agentes capacidade de projetar interfaces estruturadas, não apenas gerar imagens de telas.

**Modelo interno de design:**

- árvore de componentes;
- tokens de design (cores, fontes, dimensões, raios, sombras);
- constraints e regras responsivas;
- estados e eventos dos componentes;
- hierarquia e fluxo de navegação;
- propriedades de acessibilidade;
- referências visuais e origem dos assets;
- ligação de componente lógico a código real.

**Operações:**

- criar e editar árvores de componentes;
- calcular layouts em tamanhos de tela diferentes;
- validar overflow, clipping, contrastes e áreas de toque;
- comparar screenshots com referência;
- converter estrutura em código Android suportado;
- manter identidade visual e componentes já aprovados;
- fazer diff visual e rollback.

**Referências conceituais:** Figma e Penpot demonstram componentes, design systems, variáveis e prototipagem. A implementação Kellvyn será uma representação interna de dados e operações, não uma cópia de seus editores ou serviços.

### 3.4 Image Studio — imagem raster, vetor e composição

**Objetivo:** analisar, criar e transformar imagens com pipeline não destrutivo e consumo controlado.

**Modelo interno de documento:**

- dimensões, orientação, perfil de cor e formato;
- camadas e grupos;
- operações/ajustes não destrutivos;
- máscaras e seleções;
- transforms e pontos de controle;
- texto, formas e objetos vetoriais;
- referências de assets;
- histórico com operações reversíveis;
- exportação com qualidade e tamanho definidos.

**Operações iniciais:**

- inspecionar dimensões, metadados e regiões;
- redimensionar, recortar, girar e espelhar;
- ajustes de brilho, contraste, saturação, tonalidade e níveis;
- compor camadas, alpha, máscaras simples e modos de mesclagem;
- inserir texto, formas e SVG;
- comparar imagens, regiões e histograma;
- exportar PNG, JPEG, WebP e SVG quando suportado.

**Bases Android avaliadas:**

- Android Canvas, Bitmap, ImageDecoder, BitmapFactory e BitmapRegionDecoder para desenho e processamento por regiões;
- AndroidSVG para renderização de SVG estático — a documentação informa suporte amplo a elementos visuais SVG 1.1/1.2 Tiny, mas não a filtros SVG; não tratar como renderizador SVG completo;
- processamento por tiles e buffers reutilizáveis implementados no projeto;
- GPU somente para operações em que benchmark demonstre ganho líquido. A aceleração de Canvas pode aumentar RAM e não será ligada indiscriminadamente.

**Regra de memória:** a imagem original fica em disco/URI; prévias usam resolução reduzida; tiles são carregados quando necessário; operações geram novas referências/tiles em vez de manter várias cópias completas do bitmap.

**Limite:** geração visual neural é um motor separado do editor. O laboratório pode organizar prompt, referências, máscaras, composição, edição e avaliação; isso não garante, por si só, geração equivalente a serviços comerciais nem offline de alta qualidade.

### 3.5 3D/CAD Studio

**Objetivo:** criar e modificar cenas e modelos de forma programática, com visualização apenas quando necessária.

**Modelo interno:**

- cena, nós e hierarquia;
- posição, rotação, escala e transformações;
- vértices, arestas, faces e malhas;
- materiais, texturas e UVs;
- câmeras e luzes;
- esqueletos, animações e keyframes;
- parâmetros e unidades de medida para CAD;
- importação/exportação e proveniência de assets.

**Operações iniciais:**

- criar cena e objetos geométricos básicos;
- modificar transforms e parâmetros;
- gerar malhas primitivas e paramétricas;
- aplicar materiais e luzes;
- validar topologia e dimensões;
- importar/exportar GLB/glTF e formatos simples adicionais conforme compatibilidade;
- obter uma imagem de prévia apenas quando o agente/auditoria solicitar.

**Pesquisa de execução Android:** Filament é um motor de renderização 3D nativo projetado para ser eficiente em Android; SceneView fornece integração Android e carregamento de formatos como glTF/GLB, OBJ, STL, PLY e 3MF. São candidatos a visualização sob demanda, não dependências aprovadas automaticamente. A estrutura de cena e as alterações de geometria devem continuar separadas do renderer.

**Regra de economia:** não criar contexto GPU, renderer, mapas HDR ou texturas grandes enquanto a tarefa não exigir renderização. Operações de geometria e exportação devem ser possíveis sem manter uma viewport ativa.

### 3.6 Neural Studio — cérebro original Kellvyn

**Objetivo:** projetar, treinar, avaliar e otimizar o modelo que pertence à Kellvyn.

**Operações:**

- configurar dimensões e blocos próprios;
- versionar arquitetura, tokenizer e vocabulário;
- importar corpus autorizado e registrar proveniência;
- limpar, deduplicar e dividir treino/validação/teste;
- calcular número de parâmetros e memória estimada;
- treinar em lotes pequenos e canceláveis;
- registrar loss, gradientes, learning rate e generalização;
- salvar/restaurar pesos e estado do otimizador;
- medir latência, tokens/s, memória e temperatura;
- executar benchmarks de protocolo e competência;
- comparar checkpoints sem sobrescrever versões aprovadas.

**Regra de autoria:** pesos e treinamento do cérebro são próprios. LiteRT/ExecuTorch podem ser avaliados como runtimes ou aceleradores de inferência somente se suportarem fielmente a arquitetura e os pesos treinados pela Kellvyn. A NNAPI direta não será base de projeto, porque foi descontinuada no Android 15.

**Regra de recursos:** treinamento ocupa a faixa de computação pesada e não compete com build, renderização 3D ou processamento de imagens. O modelo ativo de inferência e a instância de treinamento não compartilham estado mutável; checkpoints são promovidos somente após validação.

### 3.7 Data & Architecture Studio

**Operações:**

- criar e consultar esquemas SQLite;
- validar JSON e JSON Schema;
- ler e produzir OpenAPI;
- construir diagramas de componentes, sequência, estados e relacionamentos;
- converter requisitos em contratos;
- validar migrações de dados;
- visualizar relações e dependências;
- detectar inconsistências entre documentação, código e modelos.

Esta área é principalmente texto estruturado e dados. Não precisa de renderer pesado.

### 3.8 Automation Studio

**Operações:**

- compor fluxos de tarefas;
- agendar tarefas permitidas;
- conectar ferramentas por contratos;
- definir gatilhos, condições, retries e timeouts;
- pausar, cancelar e retomar;
- executar ações Android com permissões explícitas;
- gerar logs e trilha de auditoria.

WorkManager é apropriado para trabalho adiável e persistente, não para manter execução ilimitada. Trabalho longo visível ao usuário pode exigir serviço em primeiro plano e notificação. Automação de outros apps por Accessibility Service é um recurso opcional e separado, habilitado pelo proprietário; não é uma permissão implícita da Kellvyn.

---

## 4. Matriz dos 14 agentes e seus equipamentos

Os especialistas atuais serão equipados por capacidade, não multiplicados.

| Papel existente | Ferramentas principais | Acesso inicial |
|---|---|---|
| RESEARCHER | web search, docs, comparador de dependências/licenças | leitura/rede autorizada |
| CODE_EXPLORER | árvore do workspace, parser, AST, símbolos, imports, referências | leitura |
| REVIEWER | diff, regras de arquitetura, análise estática, dependências | leitura |
| TEST_ANALYST | runner de testes, relatório, screenshots, logs, cobertura | leitura/execução controlada |
| REASONING_ANALYST | cálculo, restrições, comparador de alternativas, provas | leitura |
| LEARNING_ANALYST | corpus, tokenizer, métricas, treino e benchmarks | leitura; treino por autorização |
| PROGRAMMING_ARCHITECT | linguagens, projetos, diagramas, API schemas e build matrix | leitura/planejamento |
| MEMORY_ANALYST | banco de memória, índices, deduplicação e proveniência | leitura; mutações sob política |
| SECURITY_ANALYST | manifesto, permissões, sandbox, assinatura, dependências e segredos | auditoria |
| PLANNING_ANALYST | DAG, fila, prioridade, estimativa e checkpoint | leitura/planejamento |
| DEBUGGER | Logcat, stacktrace, reprodução, profiler e replay | diagnóstico/execução controlada |
| AUDITOR | verificadores, hashes, comparação e critérios de aceitação | leitura/verificação independente |
| OPERATIONS_ANALYST | ActivityManager, memória, temperatura, bateria, cache e filas | observação/limites |
| VISUAL_ANALYST | inspeção raster, SVG, screenshots, diff visual e cena 3D | leitura/transformação autorizada |

### Comunicação entre especialistas

- O coordenador cria um pacote de tarefa com objetivo, entradas, restrições e critério de conclusão.
- O especialista recebe somente contexto mínimo e ferramentas autorizadas.
- Especialistas não delegam recursivamente por padrão.
- Resultados dos especialistas são dados não confiáveis até serem verificados.
- O coordenador decide e o executor realiza ações.
- Escrita, exclusão, rede e controle do dispositivo exigem riscos e autorizações próprias.
- Uma mesma tarefa não deve ser repetida por todos os agentes; delegação exige justificativa e ganho esperado.

---

## 5. Gerenciador de recursos para Poco F6 / 8 GB

O fato de o laboratório ser invisível evita custo de editor e viewport permanentes. O custo computacional real continua existindo. A política será baseada em medição no aparelho, não em números arbitrários.

### 5.1 Carga classificada

| Classe | Exemplos | Política |
|---|---|---|
| Leve | consulta de índice, leitura curta, planejamento | concorrência limitada |
| Média | parsing, análise de dependências, vetores, pequenos ajustes de imagem | fila com limite de memória |
| Pesada | compilação Android, render 3D, imagem grande, treinamento | uma tarefa pesada por vez |
| Persistente | download autorizado, checkpoint, tarefa adiável | WorkManager/foreground conforme regras Android |

### 5.2 Regras obrigatórias

1. Descobrir memória disponível e memória do próprio processo antes de iniciar trabalho pesado.
2. Reagir a \`ComponentCallbacks2.onTrimMemory\`; não fazer polling agressivo de memória.
3. Observar estado térmico e reduzir ou suspender carga quando necessário.
4. Nunca executar simultaneamente treinamento neural e outra carga pesada por padrão.
5. Carregar módulos e dependências sob demanda e liberar renderer, bitmaps, buffers e caches ao terminar.
6. Manter cache com limites de bytes e política LRU, com invalidação por versão/hash.
7. Trabalhar com arquivos e referências no disco; evitar copiar projetos e bitmaps completos para o heap.
8. Fazer parsing/indexação incremental e reutilizar resultados inalterados.
9. Cancelar de modo cooperativo; salvar checkpoint antes de interromper tarefas retomáveis.
10. Preservar responsividade da conversa principal; trabalho pesado nunca deve ocupar a thread de UI.
11. Avaliar processo de worker separado apenas quando medição justificar; processos Android têm heaps separados e custo de IPC, portanto separação não é gratuita.
12. Não presumir que GPU/NPU seja sempre mais rápida ou mais econômica; comparar CPU e aceleradores no aparelho real.

### 5.3 Medições obrigatórias

Por tarefa, registrar localmente:

- tempo frio e quente;
- pico de PSS/RSS do processo quando observável;
- heap Java/Kotlin e buffers nativos relevantes;
- tamanho de cache antes/depois;
- CPU e número de threads;
- uso de acelerador quando identificável;
- temperatura/thermal status;
- bateria antes/depois em testes controlados;
- falhas, cancelamentos e motivo;
- resultado funcional e quantidade de retrabalho.

Não fixar teto universal de RAM apenas por estimativa. O orçamento deve usar memória disponível, classe do aparelho, pressão do sistema e resultados de testes no Poco F6.

---

## 6. Formatos e artefatos comuns

| Tipo | Formato/estrutura preferida |
|---|---|
| Fonte e patches | UTF-8, diff unificado, AST e metadados |
| Projeto Android | diretório privado versionado + manifesto do projeto |
| APK | APK assinado, hash, certificado e relatório ApkVerifier |
| Testes | JUnit/Android test reports, JSON de critérios e logs |
| UI | árvore de componentes versionada e tokens de design |
| Imagem | PNG/JPEG/WebP, SVG e documento interno de camadas |
| 3D | GLB/glTF e metadados de cena |
| CAD paramétrico | parâmetros versionados e formatos de intercâmbio suportados |
| Dados | SQLite, JSON, JSON Schema, CSV |
| API | OpenAPI |
| Diagramas | Mermaid/C4/UML em fonte versionada |
| Modelo neural | checkpoint próprio, tokenizer, config, otimizador e manifest de proveniência |
| Execução | JSON estruturado, logs e referência a artefatos |

Todo artefato deve ter origem, versão do motor, hash, tamanho, data e vínculo com a tarefa que o produziu.

---

## 7. Segurança, confiança e permissões

- Nenhum motor recebe acesso arbitrário ao armazenamento.
- Workspace opera com caminhos relativos normalizados e limites de tamanho.
- Conteúdo de arquivos, páginas Web, memória e retorno de agente é dado não confiável, nunca instrução de sistema.
- Escrita mostra diff e exige autorização exata do proprietário.
- Exclusão tem autorização diferente de escrita.
- Instalação, acessibilidade, câmera, microfone, notificações, rede e bots têm políticas independentes.
- Credenciais e tokens nunca entram em contexto comum, logs ou memória recuperável.
- Dependências devem ser pinadas, verificadas e registradas.
- Cargas geradas não executam fora do workspace/sandbox definido.
- Checkpoints são íntegros, autenticados e recuperáveis.

---

## 8. Estratégia de dependências

Não adicionar biblioteca apenas porque é popular. Para cada candidata, gerar ficha:

1. função concreta que resolve;
2. licença e avisos exigidos;
3. versão estável e manutenção;
4. tamanho do AAR/JAR/arquivos nativos;
5. número de dependências transitivas;
6. ABI e versões Android suportadas;
7. custo de RAM e inicialização;
8. possibilidade de carregar apenas quando necessário;
9. risco de conflito com dependências já existentes;
10. alternativa baseada em API Android nativa;
11. teste de integração mínimo;
12. decisão: adotar, adaptar, implementar internamente ou rejeitar.

### Candidatas de pesquisa (não aprovadas automaticamente)

| Capacidade | Candidata | Avaliação inicial |
|---|---|---|
| Parsing incremental | Tree-sitter / KTreeSitter | boa candidata; validar gramáticas e ABI |
| Raster/tiles | Android Bitmap/Canvas/BitmapRegionDecoder | primeiro caminho; API nativa |
| SVG | AndroidSVG | licença Apache-2.0; filtros SVG não completos |
| Render 3D | Filament | engine Android eficiente; carregar sob demanda |
| Integração 3D | SceneView | avaliar somente o subconjunto necessário; não adotar UI |
| Inferência própria | Kotlin CPU baseline; LiteRT/ExecuTorch como acelerador candidato | converter/testar a arquitetura própria; sem pesos externos |
| Build Java | ECJ + D8 já usado pelo protótipo | manter o backend restrito e ampliar por etapas |
| Build recursos Android | AAPT2/Build Tools | validar binários arm64 no Android antes de depender deles |
| Indexação | banco/índice leve próprio | evitar servidor LSP pesado inicialmente |

NNAPI foi descontinuada a partir do Android 15; não será a fundação da aceleração neural.

---

## 9. O que já existe e o que ainda não existe no repositório

### Base existente identificada

- 14 papéis especialistas e um coordenador Kellvyn.
- Planejador de tarefas com dependências, riscos e critérios.
- Memória local cifrada e montagem de contexto com orçamento.
- Checkpoints persistentes com hash e cifragem.
- Cinco ferramentas de workspace: listar projetos, listar arquivos, ler, escrever e excluir arquivos.
- Especialistas filtrados para READ_ONLY.
- Aprovação de uso único para escrita e exclusão no nível do runtime.
- Um construtor Android local experimental para perfil Java simples, com ECJ/D8, assinatura, verificação e fixture de teste.
- Protótipos neurais próprios e pipeline experimental, separados do chat.

### Bloqueios e limites já registrados

- O chat principal continua sem inferência neural aprovada; \`KellvynRuntime.isReady()\` permanece falso.
- Os 14 papéis compartilham a mesma inferência e ainda não demonstram competência cognitiva independente.
- Especialistas continuam restritos à leitura; ferramentas de escrita não estão liberadas genericamente.
- A UI de aprovação do proprietário ainda precisa ser conectada.
- O construtor ainda não é um ambiente completo para Kotlin/Compose, dependências, assets e aplicações complexas.
- Não há prova de criação autônoma de aplicativo funcional a partir do chat.
- Não há medição física no Poco F6 de RAM, latência, temperatura, bateria ou sessão longa.
- Não há prova de que todas as ferramentas host do Android SDK possam executar em Android arm64.

**A especificação não transforma nenhuma dessas lacunas em funcionalidade concluída.**

---

## 10. Testes de aceitação do laboratório

### Contratos e segurança
- Ferramenta aceita parâmetros válidos e recusa parâmetros inválidos.
- Especialista não consegue chamar ferramenta fora da própria política.
- Aprovação não pode ser reutilizada nem aplicada a outro conteúdo/caminho.
- Retorno externo não altera instruções confiáveis.
- Cancelamento não deixa tarefa falsamente concluída.

### Software Studio
- Encontrar símbolo, definição, referências e imports em projeto de teste.
- Reindexar somente arquivos alterados.
- Gerar diff válido e rollback.
- Detectar erro sintático e retornar localização.
- Compilar e executar somente perfis declarados como suportados.

### Android Studio interno
- Construir, assinar e verificar APK fixture.
- Instalar apenas por fluxo permitido.
- Executar smoke test e anexar log/captura.
- Reprovar resultado quando algum critério falhar.
- Declarar limitações do perfil do compilador.

### UI/UX
- Construir árvore de componentes a partir de especificação estruturada.
- Verificar dimensões, overflow, clipping e acessibilidade.
- Comparar capturas e identificar regiões divergentes.
- Preservar componentes protegidos.

### Image Studio
- Abrir imagem grande por prévia reduzida e região.
- Criar camadas/ajustes sem alterar o original.
- Desfazer e refazer operações.
- Exportar e validar o arquivo produzido.
- Medir pico de memória em imagens de dimensões progressivas.

### 3D/CAD
- Criar cena primitiva sem renderer.
- Alterar transformações e geometria.
- Exportar GLB e validar hierarquia/materials.
- Renderizar preview apenas sob demanda.
- Liberar recursos gráficos depois da captura.

### Neural Studio
- Treino reproduzível com dados autorizados.
- Checkpoint restaurável e integridade validada.
- Testes de gradiente e generalização.
- Medir CPU, RAM, latência e comportamento térmico.
- Não promover um modelo que apenas memoriza fixture.

### Desempenho no Poco F6
- Inicialização fria e aquecida por motor.
- Cada módulo inicia somente quando chamado.
- Motores inativos são descarregados.
- Conversa permanece responsiva sob uma tarefa pesada.
- Tarefa pesada cede diante de pressão de memória/temperatura.
- Checkpoint permite retomar tarefa interrompida.
- Registrar duração, RAM, bateria e temperatura, sem inventar medições.
- Teste prolongado com alternância entre código, imagem, 3D e inferência.

---

## 11. Ordem de implementação após a especificação

1. **Auditar e estabilizar a base compartilhada:** catálogo de ferramentas, contratos, workspace, artefatos, tracing e checkpoints.
2. **Construir Resource Governor e Task Broker:** orçamento dinâmico, fila, cancelamento, recuperação e exclusividade de trabalhos pesados.
3. **Evoluir Software Studio:** parsing, símbolos, imports, índice incremental, diffs e diagnóstico.
4. **Expandir Android Build & Test:** ferramentas compatíveis com arm64, perfis de projeto, assinatura, evidência e teste.
5. **Construir UI/UX Studio como modelo estruturado:** não como editor visual.
6. **Construir Image Studio por operações e camadas:** API nativa primeiro; GPU se benchmark justificar.
7. **Construir 3D/CAD como scene graph e geometria:** renderer sob demanda.
8. **Fortalecer Neural Studio:** arquitetura própria, avaliação, profiling e aceleração validada.
9. **Conectar os agentes por permissões e ferramentas:** cada especialista recebe somente suas capacidades.
10. **Integrar ao chat principal somente quando os gates cognitivos, de execução e de segurança forem aprovados.**
11. **Executar testes no Poco F6 físico e otimizar com medições reais.**

A interface aprovada permanece intacta. Toda a oficina funciona por trás da conversa principal.

---

## 12. Fontes técnicas consultadas

A pesquisa foi feita a partir de documentação e repositórios públicos. São referências de comportamento, APIs e possíveis auxiliares, não fontes de código proprietário ou pesos de IA.

- Android graphics APIs: https://developer.android.com/reference/android/graphics/package-summary
- BitmapRegionDecoder: https://developer.android.com/reference/android/graphics/BitmapRegionDecoder
- Android memory/process management: https://developer.android.com/topic/performance/memory/guide/process-splitting
- ActivityManager memory info: https://developer.android.com/reference/android/app/ActivityManager
- Thermal API: https://developer.android.com/games/optimize/adpf/thermal
- WorkManager long-running work: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running
- Android build command line: https://developer.android.com/build/building-cmdline
- AAPT2: https://developer.android.com/tools/aapt2
- Tree-sitter Kotlin binding: https://tree-sitter.github.io/kotlin-tree-sitter/
- AndroidSVG: https://bigbadaboom.github.io/androidsvg/
- Filament: https://github.com/google/filament
- SceneView Android formats: https://sceneview.github.io/docs/formats/
- LiteRT Android: https://developers.google.com/edge/litert/android
- NNAPI deprecation: https://developer.android.com/ndk/guides/neuralnetworks
- Android app sandbox: https://source.android.com/docs/security/app-sandbox
- PackageInstaller: https://developer.android.com/reference/android/content/pm/PackageInstaller
- Android Accessibility Service: https://developer.android.com/guide/topics/ui/accessibility/service
- OpenAPI: https://spec.openapis.org/oas/latest.html
- SVG: https://www.w3.org/TR/SVG/
- glTF: https://registry.khronos.org/glTF/
- Keras custom layers: https://keras.io/guides/making_new_layers_and_models_via_subclassing/

**Próximo marco:** converter esta especificação em contratos e testes dos serviços compartilhados antes de expandir os motores individuais. Não criar uma coleção de editores visuais; criar uma oficina interna que faça o trabalho.

---

## 13. Implementação inicial da fundação de recursos — 17/11/2026

Esta seção registra o que foi escrito depois da especificação. Ela não substitui os critérios de aceitação nem declara o laboratório inteiro concluído.

### Implementado no código

**Resource Governor puro (Kotlin):**
- Tipos explícitos para classe de trabalho LIGHT, STANDARD e HEAVY.
- Snapshot com RAM total/disponível, heap usado/máximo, PSS, pressão de memória, faixa térmica, previsão térmica, economia de energia, bateria e carregamento.
- Admissão baseada em reserva de memória + working set estimado, pressão do heap, estado térmico e bateria.
- Limites configuráveis, em vez de espalhar constantes por cada agente.
- Os thresholds atuais (reserva de 6%/192 MiB, heap soft 82%/hard 93%, estimativas 16/128/512 MiB) são **pontos de partida conservadores**, não medições nem valores finais do Poco F6.

**Task Broker compartilhado:**
- Dois permits de execução com fila FIFO da Semaphore de coroutines.
- LIGHT pode reservar um permit e coexistir somente com folga de memória/heap/temperatura.
- STANDARD e HEAVY reservam ambos os permits e não se sobrepõem a outra execução que use o mesmo broker.
- O broker mede novamente o aparelho depois de adquirir os permits; ele não confia na leitura anterior.
- Espera de fila tem timeout independente (10 s por padrão). Se não houver slot, retorna RESOURCE_QUEUE_BUSY e não inicia a operação.
- Permits parciais são liberados em timeout/cancelamento.
- Isto é uma trava/admissor de concorrência, **não** ainda uma fila persistente com retomada automática e prioridades por deadline.

**Monitor Android sob demanda:**
- ActivityManager.MemoryInfo para RAM/low-memory.
- Runtime para heap Java/Kotlin.
- Debug.getPss() para PSS do processo.
- PowerManager para economia de energia e estado térmico.
- Thermal headroom consultado no máximo uma vez por segundo e reutilizado no cache.
- BatteryManager + ACTION_BATTERY_CHANGED para percentual/carga.
- Nenhum loop de polling constante: amostra só quando a tarefa é avaliada ou quando diagnóstico é solicitado.

**Integrações:**
- ResourceRuntime application-scoped faz agente e APK Builder compartilhar o mesmo broker.
- As três rotas de treinamento neural próprio entram como HEAVY.
- A compilação e a retomada do construtor Android entram como HEAVY, com estimativa inicial de 768 MiB.
- A inferência usa STANDARD (estimativa inicial de 128 MiB).
- Indexação/busca estrutural do projeto usa STANDARD (estimativa inicial de 64 MiB).
- Ferramentas têm metadados de workload e working set estimado.
- Ferramenta READ_ONLY device_resource_snapshot permite ao agente de operações inspecionar as condições atuais.
- Quando um passo planejado é adiado por recursos, seu estado volta a READY e é persistido; a aprovação e o contador de tentativas permanecem, mas as evidências de pré-condições são descartadas para nova checagem.
- Timeout de execução começa somente depois que o broker concede recursos; timeout de fila não vira falha de compilação.
- Cancelamento do chamador não deve ser absorvido como timeout interno da ferramenta.

### Testes e verificações executados

- 8 testes puros do Resource Governor/Broker executados com Kotlin/JVM local: RESOURCE_GOVERNOR_UNIT_TESTS_PASS. Cobrem admissão por memória, heap, temperatura e bateria, exclusividade de trabalhos pesados, revalidação de snapshot e adiamento por fila ocupada.
- KellvynAndroidResourceMonitor.kt compilado isoladamente contra stubs das APIs Android usadas. Isso valida sintaxe/contratos de chamadas simuladas, **não** substitui compilação Android real.
- Foram adicionados testes unitários de retorno seguro de etapa a READY e instrumentais para snapshot Android e compartilhamento do singleton. Esses testes estão escritos, mas ainda aguardam execução Gradle/Android real.
- Workflow Kellvyn neural core tests no run 44062178882 e Poco F6 virtual comparison lab no run 44062178854 terminaram em falha sem steps nem logs_url; a consulta de logs retorna BlobNotFound. A evidência indica que o job não chegou a disponibilizar etapas/logs. Não comprova falha no código e também não prova a causa exata da falha de alocação.
- Nenhum APK, build Gradle completo ou teste físico no Poco F6 foi validado nesta sequência.

### Valores que exigem calibração física

- working set do compilador (768 MiB), treino (512 MiB padrão), inferência (128 MiB) e indexação (64 MiB);
- reserva de memória, limites de heap e número de tarefas leves simultâneas;
- efeito de renderização, buffers nativos e GPU no PSS;
- resposta térmica e consumo de bateria em sessões prolongadas;
- política de descarregamento de motores e cache após tarefas.

### Próximas lacunas de engenharia

1. Substituir o broker inicial por fila persistente com prioridades, cancelamento, checkpoint e retomada automática quando o Android permitir.
2. Adicionar registro de pressão por onTrimMemory e política central de descarte de caches/motores.
3. Introduzir métricas por operação e calibrar estimativas com traces do Poco F6 físico.
4. Criar manifests de capacidades por estúdio e carregamento lazy dos motores.
5. Equipar cada um dos 14 agentes com catálogos de ferramentas, skills, permissões e verificadores adequados.
6. Expandir o indexador sintático além do extrator lexical atual.
7. Implementar e validar os motores de UI/UX, imagem, 3D/CAD e dados, seguindo esta especificação.
8. Resolver o bloqueio de CI/runner antes de declarar testes Android aprovados.

