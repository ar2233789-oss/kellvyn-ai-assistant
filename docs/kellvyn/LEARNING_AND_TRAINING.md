# Kellvyn — memória, aprendizado e treinamento próprios

## 1. Memória operacional

Memória guarda fatos e preferências explicitamente autorizados para recuperar contexto. Cada registro precisa de origem, data, motivo, política de retenção e meios de consultar, corrigir e excluir. Memória altera o contexto disponível; não altera pesos neurais.

## 2. Aprendizado dos pesos

O cérebro da Kellvyn será treinado do zero. A implementação deverá:
- inicializar pesos aleatórios com seed registrada;
- preparar dados com proveniência;
- calcular a previsão e a loss;
- calcular gradientes e atualizar parâmetros;
- registrar hiperparâmetros, passos, métricas e checkpoints;
- permitir reproduzir o treinamento;
- comparar resultados em conjuntos de avaliação que não participaram do treino.

Não usar modelo pré-treinado, pesos emprestados, fine-tuning, LoRA ou adaptadores derivados de outro modelo. Não usar outro modelo para gerar silenciosamente o dataset da Kellvyn.

## 3. Etapas de treinamento

1. **Teste matemático:** validar operações numéricas, gradientes e estabilidade.
2. **Protótipo de sequência:** treinar uma rede pequena com dados de teste explícitos; isso verifica o pipeline, não a inteligência geral.
3. **Tokenização:** desenvolver e testar uma representação própria, sem importar tokenizer de modelo externo.
4. **Corpus autorizado:** selecionar textos e código com origem, termos e escopo de uso registrados. Remover dados pessoais, segredos, duplicatas e conteúdo não autorizado.
5. **Treino-base:** iniciar todos os pesos do zero e registrar cada checkpoint.
6. **Treino de instrução:** preparar exemplos originais e autorizados, separados por tarefa e qualidade.
7. **Avaliação:** medir português, continuidade, precisão factual, raciocínio, programação, uso de ferramentas, robustez e memorização indevida.
8. **Execução móvel:** quantizar ou converter apenas pesos produzidos pelo próprio pipeline e comparar qualidade, RAM, latência, temperatura e consumo.

## 4. Separação de dados

O conjunto de treino, validação e teste deverá ser separado. Exemplos de avaliação não podem vazar para treino. Dados privados do proprietário não entram automaticamente no corpus. Feedback e histórico só poderão virar exemplos de treino mediante autorização específica e revisão.

## 5. Limites de hardware

O Poco F6 é o alvo de execução local e pode ser usado para experiências pequenas. Isso não torna seu processador móvel equivalente a uma infraestrutura de pré-treinamento de grande escala. O tamanho do primeiro modelo e o volume de dados serão definidos por medições reais, sem prometer capacidade geral antes dos resultados.

## 6. Evidência obrigatória

Uma etapa de aprendizado só será declarada concluída com: versão do código, configuração, seed, origem e hash do dataset, parâmetros treinados, hash do checkpoint, curvas de loss, métricas de avaliação e análise de regressões. Memória salva, prompt, ferramenta nova ou texto produzido por regras não contam como treinamento neural.
