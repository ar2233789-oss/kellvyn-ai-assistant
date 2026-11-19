# Auditoria de prontidão real da Kellvyn IA — 19/11/2026

**Branch de avaliação:** `kellvyn-rnd/agent-trace-duration-port-20261118`  
**HEAD na abertura desta auditoria:** `b8dc3f33107b9e2c4cf16d2a70875a7d688cbbb8`  
**Base técnica:** `lab/kellvyn-intelligence-core-20261116` (`4af672febef2cf81bbbe2a00ee8f32161711b4ec`)  
**Decisão:** não declarar a IA pronta, não ligar Runtime e não mesclar a branch enquanto os gates abaixo não forem concluídos.

## 1. O que já existe de verdade

### 1.1 Cérebro KN-003
- Tokenizer BPE byte-fallback próprio: começa com os 256 bytes UTF-8 e aprende merges do corpus explicitamente fornecido.
- Decoder-only Transformer experimental: self-attention causal multi-head com RoPE, Pre-LayerNorm, caminhos residuais e feed-forward SwiGLU.
- KV cache incremental de bloco único, com janela deslizante e checkpoints próprios com checksum.
- Treinamento supervisionado de próxima resposta: só completion tokens recebem gradiente; TRAIN ensina tokenizer/pesos; VALIDATION e TEST são mantidos fora da atualização.
- Manifesto de proveniência exige permissão, licença, referência da origem, evidência de direitos e hash de conteúdo; rejeita sinais comuns de credenciais/segredos.
- Quality Gate mede loss inicial/final de validação, held-out loss, gap de generalização, splits e proveniência.
- Adaptador de inferência emite decisões KELLVYN/1 estritas; texto livre não é executado. Saída incompleta, inválida ou sem EOS/END é recusada.

### 1.2 Agente central e especialistas
- `KellvynAgent` contém o loop de controle; não é o modelo neural.
- `KellvynOrchestrator` é o coordenador separado que aplica policy, compila perfis, reduz ferramentas por privilégio mínimo e controla timeout/cancelamento.
- O catálogo contém 14 perfis especialistas versionados. Perfil define missão/procedimento/formato, mas não concede permissões.
- `KellvynSpecialistToolPolicy` mantém a matriz de capacidades; o modelo não pode ampliar a lista de ferramentas autorizadas.
- Contexto de especialista e resultados de ferramentas são tratados como dados não confiáveis.
- Traces guardam metadados sem prompts, respostas integrais, argumentos, conteúdo de arquivo, tokens ou segredos.

### 1.3 Persistência e laboratório
- Pacotes KN-002 e KN-003 são guardados no diretório privado do app, em arquivos temporários/backup com troca atômica e verificação de integridade.
- O R&D Studio separa trilhas Brain, Agents e Integration e usa worktrees/branches por hipótese.
- Agent Lab Android já tem fila persistente, checkpoints cifrados, Resource Governor, Worker sob demanda, diagnósticos e avaliação de perfis.
- O monitor de recursos mede RAM, heap, PSS, CPU, processadores, banda térmica, thermal headroom, limiares OEM, bateria e espaço do volume privado do app.

## 2. Limites mensuráveis do cérebro atual

A configuração padrão em `KellvynTransformerLanguageModel.Config` é:
- Vocabulário: 512 tokens (valor padrão, não o tamanho garantido de qualquer checkpoint treinado).
- Contexto: 256 tokens.
- Embedding: 32 dimensões.
- Cabeças: 4.
- Feed-forward: 64.
- Profundidade: um único bloco Transformer.
- Estimativa de parâmetros nessa configuração: 43.648 parâmetros, calculada a partir das matrizes de embedding, projeções de atenção, LayerNorm, SwiGLU e saída.

Limites da pipeline:
- No máximo 1.000 exemplos por treino.
- No máximo 500.000 bytes no corpus total.
- No máximo 100.000 passos planejados por chamada.
- Contexto permitido atualmente: até 256 tokens.
- O bundle é gerado no aparelho depois de treino autorizado; não há pacote de pesos pré-treinado embutido no APK/repositório.

Isso é uma prova de conceito de modelo pequeno, não um LLM geral. Um checkpoint que reduza a loss ou passe o quality gate fica apenas **elegível para benchmark de protocolo**; não prova conversa natural, raciocínio, código, planejamento ou competência geral.

## 3. Por que o chat ainda não pode ser ligado

Inspeção do código atual:
- `KellvynRuntime.isReady()` retorna `false`.
- `KellvynRuntime.reply()` ainda informa que o núcleo está em construção.
- `MainActivity.sendMessage()` consulta `isReady()` e bloqueia o envio antes de chamar `reply()` quando está falso.
- `createOwnTransformerInferenceEngine()` só cria o adaptador se encontrar um bundle local íntegro, contexto de pelo menos 128 tokens e aprovação do Quality Gate.
- A árvore versionada não contém um arquivo de pesos `kellvyn-kn-003.bundle`; o store espera esse arquivo no diretório privado criado depois do treino.
- A pipeline espera exemplos de treino, validação e teste com proveniência/licença. Não existe corpus de treino incorporado no repositório nem evidência de um treino real já concluído.

Teste existente que documenta o comportamento correto:
- `KellvynTransformerInferenceEngineTest.randomUntrainedTransformerCannotProduceAnAcceptedAgentDecision()` espera que o modelo aleatório sem treino seja recusado pelo protocolo.

**Logo, ligar `isReady()` agora seria incorreto.** A arquitetura de agente e a camada de inferência estão preparadas para integração controlada, mas ainda não há pesos capazes de responder com segurança e qualidade.

## 4. Portes seletivos feitos nesta rodada

1. Portado de `kellvyn-rnd/brain-gradient-coverage-001` somente o modelo e o teste do KN-003:
   - cobertura explícita dos 14 grupos de parâmetros;
   - seleção de coordenadas com gradientes zero e não zero;
   - restauração de parâmetros em bloco `finally`, inclusive quando a checagem falha;
   - relatório de quantidade de coordenadas conferidas por grupo;
   - caminho de arrays do modelo não constrói pares nomeados no caminho usado fora do diagnóstico.
2. O teste de gradientes foi endurecido:
   - head counts 1, 2 e 4;
   - seeds determinísticas;
   - todos os 14 grupos devem receber coordenadas;
   - erro absoluto < 0,004 e erro relativo < 0,06 em cada configuração.
3. Portado de `kellvyn-rnd/brain-agent-contract-001` o teste `KellvynBrainAgentIntegrationContractTest`:
   - protocolo KELLVYN/1 → decisão DELEGATE → agente principal → orquestrador → ferramentas READ_ONLY → retorno UNTRUSTED;
   - ferramenta destrutiva solicitada pelo modelo não é executada;
   - decisão de fio malformada é rejeitada antes de qualquer ferramenta.
   O motor do teste é roteirizado; isso testa o contrato entre camadas, não a competência de pesos treinados.
4. A suíte estática Integration do Studio já lista esse teste e agora seu teste Python exige explicitamente sua presença.

Nenhuma branch experimental antiga foi mesclada; os arquivos necessários foram portados individualmente sobre a branch ativa.

## 5. Melhorias do laboratório e recursos

- CI consolidado de três jobs paralelos em um job sequencial, com preflight de OS/arquitetura/CPU/RAM/disco e versões de Python/Java/Gradle/ADB, snapshot final e artefatos.
- R&D Studio aceita wrapper íntegro quando disponível ou Gradle externo confiável fixado por versão e SHA-256 quando não há wrapper no repositório.
- Rastreamento do orquestrador agora mede duração monotônica de sucesso, bloqueio, falha e cancelamento.
- Sampler térmico atualiza amostras válidas a cada 1 segundo e recua 10 segundos em NaN/valor inválido/exceção; não cria polling.
- Governador usa os limiares térmicos OEM em Android 15 quando disponíveis; mantém fallback conservador quando ausentes.
- Snapshot READ_ONLY expõe armazenamento privado disponível/total, CPU e processadores.
- WorkManager exige bateria não baixa e armazenamento não baixo para drenar fila em segundo plano.

## 6. Evidência executada versus pendente

### Execução local real
- Classe pura `KellvynThermalHeadroomSampler` compilada com Kotlin/JVM disponível no ambiente e quatro cenários executados: 4/4.
- Simulação exaustiva do seletor de coordenadas do gradient checker em Python: 36.848 combinações de tamanhos/limites/distribuições zero-não-zero; nenhum índice inválido ou laço sem término.
- Esses resultados são limitados ao sampler isolado e à seleção de índices. Não representam Gradle/JUnit da aplicação, backprop completo, integração Android nem teste físico.

### Testes escritos, ainda não executados no projeto
- KN-003: 10 métodos de teste no arquivo do Transformer, incluindo cobertura de gradientes, treino supervisionado, EOS, checkpoint e equivalência KV cache.
- Brain + Agents: 2 métodos no teste de contrato integrado.
- R&D Studio Python: 20 métodos de teste escritos no total, incluindo Gradle externo, integridade de binário e escopos Brain/Agents/Integration.
- Resource Governor: 10 métodos unitários no arquivo atual.
- Thermal sampler: 4 métodos JUnit.
- Resource snapshot: 2 métodos unitários; Worker: testes instrumentados.

### CI bloqueado antes dos steps
Run mais recente observado nesta auditoria:
- Run `44432449442`
- Job `135252947870`
- `runner_id=0`, `runner_name` vazio, `steps=[]`.

Classificação: **NOT RUN / runner não alocado**. Python, Gradle, JUnit e Android instrumented não iniciaram. Não existe aprovação nem falha de compilação do código nesse run.

## 7. Gates restantes para declarar a IA funcional

1. Resolver executor real: GitHub Actions após a conta/runners voltarem a alocar, ou CoGo em cópia isolada para build manual; não instalar runner remoto no Poco F6 pessoal.
2. Executar os 20 testes Python do Studio e a suíte Gradle de Brain.
3. Executar Agents e Integration, incluindo `KellvynBrainAgentIntegrationContractTest`.
4. Criar/importar corpus autorizado com TRAIN, VALIDATION e TEST não contaminados, identidade de licença/proveniência e sem segredos.
5. Treinar KN-003 no próprio aparelho/ambiente com medição de RAM, PSS, CPU, armazenamento, bateria e térmica; guardar manifesto e bundle.
6. Passar Quality Gate, benchmark de protocolo, benchmark comportamental held-out e testes de decisão/ferramenta.
7. Só então ligar a inferência no Runtime/Chat com tratamento de histórico, timeout, cancelamento, anexos suportados e traces.
8. Build APK, instalação AVD Android 15, validação visual sem alterar layout e teste físico no Poco F6.

**Critério de pronto:** bundle treinado e íntegro + benchmark comportamental aprovado + runtime conectado + Gradle/JUnit/Android instrumented passados + teste em aparelho físico. Até lá, o estado correto é experimental/desconectado.
