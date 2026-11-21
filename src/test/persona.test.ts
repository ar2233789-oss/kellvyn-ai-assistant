import { describe, expect, it } from "vitest";
import { buildKellvynSystemInstruction, creatorAnswer, hasConversationContext, KELLVYN_PERSONA } from "../components/kellvyn/persona";

describe("Kellvyn persona policy", () => {
  it("keeps the project creator identity in the system instruction", () => {
    const instruction = buildKellvynSystemInstruction("Base");
    expect(instruction).toContain("Base");
    expect(instruction).toContain("Kellvyn é apresentado como seu criador");
    expect(instruction).toContain("Kellvyn IA");
  });

  it("answers direct creator questions in Portuguese without inventing biography", () => {
    const answer = creatorAnswer("Quem criou a Kellvyn?");
    expect(answer).toContain("Kellvyn é apresentado como o criador");
    expect(answer).toContain("Não invento detalhes");
    expect(creatorAnswer("Qual é a capital do Brasil?")).toBeNull();
  });

  it("sets an intense but non-glorifying tone", () => {
    expect(KELLVYN_PERSONA).toContain("intensa, profunda, sóbria");
    expect(KELLVYN_PERSONA).toContain("nunca glorifique depressão");
  });

  it("detects whether actual conversation context exists", () => {
    expect(hasConversationContext([{ id: "1", role: "user", text: "  " }])).toBe(false);
    expect(hasConversationContext([{ id: "1", role: "user", text: "Olá" }])).toBe(true);
  });
});
