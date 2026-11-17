# Arquitetura preliminar — Repositório de Recursos e Studio de Criação Kellvyn

**Data:** 17/11/2026  
**Fase:** levantamento de requisitos e hipótese arquitetural.  
**Estado:** NÃO IMPLEMENTADO; ferramentas, formatos e tecnologias ainda serão pesquisados e comparados. Este documento não escolhe fornecedor.

## 1. Objetivo

O Studio de Criação Kellvyn deve ser uma infraestrutura real para criar, executar, inspecionar, testar, corrigir, empacotar e recuperar projetos complexos. Não deve ser apenas uma tela de laboratório ou um emulador.

O repositório de recursos deve fornecer aos especialistas, por meio de uma camada de controle, ferramentas, SDKs, dependências, templates, componentes visuais, documentação, conjuntos de testes e artefatos versionados. Os especialistas não devem baixar e executar qualquer coisa livremente.

## 2. Princípios de arquitetura

- **Offline-first:** recursos essenciais ficam disponíveis localmente; sincronização externa é opcional e controlada.
- **Local-first para dados privados:** conversas, memória, chaves e projetos privados não são enviados por padrão.
- **Separação de responsabilidades:** código-fonte, artefatos binários, datasets, modelos, credenciais e logs não devem ser armazenados todos no mesmo lugar.
- **Imutabilidade por versão:** recursos publicados são identificados por versão e hash; atualização cria uma nova versão em vez de sobrescrever silenciosamente.
- **Confiança explícita:** origem, licença, integridade, compatibilidade e estado de segurança devem ser conhecidos antes de liberar um recurso.
- **Menor privilégio:** agentes pedem recursos por uma API interna; um broker decide se a tarefa e o papel têm autorização.
- **Reprodutibilidade:** cada build/teste pode ser associado ao código, ferramentas, dependências e configuração exatos.
- **Recuperação:** toda alteração relevante possui snapshot e rollback verificável.
- **Escalabilidade gradual:** a arquitetura deve caber inicialmente em um telefone Android e permitir ferramentas mais pesadas opcionais, sem prometer que o Poco F6 executará qualquer tarefa de desktop.

## 3. Planos lógicos a investigar

### 3.1 Plano de código e configuração

Responsável por:
- código-fonte do cérebro, agentes, runtime e ferramentas;
- manifests e schemas;
- documentação, skills, testes e workflows;
- histórico de alterações, branches, revisões e tags.

Requisitos candidatos:
- repositório Git privado;
- branch de trabalho isolada e branch estável protegida;
- revisão de diff;
- commits assinados ou artefatos assinados por uma chave independente;
- regras para impedir segredos, dados pessoais, checkpoints privados e grandes binários no histórico Git.

GitHub pode ser um espelho remoto conveniente, mas não deve ser o único ponto de confiança nem o cofre de segredos. Se a conta for comprometida, a chave de assinatura independente e a política local de confiança precisam impedir que commits não autorizados sejam aceitos como pacotes oficiais.

### 3.2 Catálogo de recursos

Cada recurso deve ter um manifesto verificável:
- ID estável e nome humano;
- versão e canal (experimental, estável, retirada);
- origem e método de obtenção;
- licença, atribuição e restrições de redistribuição;
- plataforma, arquitetura e versão mínima;
- dependências e compatibilidade;
- hash criptográfico do conteúdo;
- assinatura e identidade do publicador;
- SBOM ou inventário de componentes, quando aplicável;
- riscos conhecidos, data da última verificação e estado de quarentena;
- tamanho, espaço temporário e requisitos de memória;
- instruções de instalação e remoção;
- compatibilidade com projetos e versões do Kellvyn.

Não basta registrar URL e nome do pacote. Uma URL pode mudar ou entregar conteúdo diferente; a integridade precisa ser conferida por hash/assinatura.

### 3.3 Armazenamento de artefatos

Separar:
- cache local de ferramentas e dependências;
- SDKs e toolchains;
- bibliotecas e componentes;
- templates e exemplos;
- APKs e pacotes de teste;
- modelos e checkpoints próprios;
- datasets e corpora com proveniência;
- screenshots, traces, logs e relatórios.

Candidato a estudar: armazenamento endereçado por conteúdo, em que o objeto é identificado pelo hash e versões compartilham blocos iguais. Git continua adequado ao código e manifests; binários grandes e checkpoints devem ter estratégia própria.

### 3.4 Cofre de credenciais

Credenciais de bots, tokens de integração, chaves de assinatura e segredos de teste:
- nunca em commits, manifests públicos, logs ou prompts;
- criptografados com chaves controladas pelo sistema operacional/dispositivo;
- acessados somente por uma ferramenta de cofre com escopo e duração limitados;
- mascarados em logs;
- revogáveis e auditáveis.

O agente não recebe o segredo bruto quando uma ferramenta pode executar a operação em seu nome. Uma tarefa de leitura não deve ter acesso a credenciais de escrita.

### 3.5 Workspaces isolados

Cada tarefa complexa recebe:
- cópia ou branch isolada do projeto;
- diretórios temporários separados;
- orçamento de CPU, RAM, armazenamento e tempo;
- conjunto mínimo de ferramentas;
- rede desativada por padrão ou limitada a uma allowlist explícita;
- ausência de credenciais por padrão;
- diff, logs, evidências e checkpoint associados ao ID da tarefa.

Agentes concorrentes não editam o mesmo diretório simultaneamente. Integração de alterações ocorre por diff revisado e testes após merge.

### 3.6 Executor e ambientes de teste

O Studio deve poder organizar ambientes separados:
- execução e teste web no navegador;
- Android build e verificação do APK;
- emulador Android quando a infraestrutura suportar;
- teste em dispositivo físico conectado, quando autorizado;
- testes de API e bots em ambientes controlados;
- inspeção visual, acessibilidade, logs, rede e desempenho;
- comparação de screenshots e replay de fluxos.

Cada ambiente informa claramente o que consegue provar. Unit test, build, instalação, navegação automatizada, teste físico e teste de desempenho são provas diferentes.

### 3.7 Gerenciador de instalação, patch e rollback

Ciclo obrigatório:
1. descobrir recurso ou atualização;
2. obter metadados e origem;
3. validar licença e compatibilidade;
4. verificar hash e assinatura;
5. baixar para quarentena;
6. analisar dependências e conteúdo;
7. testar em sandbox;
8. executar smoke tests e critérios de aceitação;
9. promover para catálogo aprovado;
10. instalar de forma atômica;
11. fazer health check;
12. manter versão anterior e rollback.

Um patch não deve sobrescrever arquivos ativos sem verificar versão-base, precondições, assinatura e espaço para recuperação.

### 3.8 Observabilidade e auditoria

Toda execução deve produzir eventos estruturados:
- quem solicitou e qual tarefa;
- qual papel interno foi autorizado;
- recurso e versão usados;
- ferramentas invocadas e parâmetros sanitizados;
- arquivos/diffs alterados;
- tempo, CPU, RAM, armazenamento e erros;
- evidência de teste;
- estado de aprovação;
- rollback, se executado.

Logs não devem guardar tokens, chaves, dados privados desnecessários ou conteúdo completo de arquivos sensíveis.

## 4. Ciclo de vida dos recursos

Estados propostos:
- **DISCOVERED:** localizado, mas ainda não obtido;
- **DOWNLOADING:** obtenção limitada e cancelável;
- **QUARANTINED:** baixado, sem confiança concedida;
- **VERIFIED:** integridade, origem, licença e compatibilidade conferidas;
- **TESTING:** testes em sandbox;
- **APPROVED:** liberado para uso por papéis autorizados;
- **DEPRECATED:** substituição recomendada;
- **REVOKED:** comprometido, incompatível ou não autorizado.

O catálogo deve guardar a transição e o motivo. Uma atualização que falha permanece separada da versão estável.

## 5. Segurança e ameaças a considerar

| Ameaça | Proteção candidata |
|---|---|
| Conta GitHub comprometida | chave de assinatura independente, cópia local, revisão de mudanças e recuperação |
| Pacote adulterado | hash, assinatura, origem e verificação antes de instalar |
| Dependência maliciosa | SBOM, análise de licença/vulnerabilidade e sandbox |
| Prompt injection em docs/issues/sites | conteúdo externo tratado como dado, nunca política confiável |
| Agente com acesso excessivo | broker com permissões por tarefa e papel |
| Vazamento de token | cofre separado, mascaramento e escopo mínimo |
| Patch incompatível | precondições, teste, instalação atômica e rollback |
| Corrupção/perda de armazenamento | snapshots, verificações de integridade e backup controlado |
| Esgotamento de espaço/RAM | quotas, estimativa de recursos, limpeza segura e cancelamento |
| Dois agentes em conflito | workspace isolado, locking e merge revisado |

## 6. Compatibilidade com o cenário inicial

O alvo inicial é o Poco F6 com 8 GB de RAM e 512 GB de armazenamento, local/offline primeiro. A arquitetura precisa:
- carregar ferramentas sob demanda, sem instalar todos os SDKs sempre;
- manter apenas metadados e caches essenciais;
- estimar RAM/espaço antes de baixar ou executar;
- deixar operações pesadas canceláveis;
- usar builds e testes que caibam no dispositivo quando possível;
- admitir ambiente auxiliar opcional para tarefas que excedam o telefone, sem depender dele para o uso básico;
- documentar claramente o que é possível no aparelho, o que exige dispositivo externo e o que ainda não é suportado.

Não pressupor que o Android consiga executar um emulador completo, toolchain de desktop ou treinamento grande de forma eficiente. Isso será medido e pesquisado antes de escolher tecnologia.

## 7. Pesquisa técnica futura — sem decisões de fornecedor ainda

Investigar e comparar:
- formatos e registries de artefatos (OCI, registry de pacotes e cache local);
- armazenamento content-addressed e deduplicação;
- Git, Git LFS e opções para binários/checkpoints;
- assinatura de artefatos, transparência e secure update (Sigstore, TUF e equivalentes);
- inventário e licenças (SPDX, CycloneDX, SBOM);
- builds reproduzíveis e proveniência (SLSA e equivalentes);
- ambientes de sandbox e limites (containers, WASI, processos isolados e limitações Android);
- automação de navegador e Android (Playwright, UI Automator, Espresso, ADB);
- indexação e busca de projetos/recursos;
- gerenciamento de versões, patches diferenciais e rollback;
- sincronização offline-first e backup criptografado;
- execução de jobs, filas, agendamento, cancelamento e observabilidade.

Cada opção deverá ser avaliada por licença, manutenção, segurança, custo, tamanho, consumo de RAM/CPU, compatibilidade Android, operação offline, facilidade de recuperação e dependência de serviço externo.

## 8. Ordem de trabalho futura

1. concluir estudo de ferramentas e ameaças;
2. mapear requisitos e restrições de hardware;
3. selecionar protocolos e formatos interoperáveis;
4. produzir um protótipo do catálogo e do cache local;
5. validar obtenção/verificação/quarentena de um recurso benigno;
6. criar workspace descartável e executar teste simples;
7. provar checkpoint, rollback e restauração;
8. adicionar ambientes web e Android em etapas separadas;
9. ligar os especialistas por meio do broker;
10. avaliar o Studio com projetos reais e critérios de qualidade.

**Decisão de escopo:** este documento não autoriza começar o Studio agora. A prioridade imediata continua sendo o cérebro e a validação dos especialistas. A arquitetura do Studio será aprofundada em pesquisa própria depois.
