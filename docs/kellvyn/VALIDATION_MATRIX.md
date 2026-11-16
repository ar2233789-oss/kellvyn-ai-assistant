# Kellvyn — matriz de validação

Nenhuma fase passa apenas porque o código existe. Cada prova precisa estar associada a um commit, logs, resultados e, quando aplicável, artefato.

## 1. Engenharia de repositório

| Teste | Evidência | Aprovação |
|---|---|---|
| Branch isolada | branch e diff | não tocar distribuição |
| Interface protegida | hashes da lock file | hashes preservados |
| Inventário de arquivos | tree e manifest | nenhuma referência órfã |
| Licença | arquivos LICENSE/NOTICE | origem registrada |
| Dependências | SBOM e versões | sem componente desconhecido |

## 2. Modelo neural

| Teste | Evidência | Aprovação |
|---|---|---|
| Tokenizer | round-trip e taxa de bytes/token | sem corrupção e cobertura avaliada |
| Inicialização | seed, config, hash | reproduzível |
| Treinamento | logs, loss, dataset, checkpoint | pipeline termina sem NaN/inf |
| Avaliação | conjunto fixo | resultados comparáveis |
| Privacidade | relatório do corpus | sem dados proibidos/segredos |
| Proveniência | model card e licenças | lineage declarado |

## 3. Runtime móvel

Registrar no Poco F6:
- modelo exato e hash;
- backend;
- quantização;
- contexto;
- RAM em repouso e pico;
- tempo de inicialização;
- prefill e tokens/s;
- latência até primeiro token;
- temperatura no início e após sessão longa;
- consumo de bateria;
- crash/OOM;
- funcionamento após pausar/retomar e reabrir.

Executar pelo menos 10 repetições de cada benchmark relevante e guardar valores brutos. Não usar benchmark de outro celular como se fosse medição do Poco F6.

## 4. Agente

- pedido complexo é convertido em tarefas dependentes;
- ferramentas inválidas são recusadas;
- chamadas repetidas são limitadas;
- dados de arquivo não conseguem sobrescrever as regras;
- o agente pede aprovação para ações protegidas;
- erro de ferramenta é observado e não vira sucesso;
- cancelamento realmente interrompe o trabalho;
- memória não mistura projetos e conversas;
- resumo de contexto preserva restrições.

## 5. Programação e criação de APK

- mapa de repositório;
- seleção de contexto;
- diff correto;
- testes unitários;
- lint;
- build;
- assinatura;
- integridade do APK;
- instalação;
- início do app;
- fluxo real de tela;
- comparação visual quando o escopo envolver interface.

Um APK assinado pode continuar com bugs de lógica. Build, instalação e execução são gates diferentes.

## 6. Poco F6

Executar:
1. primeira inicialização;
2. conversa curta e longa;
3. anexo pequeno;
4. tarefa de projeto com várias leituras;
5. geração de app simples;
6. compilação;
7. falha induzida e recuperação;
8. cancelamento;
9. sessão longa;
10. reinício do processo.

Registrar HyperOS/Android, versão do APK, hash, espaço disponível, RAM, temperatura, logs e capturas.

## 7. Play Store

- verificar propriedade intelectual;
- verificar licenças e dependências;
- verificar dados e SDKs;
- verificar permissões e acessibilidade;
- verificar política de privacidade e Data Safety;
- testar as funcionalidades com e sem rede;
- confirmar que a descrição não exagera capacidades.

## 8. Regra final

Se um teste não foi executado, o estado deve ser “não testado”. Se falhou, o estado é “falhou”. “Parece funcionar” não é um status aceito.
