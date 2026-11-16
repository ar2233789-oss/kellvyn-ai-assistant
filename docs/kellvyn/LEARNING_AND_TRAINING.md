# Kellvyn — memória, personalização e aprendizado

## 1. Memória operacional

Memória guarda informações que ajudam a dar continuidade a projetos e conversas. Cada item precisa de origem, data, motivo, política de retenção, consulta, correção e exclusão. Memória altera contexto; não altera parâmetros neurais.

## 2. Personalização sem treino de pesos

A primeira evolução pode ocorrer com:
- instruções e regras próprias;
- skills e procedimentos versionados;
- recuperação de documentos e código;
- memória estruturada;
- ferramentas e conectores;
- contexto seletivo e compactação;
- avaliações e correção de fluxos.

Isso melhora o sistema sem alterar pesos. Não será chamado de treinamento neural.

## 3. Personalização de um modelo aberto

Quando a fundação neural for escolhida e validada, Kellvyn poderá ser personalizada com:
- exemplos autorais e autorizados;
- supervised fine-tuning;
- LoRA/adapters, se forem compatíveis com o modelo e com o runtime;
- preferência/feedback estruturado;
- avaliação antes/depois.

O modelo-base, seus autores, licença, hashes e os dados usados permanecem registrados. Pesos/adapters produzidos por Kellvyn serão identificados como derivados desse modelo-base, nunca como pesos inteiramente originais.

## 4. Treinar um modelo integralmente do zero

KBN-001 é uma experiência própria para aprender e validar operações matemáticas, gradientes e checkpoints. Um pré-treinamento competitivo do zero exige volume de dados, aceleradores, armazenamento e tempo muito além do que o Poco F6 isoladamente oferece. O telefone é o alvo de inferência e teste; pode executar pequenos experimentos de treino, mas isso não o transforma em cluster de treinamento de fronteira.

Manteremos a rota de pesquisa autoral em paralelo, sem bloquear a entrega de uma Kellvyn útil baseada em uma fundação licenciada.

## 5. Dados

Cada dataset deve ter fonte, autor, URL, licença/consentimento, escopo, data de captura, hash, limpeza, deduplicação e separação entre treino/validação/teste. Remover segredos e dados pessoais não autorizados. Histórico privado do proprietário não vira treino automaticamente.

## 6. Critérios de evidência

Qualquer afirmação de aprendizado exige:
- versão de código;
- modelo-base e licença, quando houver;
- método de treino/adaptação;
- configuração, seed e hiperparâmetros;
- origem e hash dos dados;
- hash dos pesos ou adapters de saída;
- loss e métricas antes/depois;
- testes de memorização, privacidade e regressões.

Uma memória salva, prompt, skill, ferramenta ou resposta melhor não prova atualização dos pesos.
