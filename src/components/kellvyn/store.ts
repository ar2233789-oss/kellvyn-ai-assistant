export type Attachment = { name: string; type: string; dataUrl: string };
export type Msg = { id: string; role: "user" | "assistant"; text: string; attachment?: Attachment | undefined; error?: boolean };
export type Conversation = { id: string; title: string; updatedAt: number; messages: Msg[] };
export type Effect = "chuva" | "neve" | "folhas" | "raios" | "nenhum";
export type Settings = { color: string; bg: string; effect: Effect; web: boolean; rain: boolean; strongProtection: boolean };

export const EFFECTS: [Effect, string][] = [["chuva", "Chuva"], ["neve", "Neve"], ["folhas", "Folhas"], ["raios", "Raios"], ["nenhum", "Nenhum"]];

/** [name, accent, background] */
export const PALETTES: [string, string, string][] = [
  ["Esmeralda", "#38E6BC", "#0B1413"], ["Preto e Dourado", "#FFD166", "#0A0907"],
  ["Preto e Vermelho", "#FF4D4D", "#0D0707"], ["Azul Noturno", "#5CB8FF", "#070D18"],
  ["Roxo Neon", "#C59BFF", "#0E0A16"], ["Rosa Noite", "#FF79C6", "#140A11"],
  ["Prata Grafite", "#E6E6E6", "#111214"], ["Laranja Brasa", "#FF7A45", "#120A06"],
  ["Lima Escuro", "#B5E853", "#0A0F06"], ["Branco e Preto", "#FFFFFF", "#000000"],
];
export type Memory = { id: string; text: string };

export const COLORS: [string, string][] = [
  ["Esmeralda", "#38E6BC"], ["Azul", "#5CB8FF"], ["Violeta", "#C59BFF"], ["Vermelho", "#FF8A8A"],
  ["Dourado", "#FFD166"], ["Rosa", "#FF79C6"], ["Lima", "#B5E853"], ["Prata", "#E6E6E6"],
  ["Laranja", "#FF7A45"], ["Índigo", "#7C83FF"], ["Ciano", "#38D6E8"], ["Branco", "#FFFFFF"],
];

export const DEFAULT_SETTINGS: Settings = { color: "#38E6BC", bg: "#0B1413", effect: "chuva", web: false, rain: true, strongProtection: true };

export function normalizeHex(input: string): string | null {
  const v = input.trim().replace(/^#/, "");
  return /^[0-9a-fA-F]{6}$/.test(v) ? `#${v.toUpperCase()}` : null;
}

export const uid = () => Math.random().toString(36).slice(2, 10) + Date.now().toString(36);

function read<T>(key: string, fallback: T): T {
  try { const v = localStorage.getItem(key); return v ? (JSON.parse(v) as T) : fallback; } catch { return fallback; }
}
function write(key: string, v: unknown) { try { localStorage.setItem(key, JSON.stringify(v)); } catch { /* quota */ } }

export const storage = {
  settings: () => ({ ...DEFAULT_SETTINGS, ...read<Partial<Settings>>("kv.settings", {}) }),
  saveSettings: (s: Settings) => write("kv.settings", s),
  conversations: () => read<Conversation[]>("kv.conversations", []),
  saveConversations: (c: Conversation[]) => write("kv.conversations", c),
  activeId: () => read<string | null>("kv.active", null),
  saveActiveId: (id: string) => write("kv.active", id),
  memories: () => read<Memory[]>("kv.memories", []),
  saveMemories: (m: Memory[]) => write("kv.memories", m),
};
