# Kellvyn — estratégia do cérebro e modelos

**Decisão de produto:** Kellvyn será um sistema próprio, mas não precisa rejeitar modelos, pesos ou código de terceiros. O uso é permitido quando houver licença e proveniência verificadas, adequação técnica e atribuição correta.

## 1. O que é próprio e o que pode ser aproveitado

### Próprio da Kellvyn
- identidade e comportamento do assistente;
- arquitetura cognitiva de planejamento, estado de tarefas, memória e execução;
- orquestrador, políticas, ferramentas, permissões, validação e rollback;
- camada de contexto, índice de projetos e skills;
- integração Android, interface, fluxos e experiência;
- avaliação de tarefas, testes e evidências;
- código específico escrito para o projeto e adaptações claramente documentadas.

### Aproveitável de terceiros
- pesos de modelos abertos com licença e termos compatíveis;
- runtimes e bibliotecas licenciados;
- código aberto selecionado, com licença e atribuição preservadas;
- técnicas, artigos e arquiteturas publicamente documentadas;
- dados sob licenças/permissões verificadas.

O aproveitamento não será ocultado. Modelo-base, versão, autores, licença, hash, quantização, alterações e limitações ficarão registrados. Pesos derivados continuam derivados; não serão anunciados como pré-treinados do zero pela Kellvyn.

## 2. Modelos proprietários e abertos

Claude, ChatGPT, Replit e Gemini/Android Studio são referências de capacidade e fluxo. O código interno e os pesos proprietários não serão presumidos acessíveis. Suas documentações públicas podem orientar capacidades; APIs remotas só entram se o proprietário aceitar explicitamente rede, custos, privacidade e dependência externa.

Modelos de pesos abertos são candidatos reais a fundação neural local. O projeto pode utilizar uma base licenciada, personalizá-la e integrá-la a um sistema cognitivo original. Isso não é plágio quando os direitos são respeitados e a origem é declarada.

## 3. Candidatos atuais para benchmark

### Gemma 4 E2B — primeira prioridade de investigação móvel
- Licença declarada: Apache 2.0.
- Texto, imagem e áudio; suporte nativo a chamadas estruturadas de ferramentas.
- Contexto de 128K na variante E2B.
- 2,3B parâmetros efetivos e 5,1B contando embeddings.
- Existem checkpoints mobile QAT e amostras Android com LiteRT-LM.
- A documentação Google estima footprint mobile de aproximadamente 1,1 GB (0,84 GB em texto apenas); contexto, runtime, buffers e demais partes do app usam memória adicional.
- Ainda precisa de teste no Poco F6 (Snapdragon 8s Gen 3, 8 GB). O suporte da família a dispositivos móveis não prova compatibilidade/estabilidade nesse aparelho.

### Qwen3.5-2B — comparação de arquitetura e desempenho
- Licença declarada nos repositórios oficiais: Apache 2.0.
- Modelo de 2B, multimodal com encoder de visão, contexto nativo de 262.144 tokens.
- A página atual do Qualcomm AI Hub informa que a versão ali listada não está suportada em chipsets móveis. Não presumir aceleração NPU ou compatibilidade com o Poco F6.
- Só será testado no telefone se encontrarmos uma conversão e runtime tecnicamente compatíveis; caso contrário, fica como referência comparativa.

Os candidatos podem mudar conforme novas versões, pesos e resultados. Nenhum é declarado vencedor antes de benchmark reproduzível.

## 4. Critérios de escolha

- compatibilidade de licença, model card e arquivos de pesos;
- português brasileiro e qualidade de instrução;
- raciocínio, código, planejamento e chamadas de ferramentas;
- visão e documentos, quando necessário;
- tamanho real dos pesos e memória de pico;
- latência de carregamento e geração;
- temperatura, bateria e throttling;
- estabilidade em contexto longo;
- execução offline;
- suporte real no Poco F6 e runtime Android;
- capacidade de fine-tuning/adapters, caso exista ambiente adequado.

A escolha não será pelo nome, hype, licença isolada ou número de parâmetros.

## 5. Caminho recomendado

1. Manter KBN-001 como laboratório didático de pesos próprios, matemática, gradientes e checkpoint. Ele não é o cérebro final.
2. Avaliar modelos abertos adequados ao Poco F6, começando pelo Gemma 4 E2B mobile QAT e comparando Qwen3.5-2B quando tecnicamente viável.
3. Implementar a camada cognitiva Kellvyn independente do modelo: planejamento, memória, skills, subagentes internos, tools, permissões, verificação e rollback.
4. Conectar o modelo escolhido à camada cognitiva por uma interface própria.
5. Personalizar o comportamento com instruções, exemplos e, depois, fine-tuning/adapters autorizados se a infraestrutura permitir.
6. Medir e manter um benchmark que impeça regressão e revele quando o modelo não consegue executar uma tarefa.

## 6. Treino próprio e adaptação

Há duas rotas legítimas, com nomes diferentes:

- **Modelo integralmente treinado pela Kellvyn:** pesos inicializados aleatoriamente e pré-treinados pelo pipeline do projeto. É a rota mais autoral, porém muito mais cara em dados e computação.
- **Modelo derivado e personalizado pela Kellvyn:** pesos-base de um modelo aberto licenciado, adaptados por treino supervisionado, LoRA ou método permitido. É uma rota válida e potencialmente muito mais eficiente, mas a origem-base deve permanecer declarada.

A rota derivada é aceita. Não será chamada de treinamento integral do zero. O objetivo é a inteligência completa de Kellvyn, não uma declaração enganosa sobre a linhagem dos pesos.

## 7. O que não será confundido

- modelo neural não é agente;
- agente não é conjunto de prompts;
- memória não é treinamento de pesos;
- ferramenta nova não é aprendizado neural;
- benchmark de texto não prova capacidade de operar o Android;
- APK compilado não prova que a aplicação funciona;
- modelo-base licenciado não torna automaticamente legal qualquer dataset, adapter ou conversão.

## 8. Estado atual

KBN-001 existe como protótipo neural próprio de previsão de bytes e treino mínimo. O agente Kellvyn possui um ciclo de ferramentas experimental, mas não tem backend neural conectado. Nenhum modelo final foi selecionado e nenhum teste físico no Poco F6 foi feito.
