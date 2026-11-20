export function textFromAttachment(attachment: {
  name: string;
  type: string;
  dataUrl: string;
}): string {
  const extension = attachment.name.split(".").pop()?.toLowerCase() ?? "";
  const textExtensions = new Set([
    "txt", "md", "json", "kt", "java", "ts", "tsx", "js", "jsx", "xml", "html", "css", "py",
  ]);
  if (
    !attachment.type.startsWith("text/") &&
    !textExtensions.has(extension) &&
    attachment.type !== "application/json"
  ) {
    throw new Error("Esta prévia aceita anexos de texto e código. A leitura local de imagens e PDFs pertence à versão Android nativa.");
  }

  const encoded = attachment.dataUrl.split(",")[1];
  if (!encoded) throw new Error("Não consegui ler o arquivo anexado.");
  const binary = atob(encoded);
  const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
  const content = new TextDecoder("utf-8").decode(bytes);
  if (!content.trim()) throw new Error("O arquivo anexado não contém texto legível.");
  return content.slice(0, 120_000);
}
