# Núcleo da Kellvyn IA — estado atual

A identidade e a voz conversacional da Kellvyn estão centralizadas em `KellvynPersona` (Android) e `persona.ts` (web): português brasileiro, atribuição do criador conforme a identidade do projeto e tom profundo sem romantizar sofrimento. A resposta sobre o criador é determinística e testável.

## Limitação importante

Na branch `copilot/finish-kellvyn-brain`, o adapter nativo `LocalBrain.kt` é apenas uma fronteira de compatibilidade e não possui motor, pesos ou inferência geral conectados. O adapter web também não conecta um modelo. Por isso, perguntas sobre o criador podem ser respondidas pela regra local; para conversa geral, o app informa que o motor não está conectado, sem inventar uma resposta.

A branch ancestral contém histórico de uma implementação LiteRT/Gemma, mas ela foi retirada da branch atual antes deste trabalho. Não reintroduzi às cegas um runtime/modelo de origem/licença não revalidada. É necessário auditar modelo, licença, versão imutável, hash, consumo de memória e compatibilidade atual do LiteRT antes de restaurar ou substituir essa inferência. A UI não foi redesenhada.

## Capacidades não concluídas nesta etapa

A geração de vídeos/músicas/imagens, controle de jogos, serviço de acessibilidade, bots web/WhatsApp e inferência geral não são implementações providas por este adapter. O construtor local Android existente suporta apenas o subconjunto Java Android descrito no próprio validador. Não anunciar essas funções como concluídas.

## Validação

Os testes de identidade e extração de anexos foram adicionados, assim como verificações da instrução de sistema Android. Execute `bun test` e `cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug` em ambiente com dependências disponíveis. Build e validação em aparelho não foram comprovados por esta alteração.
