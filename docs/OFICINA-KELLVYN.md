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

- O repositório contém duas camadas: a aplicação web React/TanStack na raiz e um projeto Android nativo em `android/`.
- A camada nativa inclui `android/settings.gradle`, `android/app/build.gradle`, `MainActivity.java`, `LocalBrain.kt`, `NativeModelPolicy.java` e `BotRuntimeService.kt`.
- O app nativo declara LiteRT-LM `0.18.0` e prepara o modelo `gemma-4-E2B-it-gpu.litertlm`, com download autorizado pelo usuário, verificação SHA-256 e processamento local depois do download. O arquivo de modelo é aproximadamente 2,01 GB; a performance e a estabilidade ainda precisam ser medidas no Poco F6.
- Não existe `android/gradlew` nem pasta `android/gradle/wrapper`. Por isso o CI instala Gradle 8.13 diretamente e compila pelo projeto já existente.
- A interface nativa encontrada é construída por código Java com Android Views e desenho manual. Ela não prova fidelidade visual à interface do Lovable. Não editar a interface até confirmar a captura original e fazer comparação visual.
- O projeto de referência Lovable acessível pela integração é a versão de ID `05d1c6e7-ffe3-40aa-ab6e-707f6f29f5fb`; o ID `53c2bba5-162c-46c1-baaf-0d5fea3a6c19` fornecido anteriormente não foi localizado nessa conexão. A identidade exata do arquivo de referência continua sendo uma verificação obrigatória.
- O workspace acessível do Lovable lista um projeto recente chamado **Kellvyn: Assistente IA** (ID `05d1c6e7-ffe3-40aa-ab6e-707f6f29f5fb`). O endereço fornecido anteriormente com ID `53c2bba5-162c-46c1-baaf-0d5fea3a6c19` não foi localizado pela integração; confirmar a fonte exata da interface antes de ligar ou portar componentes.

## Ferramentas da oficina

### 1. Compilação e validação contínua

Workflow: [`.github/workflows/workshop-checks.yml`](../.github/workflows/workshop-checks.yml).

- Valida instalação reproduzível via `bun install --frozen-lockfile`, build, lint e testes.
- Se `android/gradlew` existir, prepara Java 17 e Android SDK, executa `assembleDebug` e publica o APK como artefato de CI por 14 dias.
- Se não existir, o job informa explicitamente que nenhum APK nativo foi gerado. Isso evita confundir validação web com entrega Android.

### 2. Camada Android nativa

A pasta `android/` já contém uma implementação Java/Android Views, um motor Kotlin LiteRT-LM e um serviço para bots. O pipeline compila essa camada sem mudanças visuais. Compose é uma opção para um futuro port controlado caso seja necessário reproduzir com maior fidelidade o layout do Lovable; não se deve trocar a interface existente às cegas. Gradle 8.13 é instalado pelo CI, já que não há Gradle Wrapper no repositório.

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
3. Compilar a camada Android que já existe e guardar o APK de CI para diagnóstico (não é ainda a versão final).
4. Comparar a tela nativa atual com a captura Lovable; planejar qualquer ajuste visual em separado, com regressão visual aprovada.
5. Rodar testes locais com o modelo LiteRT-LM no Poco F6: memória, tempo, temperatura, bateria, português e visão.
6. Integrar ou reparar capacidades uma por uma através do chat principal, sem controles extras.
7. Validar o pipeline de Blender/GLB e os testes de UI por captura.
8. Só então chamar o resultado de APK do Kellvyn pronto.

## Definição de pronto

A oficina só estará validada quando houver: build web e Android reproduzível, CI verde, APK de diagnóstico baixável em CI, teste de instalação no Poco F6, regressão visual comparando a fonte exata do Lovable, teste de inferência local sem Internet e teste real de exportação Blender/GLB. O APK final só será publicado como arquivo `.apk` direto num GitHub Release após autorização de versão e validações. Um workflow configurado, uma biblioteca adicionada ou um APK de diagnóstico não provam que o app final está pronto.


## Entrega direta e ferramenta 3D

O workflow também tem dois caminhos condicionais adicionais:

- **GitHub Release:** ao publicar uma tag de versão `v*`, o job tenta anexar o APK como arquivo `.apk` direto. A publicação falha de forma explícita se o módulo Android ou o artefato não existirem; não será publicado um APK falso nem um ZIP de projeto.
- **Blender em CI:** `workshop/blender/smoke_test.py` cria uma cena com animação por keyframes e exporta GLB. O workflow salva o GLB e um manifesto como artefatos de teste. Isso comprova o caminho de criação/exportação 3D por script quando o runner executar o teste; ainda não é um editor 3D dentro do APK.

Para o app nativo, a candidata para **visualizar** modelos e animações GLB é [SceneView](https://github.com/sceneview/sceneview), uma biblioteca Android/Jetpack Compose baseada em Filament. Ela é um renderizador/visualizador, não substitui Blender como ferramenta de criação.

Para não confundir os escopos:
- **Criar e animar ativos 3D:** scripts Python do Blender no runner de CI.
- **Exibir ativos 3D no Android:** SceneView após o módulo Compose existir.
- **Animações da interface:** Compose/Lottie conforme apropriado, sem substituir a chuva/raios nem a animação original já pronta.
- **Reproduzir a interface de uma imagem:** inspeção visual do screenshot e implementação Compose com comparação de regressão (candidata: [Roborazzi](https://github.com/takahirom/roborazzi)); essa etapa espera a captura exata da tela original e o módulo nativo.
- **Imagem/PDF/voz e geração de imagem:** não contar como implementados até haver integração Android e teste offline/online conforme cada capacidade.

## Entrega direta pelo GitHub

Quando houver uma versão nativa realmente compilável, a sequência prevista é:
1. o módulo Android `android/` passa pelo `assembleDebug`;
2. o CI anexa o APK como artefato temporário para inspeção;
3. somente uma tag intencional `vX.Y.Z` inicia a publicação em GitHub Releases, com o arquivo `.apk` direto.

O workflow está preparado; não há release de APK enquanto o módulo nativo estiver ausente.
