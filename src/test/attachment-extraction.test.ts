import { describe, expect, it } from "vitest";
import { textFromAttachment } from "../components/kellvyn/localBrain";

const attachment = (name: string, type: string, content: string) => ({
  name,
  type,
  dataUrl: `data:${type};base64,${btoa(unescape(encodeURIComponent(content)))}`,
});

describe("web text attachment extraction", () => {
  it("decodes UTF-8 text locally", () => {
    expect(textFromAttachment(attachment("nota.txt", "text/plain", "Olá, Kellvyn — ação."))).toBe("Olá, Kellvyn — ação.");
  });

  it("rejects image and PDF attachments instead of claiming to read them", () => {
    expect(() => textFromAttachment(attachment("foto.png", "image/png", "not really an image"))).toThrow("Imagens e PDFs");
    expect(() => textFromAttachment(attachment("relatorio.pdf", "application/pdf", "conteúdo"))).toThrow("Imagens e PDFs");
  });

  it("rejects malformed or empty data", () => {
    expect(() => textFromAttachment({ name: "nota.txt", type: "text/plain", dataUrl: "broken" })).toThrow("Não consegui ler");
    expect(() => textFromAttachment(attachment("vazio.txt", "text/plain", "   "))).toThrow("vazio");
  });
});
