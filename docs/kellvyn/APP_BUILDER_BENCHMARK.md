# Benchmark de qualidade do agente construtor de aplicativos

**Estado:** especificação para ativar assim que o backend neural estiver conectado ao chat principal.  
**Alvo inicial:** Android nativo Java, execução local, emulador Android 15 com perfil visual semelhante ao Poco F6.

## Regra central

O agente não avalia o próprio trabalho. Uma resposta dizendo "feito", um plano concluído ou um log produzido pelo modelo não conta como evidência. A avaliação aceita apenas resultados externos: saída do compilador, verificador de assinatura, Package Manager/ADB, hierarquia UIAutomator/Espresso, estado persistido lido pela aplicação de teste e logs de processo.

Cada caso começa num workspace vazio e num dispositivo limpo. O prompt original, a saída bruta do agente, os arquivos finais, hashes, logs, relatórios de teste e APK são guardados com um identificador de execução. O teste nunca injeta a solução de referência no contexto do modelo.

## Escada de tarefas

| ID | Pedido de teste em português | Prova obrigatória |
|---|---|---|
| APP-01 | Criar um app simples com botão e contador | Compilar, instalar, abrir, tocar no botão e verificar 0 → 1 |
| APP-02 | Criar uma calculadora de soma, subtração, multiplicação e divisão | Valores conhecidos, divisão por zero tratada, resultado correto e entrada inválida rejeitada |
| APP-03 | Criar um bloco de notas local | Criar, editar, apagar, fechar processo, reabrir e verificar persistência |
| APP-04 | Criar um formulário de cadastro | Campos obrigatórios, validação de formato, erros visíveis e envio válido |
| APP-05 | Criar um gerenciador de tarefas | Criar, concluir, filtrar e remover tarefas sem duplicação |
| APP-06 | Criar um catálogo que funcione sem internet | Desligar rede, navegar e pesquisar o conteúdo local sem crash |
| APP-07 | Criar app com duas telas e navegação | Abrir tela secundária, voltar, preservar estado solicitado e impedir tela vazia |
| APP-08 | Criar um leitor de arquivo escolhido pelo usuário | Fluxo de seletor do Android, permissão negada/cancelada e leitura segura do arquivo autorizado |
| APP-09 | Criar app com acesso de rede opcional | Com rede: resposta válida; sem rede/timeout: estado de erro recuperável, sem travar interface |
| APP-10 | Corrigir um defeito numa aplicação existente | Reproduzir teste que falha antes, editar apenas o escopo permitido, passar regressão e manter arquivos não relacionados |

O primeiro perfil local do compilador só suporta parte desta lista. Casos com seletor de arquivos, múltiplas telas, recursos adicionais ou rede só entram na execução automática quando a ferramenta e o manifesto suportarem esses recursos com testes próprios. Não reclassificar um caso como aprovado por reduzir silenciosamente o pedido.

## Critérios de aprovação

**Bloqueios absolutos (qualquer falha reprova o caso):**
- projeto passa pelo parser e pelo validador sem caminho inseguro ou arquivo truncado;
- compilação real termina e gera APK não vazio;
- assinatura do APK é verificada;
- pacote instala e abre;
- todos os comportamentos obrigatórios passam por interação automatizada;
- nenhum crash do processo durante o fluxo;
- nenhuma permissão, dependência, rede ou serviço não solicitado é introduzido.

**Pontuação comparável, depois dos bloqueios:**
- 15 pontos: estrutura e compilação reproduzível;
- 15 pontos: instalação, inicialização e estabilidade;
- 35 pontos: funções principais e estados de erro;
- 15 pontos: persistência, quando solicitada;
- 10 pontos: comportamento offline/rede/permissões, quando aplicável;
- 10 pontos: segurança, escopo e ausência de regressões.

Uma tarefa não aplicável recebe "N/A" e os pesos são normalizados entre os critérios aplicáveis; nunca recebe pontos gratuitos. O relatório deve exibir cada critério individualmente, não só uma média.

## Como medir a inteligência sem autoavaliação

- Executar cada prompt em workspace zerado, sem arquivos residuais.
- Guardar versão do modelo/runtime, instrução de sistema, parâmetros de inferência, orçamento de contexto, ferramentas disponíveis e commit do agente.
- Fazer pelo menos três tentativas independentes por tarefa quando o backend existir; reportar mediana e dispersão, não apenas a melhor tentativa.
- Separar defeitos de geração, parser, validador, compilador, runtime e teste.
- Registrar taxa de compilação, taxa de instalação, taxa de aprovação funcional, primeira tentativa aprovada, número de correções, regressões, tempo de CPU/RAM/armazenamento e falhas por severidade.
- Comparar alterações do agente no mesmo conjunto fixo, com o mesmo ambiente. Uma melhoria só é real se aumentar a aprovação funcional sem elevar bloqueios de segurança.
- Para correção de bugs reais, usar a lógica do SWE-bench: estado inicial reproduzível, patch isolado, suíte de regressão e avaliação num ambiente limpo; testes passam só quando o defeito é realmente resolvido.

## Arquitetura de teste recomendada

1. **Unitários JVM:** parser, validação de paths, planejamento, schemas das ferramentas, políticas e algoritmos puros.
2. **Integração Android:** workspace, SharedPreferences/SQLite, Android Keystore, permissões e ciclo de vida real.
3. **Build gate:** ECJ → D8 → manifesto → APK → assinatura → verificação.
4. **Teste externo de app:** instalar o APK com ADB/UI Automator, abrir, clicar, digitar, verificar hierarquia, relançar e inspecionar Logcat. Espresso pode complementar os testes de Views dentro da aplicação.
5. **Comparação visual:** capturas alinhadas no mesmo viewport, sobreposição e mapa de diferenças. Uma diferença visual não é automaticamente bug: conferir hierarquia e geometria antes de editar.
6. **Teste de robustez:** rede desligada, permissões negadas, rotação, pouco armazenamento, fechamento/reabertura e repetição do fluxo.

A documentação Android recomenda separar testes locais, instrumentados e de UI; UI Automator é adequado a fluxos que atravessam apps e componentes do sistema. Dispositivos gerenciados pelo Gradle podem criar, implantar, limpar e executar emuladores automaticamente, além de produzir relatórios reprodutíveis.

## Limite atual

O fixture APP-01 do smoke test é uma saída determinística, não geração neural. Até ligar um backend de inferência e executar esta escada, não há medição honesta da capacidade do cérebro de projetar e implementar aplicativos. O compilador atual é restrito a Java Android simples, sem Kotlin/Compose, bibliotecas externas, assets ou recursos XML adicionais.
