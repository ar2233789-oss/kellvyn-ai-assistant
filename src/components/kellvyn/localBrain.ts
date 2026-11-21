import type { Attachment, Msg } from "./store";

/**
 * Compatibility adapter only. The previous inference implementation was
 * removed; this module contains no model, weights, tokenizer, provider, or
 * network inference. It preserves the existing interface and UI build.
 */
export function textFromAttachment(_attachment: Attachment): string {
  return "";
}

export async function askLocalBrain(
  _conversationId: string,
  _prompt: string,
  _history: Msg[],
  _onPartial?: (partial: string) => void,
): Promise<string> {
  throw new Error("O mecanismo neural anterior foi removido. Nenhum modelo substituto está conectado.");
}
