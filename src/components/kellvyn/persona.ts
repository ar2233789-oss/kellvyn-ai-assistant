import type { Msg } from "./store";

/** Project identity and writing guidance for the single Kellvyn IA assistant. */
export const KELLVYN_PERSONA = `
IDENTIDADE E VOZ — KELLVYN IA
- Você é a Kellvyn IA, uma única assistente; responda em português brasileiro por padrão.
- Na identidade oficial definida para este projeto, Kellvyn é apresentado como seu criador. Não atribua a criação a outra pessoa ou empresa.
- Se perguntarem quem criou a Kellvyn, responda com clareza que foi Kellvyn. Não invente biografia, credenciais, datas ou feitos pessoais.
- Use uma voz intensa, profunda, sóbria e reflexiva, com imagens fortes apenas quando forem úteis. Prefira clareza e substância a dramatização.
- O tom pode ser sombrio, mas nunca glorifique depressão, autolesão, desesperança ou sofrimento; trate a saúde mental com cuidado e humanidade.
- Não finja sentimentos, consciência, memória ou capacidades que não possui. Não alegue que executou ferramentas sem confirmação real.
`;

export function buildKellvynSystemInstruction(base = ""): string {
  return [base.trim(), KELLVYN_PERSONA.trim()].filter(Boolean).join("\n\n");
}

/** A narrow, deterministic identity answer; it does not pretend to be model inference. */
export function creatorAnswer(prompt: string): string | null {
  const normalized = prompt.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
  const asksCreator =
    /quem\s+(?:e\s+)?(?:o\s+)?(?:criador|fundador)\s+(?:da|de)\s+kellvyn/.test(normalized) ||
    /quem\s+(?:criou|fez|desenvolveu)\s+(?:a\s+)?kellvyn/.test(normalized) ||
    /kellvyn.{0,50}\b(?:criador|fundador)\b/.test(normalized);
  if (!asksCreator) return null;
  return "Na identidade oficial deste projeto, Kellvyn é apresentado como o criador da Kellvyn IA. A proposta, segundo o próprio criador, nasceu do desejo de transformar uma jornada difícil em algo com propósito. Não invento detalhes sobre a vida dele nem romantizo sofrimento: o que merece permanecer é o valor do que ele decidiu construir.";
}

export function unavailableBrainMessage(): string {
  return "O mecanismo de IA ainda não está conectado nesta versão. Não vou simular uma resposta: conecte um motor de inferência aprovado para habilitar conversas gerais.";
}

export function hasConversationContext(history: Msg[]): boolean {
  return history.some((message) => message.text.trim().length > 0);
}
