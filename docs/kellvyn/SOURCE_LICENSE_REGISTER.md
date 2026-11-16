# Kellvyn — registro de origem e licenças

**Data da consulta:** 16/11/2026  
**Objetivo:** manter a origem correta dos componentes e evitar plágio, atribuição falsa ou uso incompatível com as licenças, mesmo em uma instalação privada.

> Este registro é uma triagem técnica, não um parecer jurídico. Antes de distribuir qualquer componente, conferir a licença do commit exato, arquivos NOTICE, dependências transitivas, marcas, modelo/pesos e datasets separadamente.

## 1. Projetos de referência

| Projeto / fonte | Licença localizada | O que pode ser considerado | Condições / restrições |
|---|---|---|---|
| Android Studio + Gemini Agent Mode | Produto/serviço proprietário; termos Google | Fluxo de planejamento, aprovações, ferramenta, teste em dispositivo, Logcat e correção iterativa como referência de produto | Não copiar código interno, modelo Gemini, prompts privados, marca/ícones ou serviços proprietários. A página de Agent Mode documenta o comportamento público |
| OpenHands/software-agent-sdk | MIT | Código de sessões, eventos, workspaces e ferramentas, se uma parte concreta se mostrar compatível | Preservar copyright e texto da licença em cópias ou partes substanciais; verificar dependências |
| Aider | Apache 2.0 | Implementação selecionada de mapas de repositório, contexto, Git/diff ou utilitários, se justificável | Incluir licença, manter avisos, marcar arquivos modificados e preservar NOTICE quando houver |
| SWE-agent | MIT | Componentes selecionados de interface agente-ambiente, trajetórias e testes | Preservar aviso de copyright e licença |
| mini-SWE-agent | MIT | Alternativa atual compacta para estudar ou reaproveitar ciclo de agente | Preservar aviso e licença; conferir versão |
| OpenCode | MIT | Sistema de permissões, modos de planejamento/execução, agentes especializados e compaction, se um componente for reutilizado | Preservar aviso e licença; não reutilizar identidade visual como se fosse própria |
| nanochat | MIT | Código de treinamento e avaliação em experimentos fora do Android, quando viável | Preservar aviso e licença; o dataset usado em treinamento tem licença independente |
| OLMo-core | Apache 2.0 | Infraestrutura de treino PyTorch, otimizadores, dados, checkpoints e métricas se necessário | Incluir licença, manter avisos e indicar alterações |
| LiteRT-LM | Apache 2.0 | Runtime de inferência Android/JVM, caso passe no gate de modelo próprio | Runtime não é modelo nem pesos; cumprir Apache 2.0 e auditar bibliotecas transitivas |
| ExecuTorch | BSD 3-Clause (repo principal) | Runtime/exportação de modelos PyTorch e API Android, se compatível | Preservar avisos, condições e disclaimer; conferir componentes e backend específicos |
| llama.cpp | MIT (repo principal) | Runtime de inferência CPU/GPU, suporte a GGUF, ARM64 e kernels Android, se compatível com o modelo | Preservar aviso e licença; auditar backends e dependências selecionados |
| MLC-LLM | Apache 2.0 no projeto principal | Runtime/compilação de modelos para GPU móvel se a cadeia de build fizer sentido | Incluir licença/NOTICE e auditar TVM, submodules, kernels e dependências |

## 2. Licenças conferidas em repositórios

Os arquivos de licença foram consultados diretamente nos repositórios:

- Aider: `Aider-AI/aider/LICENSE.txt`, SHA `d645695673349e3947e8e5ae42332d0ac3164cd7` — Apache 2.0.
- OpenHands SDK: `OpenHands/software-agent-sdk/LICENSE`, SHA `b0bd86a6335a564bd3766666bebbf014fd6a8013` — MIT.
- SWE-agent: `SWE-agent/SWE-agent/LICENSE`, SHA `e702436e21844c5c519de31ab68277a6d3b427d9` — MIT.
- mini-SWE-agent: `SWE-agent/mini-swe-agent/LICENSE.md`, SHA `503821c9dbe06f13a48e54088eb6cd203adb407f` — MIT.
- OpenCode: `anomalyco/opencode/LICENSE`, SHA `6439474beed8e0271df9862eff97ffd70ec2464c` — MIT.
- nanochat: `karpathy/nanochat/LICENSE`, SHA `72d95c190c0d14341d5b0720e28c88fb58690821` — MIT.
- OLMo-core: `allenai/OLMo-core/LICENSE`, SHA `9b259bdfcf9022e7f4999a318ab7261300644e11` — Apache 2.0.
- LiteRT-LM: `google-ai-edge/LiteRT-LM/LICENSE`, SHA `261eeb9e9f8b2b4b0d119366dda99c6fd7d35c64` — Apache 2.0.
- ExecuTorch: `pytorch/executorch/LICENSE`, SHA `c16e59652bbf3e8b83d6750fa0294657902b7eaf` — BSD 3-Clause.
- Gemini/Android Studio: não é repositório de código aberto para copiar; usar somente comportamento documentado como referência.

Os SHAs acima identificam os arquivos de licença consultados nessa auditoria; antes de cada release, comparar com a versão/commit realmente incluído.

## 3. Regras para copiar código de terceiros

Antes de qualquer cópia:
1. confirmar que o arquivo pertence ao repositório e versão declarados;
2. abrir o arquivo LICENSE e NOTICE no mesmo commit;
3. verificar cabeçalhos de copyright do próprio arquivo;
4. verificar se ele tem licença diferente da raiz;
5. verificar licenças das dependências que ele importa;
6. confirmar que o uso privado e as modificações previstas são permitidos pelos termos;
7. copiar somente o mínimo necessário;
8. manter avisos legais;
9. anotar mudanças realizadas;
10. executar revisão de segurança e testes;
11. adicionar o componente ao inventário de dependências e pacote de notices.

Não assumir que todo arquivo em um repositório tem a mesma licença da raiz. Não assumir que “público no GitHub” equivale a permissão de uso.

## 4. Regra adicional para modelos e pesos

A licença do código do runtime não licencia os pesos. Para cada modelo candidato, registrar:
- nome e versão exatos;
- URL oficial e commit/revisão;
- hashes dos pesos e tokenizer;
- licença dos pesos;
- termos de uso aceitável;
- permissão explícita para uso comercial e redistribuição no APK, se houver;
- condições para derivados/fine-tuning;
- avisos e atribuições exigidos;
- origem declarada dos dados de treino;
- limitações conhecidas e modelo card;
- formato, quantização e conversões aplicadas.

Um modelo aberto pode ter pesos disponíveis sem ter todos os dados e processos de treinamento abertos. Não chamar um modelo de “open source” sem qualificar o que está de fato aberto.

## 5. Regra para datasets

Cada dataset precisa de registro próprio: autor/fonte, URL, licença/termos, uso comercial, atribuição, data de captura, processamento e possibilidade de redistribuição. Excluir dados pessoais e segredos. Não usar conversas do usuário para treinamento sem consentimento específico e informado.

## 6. Instalação privada

A aplicação será de uso exclusivo do proprietário e não será preparada para publicação pública. Antes de instalar versões pessoais:
- manter cópia das licenças dos componentes efetivamente incluídos;
- registrar versões e hashes;
- verificar os termos específicos dos pesos e datasets usados;
- documentar os dados acessados e as conexões externas;
- proteger tokens e dados pessoais;
- permitir ao proprietário apagar memórias, projetos e credenciais.

A privacidade do produto não transforma código de terceiros em código próprio. Os avisos e as condições das licenças aplicáveis continuarão sendo respeitados.
 
## 7. Fontes oficiais

- Android Studio Agent Mode: https://developer.android.com/studio/gemini/agent-mode
- Play Intellectual Property: https://support.google.com/googleplay/android-developer/answer/9888072
- Play User Data / Data Safety: https://support.google.com/googleplay/android-developer/answer/10144311
- Hugging Face: distinção entre open model, open-weight e open-source: https://huggingface.co/open-model
