# Kellvyn — plano de validação

## Gate A: build e regressão

- [ ] `testDebugUnitTest` passa na branch de laboratório.
- [ ] `assembleDebug` produz APK nativo válido.
- [ ] O workflow de interface confirma que os arquivos protegidos não mudaram.
- [ ] Nenhum WebView foi introduzido.
- [ ] O repositório de distribuição não recebe alterações.

## Gate B: ferramentas locais

- [ ] `listLocalProjects` devolve apenas metadados do workspace privado.
- [ ] `listProjectFiles` devolve caminhos relativos, sem caminhos absolutos do telefone.
- [ ] `readProjectFile` lê apenas extensões textuais permitidas.
- [ ] Caminhos fora do projeto são recusados pelo `ProjectWorkspaceStore`.
- [ ] `searchProjectFiles` respeita limite de arquivos e ocorrências.
- [ ] O orçamento de ferramentas reseta por mensagem e bloqueia chamadas extras.
- [ ] Erro numa ferramenta é devolvido como erro observado; não vira sucesso.

## Gate C: uso real do modelo

- [ ] Gemma 4 E2B produz uma chamada estruturada de ferramenta no formato esperado.
- [ ] O LiteRT-LM 0.18.0 executa a ferramenta registrada e devolve o resultado ao modelo.
- [ ] A resposta final distingue resultado da ferramenta de suposição.
- [ ] Se o modelo não chamar a ferramenta, Kellvyn não inventa o resultado.
- [ ] Testar o mesmo pedido na GPU e no fallback CPU, quando disponível.

O código do LiteRT-LM 0.18.0 inclui implementação e dados de teste de tool calls para Gemma 4; isso sustenta a hipótese técnica, mas não substitui o teste do arquivo quantizado específico no Poco F6.

## Gate D: Poco F6 (dispositivo real)

- [ ] Confirmar modelo transferido e hash verificado.
- [ ] Medir memória livre antes/depois da inicialização.
- [ ] Medir tempo de inicialização do motor.
- [ ] Executar ao menos 10 tarefas reais de tool calling.
- [ ] Registrar latência, falhas, temperatura e encerramentos do processo.
- [ ] Confirmar comportamento após fechar e abrir o app.
- [ ] Confirmar que o motor libera recursos corretamente após falha/cancelamento.

## Gate E: evolução do agente

- [ ] Sessão consegue manter objetivo e estado entre múltiplas ferramentas.
- [ ] Resumo de contexto não perde requisitos críticos.
- [ ] Escrita de arquivos só entra com confirmação real e diff verificável.
- [ ] Compilação só é relatada como concluída após artefato verificado.
- [ ] Avaliação mede sucesso da tarefa, não apenas fluência do texto.

## Estado

A implementação read-only inicial está sendo adicionada na branch de laboratório. Nenhum resultado de build nesta branch ou teste de inferência no Poco F6 deve ser presumido antes de receber evidência correspondente.
