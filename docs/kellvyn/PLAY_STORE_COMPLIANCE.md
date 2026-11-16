# Kellvyn — gate de distribuição Google Play

**Objetivo:** evitar que uma boa engenharia seja bloqueada por direitos, licenças, dados ou políticas de Android.

> Este documento é uma lista de verificação técnica, não aconselhamento jurídico nem garantia de aprovação da loja. Políticas mudam; reler a página oficial imediatamente antes da publicação.

## 1. Propriedade intelectual

O Google Play exige que conteúdo do aplicativo e da página de listagem seja próprio ou tenha as licenças/permissões necessárias. A política abrange direitos autorais, marcas, patentes e outros direitos.

Obrigatório antes do release:
- registrar nome, ícone, logos, fontes, sons, animações e screenshots;
- usar somente conteúdo próprio ou licenciado;
- manter licenças e permissões por escrito;
- não imitar a identidade visual de Android Studio, OpenHands, Aider, OpenCode ou outro produto;
- não copiar código sem licença e avisos;
- atribuir corretamente bibliotecas e modelos;
- garantir que não existam recursos proprietários no APK sem autorização.

## 2. Licenças de software

- manter SBOM/inventário de bibliotecas;
- manter cópias de MIT, Apache, BSD e NOTICE;
- registrar versões/commits exatos;
- inspecionar licenças de submodules e dependências transitivas;
- marcar arquivos modificados quando a licença exigir;
- não presumir que um repositório é uniformemente licenciado.

## 3. Modelos e datasets

A licença de um runtime não cobre os pesos do modelo. A licença dos pesos não cobre automaticamente dados de treino. Guardar:
- licença/termos dos pesos;
- restrições comerciais;
- regras de redistribuição e derivados;
- model card e proveniência;
- hashes e revisão;
- licença dos tokenizers;
- licença de datasets;
- relatórios de remoção de dados privados e segredos;
- avisos ao usuário quando aplicável.

## 4. Privacidade e Data Safety

O Google Play exige política de privacidade e Data Safety precisos e atualizados, incluindo práticas de SDKs de terceiros. O app deve divulgar:
- quais dados acessa;
- se coleta ou compartilha;
- quais terceiros os recebem;
- onde os dados são armazenados;
- segurança, retenção e eliminação;
- uso de microfone, câmera, arquivos, tela e outros dados sensíveis;
- comportamento quando Web ou bots são ligados.

O design local/offline reduz transmissão, mas não elimina a necessidade de disclosures corretos quando existirem SDKs, Web, bots ou processamento externo.

## 5. AccessibilityService — bloqueio importante para automação autônoma

A política atual do Google Play declara que AccessibilityService não foi projetado para apps que autonomamente iniciam, planejam e executam ações ou decisões. Essa proibição não deve ser contornada classificando a Kellvyn como ferramenta de acessibilidade, a menos que a funcionalidade principal realmente se destine a apoiar diretamente pessoas com deficiência e cumpra os critérios.

Impacto no escopo:
- não usar AccessibilityService como motor genérico para a IA controlar qualquer app, jogo ou interface de forma autônoma;
- não usar a flag `isAccessibilityTool` de forma indevida;
- avaliar APIs Android de escopo mais estreito;
- automações determinísticas definidas pelo usuário precisam ser limitadas ao que a política autoriza;
- recursos de leitura de tela precisam de disclosure e consentimento;
- qualquer recurso de controle de jogos deve receber análise própria de políticas, termos do jogo e mecanismos Android antes de ser anunciado ou distribuído.

Este é um requisito de arquitetura, não um detalhe a ser corrigido no fim.

## 6. Bots e rede

- não esconder uso de rede;
- não expor tokens;
- guardar credenciais cifradas;
- permitir desativação e remoção;
- registrar o serviço real que recebe/transmite dados;
- informar claramente quando um bot não está conectado;
- avaliar termos das plataformas e APIs oficiais;
- não prometer manter processos que o Android não garante em segundo plano.

## 7. Release checklist

- [ ] licença e versão de todas as dependências confirmadas;
- [ ] diretório THIRD_PARTY_NOTICES incluído;
- [ ] modelo e pesos auditados;
- [ ] datasets auditados;
- [ ] assets e marca auditados;
- [ ] política de privacidade publicada e acessível;
- [ ] Data Safety preenchido conforme o comportamento real;
- [ ] disclosure e consentimento de permissões;
- [ ] uso de AccessibilityService dentro da política;
- [ ] teste offline e online;
- [ ] teste de segurança e vazamento de credenciais;
- [ ] pacote/APK/AAB assinado e verificado;
- [ ] descrição e screenshots não prometem funcionalidades não implementadas.

## Fontes oficiais

- Política de propriedade intelectual: https://support.google.com/googleplay/android-developer/answer/9888072
- Dados do usuário e Data Safety: https://support.google.com/googleplay/android-developer/answer/10144311
- Uso da API AccessibilityService: https://support.google.com/googleplay/android-developer/answer/10964491
