# GitHub Actions — diagnóstico de alocação de runners

Atualizado: 18/11/2026 22:35 UTC
Repositório: ar2233789-oss/kellvyn-ai-assistant
Visibilidade: private (confirmada pelo endpoint de repositório)
Causa final: ainda não determinada.

## Evidência direta do repositório

Run 44410874611, branch kellvyn-rnd/brain-agent-contract-001, HEAD 8ddab7b0aaa87f8f4e6ca11e59a6bca3ad73a1f6:

| Job | Job ID | Runner label | runner_id | Steps | Janela UTC |
|---|---:|---|---:|---:|---|
| R&D Studio Python CLI tests | 135178351883 | ubuntu-latest | 0 | 0 | 22:22:02–22:22:04 |
| Unit and Android instrumented tests | 135178352262 | ubuntu-latest | 0 | 0 | 22:22:02–22:22:04 |
| Minimal runner allocation probe | 135178352413 | ubuntu-slim | 0 | 0 | 22:22:02–22:22:05 |

Run 44412348657, branch lab, repetiu o mesmo padrão às 22:31 UTC. Os runs 44412360596, 44412372652 e 44412382401 fizeram o mesmo nas branches Agents, Brain e Integration. Todos falharam em 2–6 segundos, antes de qualquer step. Não é evidência de falha do script Python, Gradle, Kotlin ou código neural.

## Estado público do GitHub

Em 18/11, a página pública de status exibia All Systems Operational e Actions como Operational. Houve degradação de início dos workflows em 16/11, entre 15:33 e 16:26 UTC, resolvida antes das nossas tentativas noturnas.

## Hipótese de conta/orçamento que precisa ser inspecionada

A documentação oficial diz que uso de runners hospedados em repositórios privados consome a franquia de compute incluída. A documentação também explica que um budget com a opção Stop usage when budget limit is reached bloqueia novos runners hospedados quando o limite é atingido.

Há relatos comunitários recentes com exatamente runner_id=0 e steps=[] em repositórios privados. Em um deles, o autor viu Usage blocked com budget de Actions em US$ 0 e sem forma de pagamento; outro relato de 15/11 registrou consumo total da franquia incluída e budget bloqueado. Esses casos são pistas diagnósticas, não prova de que esta conta tenha a mesma configuração.

Referências oficiais:
- Billing e uso do GitHub Actions: https://docs.github.com/en/actions/concepts/billing-and-usage
- Compute incluído e custo de runners privados: https://docs.github.com/en/billing/concepts/product-billing/github-actions
- Budgets que bloqueiam uso: https://docs.github.com/en/billing/how-tos/set-up-budgets
- Franquia incluída por plano: https://docs.github.com/en/billing/reference/product-usage-included

Relatos comunitários comparáveis:
- 15/11/2026, private repo, runner_id=0, steps=[], autor relatou franquia incluída de $12/$12 consumida: https://github.com/orgs/community/discussions/213180
- 12/11/2026, budget de Actions em $0 e Usage blocked mesmo com compute incluído ainda disponível: https://github.com/orgs/community/discussions/212270

## Checklist seguro para o proprietário da conta

Somente inspecionar; não alterar orçamento, não adicionar cartão e não habilitar gasto pago sem decisão explícita:

1. Abrir Settings → Billing → Budgets and alerts da conta proprietária do repositório.
2. Localizar budget de GitHub Actions e verificar se aparece Usage blocked.
3. Conferir o valor do budget, Amount spent e se Stop usage when budget limit is reached está habilitado.
4. Conferir o compute incluído restante e o período de faturamento.
5. Em Settings → Actions → General do repositório, confirmar que Actions está permitido e que a política não bloqueia GitHub-hosted runners.
6. Se o budget não explicar o bloqueio, enviar ao GitHub Support os run IDs, job IDs, horário UTC, labels e o padrão runner_id=0/steps=[].

Não automatizar a mudança de billing. O Studio não deve modificar plano, orçamento ou método de pagamento.
