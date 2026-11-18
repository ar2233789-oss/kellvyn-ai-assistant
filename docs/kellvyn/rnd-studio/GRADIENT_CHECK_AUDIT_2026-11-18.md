# KN-003 — auditoria de cobertura de gradientes

**Registrado:** 2026-11-18 20:21:19 UTC  
**Commit-base:** `4598cab4e831dd3e1f9d96c0c2158de8e77ff566`  
**Estado:** hipótese PLANNED; não executada.

## Evidência observada

Em `KellvynTransformerLanguageModel.checkGradients()`, a seleção começa filtrando `gradient.indices` por `abs(gradient[it]) > 1.0e-8f`. Para cada grupo de parâmetros, a ferramenta só calcula diferenças finitas quando essa lista tem itens. A única asserção final de cobertura é `checked > 0`.

O teste `multiHeadTransformerGradientsMatchFiniteDifferences()` usa um seed/configuração, duas cabeças e `maxCoordinatesPerGroup = 2`. Não afirma que cada família de parâmetros recebeu uma comparação numérica.

## Por que isso importa

Uma falha no código de backprop que zerasse todo o gradiente de uma família poderia fazer a família desaparecer da lista, em vez de reprovar o teste. As demais famílias ainda poderiam fazer `checked > 0`, deixando o teste passar. Isso é uma fraqueza do verificador, não evidência de que tal regressão esteja presente hoje.

## Ação proposta

Executar o experimento `brain-gradient-coverage-001`: incluir amostras determinísticas uniformes por array, inclusive onde o gradiente analítico é zero; manter amostras de magnitude significativa; avaliar múltiplas seeds, head counts, contextos e EOS. Medir erros absolutos/relativos por família. Só alterar código neural em worktree separado e depois de executar a baseline.

**Não foi alterado:** implementação do Transformer, parâmetros, pesos, tokenizer, checkpoint ou runtime.

## Orientação metodológica pesquisada

A proposta foi refinada após estudar três referências públicas:

- Stanford CS231n, Lecture 6, slide 64: recomenda diferenças centrais, comparação relativa simétrica com tratamento explícito do caso em que ambos os gradientes são zero, atenção a float32/epsilon e amostragem de alguns elementos de cada tensor/parâmetro (inclusive vetores pequenos como biases). Fonte: https://cs231n.stanford.edu/slides/2016/winter1516_lecture6.pdf
- PyTorch gradcheck: reconstrói a derivada numérica por diferenças centrais e avisa que a configuração padrão é projetada para precisão dupla, podendo falhar com precisão inferior. Fonte: https://docs.pytorch.org/docs/stable/notes/gradcheck.html
- JAX check_grads: compara derivadas numa direção aleatória para reduzir o custo em espaços de parâmetros grandes. Adaptação proposta: uma ou mais direções determinísticas por grupo de parâmetros, além da amostragem coordenada. Fonte: https://docs.jax.dev/en/latest/_autosummary/jax.test_util.check_grads.html

### Ajuste do experimento

Não basta incluir coordenadas zero; a cobertura deve combinar amostragem determinística em cada array e uma derivada direcional por array. Como KN-003 usa `FloatArray`, a escolha de epsilon/tolerâncias deve ser calibrada por estabilidade e pelo erro absoluto, sem copiar limites de ferramentas que assumem double precision. O experimento também terá um controle negativo que zera intencionalmente uma família de gradientes e deve ser detectado.

A hipótese permanece PLANNED / NOT RUN. As fontes orientam a metodologia; nenhuma implementação externa foi copiada.
