# Kellvyn — matriz de validação

Nenhuma fase passa apenas porque o código existe. Cada prova precisa estar associada a um commit, logs, resultados e, quando aplicável, artefato.

## 1. Engenharia de repositório

| Teste | Evidência | Aprovação |
|---|---|---|
| Branch isolada | branch e diff | não tocar versão estável nem repositório antigo |
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

## 7. Privacidade e execução privada

- nenhuma dependência obrigatória de API de IA hospedada;
- conversas e memória permanecem locais por padrão;
- conexões Web são opcionais e claramente identificadas;
- credenciais de bots protegidas;
- controle de acesso do sistema operacional documentado;
- interrupção e encerramento do agente testados;
- backups sob controle do proprietário;
- sem telemetria ou envio de conteúdo privado não documentados.

## 8. Gate final privado

- APK pessoal compilado e assinatura verificada;
- instalação no Poco F6;
- inicialização e fluxo real testados;
- funcionamento offline após preparação;
- comportamento Web 5G opcional testado;
- recuperação de crash e reinício;
- consumo de memória, temperatura e bateria medidos.

## Novas provas de agente adicionadas em 16/11/2026

Foram escritos, mas ainda aguardam execução Gradle/CI verificável:
- `KellvynSkillRegistryTest.kt`: relevância, seleção determinística, orçamento e injeção de skills.
- `KellvynProjectCodeIndexerTest.kt`: Kotlin/Java, imports, chamadas, comentários, strings e limites.
- `KellvynAgentPlanTest.kt`: plano persistente, ferramenta não registrada, aprovação explícita, executor incremental e verificação independente.
- `KellvynAgentDelegationTest.kt`: contexto separado, ferramentas READ_ONLY, retorno como dado não confiável e bloqueio recursivo.

Smoke tests isolados de código auxiliar não substituem JUnit/Gradle do repositório. Os checks do commit e a consulta disponível de workflow runs não confirmaram execução; build Android, integração de Keystore e teste Poco F6 permanecem pendentes.
