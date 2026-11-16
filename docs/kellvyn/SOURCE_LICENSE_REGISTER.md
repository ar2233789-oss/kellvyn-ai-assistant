# Kellvyn — registro de origem e licenças

**Data da consulta:** 16/11/2026  
**Objetivo:** registrar as referências estudadas e evitar plágio ou atribuição falsa. Os projetos abaixo são material de pesquisa; nenhum código, modelo, peso, tokenizer ou pipeline de treinamento deles está autorizado a ser incorporado ao cérebro da Kellvyn.

> Este registro é uma triagem técnica, não um parecer jurídico. Antes de distribuir qualquer componente, conferir a licença do commit exato, arquivos NOTICE, dependências transitivas, marcas, modelo/pesos e datasets separadamente.

## 1. Projetos de referência

| Projeto / fonte | Licença identificada na triagem | Uso permitido neste projeto | Limite |
|---|---|---|---|
| Android Studio + Gemini Agent Mode | Produto/serviço proprietário; termos Google | Fluxo de planejamento, aprovações, ferramenta, teste em dispositivo, Logcat e correção iterativa como referência de produto | Não copiar código interno, modelo Gemini, prompts privados, marca/ícones ou serviços proprietários. A página de Agent Mode documenta o comportamento público |
| OpenHands/software-agent-sdk | MIT | Estudo conceitual de sessões, eventos e workspaces | Não copiar nem incorporar código |
| Aider | Apache 2.0 | Estudo conceitual de mapa de repositório e diffs | Não copiar nem incorporar código |
| SWE-agent | MIT | Estudo conceitual de ciclos agente-ambiente | Não copiar nem incorporar código |
| mini-SWE-agent | MIT | Estudo conceitual de ciclo compacto de agente | Não copiar nem incorporar código |
| OpenCode | MIT | Estudo conceitual de permissões, sessões e contexto | Não copiar nem incorporar código ou identidade visual |
| nanochat | MIT | Estudo conceitual da sequência de treinamento e avaliação | Não copiar código, tokenizer, dataset ou pesos |
| OLMo-core | Apache 2.0 | Estudo conceitual de dados, treino e checkpoints | Não copiar nem incorporar infraestrutura de treinamento |
| LiteRT-LM | Apache 2.0 | Estudo da separação entre runtime e pesos | Não incorporar ao cérebro nesta etapa |
| ExecuTorch | BSD 3-Clause (repo principal) | Estudo conceitual de exportação e execução | Não incorporar ao cérebro nesta etapa |
| llama.cpp | MIT (repo principal) | Estudo conceitual de kernels e execução ARM64 | Não copiar nem incorporar código ou pesos |
| MLC-LLM | Apache 2.0 no projeto principal | Estudo conceitual de compilação de modelos | Não copiar nem incorporar código ou pesos |

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

## 3. Regra de autoria

A regra atual é não copiar código de projetos estudados. Os arquivos serão escritos para o projeto Kellvyn, com commits, autores e testes próprios. A licença de um repositório público não transforma uma cópia em autoria original e não altera a decisão de manter o cérebro integralmente próprio.

## 4. Regra para pesos e modelos

Não há modelos candidatos. Nenhum peso, modelo-base, tokenizer, adaptador, checkpoint ou saída de outra IA será baixado ou integrado. Os checkpoints aceitos serão exclusivamente os produzidos pela implementação Kellvyn a partir de pesos inicializados aleatoriamente. Cada checkpoint terá hash, configuração e linhagem de dados documentados.

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
