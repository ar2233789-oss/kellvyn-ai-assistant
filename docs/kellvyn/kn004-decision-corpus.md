# Corpus local de decisões KN-004

Este arquivo descreve o formato aceito pelo seletor local do aplicativo. O corpus não é baixado pelo app; o proprietário escolhe um arquivo `.jsonl` no menu `+` e o KN faz a auditoria, o treino e o benchmark no próprio aparelho.

## Contrato de cada linha

Cada linha precisa conter exatamente sete campos, todos strings:

```json
{
  "id": "exemplo-0001",
  "prompt": "Pesquise o registro local KN.",
  "completion": "KELLVYN/1\nCALL_TOOL\nNAME\t6C6F63616C5F6C6F6F6B7570\nARG\t7175657279\t4B4E\nEND",
  "permission": "OWNER_CREATED",
  "licenseId": "owner-created",
  "sourceReference": "owner://kn004/exemplo-0001",
  "rightsEvidence": "Exemplo original criado pelo proprietário para treino local."
}
```

Não inclua um campo `split`: o leitor calcula TRAIN, VALIDATION e TEST por uma regra determinística baseada no prompt normalizado e na semente do treino. Prompts repetidos são vinculados à mesma partição e exemplos de conteúdo duplicado são recusados.

Valores permitidos para `permission`:

- `OWNER_CREATED`
- `OWNER_AUTHORIZED`
- `PUBLIC_DOMAIN`
- `PERMISSIVE_LICENSE`
- `EXPLICIT_LICENSE`

Para `OWNER_CREATED`, `licenseId` deve ser `owner-created` ou `proprietary-owner`. Para as demais permissões, informe a licença e a evidência real de autorização. Uma URL sozinha não prova direito de uso.

## Formatos de decisão KELLVYN/1

A conclusão deve ser uma única decisão estruturada, sem Markdown ou explicação fora do protocolo. Todo texto livre dentro dos campos é codificado em hexadecimal UTF-8.

**Resposta final**

```text
KELLVYN/1
FINAL
TEXT	4F6CC3A12E
END
```

O texto hexadecimal acima representa `Olá.`.

**Consulta de ferramenta**

```text
KELLVYN/1
CALL_TOOL
NAME	6C6F63616C5F6C6F6F6B7570
ARG	7175657279	4B4E
END
```

Isso solicita `local_lookup(query="KN")`. O nome precisa existir no catálogo e os argumentos precisam passar pelo esquema real da ferramenta. A ferramenta também precisa estar entre as quatro opções apresentadas ao KN para aquela decisão.

**Plano estruturado mínimo**

```text
KELLVYN/1
PLAN
ID	7461736B5F31
GOAL	436F6E73756C746172204B4E20312E
STEP	726561645F31	436F6E73756C74617220726567697374726F	4C657220726567697374726F206C6F63616C20312E	READ_ONLY	false	-	V1
TOOL	726561645F31	6C6F63616C5F6C6F6F6B7570
CRITERION	726561645F31	526567697374726F203120666F69207265746F726E61646F2E
END
```

O exemplo contém uma etapa de leitura chamada `read_1`, que utiliza `local_lookup`. Riscos de escrita exigem aprovação do proprietário e plano de recuperação. As ferramentas precisam estar visíveis na seleção real do KN; chamadas a ferramentas escondidas são recusadas antes da execução.

Não use conclusões do tipo transferência de decisão: elas não são aceitas pelo treino e qualquer saída desse tipo reprova o benchmark.

## Quantidade e cobertura mínimas

A configuração atual exige, após a seleção determinística:

- pelo menos 100 exemplos TRAIN;
- pelo menos 20 exemplos VALIDATION;
- pelo menos 20 exemplos TEST;
- ao menos 5 exemplos de cada classe (FINAL, CALL_TOOL e PLAN) em TRAIN;
- ao menos 1 exemplo de cada classe em VALIDATION e TEST.

O leitor examina o arquivo inteiro em streaming, mas retém uma amostra limitada a 1.000 exemplos e ao orçamento de bytes configurado. Cada registro tem limite individual de 65.536 bytes. O arquivo-fonte pode ter no máximo 512 MiB. Segredos, chaves privadas, tokens, e-mails, telefones brasileiros e CPFs válidos são recusados pelo filtro heurístico.

## O que acontece depois da auditoria

1. O app calcula SHA-256 e proveniência, verifica os campos e seleciona os exemplos.
2. Reabre o mesmo arquivo e confere se hash, parâmetros e resultado da auditoria continuam idênticos.
3. Formata cada prompt com as mesmas regras, procedimentos internos e seleção de ferramentas usadas na inferência ao vivo.
4. Recusa alvos inválidos, planos com ferramentas inexistentes e exemplos que dependam de ferramentas fora da seleção real.
5. Treina apenas com TRAIN; VALIDATION e TEST não atualizam pesos.
6. Instala o checkpoint apenas se o gate numérico passar.
7. Executa inferência real em todos os exemplos TEST. A conversa só é liberada com pelo menos 90% de correspondência exata geral, 80% em cada classe e nenhuma tentativa de transferir uma decisão.

O perfil padrão usa contexto 1024, embedding 128, quatro cabeças, FFN 512, profundidade 2, até 768 merges BPE e três passagens supervisionadas, sujeito ao limite de 100.000 passos. A admissão considera pesos, gradientes, estado do otimizador, atenção, ativações, buffers do BPE e exemplos codificados, com teto de 256 MiB; no perfil máximo e na reserva conservadora de 1,6 MB TRAIN/2 MB total, a estimativa estática fica em aproximadamente 190,5 MiB. O pipeline aceita até 2 MB de exemplos já formatados. O número efetivo de merges é reduzido automaticamente para respeitar o orçamento de processamento e memória.

**Importante:** passar na auditoria só significa que os dados podem ser treinados. Não significa que o modelo aprendeu. A liberação depende do benchmark real do checkpoint produzido. A estimativa de memória ainda precisa ser medida no Poco F6 e não substitui a validação de compilação, execução e desempenho no aparelho.
