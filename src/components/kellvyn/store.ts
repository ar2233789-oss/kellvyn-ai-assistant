export type Attachment = { name: string; type: string; dataUrl: string };
export type Msg = { id: string; role: "user" | "assistant"; text: string; attachment?: Attachment; error?: boolean };
export type Conversation = { id: string; title: string; updatedAt: number; messages: Msg[] };
export type Settings = { color: string; web: boolean; rain: boolean; strongProtection: boolean };
export type Memory = { id: string; text: string };

export const COLORS: [string, string][] = [
  ["Esmeralda", "#38E6BC"], ["Azul", "#5CB8FF"], ["Violeta", "#C59BFF"], ["Vermelho", "#FF8A8A"],
  ["Dourado", "#FFD166"], ["Rosa", "#FF79C6"], ["Lima", "#B5E853"], ["Prata", "#E6E6E6"],
  ["Laranja", "#FF7A45"], ["Índigo", "#7C83FF"], ["Ciano", "#38D6E8"], ["Branco", "#FFFFFF"],
];

export const DEFAULT_SETTINGS: Settings = { color: "#38E6BC", web: false, rain: true, strongProtection: true };

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
