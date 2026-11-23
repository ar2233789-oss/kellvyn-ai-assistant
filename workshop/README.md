# Oficina experimental do Kellvyn

Este diretório contém ferramentas técnicas chamadas diretamente pelo **cérebro principal do Kellvyn**. As ferramentas não são inteligências, não tomam decisões, não planejam tarefas e não iniciam trabalhos por conta própria. O cérebro principal continua sendo a única inteligência e o único responsável por decidir o que executar e como interpretar os resultados.

## Catálogo local de datasets

O arquivo **tools/dataset_catalog.py** inventaria uma pasta sem alterar os dados e cria uma impressão digital determinística de seu conteúdo. Ele registra caminho relativo, tamanho, SHA-256 por arquivo, categorias, grupos de arquivos duplicados e resumo de JSONL. Arquivos JSONL/NDJSON são validados linha a linha sem carregar o arquivo inteiro na memória.

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

O campo dataset_sha256 dos manifestos pode preencher comparison_context.dataset_sha256 e comparison_context.evaluation_suite_sha256 no registro de experimento. A pasta do manifesto precisa ficar fora da pasta inventariada. Links simbólicos e arquivos especiais são rejeitados; os limites padrão são 100 mil arquivos e 20 GiB, ambos configuráveis no comando inspect.

O catálogo não interpreta o significado de imagens, áudio ou documentos, não faz limpeza/rotulagem automática e não treina modelos. Ele fornece inventário e identidade de conteúdo reproduzíveis; transformações e treinamento serão etapas separadas, comandadas pelo cérebro principal.

## Motor local de experimentos

O arquivo **tools/experiment_engine.py** fornece três operações determinísticas, usando somente a biblioteca padrão do Python:

- **record:** valida uma especificação, calcula SHA-256 e tamanho de cada artefato e grava um manifesto selado. Não sobrescreve um registro existente.
- **verify:** recalcula os hashes e tamanhos, verifica a integridade dos artefatos e confere se o contexto de comparação não foi alterado.
- **compare:** compara métricas de duas execuções seladas sob o mesmo contexto, aplicando tolerâncias explícitas para melhoria e regressão.

O motor **não executa comandos, scripts, código de projeto, modelos ou ferramentas externas**. Ele recebe resultados produzidos por uma operação autorizada pelo cérebro principal e registra/avalia os dados fornecidos. A execução isolada continuará sendo uma responsabilidade separada da oficina de execução.

### Exemplo de uso

A partir da raiz do repositório, com um diretório local contendo predictions.json:

~~~bash
python3 workshop/tools/experiment_engine.py record \
  --spec workshop/examples/experiment-spec.json \
  --artifact-root workshop/examples \
  --output out/experiments/sample-run-001.run.json

python3 workshop/tools/experiment_engine.py verify \
  --run out/experiments/sample-run-001.run.json \
  --artifact-root workshop/examples

python3 workshop/tools/experiment_engine.py compare \
  --baseline out/experiments/baseline.run.json \
  --candidate out/experiments/candidate.run.json \
  --policy workshop/examples/comparison-policy.json \
  --output out/experiments/comparison.json
~~~

Os hashes do dataset e da suíte de avaliação no exemplo correspondem aos arquivos incluídos em examples/dataset e examples/evaluation. Quando os dados forem alterados, gere novamente os manifestos e atualize os hashes antes de comparar execuções.

### Regras de comparação

Cada execução registra um comparison_context com hashes do dataset e da suíte de avaliação, dispositivo, runtime e precisão. As duas execuções só podem ser comparadas quando esse contexto for idêntico. Parâmetros que se deseja experimentar ficam fora desse contexto, em parameters.

A política define, por métrica:

- **direction:** higher quando valores maiores são melhores; lower quando valores menores são melhores.
- **min_improvement:** ganho mínimo necessário para contar como melhoria relevante.
- **max_regression:** piora máxima tolerável.

Uma regressão além da tolerância bloqueia o resultado improved, mesmo que outra métrica tenha melhorado. Métricas ausentes ou contextos diferentes produzem inconclusive; o motor não inventa resultados nem calcula uma nota agregada arbitrária.

### Integridade e limites

- Caminhos de artefatos são relativos ao diretório-raiz indicado; caminhos absolutos, travessia .. e links simbólicos são rejeitados.
- Arquivos são identificados por SHA-256 e tamanho. A verificação detecta divergência em relação ao manifesto.
- O manifesto registra explicitamente que hashes não provam autoria, aprovação humana ou autenticidade do registro.
- O registro é criado sem sobrescrever uma execução já existente.
- Não há rede, serviço externo, API paga, subprocesso ou dependência Python de terceiros.
- Um resultado improved significa apenas que as métricas fornecidas passaram pelas regras definidas. Não prova, sozinho, que a métrica mede qualidade real: a suíte e os dados precisam ser adequados e confiáveis.

### Testes

~~~bash
python3 -m unittest discover -s workshop/tests -v
python3 -m py_compile workshop/tools/experiment_engine.py workshop/tools/dataset_catalog.py
~~~

Os testes cobrem catálogo de datasets, impressão digital, duplicatas, JSONL, integridade, caminhos inseguros, links simbólicos, métricas inválidas, contexto incompatível, regressões e políticas inválidas.
