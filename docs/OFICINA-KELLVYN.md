# Catálogo da Oficina — Kellvyn IA

**Revisão:** 20/10/2026  
**Repositório:** [kellvyn-ai-assistant](https://github.com/ar2233789-oss/kellvyn-ai-assistant)  
**Regra principal:** preservar a interface aprovada; trabalhar no cérebro, nos serviços, nos testes e no compilador sem redesenhar a tela.

> Este catálogo distingue código existente, validações automatizadas e testes que ainda dependem do aparelho. Um APK compilado no CI não prova que a inferência está estável no Poco F6 nem que a interface nativa é visualmente idêntica à fonte Lovable.

## 1. Trava da interface e fonte de verdade

- A interface original definida por Kellvyn é a referência visual. Não mudar geometria, botões, textos, cores, chuva/raios, animações, ordem ou navegação durante trabalho de infraestrutura.
- Não adicionar botões de modos/modelos ou menus só porque uma ferramenta nova foi incorporada. O chat principal continua como único ponto de comando; ferramentas são internas.
- A proteção de código existente é `.github/interface-lock.sha` + `scripts/check-interface-lock.sh`. A execução do workflow APK em 20/10 passou pela checagem da trava. Alterações a arquivos bloqueados exigem autorização explícita.
- **Divergência de fonte ainda aberta:** o ID salvo anteriormente, `53c2bba5-162c-46c1-baaf-0d5fea3a6c19`, não apareceu na conta Lovable conectada. O projeto acessível mais recente é **Kellvyn: Assistente IA**, ID `05d1c6e7-ffe3-40aa-ab6e-707f6f29f5fb` ([editor](https://lovable.dev/projects/05d1c6e7-ffe3-40aa-ab6e-707f6f29f5fb)). O código atual desse projeto usa `app-shell.tsx`, `chat.tsx`, `builder.tsx` e `orbit.tsx`; a árvore do GitHub, por outro lado, contém a família `src/components/kellvyn/KellvynApp.tsx`, `DigitalRain.tsx`, `ColorStudio.tsx` e `store.ts`. Essas árvores não são equivalentes neste momento.
- **Não copiar código entre as duas árvores, nem substituir nenhuma delas por aproximação.** Confirmar a fonte visual exata e estabelecer uma captura de referência antes de qualquer port ou ajuste visual. Até então, corrigir apenas lógica independente da aparência.

Arquivos de referência técnica:
- `AGENTS.md`: limites arquiteturais e proteção de interface.
- `.github/interface-lock.sha`: fingerprints aprovados.
- `scripts/check-interface-lock.sh`: validação dos fingerprints.
- `src/components/kellvyn/KellvynApp.tsx`, `DigitalRain.tsx`, `ColorStudio.tsx`, `store.ts`: interface React/efeitos no ramo GitHub atualmente protegido.
- `android/app/src/main/java/com/kellvyn/assistant/MainActivity.java`: interface nativa atual; mantê-la intacta durante ajustes no cérebro.

## 2. Inventário da estrutura

### Fonte Lovable acessível

O projeto Lovable mais recente identificado (ID `05d1c6e7-ffe3-40aa-ab6e-707f6f29f5fb`) é uma aplicação web React/TanStack Start. A lista de arquivos inclui:

- `src/components/app-shell.tsx`: casca e navegação.
- `src/components/chat.tsx`: conversa, envio e estado de processamento.
- `src/components/builder.tsx`: espaço de projetos/arquivos.
- `src/components/orbit.tsx`: animação orbital exibida pela interface.
- `src/lib/ai-provider.ts` e `src/routes/api/chat.ts`: transporte e endpoint da conversa.
- `src/lib/chat.server.ts`: instruções e integração do serviço.
- `src/lib/local-persistence.ts` e `src/components/workspace-provider.tsx`: dados locais do workspace.
- `src/styles.css`: tokens e estilos visuais.

**Limite confirmado da camada web:** o `GatewayProvider` faz requisições a `/api/chat`; o endpoint servidor usa o gateway de IA do Lovable e o modelo configurado `openai/gpt-6-astra`. Isso não é inferência offline no telefone. O construtor web organiza e edita arquivos locais e exporta ZIP, mas informa que não compila nem corrige projetos automaticamente. Não tratar o status da web como prova de funcionamento do cérebro Android.

### Android nativo existente no GitHub

- `android/app/src/main/java/com/kellvyn/assistant/MainActivity.java`: Activity, handlers e UI Android Views; arquivo protegido pela trava.
- `LocalBrain.kt`: ciclo de vida da inferência local, conversa, preparação/recuperação do modelo, anexos e contexto Web opcional.
- `NativeModelPolicy.java`: constantes e validação independente do download.
- `ProtectionPolicy.kt` e `WebLookupPolicy.kt`: regras de proteção reforçada e decisão de consultas Web.
- `BotRuntimeService.kt` e `BotTokenStore.kt`: runtime Telegram/Discord com credenciais cifradas no Android Keystore.
- `android/app/src/test/java/com/kellvyn/assistant/`: testes unitários das políticas do modelo, proteção e consultas Web.
- `AndroidManifest.xml`: permissões e declaração do serviço de runtime.

## 3. Catálogo de ferramentas e situação

| Oficina/ferramenta | O que existe | O que ainda precisa ser validado |
|---|---|---|
| Interface protegida | Componentes web registrados na trava e UI nativa existente | Captura correta do Lovable e comparação de paridade visual; fonte histórica indicada anteriormente não resolveu |
| Cérebro local | LiteRT-LM Android `0.18.0`, classe `LocalBrain.kt`, prompt e ciclo de inferência | Download completo e primeira inferência no Poco F6; memória, velocidade, temperatura, bateria e qualidade em português |
| Modelo local | Gemma `gemma-4-E2B-it-litert-lm`, artefato `gemma-4-E2B-it-gpu.litertlm` em revisão upstream imutável | Confirmar que o modelo abre e gera respostas reais no dispositivo; não confundir presença da biblioteca com inferência já validada |
| Download/integridade | URL fixada, tamanho esperado de 2.008.432.640 bytes, SHA-256 e política de retomada com Range; teste unitário | Medir tempo e consumo de rede/armazenamento no telefone; o download inicial exige Internet e espaço livre adicional (margem codificada: 150 MB) |
| Imagem e documentos | Preparação de entradas nativa para imagens suportadas, documentos de texto, PDF limitado e DOCX | Testar formatos, erros e limites no aparelho; suporte real depende do modelo/caminho de visão |
| Web opcional | Contexto público limitado, incluindo busca/Wikipédia, controlado pela preferência de Web | Testar alternância e transparência; não é um navegador geral nem promete leitura de todo site |
| Voz | Android Speech Recognizer e Text-to-Speech na Activity | Testar idioma português, permissões e fallback no Poco F6 |
| Construtor | PR #15 liga pedidos explícitos de APK nativo no chat principal ao parser de arquivos, validador estrutural e workspace privado | O primeiro perfil é limitado a Java Android sem bibliotecas externas; falta testar a cadeia completa no Poco F6 e ampliar os formatos com segurança |
| Compilador Android | Além da build da própria Kellvyn no CI, PR #15 integra `LocalAndroidCompiler`: ECJ → D8 → manifesto binário → assinatura/verificação APK no aparelho | Build/testes unitários da anfitriã passaram no CI; falta confirmar compilação de projeto gerado, instalação e abertura no Poco F6 |
| Testes web | Scripts Bun/Vite, ESLint e Vitest no `package.json` | Executar o novo workflow `workshop-checks.yml` e corrigir falhas de base sem reformatar ou redesenhar a interface |
| Oficina 3D | `workshop/blender/smoke_test.py` cria uma cena animada simples e exporta GLB para validação de CI | A ferramenta é só um smoke test, não um modelador 3D de produção dentro do APK. Ainda faltam fluxos de edição, keyframes, importação/exportação e UI real |
| Visualização 3D Android | SceneView é candidata para renderizar GLB quando apropriado | Avaliar integração e desempenho somente depois de definir a necessidade real; renderizador não substitui o editor Blender |
| Regressão visual | Trava de fingerprints já existente | Adicionar comparação por screenshot quando a imagem baseline correta estiver confirmada |
| Bots | Serviço Telegram/Discord e armazenamento cifrado de tokens | Conexão real exige configuração explícita e status confirmado; WhatsApp não possui runtime nativo pronto nesta versão |
| Segurança/verdade | Política que proíbe simular sucesso; armazenamento Keystore para tokens | Revisar fluxos de permissão/erro e garantir que o chat só declare ações após confirmação real |

## 4. Compilador APK — resultado já comprovado

Em **20/10/2026**, o GitHub Actions executou o workflow **Build APK** na branch `workshop/infra-review-final-2026-10-20` e terminou com sucesso:

- `gradle testDebugUnitTest assembleDebug --no-daemon`: sucesso.
- Verificação de existência/estrutura do APK: sucesso.
- Validação do identificador `com.kellvyn.assistant`: sucesso.
- Verificação de assinatura APK: sucesso.
- Artefato `Kellvyn-IA-APK` gerado, 24.769.313 bytes.
- SHA-256 do artefato de CI informado pelo GitHub: `b08f9f6b971bf227619d14c7958f85c4eca7ba494712ed63efabf33346e43fca`.
- [Execução de CI](https://github.com/ar2233789-oss/kellvyn-ai-assistant/actions/runs/39414392712).

Esse artefato é um **APK de diagnóstico/debug**, não uma declaração de versão final: o passo de GitHub Release foi ignorado porque a execução foi disparada em pull request, não por uma publicação em `main`. O artefato temporário expira em **19/11/2026**. A verificação automática não instalou o app nem executou a inferência no Poco F6.

### Integração do compilador de projetos — PR #15

A implementação está em revisão na [PR #15](https://github.com/ar2233789-oss/kellvyn-ai-assistant/pull/15), branch `production/local-java-apk-builder-20261020`. Ela não toca nos arquivos visuais bloqueados nem acrescenta botões.

- `GeneratedProjectOutputParser.kt`: detecta pedido explícito de APK, extrai blocos `Arquivo: caminho` e rejeita caminhos absolutos, travessia de diretórios, duplicatas e excesso de arquivos.
- `GeneratedAndroidProjectValidator.kt`: recusa projetos fora do perfil suportado antes de acionar download, armazenamento ou compilação.
- `ProjectWorkspaceStore.kt`: guarda arquivos em diretório privado e valida caminhos/tamanhos.
- `LocalAndroidCompiler.kt`: compila Java contra a plataforma Android, converte classes com D8, monta o APK com manifesto binário e confere assinatura.
- `LocalProjectBuilder.kt`: coordena o fluxo no chat principal; pede aceite explícito antes de baixar a plataforma Android (~64 MB) e exporta um APK verificado para `Downloads/Kellvyn` no Android 10+.
- `LocalBrain.kt`: encaminha pedidos explícitos ao compilador e libera a sessão de inferência antes do empacotamento para reduzir pressão de memória.
- Testes novos verificam parser de arquivos, rejeição de caminhos inseguros, identificação dos pedidos e frase exata de consentimento.

**Limites do primeiro perfil:** somente apps Android Java com interface criada por código, sem Kotlin/Compose, WebView, bibliotecas externas, `res/` ou `assets/`. A compilação da anfitriã, os testes unitários e a trava da interface passaram no CI da PR #15. Isso ainda não prova que o compilador de projetos funcionou no Poco F6 físico ou que um APK gerado foi instalado/executado.

Observações de build:
- O projeto usa `android/settings.gradle` e `android/app/build.gradle` Groovy. Não há Gradle Wrapper; o CI instala Gradle 8.13.
- Foram removidos da branch da oficina os arquivos `android/settings.gradle.kts` e `android/build.gradle.kts` duplicados, para manter uma única configuração Gradle coerente com o build verificado.
- Uma release direta `.apk` só deve ser criada em versão intencional depois das validações em aparelho e com identidade/assinatura de distribuição decididas. Não publicar automaticamente um build de diagnóstico como final.

## 5. Próximas etapas, em ordem

- [x] Localizar e catalogar a camada Android já existente.
- [x] Confirmar que o CI compilou os testes e o APK debug, validou o pacote e verificou a assinatura.
- [ ] Corrigir o workflow `workshop-checks.yml`: as execuções de 20/10 terminaram em falha imediata sem criar jobs, mesmo após normalizar as versões das Actions e simplificar os gatilhos. O workflow APK separado segue compilando com sucesso.
- [ ] Identificar sem ambiguidade o projeto Lovable e a captura que Kellvyn definiu como original; não tocar no layout antes disso.
- [ ] Instalar e abrir o APK de diagnóstico em um Poco F6; guardar logs de inicialização/erros.
- [ ] Baixar e verificar o modelo no telefone e executar prompts curtos em modo avião após o download.
- [ ] Medir memória, tempo de primeira resposta, respostas seguintes, temperatura e bateria; testar português e anexos visuais.
- [ ] Validar controles existentes: histórico, +/anexos, Web, voz, chuva/raios simultâneos, editor, terminal sob solicitação, proteção e bots.
- [x] Conectar o primeiro perfil do construtor ao chat: arquivos → validação → workspace privado → compilação Java local → assinatura/verificação → exportação para Downloads, com aceite explícito do SDK (PR #15; ainda sem teste físico).\n- [ ] Testar no Poco F6 uma criação/compilação real, instalar e abrir o APK; verificar memória, armazenamento, cancelamento e retomada.\n- [ ] Ampliar gradualmente para recursos, assets e dependências controladas, com testes próprios e sem executar scripts gerados arbitrários.
- [ ] Projetar a ferramenta 3D além do smoke test do Blender; definir criação, animação, visualização e formatos separadamente.
- [ ] Considerar versão de lançamento somente após paridade visual aprovada, testes físicos e teste de inferência offline concluídos.

## 6. Definição de pronto

O Kellvyn IA só deve ser marcado como pronto quando houver simultaneamente: fonte visual confirmada e regressão aprovada; UI nativa preservada; CI web/Android verde; APK instalável no Poco F6; download e SHA verificados; inferência local demonstrada sem Internet após preparação; testes reais de voz/anexos/Web e ações autorizadas; erros e status honestos; e processo de release reproduzível. A criação/exportação GLB via CI é infraestrutura inicial, não uma ferramenta de modelagem completa.

Nenhuma ferramenta nova autoriza alterar a interface aprovada. A próxima implementação deve priorizar correções isoladas no cérebro e testes no dispositivo, sem criar telas, menus ou controles fora da especificação de Kellvyn.
