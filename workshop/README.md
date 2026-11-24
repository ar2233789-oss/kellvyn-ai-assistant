# Oficina experimental do Kellvyn

Este diretório contém ferramentas técnicas chamadas diretamente pelo **cérebro principal do Kellvyn**. Elas não são inteligências, não planejam tarefas, não tomam decisões e não iniciam trabalho por conta própria. O cérebro principal continua sendo a única inteligência e o único responsável por decidir o que executar e interpretar os resultados.

## Catálogo local de datasets

\`tools/dataset_catalog.py\` inventaria uma pasta sem alterar os dados e cria uma impressão digital determinística. Registra caminhos relativos, tamanhos, SHA-256 por arquivo, categorias, grupos de conteúdo duplicado e resumo JSONL/NDJSON. A validação de JSONL é feita linha a linha, sem carregar o dataset inteiro na memória.

~~~bash
python3 workshop/tools/dataset_catalog.py inspect \
  --root workshop/examples/dataset \
  --output out/datasets/training.manifest.json

python3 workshop/tools/dataset_catalog.py inspect \
  --root workshop/examples/evaluation \
  --output out/datasets/evaluation.manifest.json

python3 workshop/tools/dataset_catalog.py verify \
  --root workshop/examples/dataset \
  --manifest out/datasets/training.manifest.json
~~~

O campo \`dataset_sha256\` identifica os arquivos e seus caminhos relativos. Use-o em \`comparison_context.dataset_sha256\` e use a impressão digital da suíte em \`comparison_context.evaluation_suite_sha256\`. O manifesto precisa ficar fora da raiz inventariada. Links simbólicos e arquivos especiais são rejeitados; os limites padrão são 100 mil arquivos e 20 GiB.

## Auditoria de divisões de dados

\`tools/dataset_split_auditor.py\` verifica vazamento por duplicação exata entre treino, validação e teste. Compara JSONL/NDJSON por hash de JSON canônico, usando SQLite temporário para não manter todos os fingerprints em memória. Relata os nomes das divisões, caminhos relativos e linhas conflitantes, mas não grava os valores dos registros. Também identifica duplicatas dentro de cada divisão e linhas JSON inválidas/vazias.

~~~bash
python3 workshop/tools/dataset_split_auditor.py \
  --train-root workshop/examples/splits/train \
  --validation-root workshop/examples/splits/validation \
  --test-root workshop/examples/splits/test \
  --output out/datasets/split-audit.json
~~~

O comando retorna \`clean\`, \`overlap_detected\` ou \`invalid_data\`; códigos de saída diferentes de zero indicam dados que precisam de atenção. O relatório não contém os registros originais, mas fingerprints de dados previsíveis podem ser adivinhados. Restrinja o acesso ao relatório. A auditoria detecta apenas duplicatas JSON exatas após ordenação das chaves; não detecta paráfrases, quase duplicatas, exemplos semanticamente equivalentes nem vazamento indireto por origem/coleta.

## Avaliador local de benchmarks

\`tools/benchmark_evaluator.py\` recebe uma suíte de casos e as previsões produzidas pelo modelo que o cérebro principal decidiu executar. Não chama modelos, não gera respostas e não usa rede. O campo \`task\` seleciona a família de métricas:

| Tarefa | Métricas |
|---|---|
| \`exact_match\` | Correspondência exata e cobertura |
| \`classification\` | Acurácia, precisão, recall e F1 macro/micro, além de detalhes por classe |
| \`regression\` | MAE, MSE, RMSE e R² |
| \`word_error_rate\` | WER, substituições, exclusões e inserções |

Quando \`task\` é omitido, o padrão é \`exact_match\`. O avaliador exige IDs únicos, registra previsões ausentes/excedentes e marca a execução como \`partial\` quando a cobertura não é completa. O arquivo de previsões precisa ficar fora da raiz da suíte, para que o fingerprint represente somente os dados de referência. O motor experimental não aceita avaliação parcial.

Exemplo de avaliação textual exata:

~~~bash
python3 workshop/tools/benchmark_evaluator.py \
  --suite-root workshop/examples/evaluation \
  --suite-file workshop/examples/evaluation/suite.json \
  --predictions workshop/examples/predictions.json \
  --output out/evaluation/result.json
~~~

Outras suítes demonstrativas ficam em \`examples/evaluation-classification/suite\`, \`examples/evaluation-regression/suite\` e \`examples/evaluation-speech/suite\`; as respectivas previsões ficam fora de cada raiz de referência.

**Limites das métricas:** exact match mede igualdade lexical/estruturada, não qualidade semântica; classificação precisa de métricas por classe quando há desequilíbrio; regressão depende da escala e distribuição dos alvos; WER depende da normalização e pode exceder 1 por causa de inserções. O alinhamento WER é limitado a um milhão de células para manter o uso de memória previsível em dispositivos móveis. Nenhuma dessas métricas substitui revisão humana e benchmarks adequados à modalidade.

## Perfilador de desempenho de inferência

\`tools/runtime_profiler.py\` agrega medições coletadas durante chamadas que o cérebro principal decidiu executar. O perfilador não carrega modelo, não executa inferência e não grava prompts nem respostas. Recebe JSONL com \`case_id\`, estado, latência, tokens de entrada/saída, TTFT e, quando disponível, pico de memória.

Produz latência média/p50/p95, TTFT médio/p95, cobertura de TTFT e memória, tokens por segundo de ponta a ponta e velocidade de decodificação após o primeiro token. O resultado usa a mesma impressão digital da suíte de avaliação, para que a medição de runtime possa ser vinculada ao mesmo conjunto de casos dos benchmarks de qualidade.

~~~bash
python3 workshop/tools/runtime_profiler.py \
  --suite-root workshop/examples/evaluation \
  --suite-file workshop/examples/evaluation/suite.json \
  --measurements workshop/examples/runtime-measurements.jsonl \
  --output out/runtime/profile.json

python3 workshop/tools/experiment_engine.py record \
  --spec workshop/examples/runtime-experiment-spec.json \
  --artifact-root workshop/examples \
  --evaluation-result out/runtime/profile.json \
  --output out/experiments/runtime.run.json
~~~

Uma execução com caso ausente, extra ou falho fica \`partial\` e não pode ser selada como avaliação completa. Compare apenas medições feitas com mesmo modelo, tokenizador, runtime, precisão, dispositivo, aquecimento e método de medição. \`decode_tokens_per_second\` exclui o primeiro token e não deve ser confundido com throughput de ponta a ponta. O campo de memória registra o máximo fornecido pelo instrumento de origem; a consistência desse instrumento precisa ser mantida.

## Motor local de experimentos

\`tools/experiment_engine.py\` oferece três operações determinísticas:

- \`record\`: valida uma especificação, calcula SHA-256 e tamanho dos artefatos e sela um manifesto. Não sobrescreve execuções existentes.
- \`verify\`: recalcula hashes e tamanhos dos artefatos e, quando fornecido novamente, revalida o hash do resultado de avaliação.
- \`compare\`: compara duas execuções seladas sob contexto idêntico e aplica tolerâncias explícitas por métrica.

Exemplo integrado:

~~~bash
python3 workshop/tools/experiment_engine.py record \
  --spec workshop/examples/experiment-spec.json \
  --artifact-root workshop/examples \
  --evaluation-result out/evaluation/result.json \
  --output out/experiments/candidate.run.json

python3 workshop/tools/experiment_engine.py verify \
  --run out/experiments/candidate.run.json \
  --artifact-root workshop/examples \
  --evaluation-result out/evaluation/result.json

python3 workshop/tools/experiment_engine.py compare \
  --baseline out/experiments/baseline.run.json \
  --candidate out/experiments/candidate.run.json \
  --policy workshop/examples/comparison-policy.json \
  --output out/experiments/comparison.json
~~~

Cada execução registra fingerprints do dataset e da suíte, dispositivo, runtime e precisão. Parâmetros que estão sendo experimentados ficam em \`parameters\`, fora do contexto fixo. A política define por métrica a direção (\`higher\` ou \`lower\`), melhoria mínima e regressão máxima tolerada. Qualquer regressão fora da tolerância bloqueia o resultado \`improved\`; métricas ausentes ou contextos incompatíveis produzem \`inconclusive\`.

## Catálogo de capacidades e contratos de ferramentas

\`tool_registry.json\` é o inventário declarativo das ferramentas disponíveis para a inteligência principal. Cada entrada informa identificador estável, finalidade, script, subcomando, argumentos obrigatórios/opcionais, saídas declaradas e efeitos colaterais. O catálogo registra explicitamente que as ferramentas não são agentes: não planejam, não decidem, não chamam modelos e não executam código de projetos.

\`tools/tool_registry.py\` valida o contrato, verifica se cada script existe dentro do repositório e permite listar ou consultar uma capacidade. É somente leitura e **não executa nenhuma ferramenta registrada**.

~~~bash
python3 workshop/tools/tool_registry.py validate
python3 workshop/tools/tool_registry.py list
python3 workshop/tools/tool_registry.py describe runtime.profile
~~~

A inteligência principal escolhe quando e por que chamar uma ferramenta. O registro não cria agentes, filas autônomas, ciclos de planejamento nem uma segunda inteligência. A política mantém rede desabilitada, execução de código de projeto desabilitada e o cérebro principal explicitamente fora do escopo.

## Integridade, segurança e limites

- Caminhos de artefatos são relativos à raiz declarada; caminhos absolutos, travessia \`..\` e links simbólicos são rejeitados.
- Os registros usam SHA-256 e tamanho dos arquivos; isso detecta alterações, mas não prova autoria nem aprovação humana.
- Os módulos não executam código fornecido por projetos, não iniciam subprocessos, não usam rede e não exigem serviços pagos.
- O motor de experimentos registra resultados produzidos por uma execução autorizada; não é um executor de projetos.
- As amostras incluídas são demonstrações sintéticas, não medições de modelos reais nem resultados de desempenho do Poco F6.

## Testes

~~~bash
python3 -m unittest discover -s workshop/tests -v
python3 -m py_compile workshop/tools/*.py
~~~

Os testes cobrem catálogo e integridade de dados, vazamento exato entre divisões, JSONL inválido, quatro famílias de benchmarks, perfil de runtime, contratos do registro de ferramentas, cobertura, segurança de caminhos, métricas, incompatibilidade de contexto, regressões e adulteração de artefatos.

## Referências de desenho

O catálogo adota ideias de fingerprints/versionamento do DVC e Hugging Face Datasets. Métricas de classificação, regressão e WER seguem definições documentadas pelo Hugging Face Evaluate e scikit-learn. São referências de engenharia, não dependências de execução desta oficina.
