# Kellvyn — registro de origem e licenças

**Data da consulta:** 16/11/2026  
**Objetivo:** registrar origens e condições de aproveitamento. Código aberto, pesos abertos e runtimes de terceiros podem ser integrados quando licença e termos permitirem. A autoria original deve permanecer explícita; nenhuma integração transforma o componente externo em criação própria.

> Este registro é uma triagem técnica, não um parecer jurídico. Antes de distribuir qualquer componente, conferir a licença do commit exato, arquivos NOTICE, dependências transitivas, marcas, modelo/pesos e datasets separadamente.

## Estado jurídico do repositório da Kellvyn

Na inspeção desta branch não foi encontrado arquivo raiz `LICENSE`, `COPYING`, `NOTICE` ou `COPYRIGHT`. Portanto, não se deve presumir que o código próprio do repositório esteja liberado como open source ou que terceiros tenham permissão para copiá-lo. Antes de qualquer publicação ou distribuição, o titular do projeto deve decidir os termos de licença do próprio código.

O laboratório KCL-001 não incorporou código-fonte, pesos, prompts privados ou datasets de terceiros. Os arquivos novos foram escritos especificamente para este repositório, com base em requisitos próprios e padrões técnicos públicos.

As dependências já existentes no Gradle são uma questão separada: cada artefato e dependência transitiva precisa de uma SBOM e verificação de licença/NOTICE por versão. Esta inspeção não conclui que todas as dependências existentes já estejam liberadas ou auditadas. Nenhum artefato externo novo foi adicionado pelo KCL-001.

## 1. Projetos de referência

| Projeto / fonte | Licença identificada na triagem | Uso possível em Kellvyn | Obrigações e limites |
|---|---|---|---|
| Android Studio + Gemini Agent Mode | Produto/serviço proprietário; termos Google | Fluxo de planejamento, aprovações, ferramenta, teste em dispositivo, Logcat e correção iterativa como referência de produto | Não copiar código interno, modelo Gemini, prompts privados, marca/ícones ou serviços proprietários. A página de Agent Mode documenta o comportamento público |
| OpenHands/software-agent-sdk | MIT | SDK/agente, sessões, eventos, workspaces e ferramentas podem ser integrados seletivamente | Preservar copyright e MIT; auditar dependências e alterações |
| Aider | Apache 2.0 | Componentes selecionados de mapa de repositório, contexto e diff | Preservar licença/NOTICE, indicar arquivos modificados e auditar dependências |
| SWE-agent | MIT | Ferramentas ou componentes selecionados de ciclo agente-ambiente e avaliação | Preservar aviso e licença; auditar dependências |
| mini-SWE-agent | MIT | Implementação compacta de ciclo agente-ambiente, se tecnicamente útil | Preservar aviso e licença; auditar dependências |
| OpenCode | MIT | Componentes selecionados de permissões, sessões, skills e contexto | Preservar aviso e licença; não reutilizar identidade visual |
| nanochat | MIT | Código de treino/avaliação pode ser integrado se ajudar em um pipeline separado | Preservar licença; dataset e pesos precisam de verificação independente |
| OLMo-core | Apache 2.0 | Componentes de pipeline de treino, checkpoints ou otimização podem ser integrados se necessários | Preservar Apache 2.0/NOTICE e auditar dependências |
| LiteRT-LM | Apache 2.0 | Runtime Android/JVM que pode ser avaliado com componentes neurais compatíveis | Preservar Apache 2.0/NOTICE e testar compatibilidade/consumo no Poco F6 |
| ExecuTorch | BSD 3-Clause (repo principal) | Runtime/exportação de modelos próprios ou licenciados | Preservar condições e disclaimer; verificar API Android e backend |
| llama.cpp | MIT (repo principal) | Runtime ARM64/GGUF candidato para modelos com arquitetura compatível | Preservar licença; auditar backend, kernels e dependências |
| MLC-LLM | Apache 2.0 no projeto principal | Runtime/compilação de pesos abertos para GPU móvel, se viável | Preservar Apache 2.0/NOTICE e auditar submódulos/dependências |

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

## 3. Regras para integrar código e bibliotecas

Antes de integrar um componente:
1. confirmar projeto, repositório, versão e commit exatos;
2. abrir LICENSE, NOTICE e cabeçalhos do arquivo;
3. conferir exceções e licenças de dependências transitivas;
4. verificar compatibilidade com o uso previsto, incluindo redistribuição no APK pessoal;
5. preservar os avisos e atribuições exigidos;
6. registrar caminho, versão, hash, licença, alterações e motivo técnico;
7. testar compatibilidade, segurança e regressões.

É permitido usar código aberto de terceiros; não é permitido apagar autoria, copiar sem licença, esconder origem ou apresentar um sistema copiado como se fosse desenvolvido integralmente por Kellvyn. Não copiar código ou pesos proprietários que não tenham sido disponibilizados para esse uso.

## 4. Regra para modelos e pesos

Modelos de pesos abertos podem ser fundações neurais de Kellvyn. Para cada um, registrar:
- nome, autores, versão/revisão e URL oficial;
- licença dos pesos e termos de uso;
- hashes dos pesos, tokenizer e arquivos auxiliares;
- formato, quantização, runtime e conversões;
- model card, idioma, tarefas e limitações;
- requisitos de RAM, armazenamento, aceleração e dispositivo;
- condições para uso privado, adaptação e redistribuição;
- adapters/fine-tuning produzidos, método, dados e hashes.

Pesos derivados continuam identificados como derivados do modelo-base. Não afirmar que foram criados integralmente do zero pela Kellvyn. A licença do código do runtime não substitui a licença dos pesos.

### Estado dos modelos neurais

Nenhum modelo-base externo foi selecionado, priorizado, baixado ou integrado como cérebro da Kellvyn nesta fase. Nomes de modelos encontrados durante pesquisa não constituem escolha de arquitetura nem autorização de uso.

Quando uma opção específica for avaliada, ela terá uma ficha individual com:
- repositório e autores oficiais;
- nome, versão/revisão e hash de cada artefato;
- licença e termos dos pesos, tokenizer e arquivos auxiliares;
- licença do runtime e de cada dependência;
- model card, tarefas, idiomas, limites e restrições;
- origem, licença e consentimento dos datasets;
- método e configuração de qualquer adaptação;
- memória/armazenamento medidos no aparelho-alvo;
- compatibilidade e benchmark reproduzível no Poco F6.

Não se deve inferir que todos os elementos de uma solução tenham a mesma licença. Código, pesos, dados, documentação, marca e serviços são categorias independentes e precisam ser auditados separadamente.

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
