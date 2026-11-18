# Agent R&D — protocolo de avaliação

## Dimensões
1. Routing: papel correto para a tarefa.
2. Tool discipline: ferramentas permitidas e schemas respeitados.
3. Context isolation: especialista recebe contexto mínimo; sem vazamento de instruções privadas.
4. Long-horizon: plano, passos, checkpoints, retries e limites respeitados.
5. Independent success: critério só passa com evidência externa.
6. Reliability: repetições e taxa pass^k, mediana e dispersão.
7. Resource cost: latência, RAM/PSS, temperatura e bateria quando medidos.

## Prática
- Casos por missão de papel, com pré-condições, estado inicial/alvo e oráculos.
- Positivos, negativos, limites, instruções conflitantes, dados não confiáveis, ferramenta indisponível e cancelamento.
- Separar resposta, trace e estado produzido pelas ferramentas.
- Manter conjuntos de avaliação fora dos dados de treino.
- Comparar baseline/candidato sob mesmas condições.
- REVIEW_REQUIRED não vira aprovação automática.

## Referências
- AgentBench (ICLR 2024): avaliação em oito ambientes, incluindo raciocínio prolongado, decisão e seguimento de instruções. https://proceedings.iclr.cc/paper_files/paper/2024/hash/e9df36b21ff4ee211a8b71ee8b7e9f57-Abstract-Conference.html
- tau-bench: interação agente-usuário, estado final anotado e confiabilidade pass^k. https://arxiv.org/abs/2406.12045
- OpenAI agent evals: traces de decisões, chamadas de ferramentas, guardrails e handoffs. https://developers.openai.com/api/docs/guides/agent-evals
- Evaluation best practices: casos representativos, logging, avaliações específicas e feedback calibrado; evitar métrica genérica e avaliação subjetiva. https://developers.openai.com/api/docs/guides/evaluation-best-practices

Referências metodológicas; nenhum código, peso ou serviço externo foi incorporado.
