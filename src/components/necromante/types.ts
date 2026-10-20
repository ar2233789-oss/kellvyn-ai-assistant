/**
 * Tipos e pontuação compartilhados pelos módulos de proteção do Necromante.
 * Nada aqui importa interface: é lógica pura, pode rodar em qualquer lugar.
 */

export type Verdict = "seguro" | "suspeito" | "perigoso";

export interface Finding {
  id: string;
  weight: number;
  reason: string;
}

export interface ScanResult {
  verdict: Verdict;
  /** 0 a 100. Quanto maior, mais perigoso. */
  score: number;
  findings: Finding[];
  summary: string;
  /** Limitações da análise que a pessoa precisa saber. */
  notes: string[];
}

export function verdictFromScore(score: number): Verdict {
  if (score >= 60) return "perigoso";
  if (score >= 25) return "suspeito";
  return "seguro";
}

const SUMMARIES: Record<Verdict, string> = {
  perigoso: "Parece perigoso. Não abra, não instale e não informe nenhum dado.",
  suspeito: "Tem sinais de golpe ou de comportamento arriscado. Só siga em frente se tiver certeza da origem.",
  seguro: "Não encontrei sinais de risco na análise local. Isso reduz a dúvida, mas não garante que seja seguro.",
};

export function buildResult(findings: Finding[], notes: string[] = []): ScanResult {
  const unique = new Map<string, Finding>();
  for (const f of findings) if (!unique.has(f.id)) unique.set(f.id, f);
  const list = Array.from(unique.values()).sort((a, b) => b.weight - a.weight);
  const score = Math.min(100, list.reduce((sum, f) => sum + f.weight, 0));
  const verdict = verdictFromScore(score);
  return { verdict, score, findings: list, summary: SUMMARIES[verdict], notes };
}
