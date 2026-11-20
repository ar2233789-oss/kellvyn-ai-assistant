# KN-004 — projeto de Transformer multi-bloco

**Data:** 20/11/2026  
**Branch de implementação isolada:** `kellvyn-rnd/kn004-multiblock-clean-20261120`  
**Base:** `kellvyn-rnd/polyglot-lab-foundation-20261119`, incluindo a pesquisa de capacidades e a remoção proposta do runtime Gemma da prévia web.  
**Estado:** projeto técnico; ainda não é implementação nem validação.  
**Regra:** preservar integralmente KN-003 e o trabalho Android Studio na branch-base.

## 1. Problema que KN-004 resolve

KN-003 é um Transformer decoder-only autoral de um único bloco. A implementação contém atenção causal multi-head com RoPE, Pre-LayerNorm, residual e SwiGLU, treinamento supervisionado, AdamW, gradient check e KV cache incremental. A própria classe documenta que profundidade multi-bloco, retropropagação entre camadas e caches por camada pertencem ao próximo marco.

KN-004 adiciona profundidade real sem trocar o cérebro por outro modelo, sem importar pesos externos e sem fingir que mais profundidade, por si só, produz competência geral.

## 2. Princípios de compatibilidade

1. KN-003 permanece legível e testável; seus checkpoints não podem ser reinterpretados silenciosamente.
2. O formato KN-004 precisa ter versão própria, checksum e dimensões validadas antes de alocar grandes arrays.
3. Um checkpoint KN-003 pode ser carregado como configuração de profundidade 1 somente por caminho de migração explícito e testado. Nunca inventar camadas copiando pesos ou alegar que foram treinadas.
4. A arquitetura continua um único modelo autoregressivo central; não há modelos por camada, especialistas ou roteamento para cérebros externos.
5. O runtime principal continua indisponível até existir checkpoint treinado, quality gate, benchmark comportamental e teste de dispositivo.
6. A implementação deve manter os limites de recursos e cancelamento do runtime existente.

## 3. Modelo de dados proposto

### Configuração

Adicionar profundidade explícita ao contrato de configuração:
- `depth`: inicialmente 1 ou 2; teto experimental configurável até 8, sem tornar 8 o padrão;
- manter `vocabularySize`, `contextLength`, `embeddingSize`, `headCount`, `feedForwardSize` e `seed`;
- validar divisibilidade de dimensão por cabeça e dimensão par por cabeça para RoPE;
- limitar a profundidade pela estimativa de memória e pelo Resource Governor antes de inicializar parâmetros.

### Parâmetros

Separar parâmetros compartilhados de parâmetros por bloco:
- compartilhados: token embeddings e projeção final de vocabulário;
- por bloco: Q/K/V, projeção de atenção, LayerNorm de atenção, LayerNorm de feed-forward, gate/up/down do SwiGLU;
- manter Adam first/second moments com a mesma topologia dos parâmetros;
- cada camada possui seu próprio conjunto de pesos e momentos; nenhum compartilhamento implícito.

A forma conceitual é:
`embedding -> block[0] -> ... -> block[depth-1] -> output norm/head -> logits`.

Se uma normalização final for introduzida, deve ser explicitamente versionada e testada; não alterar silenciosamente o comportamento de depth=1.

## 4. Forward e cache de treinamento

Para cada bloco, guardar somente os intermediários necessários ao backward:
- entrada/residual da camada;
- valores normalizados e estatísticas de LayerNorm;
- Q/K/V, Q/K rotacionados;
- pesos de atenção causal;
- concatenação e projeção de atenção;
- entrada e ativações do SwiGLU;
- saída residual do bloco.

A camada superior recebe a saída da camada anterior para todas as posições do contexto. A cabeça de vocabulário calcula loss no token-alvo supervisionado atual, mantendo a política de que somente tokens de completion recebem gradiente no treinamento de instrução.

Evitar guardar tensores duplicados. A memória de ativações deve ser estimada por batch/sequence/depth e contabilizada além dos parâmetros.

## 5. Backpropagation multi-bloco

A retropropagação deve atravessar a rede inteira em ordem reversa:
1. derivar logits/loss e gradientes da cabeça de saída;
2. enviar o gradiente da representação final para o último bloco;
3. em cada bloco, somar gradientes dos caminhos residuais;
4. retropropagar pela projeção SwiGLU, LayerNorm, projeção de atenção e softmax causal;
5. calcular gradientes de Q/K/V e inverter RoPE para obter gradientes das projeções não rotacionadas;
6. propagar gradientes da entrada de cada bloco para a saída do bloco anterior;
7. acumular gradientes de embeddings para todos os tokens que contribuíram;
8. aplicar clipping global sobre todos os grupos e camadas;
9. atualizar todos os parâmetros e momentos AdamW usando um único contador coerente de passos.

Não basta que cada bloco produza gradiente isoladamente. O teste essencial é provar que a loss da saída final propaga gradiente através de todos os blocos e que cada grupo de parâmetros recebe gradiente correto.

## 6. Inferência incremental e KV cache

Cada camada mantém seu próprio cache:
- chaves rotacionadas por posição absoluta;
- valores projetados;
- contagem e posições absolutas;
- política de janela deslizante compatível com `contextLength`.

Ao anexar um novo token:
1. embedding entra no bloco 0;
2. cada bloco calcula Q/K/V somente para o novo token;
3. consulta o cache KV daquele bloco;
4. produz a saída do bloco, que alimenta o próximo;
5. o último bloco gera logits;
6. ao ultrapassar a janela, cada cache remove o mesmo token antigo.

**Teste de equivalência obrigatório:** para um mesmo prefixo e pesos fixos, logits de inferência incremental devem corresponder aos logits do forward completo, dentro de tolerância numérica definida, em diferentes comprimentos e após a janela começar a deslizar.

## 7. Checkpoint e migração

KN-004 deve persistir:
- magic e versão do formato;
- configuração completa, inclusive profundidade;
- contadores de treino/otimizador;
- parâmetros compartilhados;
- parâmetros e momentos de cada bloco;
- manifesto de proveniência/treino;
- checksum do payload.

A leitura deve validar versão, profundidade, tamanhos esperados, bytes totais, valores finitos e ausência de dados extras antes de aceitar o checkpoint.

O leitor KN-003 permanece separado e continua aceitando apenas seu formato original. A migração KN-003 → KN-004 depth=1 deve ser explícita, produzir novo checkpoint KN-004 e manter os valores idênticos; não altera pesos nem cria camadas novas.

## 8. Integração com a pipeline e orçamento

A evolução não termina na classe do modelo. Auditar e atualizar, com testes:
- configuração e pipeline de treino;
- bundle/store e manifesto de proveniência;
- quality gate;
- estimador de capacidade;
- adaptador de inferência;
- runtime e governança de tarefas.

O estimador deve incluir pelo menos:
- parâmetros do modelo;
- first/second moments do otimizador;
- ativações/intermediários do forward/backward;
- caches KV por camada;
- buffers temporários e arrays de gradiente;
- overhead de serialização/checkpoint;
- margem para heap/JVM/Android e outras funções do app.

O estimador é previsão, não medição. A aprovação final exige medir PSS/heap e pico de memória no ambiente Android e, posteriormente, no Poco F6.

## 9. Plano de implementação incremental

### Gate 0 — congelar base
- registrar SHA da branch-base e dos arquivos KN-003;
- preservar a branch Android Studio;
- manter KN-003 sem alterações enquanto KN-004 é desenvolvido em branch isolada.

### Gate 1 — estruturas e depth=1
- introduzir representação de blocos sem alterar a matemática;
- carregar KN-003 como depth=1;
- demonstrar equivalência de forward, loss, fingerprint e checkpoint após migração.

### Gate 2 — depth=2 forward
- criar segundo bloco com inicialização própria e seed determinística;
- validar shapes, finitude, causalidade e determinismo;
- testar que mudar parâmetros do bloco 0 e do bloco 1 altera a saída conforme esperado.

### Gate 3 — backward completo
- implementar gradientes analíticos através de ambos os blocos;
- finite differences em coordenadas zero e não zero de todos os grupos em todas as camadas;
- restaurar parâmetros em `finally` em qualquer falha;
- rejeitar NaN/Inf e erro acima da tolerância.

### Gate 4 — treino e checkpoint
- provar redução de loss em corpus sintético pequeno e autorizado, sem confundir isso com qualidade de linguagem;
- provar round-trip de checkpoint, checksum, migração e rejeição de payload malformado;
- provar cancelamento e limites de passos.

### Gate 5 — KV cache multi-camada
- cache independente por camada;
- equivalência incremental/full forward;
- janela deslizante, posição absoluta e limites de contexto;
- teste de retomada e descarte de cache.

### Gate 6 — escalonamento e benchmark
- testar depth 2, 4 e 8 apenas quando gates anteriores passarem;
- comparar parâmetros, ativações, RAM, PSS, latência, tokens/s, temperatura e bateria;
- escolher profundidade padrão pelo orçamento real do Poco F6, não por preferência estética.

## 10. Matriz mínima de testes

| Área | Teste obrigatório |
|---|---|
| Compatibilidade | KN-003 continua lendo/escrevendo seu checkpoint original |
| Migração | KN-003 → KN-004 depth=1 preserva pesos e saídas |
| Inicialização | seed idêntica produz parâmetros idênticos |
| Forward | depth 1/2 é determinístico e causal |
| Backward | finite differences em todos os grupos de todas as camadas |
| Treino | loss finita, atualização em todas as camadas e cancelamento |
| Optimizer | clipping global e momentos por camada coerentes |
| Checkpoint | round-trip, checksum, dimensões, truncamento e bytes extras |
| KV cache | incremental equivale ao forward completo em várias camadas |
| Janela | remoção do token antigo mantém cache e posições consistentes |
| Recursos | estimativa inclui parâmetros, momentos, ativações e KV |
| Android | build/JUnit/instrumentados com logs vinculados ao SHA |
| Dispositivo | medição no Poco F6 antes de qualquer ativação no chat |

## 11. Bloqueios que não podem ser ignorados

- Sem corpus autorizado e suficiente, não há pré-treinamento geral.
- Redução de loss em corpus pequeno não prova português natural, raciocínio ou competência de assistente.
- O chat principal não deve ser ativado com pesos aleatórios ou checkpoint sem benchmark.
- O build Android e os testes precisam executar de verdade; workflow sem runner é NOT_RUN.
- O Poco F6 tem 8 GB de RAM compartilhados com o sistema e o app; parâmetros não são a única parcela da memória.
- A arquitetura pode avançar no código sem prometer que o telefone conseguirá treinar um modelo geral de grande porte.

## 12. Estado desta especificação

Esta é uma especificação para implementação isolada. Nenhum arquivo de código neural foi alterado neste commit. O próximo commit de código deve implementar somente um gate verificável e incluir testes correspondentes; não empilhar profundidade, multimodalidade e integração do chat em uma única mudança.
