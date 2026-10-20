# Oficina de desenvolvimento — Kellvyn IA

Atualizado em 2026-10-20. Este documento descreve a infraestrutura de construção e validação. **Não é uma declaração de que o APK final ou todos os recursos já estejam prontos.**

## Regras de proteção

1. A interface original do Lovable é a referência visual. O trabalho de infraestrutura não pode adicionar, remover ou reposicionar controles, alterar animações, cores, espaçamentos ou textos visíveis sem uma solicitação explícita.
2. A branch `workshop/infra-bootstrap-2026-10-20` é isolada. Nenhuma alteração foi feita na branch `main` por esta preparação.
3. O app de entrega deve ser Android nativo, com Kotlin e Jetpack Compose; **não** usar WebView como casca da aplicação final.
4. Um check verde de web não significa APK nativo pronto. O APK só existe quando o módulo Gradle nativo está presente, compilado e publicado como artefato.
5. O chat principal será o único despachante de comandos. Ferramentas são capacidades internas, não novos botões ou modos de IA.
6. Nenhuma integração do ChatGPT (GitHub, Lovable ou outros conectores) passa automaticamente a existir dentro do APK. Cada capacidade usada pelo app precisa de uma implementação Android explícita.

## O que foi verificado no repositório

- A raiz atual contém uma app React 19 + TypeScript + Vite/TanStack Start e usa `bun.lock`.
- Há scripts de build, lint e teste no `package.json`.
- Não foram encontrados `android/gradlew`, módulos Gradle, `AndroidManifest.xml` nativo, configuração Capacitor nem pipeline Android preexistente na raiz pesquisada.
- Portanto, o repositório de hoje não prova a existência de um APK nativo nem de inferência local de IA.
- O workspace acessível do Lovable lista um projeto recente chamado **Kellvyn: Assistente IA** (ID `05d1c6e7-ffe3-40aa-ab6e-707f6f29f5fb`). O endereço fornecido anteriormente com ID `53c2bba5-162c-46c1-baaf-0d5fea3a6c19` não foi localizado pela integração; confirmar a fonte exata da interface antes de ligar ou portar componentes.

## Ferramentas da oficina

### 1. Compilação e validação contínua

Workflow: [`.github/workflows/workshop-checks.yml`](../.github/workflows/workshop-checks.yml).

- Valida instalação reproduzível via `bun install --frozen-lockfile`, build, lint e testes.
- Se `android/gradlew` existir, prepara Java 17 e Android SDK, executa `assembleDebug` e publica o APK como artefato de CI por 14 dias.
- Se não existir, o job informa explicitamente que nenhum APK nativo foi gerado. Isso evita confundir validação web com entrega Android.

### 2. Camada Android nativa

Alvo: Kotlin + Jetpack Compose + Gradle. O código nativo deverá ficar isolado em `android/`, sem reconstruir a interface no branch de produção até a captura visual de referência estar validada.

Critérios mínimos:
- instalação em um dispositivo Android real;
- permissões e seletores nativos para anexos e arquivos;
- armazenamento local do histórico;
- execução em segundo plano apenas quando o usuário ativar a função correspondente;
- captura de logs e testes de unidade;
- APK depurável produzido pelo workflow de GitHub Actions.

### 3. Motor da IA local

Primeira tecnologia a avaliar: **LiteRT-LM com API Kotlin para Android**. A documentação atual descreve execução no dispositivo, aceleração por hardware, multimodalidade e uso de ferramentas. A API anterior MediaPipe LLM Inference está em modo de manutenção, e a própria documentação recomenda migrar projetos Android para LiteRT-LM.

Isso é uma escolha de protótipo, não um modelo final já selecionado. Antes de fixar o modelo, medir no Poco F6 (8 GB RAM): memória máxima, velocidade de resposta, temperatura, bateria, qualidade em português, visão e uso de ferramentas. Não prometer operação multimodal offline até esses testes passarem.

Referências:
- https://developers.google.com/edge/litert-lm/overview
- https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android

### 4. Despachante e ferramentas internas

A IA principal deverá selecionar uma capacidade nomeada a partir de uma lista explícita, validar argumentos, pedir confirmação para operações de impacto e devolver o resultado ao mesmo chat.

Capacidades a implementar/testar em camadas:
- leitura e análise de imagens, PDFs e documentos;
- edição de arquivos de projeto com diff revisável;
- compilação e testes do projeto;
- terminal isolado, somente quando solicitado no chat principal;
- navegação Web quando a opção de Web estiver ligada;
- voz de entrada e leitura em voz alta em português;
- geração/edição de imagens;
- criação de cenas e animações 3D com exportação de formatos definidos.

**Importante:** uma capacidade não fica pronta só por aparecer na lista. Precisa ter código, permissões, limites, teste de sucesso e tratamento de erro. Nunca alegar que um comando foi executado sem resultado confirmado.

### 5. Imagem → interface e animações

- Usar a captura original e o código-fonte acessível do Lovable como referência, nunca redesenhar por aproximação sem comparar.
- Para a versão nativa, mapear componentes visuais a Compose mantendo uma lista de medidas, cores, ícones, textos, estados e animações.
- Acrescentar testes de regressão visual só depois de obter uma captura baseline confiável.
- A ferramenta de criação 3D/animação ainda precisa de uma prova de conceito separada: renderização/edição de malha 3D, keyframes, formatos de importação/exportação e tamanho final. Um gerador de vídeo não é o mesmo que um editor de modelo 3D.

## Ordem de execução

1. Fazer os checks de build/lint/teste passarem na branch isolada.
2. Confirmar e arquivar a captura correta da interface de referência, sem alterá-la.
3. Adicionar o projeto Kotlin/Compose e provar que o CI produz um APK nativo mínimo.
4. Portar a interface para Compose somente com testes visuais comparativos.
5. Integrar as capacidades internas uma por uma através do despachante único.
6. Integrar LiteRT-LM e comparar modelos diretamente no Poco F6; manter Web opcional.
7. Provar recursos de imagem/documentos, geração de imagem, 3D/animação e automações com testes reais.
8. Só então chamar o resultado de APK do Kellvyn pronto.

## Definição de pronto

A oficina só estará validada quando houver: build reproduzível, CI verde, APK nativo baixável, teste de instalação no Poco F6, regressão visual aprovada, teste de inferência local sem Internet e relatório de cada ferramenta com capacidade/limitações. A compilação mínima de infraestrutura, isoladamente, não satisfaz essa definição.
