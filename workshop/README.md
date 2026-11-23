# Oficina experimental do Kellvyn

Este diretório contém ferramentas técnicas chamadas diretamente pelo **cérebro principal do Kellvyn**. Elas não são inteligências, não planejam tarefas, não tomam decisões e não iniciam trabalho por conta própria. O cérebro principal continua sendo a única inteligência e o único responsável por decidir o que executar e interpretar os resultados.

## Catálogo local de datasets

\`tools/dataset_catalog.py\` inventaria uma pasta sem alterar os dados e cria uma impressão digital determinística. Registra caminho relativo, tamanho, SHA-256 por arquivo, categorias, arquivos com conteúdo duplicado e resumo de JSONL/NDJSON. A validação de JSONL é feita linha a linha, sem carregar o dataset inteiro na memória.

~~~bash
python3 workshop/tools/dataset_catalog.py inspect \\
  --root workshop/examples/dataset \\
  --output out/datasets/training.manifest.json

python3 workshop/tools/dataset_catalog.py inspect \\
  --root workshop/examples/evaluation \\
  --output out/datasets/evaluation.manifest.json

python3 workshop/tools/dataset_catalog.py verify \\
  --root workshop/examples/dataset \\
  --manifest out/datasets/training.manifest.json
~~~

O campo \`dataset_sha256\` identifica os arquivos e seus caminhos relativos. Use-o em \`comparison_context.dataset_sha256\` e use a impressão digital da suíte em \`comparison_context.evaluation_suite_sha256\`. O manifesto deve ficar fora da raiz inventariada. Links simbólicos e arquivos especiais são rejeitados; os limites padrão são 100 mil arquivos e 20 GiB.

O catálogo não interpreta semanticamente imagens, áudio ou documentos, não faz rotulagem automática e não treina modelos. Ele garante inventário e identidade de conteúdo reproduzíveis.

## Avaliador local de benchmarks

\`tools/benchmark_evaluator.py\` recebe uma suíte de casos e as previsões produzidas pelo modelo que o cérebro principal decidiu executar. Não chama modelos, não gera respostas e não usa rede. O campo \`task\` da suíte seleciona a família de métricas:

| Tarefa | Métricas produzidas |
|---|---|
| \`exact_match\` | Correspondência exata, cobertura e casos individuais |
| \`classification\` | Acurácia, precisão, recall e F1 macro/micro, além de detalhes por classe |
| \`regression\` | MAE, MSE, RMSE e R² |
| \`word_error_rate\` | WER, substituições, exclusões e inserções |

Quando \`task\` é omitido, o padrão é \`exact_match\`. O avaliador exige IDs únicos, registra previsões ausentes/excedentes e marca a execução como \`partial\` quando a cobertura não é completa. O motor experimental não aceita avaliação parcial para selar uma execução.

Exemplo de avaliação textual exata:

~~~bash
python3 workshop/tools/benchmark_evaluator.py \\
  --suite-root workshop/examples/evaluation \\
  --suite-file workshop/examples/evaluation/suite.json \\
  --predictions workshop/examples/predictions.json \\
  --output out/evaluation/result.json
~~~

Outras suítes demonstrativas estão em \`examples/evaluation-classification\`, \`examples/evaluation-regression\` e \`examples/evaluation-speech\`.

**Limites das métricas:** exact match mede igualdade lexical/estruturada, não qualidade semântica; classificação precisa de métricas por classe quando há desequilíbrio; regressão depende da escala e distribuição dos alvos; WER depende da normalização e pode exceder 1 por causa de inserções; o alinhamento tem limite de um milhão de células para manter o uso de memória previsível em dispositivos móveis. Essas métricas não substituem revisão humana ou benchmarks específicos de cada modalidade.

## Motor local de experimentos

\`tools/experiment_engine.py\` oferece três operações determinísticas, usando somente a biblioteca padrão do Python:

- \`record\`: valida a especificação, calcula SHA-256 e tamanho dos artefatos e sela um manifesto. Não sobrescreve execuções existentes.
- \`verify\`: recalcula hashes e tamanhos dos artefatos e, se informado, revalida o hash do resultado de avaliação.
- \`compare\`: compara duas execuções seladas sob contexto idêntico e aplica tolerâncias explícitas por métrica.

Exemplo integrado:

~~~bash
python3 workshop/tools/experiment_engine.py record \\
  --spec workshop/examples/experiment-spec.json \\
  --artifact-root workshop/examples \\
  --evaluation-result out/evaluation/result.json \\
  --output out/experiments/candidate.run.json

python3 workshop/tools/experiment_engine.py verify \\
  --run out/experiments/candidate.run.json \\
  --artifact-root workshop/examples \\
  --evaluation-result out/evaluation/result.json

python3 workshop/tools/experiment_engine.py compare \\
  --baseline out/experiments/baseline.run.json \\
  --candidate out/experiments/candidate.run.json \\
  --policy workshop/examples/comparison-policy.json \\
  --output out/experiments/comparison.json
~~~

O conjunto de métricas da política precisa corresponder à família da tarefa. Uma política de classificação não deve ser reutilizada para regressão ou reconhecimento de fala. As execuções só são comparáveis se hashes do dataset e da suíte, dispositivo, runtime e precisão forem idênticos. Parâmetros que estão sendo experimentados ficam em \`parameters\`, fora do contexto fixo.

Cada regra de métrica define \`direction\` (\`higher\` ou \`lower\`), \`min_improvement\` e \`max_regression\`. Uma regressão além da tolerância bloqueia a aprovação, mesmo se outra métrica melhorar. Métricas ausentes ou contextos incompatíveis produzem \`inconclusive\`; o motor não inventa uma nota agregada.

## Integridade e segurança

- Caminhos de artefatos são relativos à raiz declarada; caminhos absolutos, travessia \`..\` e links simbólicos são rejeitados.
- O registro calcula SHA-256 e tamanho dos arquivos; a verificação detecta divergências.
- Um hash detecta alteração do conteúdo registrado, mas não prova autoria, aprovação humana ou autenticidade do manifesto.
- Nenhum desses módulos executa código fornecido por um projeto, dispara subprocessos, usa rede ou precisa de serviço pago.
- O motor de experimentos registra resultados produzidos por uma execução autorizada; não é um executor de projetos.
- A avaliação objetiva é limitada pela qualidade da suíte, dos rótulos e dos dados de referência. Um score alto em uma amostra pequena não prova capacidade geral.

## Testes

~~~bash
python3 -m unittest discover -s workshop/tests -v
python3 -m py_compile workshop/tools/*.py
~~~

Os testes cobrem integridade de dados, fingerprints, duplicatas, JSONL, métricas de quatro famílias de tarefas, caminhos inseguros, avaliação parcial, incompatibilidade de contexto, regressões e adulteração de artefatos.

## Referências de desenho

O catálogo adota ideias de fingerprint/versionamento de dados presentes em DVC e Hugging Face Datasets. As métricas de classificação, regressão e WER seguem definições documentadas pelo Hugging Face Evaluate e scikit-learn. Essas plataformas são referências de engenharia, não dependências de execução desta oficina.
