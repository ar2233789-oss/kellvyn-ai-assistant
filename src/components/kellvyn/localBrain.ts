import type { Attachment, Msg } from "./store";
import { creatorAnswer, unavailableBrainMessage } from "./persona";

/**
 * Compatibility adapter. General inference and attachment extraction are not
 * connected in the web build. A small project-identity response is deterministic
 * and must not be mistaken for general model inference.
 */
export function textFromAttachment(_attachment: Attachment): string {
  return "";
}

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
