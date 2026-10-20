# Necromante

IA separada do Kellvyn IA, com personalidade de mascote e função de guardião. Mais tarde entra na IA original como uma opção.

**Esta pasta é nova e não altera nenhum arquivo do layout** (KellvynApp.tsx, DigitalRain.tsx, ColorStudio.tsx, store.ts, styles.css, rotas).

## O que já existe (lógica pura, roda no aparelho)

| Arquivo | Função |
| --- | --- |
| `persona.ts` | Prompt do Necromante, regras de honestidade, resposta sobre o criador, comandos `/gerar apk`, `/criar bot`, `/automatizar`, `/script`, `/painel`, `/ajuda` |
| `linkScanner.ts` | `scanLink(url)`: imitação de marca, punycode, IP, encurtadores, TLDs de golpe, download de APK/EXE, iscas |
| `fileScanner.ts` | `scanFile(file)`: nome enganoso, tipo real x extensão, EICAR, hashes conhecidos, PDF (JS/ações automáticas/anexos/ofuscação), ZIP/Office (macros, executáveis, path traversal, zip bomb), APK (assinatura, permissões, combos de trojan bancário, payload escondido, marcadores no dex) |
| `messageGuard.ts` | `analyzeMessage(text)` e `analyzeConversation(msgs)`: ameaça, perseguição, controle, golpe, extorsão, pressão por fotos, com orientação (190/180) |

## Limites honestos

- É análise **estática**: nada é executado. Não substitui antivírus nativo e nunca afirma "100% seguro".
- Links: sem consulta a listas de reputação online ainda.
- APK: lê manifesto e `classes.dex` por padrões; não decompila nem emula.
- Ler WhatsApp automaticamente **não dá pelo navegador**: precisa de um serviço nativo Android (acesso a notificações) repassando o texto para `analyzeMessage`. Só com permissão explícita do usuário.
- Varredura "profunda" de todo o aparelho também depende da camada nativa.

## Próximos passos

1. Janela flutuante: chat roxo pequeno, animado, que cresce pouco e tem botão de rolar para baixo.
2. Ligar o cérebro (Gemma local) ao `NECROMANTE_PROMPT`, respostas curtas e em streaming para não travar.
3. Opção "Necromante" dentro da IA original (exige um gancho mínimo em `KellvynApp.tsx`, **só com autorização**).
4. Serviço nativo Android para notificações e varredura de apps instalados.
