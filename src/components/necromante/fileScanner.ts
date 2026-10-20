/**
 * Varredura estática de arquivos do Necromante (roda 100% no aparelho).
 *
 * Cobre: nome enganoso, tipo real x extensão, assinatura de teste EICAR, hashes
 * conhecidos, PDFs (JavaScript, ações automáticas, anexos, ofuscação), ZIP/Office
 * (macros, executáveis dentro, path traversal, zip bomb) e APKs (assinatura,
 * permissões do manifesto, combinações típicas de trojan bancário, código externo
 * escondido, marcadores no classes.dex).
 *
 * Não executa o arquivo em momento nenhum.
 */
import { buildResult, type Finding, type ScanResult } from "./types";

export interface ApkInfo {
  permissions: string[];
  signedV1: boolean;
  signedV2OrNewer: boolean;
  dexCount: number;
  nativeLibs: number;
}

export interface FileScanResult extends ScanResult {
  file: { name: string; size: number; kind: string; sha256: string | null };
  apk?: ApkInfo;
}

const MAX_FULL_READ = 96 * 1024 * 1024;
const MAX_DEX_INFLATE = 40 * 1024 * 1024;
const MAX_MANIFEST = 4 * 1024 * 1024;

/** Hashes SHA-256 de ameaças conhecidas. Alimente com addKnownBadHashes(). */
const KNOWN_BAD_SHA256 = new Set<string>();
export function addKnownBadHashes(hashes: string[]): void {
  for (const h of hashes) KNOWN_BAD_SHA256.add(h.trim().toLowerCase());
}

// Assinatura de teste padrão da indústria (inofensiva). Serve para você testar o scanner.
const EICAR = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

const DANGEROUS_EXT = new Set([
  "exe", "scr", "bat", "cmd", "vbs", "vbe", "js", "jse", "wsf", "ps1", "msi", "jar", "lnk", "com", "pif", "hta", "cpl",
]);

const EXPECTED_KIND: Record<string, string> = {
  pdf: "pdf", png: "png", jpg: "jpeg", jpeg: "jpeg", gif: "gif", apk: "zip", jar: "zip",
  zip: "zip", docx: "zip", xlsx: "zip", pptx: "zip", dex: "dex",
};
const STRICT_EXT = new Set(["pdf", "apk", "zip", "docx", "xlsx", "pptx", "jar"]);

const PERMISSION_WEIGHTS: Record<string, number> = {
  BIND_ACCESSIBILITY_SERVICE: 30,
  BIND_DEVICE_ADMIN: 25,
  BIND_NOTIFICATION_LISTENER_SERVICE: 20,
  READ_SMS: 15,
  RECEIVE_SMS: 15,
  SEND_SMS: 15,
  REQUEST_INSTALL_PACKAGES: 15,
  SYSTEM_ALERT_WINDOW: 15,
  MANAGE_EXTERNAL_STORAGE: 10,
  REQUEST_DELETE_PACKAGES: 10,
  READ_CALL_LOG: 8,
  RECORD_AUDIO: 8,
  READ_CONTACTS: 6,
  CALL_PHONE: 6,
  WRITE_SETTINGS: 6,
  QUERY_ALL_PACKAGES: 6,
  READ_PHONE_STATE: 4,
  RECEIVE_BOOT_COMPLETED: 4,
  ACCESS_FINE_LOCATION: 4,
};

const DEX_MARKERS: { text: string; weight: number; reason: string }[] = [
  { text: "Ldalvik/system/DexClassLoader;", weight: 10, reason: "carrega código externo em tempo de execução" },
  { text: "InMemoryDexClassLoader", weight: 10, reason: "carrega código externo em tempo de execução" },
  { text: "/system/bin/su", weight: 25, reason: "procura acesso root" },
  { text: "/system/xbin/su", weight: 25, reason: "procura acesso root" },
  { text: "Landroid/telephony/SmsManager;", weight: 8, reason: "usa envio de SMS" },
  { text: "createScreenCaptureIntent", weight: 10, reason: "pode gravar a tela" },
  { text: "Landroid/app/admin/DevicePolicyManager;", weight: 10, reason: "usa administrador do dispositivo" },
];

function startsWith(b: Uint8Array, sig: number[], at = 0): boolean {
  return sig.every((v, i) => b[at + i] === v);
}

function detectKind(b: Uint8Array): string {
  if (startsWith(b, [0x25, 0x50, 0x44, 0x46])) return "pdf";
  if (startsWith(b, [0x50, 0x4b, 0x03, 0x04]) || startsWith(b, [0x50, 0x4b, 0x05, 0x06])) return "zip";
  if (startsWith(b, [0x4d, 0x5a])) return "pe";
  if (startsWith(b, [0x7f, 0x45, 0x4c, 0x46])) return "elf";
  if (startsWith(b, [0x89, 0x50, 0x4e, 0x47])) return "png";
  if (startsWith(b, [0xff, 0xd8, 0xff])) return "jpeg";
  if (startsWith(b, [0x47, 0x49, 0x46, 0x38])) return "gif";
  if (startsWith(b, [0x64, 0x65, 0x78, 0x0a])) return "dex";
  if (startsWith(b, [0x23, 0x21])) return "script";
  if (startsWith(b, [0x52, 0x61, 0x72, 0x21])) return "rar";
  if (startsWith(b, [0x37, 0x7a, 0xbc, 0xaf])) return "7z";
  if (startsWith(b, [0x66, 0x74, 0x79, 0x70], 4)) return "mp4";
  return "desconhecido";
}

async function sha256(file: File): Promise<string | null> {
  try {
    if (file.size > MAX_FULL_READ || !globalThis.crypto?.subtle) return null;
    const digest = await crypto.subtle.digest("SHA-256", await file.arrayBuffer());
    return Array.from(new Uint8Array(digest)).map((x) => x.toString(16).padStart(2, "0")).join("");
  } catch {
    return null;
  }
}

async function inflateRaw(data: Uint8Array, limit: number): Promise<Uint8Array | null> {
  if (typeof DecompressionStream === "undefined") return null;
  try {
    const stream = new Blob([data as BlobPart]).stream().pipeThrough(new DecompressionStream("deflate-raw"));
    const reader = stream.getReader();
    const chunks: Uint8Array[] = [];
    let total = 0;
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.length;
      if (total > limit) {
        await reader.cancel();
        return null;
      }
      chunks.push(value);
    }
    const out = new Uint8Array(total);
    let pos = 0;
    for (const c of chunks) {
      out.set(c, pos);
      pos += c.length;
    }
    return out;
  } catch {
    return null;
  }
}

interface ZipEntry {
  name: string;
  method: number;
  compSize: number;
  size: number;
  offset: number;
}

async function readZipEntries(file: File): Promise<{ entries: ZipEntry[]; cdOffset: number } | null> {
  if (file.size < 22) return null;
  const tailLen = Math.min(file.size, 65557);
  const tail = new Uint8Array(await file.slice(file.size - tailLen).arrayBuffer());
  const tdv = new DataView(tail.buffer, tail.byteOffset, tail.byteLength);
  let eocd = -1;
  for (let i = tail.length - 22; i >= 0; i--) {
    if (tdv.getUint32(i, true) === 0x06054b50) {
      eocd = i;
      break;
    }
  }
  if (eocd < 0) return null;
  const total = tdv.getUint16(eocd + 10, true);
  const cdSize = tdv.getUint32(eocd + 12, true);
  const cdOffset = tdv.getUint32(eocd + 16, true);
  if (cdOffset === 0xffffffff || cdOffset + cdSize > file.size) return null;

  const cd = new Uint8Array(await file.slice(cdOffset, cdOffset + cdSize).arrayBuffer());
  const dv = new DataView(cd.buffer, cd.byteOffset, cd.byteLength);
  const dec = new TextDecoder("utf-8");
  const entries: ZipEntry[] = [];
  let p = 0;
  for (let n = 0; n < Math.min(total, 20000) && p + 46 <= cd.length; n++) {
    if (dv.getUint32(p, true) !== 0x02014b50) break;
    const nl = dv.getUint16(p + 28, true);
    const el = dv.getUint16(p + 30, true);
    const cl = dv.getUint16(p + 32, true);
    entries.push({
      method: dv.getUint16(p + 10, true),
      compSize: dv.getUint32(p + 20, true),
      size: dv.getUint32(p + 24, true),
      offset: dv.getUint32(p + 42, true),
      name: dec.decode(cd.subarray(p + 46, p + 46 + nl)),
    });
    p += 46 + nl + el + cl;
  }
  return { entries, cdOffset };
}

async function readEntry(file: File, e: ZipEntry, limit: number): Promise<Uint8Array | null> {
  if (e.size > limit) return null;
  const lh = new DataView(await file.slice(e.offset, e.offset + 30).arrayBuffer());
  if (lh.byteLength < 30 || lh.getUint32(0, true) !== 0x04034b50) return null;
  const start = e.offset + 30 + lh.getUint16(26, true) + lh.getUint16(28, true);
  const data = new Uint8Array(await file.slice(start, start + e.compSize).arrayBuffer());
  if (e.method === 0) return data;
  if (e.method === 8) return inflateRaw(data, limit);
  return null;
}

function latin1(bytes: Uint8Array): string {
  return new TextDecoder("latin1").decode(bytes);
}

function manifestStrings(manifest: Uint8Array): Set<string> {
  const re = /(?:android\.permission|android\.intent\.action|android\.accessibilityservice|android\.service\.notification|android\.app\.admin)[A-Za-z0-9_.]*/g;
  const out = new Set<string>();
  const a = latin1(manifest);
  const b = new TextDecoder("utf-16le").decode(manifest.subarray(0, manifest.length - (manifest.length % 2)));
  for (const m of a.matchAll(re)) out.add(m[0]);
  for (const m of b.matchAll(re)) out.add(m[0]);
  return out;
}

async function scanPdf(file: File, findings: Finding[], notes: string[]): Promise<void> {
  const limit = 16 * 1024 * 1024;
  const text = latin1(new Uint8Array(await file.slice(0, limit).arrayBuffer()));
  if (file.size > limit) notes.push("PDF grande: analisei só os primeiros 16 MB.");
  const count = (re: RegExp) => (text.match(re) ?? []).length;

  const js = count(/\/(JavaScript|JS)\b/g);
  const auto = count(/\/(OpenAction|AA)\b/g);
  const launch = count(/\/Launch\b/g);
  const embedded = count(/\/EmbeddedFile\b/g);
  const rich = count(/\/RichMedia\b/g);
  const submit = count(/\/SubmitForm\b/g);
  const uris = count(/\/URI\b/g);
  const objStm = count(/\/ObjStm\b/g);
  const obf = count(/\/[A-Za-z]{1,20}#[0-9A-Fa-f]{2}/g);

  if (js > 0 && auto > 0) {
    findings.push({ id: "pdf-js-auto", weight: 55, reason: "O PDF tem JavaScript que pode rodar sozinho ao abrir." });
  } else if (js > 0) {
    findings.push({ id: "pdf-js", weight: 25, reason: "O PDF contém JavaScript." });
  }
  if (launch > 0) findings.push({ id: "pdf-launch", weight: 50, reason: "O PDF tenta executar programas (ação Launch)." });
  if (embedded > 0) findings.push({ id: "pdf-embutido", weight: 25, reason: "O PDF carrega outro arquivo escondido dentro dele." });
  if (rich > 0) findings.push({ id: "pdf-rich", weight: 20, reason: "O PDF tem conteúdo multimídia/Flash embutido." });
  if (obf > 0) findings.push({ id: "pdf-ofuscado", weight: 25, reason: "Nomes internos do PDF estão ofuscados, técnica usada para fugir de antivírus." });
  if (submit > 0) findings.push({ id: "pdf-submit", weight: 10, reason: "O PDF envia formulário para fora." });
  if (uris > 10) findings.push({ id: "pdf-links", weight: 10, reason: `O PDF tem ${uris} links embutidos.` });
  if (objStm > 0) notes.push("Partes do PDF estão comprimidas; itens escondidos nelas podem não ter sido vistos.");
}

const SUSPICIOUS_INSIDE = /\.(exe|scr|bat|cmd|vbs|js|lnk|apk|msi|ps1|hta)$/i;

async function scanZip(
  file: File,
  ext: string,
  findings: Finding[],
  notes: string[],
): Promise<ApkInfo | undefined> {
  const parsed = await readZipEntries(file);
  if (!parsed) {
    notes.push("Não consegui ler a estrutura do ZIP (corrompido, ZIP64 ou formato incomum).");
    if (ext === "apk") findings.push({ id: "apk-ilegivel", weight: 25, reason: "APK com estrutura ilegível." });
    return undefined;
  }
  const { entries, cdOffset } = parsed;
  const names = entries.map((e) => e.name);

  if (names.some((n) => /(^|[\\/])\.\.([\\/]|$)/.test(n))) {
    findings.push({ id: "zip-traversal", weight: 50, reason: "O pacote tem caminhos que tentam escrever fora da pasta (path traversal)." });
  }
  const totalComp = entries.reduce((s, e) => s + e.compSize, 0);
  const totalSize = entries.reduce((s, e) => s + e.size, 0);
  if (totalComp > 0 && totalSize > 2 * 1024 ** 3 && totalSize / totalComp > 200) {
    findings.push({ id: "zip-bomba", weight: 40, reason: "Taxa de compressão absurda: possível zip bomb que trava o aparelho." });
  }

  const isApk = ext === "apk" || (names.includes("AndroidManifest.xml") && names.some((n) => /^classes\d*\.dex$/.test(n)));

  if (!isApk) {
    if (names.some((n) => /vbaProject\.bin$/i.test(n))) {
      findings.push({ id: "office-macro", weight: 40, reason: "Documento do Office com macros (vbaProject.bin)." });
    }
    const inside = names.filter((n) => SUSPICIOUS_INSIDE.test(n));
    if (inside.length > 0) {
      findings.push({ id: "zip-executavel", weight: 30, reason: `Contém executáveis dentro: ${inside.slice(0, 3).join(", ")}.` });
    }
    return undefined;
  }

  const dexEntries = entries.filter((e) => /^classes\d*\.dex$/.test(e.name));
  const nativeLibs = names.filter((n) => /^lib\/.+\.so$/.test(n)).length;
  const signedV1 = names.some((n) => /^META-INF\/.+\.(RSA|DSA|EC)$/i.test(n));
  let signedV2OrNewer = false;
  if (cdOffset >= 16) {
    const magic = latin1(new Uint8Array(await file.slice(cdOffset - 16, cdOffset).arrayBuffer()));
    signedV2OrNewer = magic === "APK Sig Block 42";
  }
  if (!signedV1 && !signedV2OrNewer) {
    findings.push({ id: "apk-sem-assinatura", weight: 30, reason: "APK sem assinatura de desenvolvedor." });
  }

  const hidden = names.filter((n) => /^(assets|res\/raw)\/.+\.(dex|jar|apk|zip|bin|so)$/i.test(n));
  if (hidden.length > 0) {
    findings.push({ id: "apk-payload", weight: 25, reason: `Arquivos executáveis escondidos dentro do app (${hidden.slice(0, 2).join(", ")}).` });
  }

  // Permissões do manifesto
  const perms = new Set<string>();
  const flags = new Set<string>();
  const manifestEntry = entries.find((e) => e.name === "AndroidManifest.xml");
  if (manifestEntry) {
    const manifest = await readEntry(file, manifestEntry, MAX_MANIFEST);
    if (manifest) {
      for (const s of manifestStrings(manifest)) {
        if (s.startsWith("android.permission.")) perms.add(s);
        else flags.add(s);
      }
    } else {
      notes.push("Não consegui abrir o AndroidManifest.xml (compressão não suportada neste navegador).");
    }
  }
  const has = (n: string) => perms.has(`android.permission.${n}`);
  let permWeight = 0;
  const risky: string[] = [];
  for (const [name, w] of Object.entries(PERMISSION_WEIGHTS)) {
    if (has(name)) {
      permWeight += w;
      if (w >= 8) risky.push(name);
    }
  }
  if (permWeight > 0) {
    findings.push({
      id: "apk-permissoes",
      weight: Math.min(40, permWeight),
      reason: risky.length ? `Pede permissões sensíveis: ${risky.slice(0, 5).join(", ")}.` : "Pede permissões moderadamente sensíveis.",
    });
  }
  const smsRead = has("READ_SMS") || has("RECEIVE_SMS");
  if (smsRead && has("INTERNET")) {
    findings.push({ id: "combo-sms", weight: 15, reason: "Lê SMS e tem internet: pode interceptar códigos de verificação." });
  }
  if (has("BIND_ACCESSIBILITY_SERVICE") && has("SYSTEM_ALERT_WINDOW")) {
    findings.push({ id: "combo-acessibilidade", weight: 30, reason: "Acessibilidade + sobreposição de tela: padrão típico de trojan bancário." });
  }
  if (has("REQUEST_INSTALL_PACKAGES") && hidden.length > 0) {
    findings.push({ id: "combo-dropper", weight: 25, reason: "Instala pacotes e carrega arquivos escondidos: comportamento de dropper." });
  }
  if (flags.has("android.app.admin.action.DEVICE_ADMIN_ENABLED") || flags.has("android.app.action.DEVICE_ADMIN_ENABLED")) {
    findings.push({ id: "apk-admin", weight: 15, reason: "Quer virar administrador do aparelho, o que dificulta desinstalar." });
  }

  // Marcadores dentro do classes.dex
  const seen = new Map<string, number>();
  let skippedDex = 0;
  for (const e of dexEntries.slice(0, 4)) {
    const bytes = await readEntry(file, e, MAX_DEX_INFLATE);
    if (!bytes) {
      skippedDex++;
      continue;
    }
    const text = latin1(bytes);
    for (const m of DEX_MARKERS) {
      if (text.includes(m.text)) seen.set(m.reason, Math.max(seen.get(m.reason) ?? 0, m.weight));
    }
  }
  if (skippedDex > 0) notes.push(`${skippedDex} arquivo(s) classes.dex não foram lidos (grande demais ou compressão não suportada).`);
  if (seen.size > 0) {
    const sum = Array.from(seen.values()).reduce((a, b) => a + b, 0);
    findings.push({
      id: "apk-codigo",
      weight: Math.min(35, sum),
      reason: `O código do app ${Array.from(seen.keys()).join("; ")}.`,
    });
  }

  notes.push("Apps legítimos também pedem várias permissões: considere de onde o APK veio (loja oficial ou link avulso).");
  return { permissions: Array.from(perms).sort(), signedV1, signedV2OrNewer, dexCount: dexEntries.length, nativeLibs };
}

export async function scanFile(file: File): Promise<FileScanResult> {
  const findings: Finding[] = [];
  const notes: string[] = [
    "Análise estática local: o arquivo nunca é executado. Não substitui um antivírus nativo e não garante que o arquivo seja seguro.",
  ];
  const name = file.name;
  const ext = name.includes(".") ? (name.split(".").pop() ?? "").toLowerCase() : "";

  if (file.size === 0) notes.push("O arquivo está vazio.");

  const head = new Uint8Array(await file.slice(0, 4096).arrayBuffer());
  const kind = detectKind(head);

  // Nome
  if (/\u202e/.test(name)) {
    findings.push({ id: "nome-rlo", weight: 70, reason: "O nome usa um caractere invisível que inverte a extensão para enganar você." });
  }
  if (/\.(pdf|docx?|xlsx?|pptx?|jpe?g|png|gif|txt|mp3|mp4)\.(apk|exe|scr|bat|cmd|js|vbs|jar|msi|com|lnk)$/i.test(name)) {
    findings.push({ id: "dupla-extensao", weight: 60, reason: "Extensão dupla: finge ser documento ou mídia, mas é executável." });
  } else if (DANGEROUS_EXT.has(ext)) {
    findings.push({ id: "ext-executavel", weight: 30, reason: `Arquivo .${ext} é executável.` });
  }

  // Tipo real x extensão
  if ((kind === "pe" || kind === "elf") && !["exe", "dll", "sys", "scr", "so", "bin", "elf", ""].includes(ext)) {
    findings.push({ id: "executavel-disfarcado", weight: 60, reason: `É um executável disfarçado de .${ext}.` });
  } else {
    const expected = EXPECTED_KIND[ext];
    if (expected && kind !== expected) {
      if (kind === "script") {
        findings.push({ id: "script-disfarcado", weight: 50, reason: `É um script disfarçado de .${ext}.` });
      } else if (kind === "desconhecido" && STRICT_EXT.has(ext)) {
        findings.push({ id: "tipo-nao-bate", weight: 30, reason: `O conteúdo não parece um .${ext} de verdade.` });
      } else if (kind !== "desconhecido") {
        findings.push({ id: "tipo-diferente", weight: 10, reason: `Dizia ser .${ext}, mas o conteúdo é ${kind}.` });
      }
    }
  }

  // EICAR e hashes
  if (latin1(head).includes(EICAR)) {
    findings.push({ id: "eicar", weight: 100, reason: "Assinatura de teste EICAR detectada (arquivo de teste de antivírus, inofensivo)." });
  }
  const hash = await sha256(file);
  if (hash && KNOWN_BAD_SHA256.has(hash)) {
    findings.push({ id: "hash-conhecido", weight: 100, reason: "Este arquivo é idêntico a uma ameaça conhecida." });
  }
  if (!hash) notes.push("Hash SHA-256 não calculado (arquivo muito grande ou navegador sem suporte).");

  // Análise por tipo
  let apk: ApkInfo | undefined;
  if (kind === "pdf" || ext === "pdf") {
    await scanPdf(file, findings, notes);
  } else if (kind === "zip") {
    apk = await scanZip(file, ext, findings, notes);
  } else if (ext === "apk") {
    findings.push({ id: "apk-nao-zip", weight: 40, reason: "Diz ser APK, mas não tem estrutura de APK." });
  }

  const result = buildResult(findings, notes);
  return { ...result, file: { name, size: file.size, kind, sha256: hash }, apk };
}
