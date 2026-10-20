/**
 * Análise de links do Necromante.
 * Heurística local (sem rede): endereço disfarçado, imitação de marca, encurtadores,
 * protocolos perigosos, download de executáveis e iscas típicas de golpe.
 */
import { buildResult, type Finding, type ScanResult } from "./types";

const SHORTENERS = new Set([
  "bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "cutt.ly", "rb.gy",
  "shorturl.at", "ow.ly", "buff.ly", "tiny.cc", "encurtador.com.br",
]);

const RISKY_TLDS = new Set([
  "zip", "mov", "tk", "ml", "ga", "cf", "gq", "top", "xyz", "click", "work",
  "support", "country", "kim", "loan", "men", "buzz", "rest", "icu", "monster", "cyou",
]);

const SECOND_LEVEL = new Set(["com", "org", "net", "gov", "edu", "co"]);

const BRANDS: { token: string; domains: string[] }[] = [
  { token: "whatsapp", domains: ["whatsapp.com", "whatsapp.net", "wa.me"] },
  { token: "google", domains: ["google.com", "google.com.br", "googleusercontent.com", "googleapis.com", "gstatic.com", "goo.gl"] },
  { token: "facebook", domains: ["facebook.com", "fb.com", "fbcdn.net"] },
  { token: "instagram", domains: ["instagram.com", "cdninstagram.com"] },
  { token: "nubank", domains: ["nubank.com.br", "nu.com.br"] },
  { token: "bradesco", domains: ["bradesco.com.br", "bradesco.com"] },
  { token: "itau", domains: ["itau.com.br", "itau.com"] },
  { token: "santander", domains: ["santander.com.br", "santander.com"] },
  { token: "caixa", domains: ["caixa.gov.br"] },
  { token: "picpay", domains: ["picpay.com"] },
  { token: "mercadolivre", domains: ["mercadolivre.com.br", "mercadolivre.com", "mercadolibre.com"] },
  { token: "mercadopago", domains: ["mercadopago.com.br", "mercadopago.com"] },
  { token: "paypal", domains: ["paypal.com"] },
  { token: "microsoft", domains: ["microsoft.com", "live.com", "office.com", "microsoftonline.com"] },
  { token: "netflix", domains: ["netflix.com"] },
  { token: "discord", domains: ["discord.com", "discord.gg", "discordapp.com", "discord.media"] },
  { token: "steam", domains: ["steampowered.com", "steamcommunity.com", "steamstatic.com"] },
  { token: "roblox", domains: ["roblox.com"] },
  { token: "correios", domains: ["correios.com.br"] },
];

const BAIT = [
  "login", "signin", "verify", "verifica", "secure", "seguranca", "segurança", "update",
  "atualiz", "confirm", "senha", "password", "banco", "pix", "premio", "prêmio",
  "ganhe", "bloquead", "suspens", "recuper", "resgate", "gratis", "grátis", "oferta",
];

const EXEC_EXT = /\.(apk|exe|scr|bat|cmd|msi|jar|vbs|ps1|hta|lnk)(?:$|[?#])/i;
const REDIRECT_PARAM = /[?&](url|redirect|redir|next|goto|link|dest|continue)=https?/i;

function registrableDomain(host: string): string {
  const labels = host.split(".");
  if (labels.length <= 2) return host;
  const tld = labels[labels.length - 1];
  const sld = labels[labels.length - 2];
  if (tld.length === 2 && SECOND_LEVEL.has(sld)) return labels.slice(-3).join(".");
  return labels.slice(-2).join(".");
}

function normalizeLookalike(s: string): string {
  return s
    .replace(/rn/g, "m")
    .replace(/vv/g, "w")
    .replace(/0/g, "o")
    .replace(/1/g, "l")
    .replace(/3/g, "e")
    .replace(/5/g, "s");
}

function isOnDomain(host: string, domain: string): boolean {
  return host === domain || host.endsWith(`.${domain}`);
}

export function scanLink(input: string): ScanResult {
  const findings: Finding[] = [];
  const notes: string[] = [
    "Análise local por padrões. Não consulta listas de reputação online, então um link novo e malicioso ainda pode passar.",
  ];
  const raw = input.trim();
  if (!raw) {
    return buildResult([{ id: "vazio", weight: 0, reason: "Nenhum link informado." }], notes);
  }

  if (/^(javascript|data|vbscript|file|intent|content):/i.test(raw)) {
    findings.push({ id: "esquema-perigoso", weight: 80, reason: "Usa um tipo de endereço que executa código ou abre arquivos locais, não uma página normal." });
    return buildResult(findings, notes);
  }

  const hasScheme = /^[a-z][a-z0-9+.-]*:\/\//i.test(raw);
  let url: URL;
  try {
    url = new URL(hasScheme ? raw : `https://${raw}`);
  } catch {
    return buildResult([{ id: "invalido", weight: 30, reason: "O texto não é uma URL válida." }], notes);
  }
  if (!hasScheme) notes.push("O texto veio sem protocolo; analisei como https://.");

  const host = url.hostname.toLowerCase().replace(/\.$/, "");
  const full = `${host}${url.pathname}${url.search}`.toLowerCase();

  if (url.protocol === "http:") {
    findings.push({ id: "sem-https", weight: 15, reason: "Não usa HTTPS: o que você digitar pode ser lido no caminho." });
  } else if (url.protocol !== "https:" && url.protocol !== "ftp:") {
    findings.push({ id: "protocolo-incomum", weight: 60, reason: `Protocolo incomum (${url.protocol}).` });
  }

  if (url.username || url.password) {
    findings.push({ id: "arroba", weight: 35, reason: "Usa @ no endereço para esconder o destino real." });
  }

  if (/^\d{1,3}(\.\d{1,3}){3}$/.test(host) || host.startsWith("[")) {
    findings.push({ id: "ip", weight: 35, reason: "Aponta direto para um número de IP em vez de um site com nome." });
  } else if (/^\d+$/.test(host) || /^0x[0-9a-f]+$/i.test(host)) {
    findings.push({ id: "ip-disfarcado", weight: 45, reason: "Endereço numérico disfarçado de IP." });
  }

  if (host.split(".").some((l) => l.startsWith("xn--"))) {
    findings.push({ id: "punycode", weight: 30, reason: "Usa caracteres que imitam letras comuns (punycode) para parecer outro site." });
  }

  if (SHORTENERS.has(host)) {
    findings.push({ id: "encurtador", weight: 15, reason: "Link encurtado: não dá para ver o destino antes de abrir." });
  }

  const tld = host.split(".").pop() ?? "";
  if (RISKY_TLDS.has(tld)) {
    findings.push({ id: "tld-arriscado", weight: tld === "zip" || tld === "mov" ? 25 : 15, reason: `Terminação .${tld} muito usada em golpes.` });
  }

  const labels = host.split(".");
  if (labels.length >= 5) {
    findings.push({ id: "subdominios", weight: 15, reason: "Muitos subdomínios, técnica comum para esconder o domínio verdadeiro." });
  }
  if ((host.match(/-/g) ?? []).length >= 3) {
    findings.push({ id: "hifens", weight: 10, reason: "Muitos hífens no endereço." });
  }
  if (host.length > 40) {
    findings.push({ id: "host-longo", weight: 10, reason: "Nome do site muito longo." });
  }

  const reg = registrableDomain(host);
  let onLegitBrand = false;
  for (const brand of BRANDS) {
    const legit = brand.domains.some((d) => isOnDomain(host, d));
    if (legit) {
      onLegitBrand = true;
      continue;
    }
    const parts = host.split(/[.\-]/);
    const direct = parts.some(
      (p) => p === brand.token || (brand.token.length >= 7 && (p.startsWith(brand.token) || p.endsWith(brand.token))),
    );
    if (direct) {
      findings.push({ id: `marca-${brand.token}`, weight: 40, reason: `Usa o nome "${brand.token}", mas o domínio real é ${reg}.` });
      break;
    }
    if (parts.some((p) => normalizeLookalike(p) === brand.token)) {
      findings.push({ id: `imitacao-${brand.token}`, weight: 45, reason: `Imita "${brand.token}" trocando letras por números parecidos.` });
      break;
    }
  }

  if (!onLegitBrand) {
    const hits = BAIT.filter((k) => full.includes(k));
    if (hits.length > 0) {
      findings.push({ id: "iscas", weight: Math.min(24, hits.length * 8), reason: `Palavras típicas de golpe: ${hits.slice(0, 4).join(", ")}.` });
    }
  }

  if (EXEC_EXT.test(url.pathname + url.search)) {
    findings.push({ id: "executavel", weight: 40, reason: "O link baixa um arquivo instalável/executável (APK, EXE etc.)." });
  }
  if (REDIRECT_PARAM.test(url.search)) {
    findings.push({ id: "redirecionamento", weight: 12, reason: "Tem redirecionamento embutido para outro endereço." });
  }
  if (url.port && url.port !== "80" && url.port !== "443") {
    findings.push({ id: "porta", weight: 12, reason: `Usa porta incomum (${url.port}).` });
  }
  if ((raw.match(/%[0-9a-f]{2}/gi) ?? []).length > 10) {
    findings.push({ id: "codificado", weight: 10, reason: "Muito texto codificado no endereço (%xx)." });
  }
  if (raw.length > 200) {
    findings.push({ id: "url-longa", weight: 8, reason: "Endereço extremamente longo." });
  }

  return buildResult(findings, notes);
}

/** Extrai links de um texto (mensagem, e-mail, etc.). */
export function extractLinks(text: string): string[] {
  const found = text.match(/(?:https?:\/\/|www\.)[^\s<>"']+/gi) ?? [];
  return found.map((l) => l.replace(/[).,;!?]+$/, ""));
}
