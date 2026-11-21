import type { Attachment, Msg } from "./store";

/** Inert compatibility adapter. No model, tokenizer, weights, provider or inference. */
export function textFromAttachment(_attachment: Attachment): string { return ""; }
export async function askLocalBrain(_id: string, _prompt: string, _history: Msg[], _onPartial?: (partial: string) => void): Promise<string> {
  throw new Error("O mecanismo neural anterior foi removido. Nenhum modelo substituto está conectado.");
}
