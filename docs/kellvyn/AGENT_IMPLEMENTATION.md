# Kellvyn — contrato do agente executável

## Fronteira entre agente e modelo

`KellvynAgent` é o ciclo de controle. Ele não contém pesos nem finge ser um modelo neural. O modelo escolhido futuramente implementará `KellvynInferenceEngine` e retornará uma decisão estruturada:
- resposta final;
- chamada de ferramenta com argumentos.

Isso mantém o orquestrador independente do formato/engine neural e impede que um runtime defina a identidade do projeto.

## Ciclo implementado nesta primeira prova

1. Montar contexto estruturado com instrução do sistema e pedido do usuário.
2. Solicitar uma decisão ao backend neural.
3. Se houver resposta final, concluir.
4. Se houver ferramenta, confirmar que está registrada.
5. Validar parâmetros obrigatórios e rejeitar parâmetros desconhecidos.
6. Aplicar política de autorização baseada no risco.
7. Executar com timeout.
8. Inserir a observação como conteúdo não confiável.
9. Repetir até conclusão ou limite.

## Proteções implementadas no núcleo

- número máximo de etapas e chamadas por tarefa;
- timeout de inferência e ferramentas;
- bloqueio de ferramenta não registrada;
- validação de argumentos;
- prevenção de chamada idêntica repetida;
- autorização padrão que permite somente ferramenta local de leitura;
- resultados de ferramentas explicitamente marcados como não confiáveis;
- exceções de cancelamento propagadas para permitir interromper o agente.

## Não implementado ainda

- backend neural real;
- parser do formato de tool calls do modelo;
- memória persistente;
- aprovação interativa com o proprietário;
- sandbox/terminal;
- ferramentas de escrita/build;
- integração no Poco F6.

A aplicação usa temporariamente `KellvynRuntime` como fronteira que informa de modo honesto que o backend neural não está pronto. Não há fallback Gemma, Qwen, GPT ou outro modelo.
