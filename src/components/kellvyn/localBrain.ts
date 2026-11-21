import type { Attachment, Msg } from "./store";
import { creatorAnswer, unavailableBrainMessage } from "./persona";

const MAX_ATTACHMENT_CHARS = 120_000;
const TEXT_EXTENSIONS = new Set([
  "txt", "md", "json", "csv", "xml", "html", "css", "java", "kt", "ts", "tsx", "js", "jsx", "py", "log", "yaml", "yml", "toml",
]);

/** Extract supported text attachments locally; unsupported binary files are never misrepresented as read. */
export function textFromAttachment(attachment: Attachment): string {
  const extension = attachment.name.split(".").pop()?.toLowerCase() ?? "";
  if (!attachment.type.startsWith("text/") && !TEXT_EXTENSIONS.has(extension) && attachment.type !== "application/json") {
    throw new Error("Esta versão web só consegue ler anexos de texto e código. Imagens e PDFs precisam do analisador Android compatível.");
  }
  const encoded = attachment.dataUrl.split(",", 2)[1];
  if (!encoded) throw new Error("Não consegui ler o arquivo anexado.");

  let content: string;
  try {
    const binary = atob(encoded);
    const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
    content = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  } catch {
    throw new Error("O arquivo anexado não contém texto UTF-8 legível.");
  }
  if (!content.trim()) throw new Error("O arquivo anexado está vazio ou não contém texto legível.");
  return content.slice(0, MAX_ATTACHMENT_CHARS);
}

/** General model inference is not connected in this build; never substitute fabricated replies. */
export async function askLocalBrain(
  _conversationId: string,
  prompt: string,
  _history: Msg[],
  onPartial?: (partial: string) => void,
): Promise<string> {
  const identityReply = creatorAnswer(prompt);
  if (identityReply) {
    onPartial?.(identityReply);
    return identityReply;
  }
  throw new Error(unavailableBrainMessage());
}
