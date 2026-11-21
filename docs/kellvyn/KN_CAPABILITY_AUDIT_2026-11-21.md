# Auditoria funcional KN / Studio — 21/11/2026

## Estado da recuperação

A branch `integration/kn-single-core-studio-recovery-20261121` recompõe os módulos Kotlin de runtime, planejamento, memória, checkpoints, catálogo de ferramentas e os arquivos KN-004 mais recentes. A tela `MainActivity.java` e os componentes visuais do Lovable permanecem inalterados; a ligação da conversa é feita pelo adaptador `LocalBrain`.

O caminho principal cria um único `KellvynCognitiveKernel`. O kernel desativa delegação (`maxDelegations = 0`, `allowDelegation = false`) e usa ferramentas tipadas como executores determinísticos. A presença de classes antigas de avaliação de especialistas não significa que o chat principal use várias inteligências.

## Portas de ativação do KN-004

A conversa principal não é liberada pela mera existência de um arquivo de pesos. São exigidos:

1. Checkpoint KN-004 íntegro, com tokenizer e token EOS compatíveis.
2. Gate numérico de treinamento aprovado.
3. Benchmark comportamental executado somente com os exemplos TEST registrados no manifesto.
4. Fingerprint dos parâmetros aprovado igual ao fingerprint do checkpoint carregado.
5. Construção do kernel central com a versão compacta de inferência, sem estados Adam residentes.

No treinamento, o candidato é avaliado em memória antes de substituir o checkpoint instalado. Uma reprovação mantém o checkpoint anterior. A aprovação é associada ao fingerprint, de modo que pesos diferentes invalidam a liberação anterior.

**Limite atual:** nenhum checkpoint conversacional treinado e aprovado está incluído no repositório. Sem corpus autorizado, treino, benchmark e medição no aparelho, o chat continua bloqueado corretamente. Os testes de código existentes não substituem essa evidência.

## Capacidades verificadas no código Android

| Área | O que o código executa | O que não está comprovado / não faz |
|---|---|---|
| Memória e contexto | Memória local, recuperação de contexto de projetos, indexação de arquivos e checkpoints de tarefas | Não prova qualidade de raciocínio sem checkpoint treinado |
| Planejamento | Plano estruturado, dependências, aprovações, execução limitada por ferramentas e verificação independente | Não é execução ilimitada nem trabalho sem limites |
| Automação | Fila persistente, agendamento, retry, recuperação de tarefas interrompidas e monitoramento de recursos | A ferramenta exposta ao modelo agenda/consulta/cancela principalmente probes de recursos; não há executor genérico para qualquer ação |
| Raster | Inspeção de metadados/integridade e redimensionamento de PNG/JPEG/WebP; preserva a origem e cria artefato novo | Não gera imagens por modelo generativo e não é editor por camadas |
| Vetorial | Cria SVG editável e prévia PNG a partir de formas e parâmetros estruturados | Não é um motor generativo geral de imagens |
| Animação | Renderiza timeline 2D com keyframes para spritesheet PNG e manifesto JSON | O próprio módulo declara que não codifica GIF nem MP4; não é um gerador de vídeo |
| Cena 3D | Havia um arquivo legado de exportação 3D | O arquivo tinha imports inválidos e conteúdo truncado/corrompido; foi isolado e não é registrado no runtime |
| Estudo e avaliação | Laboratório de traces, harnesses de avaliação, benchmarks e gates de treinamento | Código de teste/harness não significa competência geral já alcançada |
| Construção Android local | ECJ → D8 → manifesto binário → assinatura e verificação APK; usa workspace privado e exige aceite da licença do SDK | O caminho atual compila fontes Java Android limitadas; não substitui Gradle/Android Studio completo, não compila projetos Kotlin arbitrários e pode baixar a plataforma Android quando ausente |
| Sandbox | Workspace privado, artefatos com hash, limites e controle de recursos | Não há sandbox geral comprovado para executar código arbitrário gerado dentro do app |
| Áudio / música / vídeo | Não foi localizado motor funcional de geração de áudio, música ou vídeo no runtime Android auditado | Não declarar estas capacidades como disponíveis |

## Situação de build e testes

O PR de integração foi aberto em rascunho para validação, sem merge. As Actions foram disparadas, mas os jobs terminaram sem passos executados e sem URL de logs. Portanto o Gradle/JUnit, a criação/assinatura do APK e o laboratório Poco F6 **ainda não foram executados** nesta integração. Isso é `NOT_RUN`, não aprovação nem erro de compilação.

## Próximas condições para declarar concluído

- Obter um build real e todos os testes unitários.
- Corrigir os erros apontados pelo compilador/testes, sem tocar na interface aprovada.
- Conseguir corpus local autorizado suficiente para KN-004 e passar ambos os gates.
- Rodar benchmarks em português, uso de ferramentas, contexto e estabilidade.
- Medir memória, latência, temperatura e bateria no Poco F6; só então liberar a conversa em produção.
- Reconstruir o módulo 3D separadamente e adicionar exportação de mídia apenas com implementações e testes reais.

Esta auditoria descreve o que o código contém; não transforma capacidades declaradas em capacidades testadas.
