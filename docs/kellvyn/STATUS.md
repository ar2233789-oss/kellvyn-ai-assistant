# Kellvyn — registro do laboratório

**Data:** 16/11/2026  
**Branch:** \`lab/kellvyn-intelligence-core-20261116\`  
**Repositório:** \`ar2233789-oss/kellvyn-ai-assistant\`

## Decisão atual sobre o cérebro

Kellvyn será um sistema próprio e pode aproveitar modelos de pesos abertos, bibliotecas e código de terceiros quando isso for permitido e melhorar o resultado. Cada origem será identificada e creditada. Não haverá cópia disfarçada nem atribuição falsa de pesos ou código. Modelos proprietários serão usados como referência pelas capacidades publicamente documentadas; APIs externas não serão dependência obrigatória do uso offline.

## Pesquisa recente

Foi criada a matriz \`CAPABILITY_RESEARCH_2026-11-16.md\`, reunindo:
- Claude Code: instruções, rules, skills, subagentes, hooks e gestão de contexto;
- ChatGPT/OpenAI Agents: function calling, ferramentas Web/arquivos/código/computer use/MCP;
- Replit: Plan/Build, planejamento, testes visuais, autocorreção e checkpoints;
- Cursor: agentes coordenadores, delegação, busca no projeto, terminal, revisão e debugging;
- Android Studio Gemini: plano multi-etapas, alterações em vários arquivos, build, dispositivo, screenshots, Logcat e ADB;
- OpenHands: sessões, eventos, workspaces e ferramentas.

Modelos em investigação:
- Gemma 4 E2B mobile QAT: primeira prioridade de teste devido à variante móvel e às capacidades documentadas de visão, áudio e tool calling;
- Qwen3.5-2B: comparação secundária; a página Qualcomm AI Hub consultada informa que o pacote ali listado não é suportado atualmente em chipsets móveis.

Nenhum modelo foi selecionado como vencedor.

## Código existente

- \`KellvynRuntime.kt\`: fronteira temporária; não contém inferência.
- \`KellvynAgent.kt\`: ciclo experimental de ferramentas; não é o cérebro neural.
- \`KellvynByteBrain.kt\`: KBN-001, pequeno preditor de próximo byte com pesos inicializados aleatoriamente, gradientes e checkpoint próprios. É pesquisa experimental, não assistente conversacional.
- Testes iniciais do KBN-001 e do agente passaram em uma execução CI anterior.
- Verificação numérica dos gradientes por diferenças finitas foi adicionada depois e ainda depende de resultado CI confirmado.

## Limitações e bloqueios

- Não há modelo final conectado à conversa.
- Não há teste físico no Poco F6.
- Não há benchmark comparativo no telefone.
- Não há memória persistente cognitiva integrada ao agente.
- Não há ferramentas de escrita/terminal/build completas ligadas ao agente.
- \`MainActivity.java\` diverge do hash protegido em \`.github/interface-lock.sha\`. A proteção não será enfraquecida e o hash não será alterado sem autorização explícita.
- A branch é experimental; nenhum APK dela está validado para instalação.

## Próximas etapas

1. Finalizar a verificação matemática do KBN-001.
2. Construir um benchmark local reprodutível para Gemma 4 E2B mobile QAT e alternativas compatíveis.
3. Verificar licença, modelo, runtime e suporte real para o Poco F6 antes de incorporar pesos.
4. Fortalecer o agente Kellvyn com planner estruturado, memória, skills, delegação interna, ferramentas tipadas e verificação independente.
5. Conectar uma fundação neural aprovada ao agente próprio.
6. Testar tarefas reais de conversa, código, arquivos, documentos e construção Android.
7. Só depois validar o pacote completo no Poco F6.

Cada capacidade será declarada como concluída somente com evidência.
