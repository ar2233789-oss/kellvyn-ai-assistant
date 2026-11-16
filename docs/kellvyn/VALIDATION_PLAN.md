# Kellvyn — plano de validação

## Gate A: integridade e interface

- [ ] Testes unitários passam na branch de laboratório.
- [ ] Build Android produz APK nativo válido.
- [ ] O workflow confirma os hashes dos arquivos protegidos.
- [ ] Nenhum WebView é introduzido.
- [ ] O repositório de distribuição permanece intocado.
- [ ] Nenhuma alteração visual é feita fora de autorização.

## Gate B: fundação neural própria

- [ ] Tokenizer próprio codifica e decodifica português, acentos, símbolos, emojis e código sem perda.
- [ ] Inicialização aleatória é reproduzível com seed registrada.
- [ ] O modelo não aceita carregar pesos de formato ou origem externa.
- [ ] Passagem direta produz tensores finitos.
- [ ] A loss de previsão é finita e calculada corretamente.
- [ ] Retropropagação produz gradientes finitos e coerentes com testes numéricos.
- [ ] O treino altera parâmetros próprios.
- [ ] Um corpus de teste pequeno demonstra redução de loss sem ser confundido com qualidade conversacional.
- [ ] Checkpoint próprio salva/restaura configuração e parâmetros com integridade e hash.

## Gate C: dados e treinamento

- [ ] Cada fonte do corpus possui origem, licença/consentimento, escopo e hash.
- [ ] Treino, validação e teste não se sobrepõem.
- [ ] Dados pessoais, segredos e conteúdo não autorizado são removidos.
- [ ] Seeds, hiperparâmetros, passos, loss e checkpoints são registrados.
- [ ] Não há pesos, tokenizers, adaptadores ou saídas de outro modelo.
- [ ] Métricas de idioma, continuidade, raciocínio e programação são reportadas separadamente.

## Gate D: agente e ferramentas

- [ ] O agente não confunde ferramenta, memória e modelo neural.
- [ ] Ferramentas inexistentes ou parâmetros inválidos são recusados.
- [ ] Ações de escrita e controle exigem autorização específica.
- [ ] Resultados externos são tratados como dados não confiáveis.
- [ ] O agente não declara tarefa concluída sem evidência.
- [ ] Limites de passos, chamadas, tempo e cancelamento funcionam.

## Gate E: Poco F6 (dispositivo real)

- [ ] Instalação nativa verificada no Poco F6.
- [ ] RAM de pico e armazenamento medidos.
- [ ] Tempo de carregamento e tokens/bytes por segundo medidos.
- [ ] Temperatura, throttling e consumo monitorados.
- [ ] Testes repetidos sem rede confirmam inferência local.
- [ ] Comportamento após fechar/reabrir o app verificado.
- [ ] Cancelamento e liberação de memória verificados.

## Estado

Não há, nesta branch, um modelo conversacional Kellvyn treinado, um backend neural pronto, build Android aprovado ou teste físico concluído no Poco F6. Cada gate depende de evidência real; nenhum resultado será presumido.
