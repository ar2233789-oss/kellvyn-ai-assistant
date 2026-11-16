# Kellvyn — decisão definitiva de origem do cérebro neural

**Decisão:** o cérebro neural da Kellvyn será criado do zero. Não haverá modelo-base de terceiros, pesos pré-treinados importados, fine-tuning de modelo alheio, LoRA, fallback oculto ou troca por outro provedor.

## 1. Regra inviolável de autoria

A Kellvyn terá:
- arquitetura especificada e implementada para este projeto;
- tokenizer desenvolvido e versionado no projeto;
- pesos inicializados aleatoriamente pela própria implementação;
- pipeline próprio de preparação de dados, treinamento, checkpoint e avaliação;
- histórico reproduzível de cada execução de treinamento;
- pesos de saída identificados por hash e ligados ao código e ao conjunto de dados que os produziram.

Nenhum arquivo de pesos vindo de outro modelo poderá ser carregado como ponto de partida, mesmo que tenha licença permissiva. Não haverá modelo de terceiros como cérebro temporário, reserva, auxiliar ou fallback.

## 2. Uso de pesquisa sem plágio

Artigos, documentação e projetos públicos podem ser estudados para compreender matemática, limitações, decisões de engenharia e resultados publicados. O estudo não autoriza copiar implementação, arquitetura de um modelo identificado, tokenizer, pesos, dataset ou código de treinamento.

A implementação da Kellvyn será escrita especificamente para este repositório. Cada decisão técnica relevante terá uma nota de projeto própria. Quando um princípio científico conhecido for utilizado, a fonte será citada como referência conceitual; não será reivindicada a autoria da ciência já publicada. Não serão reproduzidos arquivos ou blocos de código de projetos estudados.

Bibliotecas genéricas fora do cérebro — por exemplo, APIs de sistema, armazenamento ou rede — só poderão ser adicionadas após avaliação de necessidade, licença e proveniência. Nenhuma biblioteca poderá fornecer silenciosamente um modelo, tokenizer ou pesos.

## 3. Desenvolvimento do cérebro

A construção seguirá etapas mensuráveis:

1. **Fundação matemática própria:** tensores/estruturas numéricas, operações, inicialização determinística, funções de ativação, normalização, loss e cálculo de gradientes implementados e testados.
2. **Tokenização própria:** representação reversível de texto, testes de português, acentos, símbolos, código e Unicode.
3. **Arquitetura Kellvyn:** especificação própria dos blocos de processamento, memória de contexto, passagem direta e retropropagação. Não clonar uma arquitetura/modelo de terceiros.
4. **Treinamento do zero:** pesos aleatórios, previsão de sequência, otimizador implementado e checkpoints reproduzíveis.
5. **Aprendizado conversacional:** dados próprios ou com autorização e proveniência verificadas, preparação, filtragem, treino supervisionado próprio e avaliação.
6. **Agente:** conexão ao orquestrador e às ferramentas somente depois de o modelo demonstrar capacidade mensurável de seguir o formato de saída.
7. **Android:** portar a inferência para o Poco F6 após os testes do modelo; o runtime não define a origem nem a identidade do cérebro.

## 4. Dados e privacidade

Não serão usados pesos ou corpora extraídos de outros modelos. Os dados de treino deverão ter origem documentada e autorização compatível. Conversas privadas do proprietário não serão usadas para treinar pesos sem consentimento específico. Dados de teste sintéticos serão identificados como testes e não serão apresentados como corpus de treinamento de qualidade.

## 5. O que não pode ser alegado

Não chamar uma rede pequena de assistente geral. Não afirmar que a Kellvyn aprendeu linguagem, raciocínio ou programação antes de medir isso em conjuntos de avaliação separados. Uma resposta gerada por regras, memória ou ferramenta não prova capacidade neural. Um checkpoint só será considerado peso próprio quando for produzido por este pipeline a partir de inicialização aleatória.

## 6. Primeiro marco verificável

O primeiro marco é um protótipo neural pequeno, inteiramente escrito no projeto, capaz de:
- tokenizar texto de forma reversível;
- inicializar pesos sem carregar arquivos externos;
- executar inferência;
- calcular loss e gradientes;
- treinar em um corpus de teste declarado;
- salvar e restaurar um checkpoint próprio;
- demonstrar por teste que os pesos mudam durante o treino e que a loss é registrada.

Esse marco prova que existe um pipeline neural autoral em funcionamento. Não significa que já exista uma IA conversacional de qualidade.
