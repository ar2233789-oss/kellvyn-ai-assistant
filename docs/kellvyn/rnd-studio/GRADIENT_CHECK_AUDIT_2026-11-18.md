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
