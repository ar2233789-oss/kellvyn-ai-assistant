# Teste funcional do construtor de aplicativos Android

**Escopo:** branch experimental `lab/kellvyn-intelligence-core-20261116`.

## O que a esteira prova

O gate usa um aplicativo Java Android de demonstração, gerado como dados de saída no mesmo formato consumido pelo chat principal. O teste instrumentado passa por:

1. `GeneratedProjectOutputParser`: extração dos arquivos e validação de caminhos;
2. `GeneratedAndroidProjectValidator`: verificação do perfil estrutural suportado;
3. `ProjectWorkspaceStore`: gravação privada do projeto;
4. `LocalProjectBuilder`: orquestração do caminho real de compilação;
5. `LocalAndroidCompiler`: ECJ compila Java, D8 produz DEX, `BinaryAndroidManifest` gera o manifesto, Android Keystore assina e `ApkVerifier` verifica;
6. `adb install`: instalação do APK gerado no emulador Android 15;
7. UI Automator: abre o app, encontra o marcador `APP_BUILD_SMOKE_OK`, toca em `Incrementar` e exige que o texto mude de `Contagem: 0` para `Contagem: 1`.

O artefato de teste inclui o APK gerado, capturas, árvore de acessibilidade e diagnósticos. O workflow de comparação Poco F6 também deve executar na branch de laboratório, sem editar a interface protegida.

## O que ainda não prova

- A saída do modelo é um fixture determinístico; isso valida o parser, builder e compilador, mas não mede a qualidade de geração de um cérebro neural.
- O backend de inferência conversacional ainda não está conectado ao runtime principal. Portanto, não se pode afirmar que a IA já gera aplicativos corretos de forma autônoma.
- O teste roda num emulador Android 15 com resolução/densidade semelhantes às usadas pelo laboratório; não substitui teste físico no Poco F6/HyperOS.
- Um único app mínimo prova o caminho de ponta a ponta, não compatibilidade universal nem qualidade de aplicações complexas.

## Perfil atual do compilador local

O primeiro caminho de compilação aceita projetos Android nativos Java simples, com Activity e interface criada por código. Não aceita Kotlin, Jetpack Compose, WebView, dependências externas, assets ou recursos Android adicionais. A validação estrutural usa verificações estáticas e heurísticas; o compilador real e a instalação são provas separadas.

## Próxima evolução do agente

Quando houver um backend de inferência aprovado, montar um conjunto fixo de tarefas em português (calculadora, lista persistente, formulário com validação, conversor, catálogo offline e app com permissões). Cada tarefa deve ter critérios observáveis, compilar e instalar sem erros, passar por interações automatizadas, manter dados após reinício quando solicitado, funcionar sem rede quando prometido e respeitar permissões. Medir taxa de compilação, taxa de fluxos aprovados, defeitos por severidade, regressões e custo de recursos. Resultados do modelo nunca podem substituir evidências de build, instalação ou execução.

## Como executar

A partir da pasta `android`:

- `gradle testDebugUnitTest --no-daemon` — testes unitários e de regras;
- `gradle assembleDebug --no-daemon` — compilação do aplicativo Kellvyn;
- `gradle connectedDebugAndroidTest --no-daemon` — teste instrumentado que constrói o app de demonstração no dispositivo conectado.

O workflow `.github/workflows/poco-f6-parity.yml` executa também a inicialização do emulador, instalação, interação e comparação visual. O APK da demonstração é descartável e exclusivo do laboratório.
