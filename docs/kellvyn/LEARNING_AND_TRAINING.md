# Kellvyn — memória, personalização e aprendizado

**Decisão vinculante:** a fundação neural da Kellvyn é própria e será treinada do zero. Consulte também `NEURAL_MODEL_DECISIONS.md`; este documento não autoriza substituir a decisão por pesos externos.

## 1. Memória operacional

Memória guarda informações que ajudam a dar continuidade a projetos e conversas. Cada item precisa de origem, data, motivo, política de retenção, consulta, correção e exclusão. Memória altera contexto; não altera parâmetros neurais.

## 2. Personalização sem treino de pesos

A evolução de comportamento do agente também pode ocorrer por:
- instruções e regras próprias;
- skills e procedimentos versionados;
- recuperação de documentos e código;
- memória estruturada com proveniência;
- ferramentas e conectores com permissões explícitas;
- contexto seletivo e compactação;
- avaliações e correção de fluxos.

Isso melhora o sistema sem alterar pesos. Não chamar essas mudanças de treinamento neural.

## 3. Regra de fundação neural

O projeto não usará modelo-base, pesos, adapters ou checkpoints pré-treinados de terceiros como cérebro principal ou atalho para a entrega. Não baixar, embutir, chamar por SDK nem incorporar pesos externos à Kellvyn.

O tokenizer, arquitetura, inicialização, pesos, treinamento, avaliação e checkpoints do cérebro são de autoria do projeto. Componentes auxiliares de runtime/compilação podem ser avaliados separadamente sob licença compatível, desde que não tragam pesos de modelos externos nem troquem a origem do cérebro.

## 4. Treinamento próprio do zero

- KN-001 é um protótipo inicial de previsão de bytes.
- KN-002 inclui tokenizer BPE próprio e uma alpha de linguagem baseada em atenção.
- KN-003 implementa um Transformer decoder-only experimental de um único bloco com RoPE, atenção causal multi-head, Pre-LayerNorm e SwiGLU.
- O pipeline de treino recebe exemplos locais explicitamente autorizados, separa TRAIN/VALIDATION/TEST, mantém proveniência e hashes, filtra padrões comuns de segredos, limita bytes e passos e salva pesos/estado do otimizador/checkpoint.
- O gate atual de qualidade só determina elegibilidade para um benchmark de protocolo; não comprova competência conversacional, raciocínio geral, programação ou produção.

**Limite de capacidade:** um pré-treinamento competitivo de um modelo geral do zero exige muito mais dados, aceleradores, armazenamento e tempo do que o Poco F6 sozinho oferece. O telefone é o alvo de inferência, teste e pequenos experimentos, não um cluster de treinamento de fronteira. Não prometer paridade com modelos de grande escala.

## 5. Dados

Cada dataset deve ter fonte, autor, URL ou referência, licença/consentimento, escopo, data de aquisição, hash, limpeza, deduplicação e separação entre treino/validação/teste. Usar apenas material criado pelo projeto, cedido com consentimento explícito, domínio público ou licença compatível e documentada. Não considerar automaticamente todo conteúdo público da Web como livre para treinamento.

Remover segredos e dados pessoais não autorizados. Histórico privado do proprietário não vira treino automaticamente. Dados de validação e teste não podem vazar para o treino nem para o aprendizado do tokenizer.

## 6. Critérios de evidência

Qualquer afirmação de aprendizado exige:
- commit e versão exata do código;
- arquitetura/configuração, seed e hiperparâmetros;
- origem, licença/consentimento e hash dos dados;
- versão do tokenizer e hash do checkpoint;
- métricas antes/depois em TRAIN e VALIDATION e resultado separado no TEST;
- avaliação de memorização, privacidade, robustez e regressões;
- benchmark comportamental em tarefas fixas de português, multi-turno, raciocínio e uso de ferramentas;
- teste de inferência no runtime principal e medições no aparelho-alvo.

Uma memória salva, prompt, skill, ferramenta, loss sintética ou resposta melhor não prova, isoladamente, aprendizado de pesos nem competência geral.

## 7. Próximos gates de aprendizagem

1. Executar os testes Kotlin/Gradle/Android já escritos e corrigir somente falhas reproduzidas.
2. Completar KN-003 com profundidade multi-bloco e backpropagation através das camadas, com gradient checks e testes numéricos.
3. Selecionar e documentar corpus autorizado suficiente; os atuais limites experimentais de até 500 KB/1.000 exemplos não representam um corpus para um assistente geral.
4. Treinar checkpoints versionados e medir generalização em dados que não foram vistos pelo modelo.
5. Integrar inferência só depois de checkpoint elegível e benchmark comportamental mínimo; não ligar um modelo aleatório ao chat.
6. Qualificar latência, RAM, temperatura, bateria e funcionamento offline no Poco F6.

## 8. Memória, feedback e aprendizagem são mecanismos distintos

- **Memória:** recupera fatos/contexto autorizados; não altera pesos.
- **Feedback:** registra avaliação/correção; não altera pesos por si só.
- **Skill/perfil:** altera procedimento e limites do agente; não altera pesos.
- **Treinamento:** atualiza parâmetros a partir de exemplos autorizados; requer checkpoint, métricas e avaliação separada.

A interface do usuário nunca deve dizer “aprendi” quando apenas armazenou uma memória ou atualizou uma instrução.
