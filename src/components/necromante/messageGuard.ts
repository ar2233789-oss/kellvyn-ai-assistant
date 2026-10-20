/**
 * Guardião de mensagens do Necromante.
 * Analisa textos que a PESSOA compartilhou (ou que o app nativo repassar com
 * permissão) procurando ameaça, perseguição, golpe, extorsão e pressão por fotos.
 *
 * Por padrões em português do Brasil. Pode errar (brincadeira entre amigos, por
 * exemplo), então o Necromante avisa e orienta, nunca acusa. Nada é guardado.
 */
import { extractLinks, scanLink } from "./linkScanner";

export type ThreatCategory =
  | "ameaca"
  | "perseguicao"
  | "controle"
  | "golpe"
  | "extorsao"
  | "pressao_por_fotos";

export interface MessageFlag {
  category: ThreatCategory;
  weight: number;
  reason: string;
}

export type GuardLevel = "ok" | "atencao" | "alerta";

export interface GuardResult {
  level: GuardLevel;
  score: number;
  flags: MessageFlag[];
  advice: string[];
  notes: string[];
}

export interface ChatMessage {
  from: "eu" | "outro";
  text: string;
}

interface Rule {
  category: ThreatCategory;
  weight: number;
  reason: string;
  re: RegExp;
}

const RULES: Rule[] = [
  // Ameaça
  { category: "ameaca", weight: 60, reason: "Ameaça de violência.", re: /\b(vou|vamos|irei|iremos)\s+(te\s+|lhe\s+)?(matar|esfaquear|espancar|queimar|acabar\s+com\s+(voce|tu|a\s+sua\s+vida))\b/ },
  { category: "ameaca", weight: 55, reason: "Ameaça de agressão.", re: /\b(vou|vamos)\s+(te\s+)?(bater|quebrar|arrebentar|dar\s+um\s+tiro|meter\s+bala)\b/ },
  { category: "ameaca", weight: 45, reason: "Frase intimidadora.", re: /\bvoce\s+(vai|ira)\s+(morrer|se\s+arrepender|pagar\s+caro)\b/ },
  { category: "ameaca", weight: 40, reason: "Intimidação para você ficar calado(a).", re: /\bse\s+(voce|tu|vc)\s+(contar|falar|denunciar|chamar\s+a\s+policia)\b/ },
  { category: "ameaca", weight: 30, reason: "Promessa de te encontrar/pegar.", re: /\bvou\s+(te\s+)?(achar|encontrar|pegar)\b/ },

  // Perseguição
  { category: "perseguicao", weight: 55, reason: "Diz estar na frente da sua casa/trabalho/escola.", re: /\b(estou|to|tou)\s+(na\s+frente|em\s+frente|perto)\s+(da|do|de)\s+(sua|seu)\s+(casa|trabalho|escola|faculdade|predio)\b/ },
  { category: "perseguicao", weight: 50, reason: "Diz saber onde você mora, trabalha, estuda ou está.", re: /\bsei\s+onde\s+(voce|tu|vc)\s+(mora|trabalha|estuda|esta|ta)\b/ },
  { category: "perseguicao", weight: 50, reason: "Diz estar te seguindo ou vigiando.", re: /\b(estou|to|tou)\s+(te\s+)?(seguindo|vigiando|observando|monitorando)\b/ },
  { category: "perseguicao", weight: 20, reason: "Comenta que te viu sem você ter dito onde estava.", re: /\b(vi|te\s+vi)\s+(voce|vc|tu)\s+(hoje|ontem|la|saindo|com)\b/ },
  { category: "perseguicao", weight: 15, reason: "Insiste em saber sua localização.", re: /\b(me\s+)?(manda|envia|compartilha|passa|liga|ativa)\s+(a\s+)?(sua\s+)?localizacao\b/ },

  // Controle
  { category: "controle", weight: 25, reason: "Pede sua senha ou acesso ao seu celular.", re: /\b(me\s+(da|passa|manda)\s+(a\s+)?(sua\s+)?senha|deixa\s+eu\s+ver\s+(o\s+)?seu\s+celular)\b/ },
  { category: "controle", weight: 15, reason: "Exige que você apague ou bloqueie alguém.", re: /\b(apaga|exclui|bloqueia)\s+(esse|essa|o|a)\s+[a-z\s]{0,20}(contato|numero|foto|amigo|amiga)\b/ },
  { category: "controle", weight: 10, reason: "Cobra explicações sobre com quem você fala.", re: /\b(quem\s+e\s+(esse|essa|aquele|aquela)|com\s+quem\s+(voce|vc|tu)\s+(esta|tava|ta)\s+falando)\b/ },

  // Golpe
  { category: "golpe", weight: 30, reason: "Pede código de verificação (golpe clássico de clonagem de WhatsApp).", re: /\b(codigo\s+(de\s+)?(verificacao|confirmacao|seguranca)|codigo\s+que\s+(chegou|enviei|mandei))\b/ },
  { category: "golpe", weight: 25, reason: "Diz que mudou de número (golpe do número novo).", re: /\b(meu\s+numero\s+(mudou|novo)|novo\s+numero|troquei\s+de\s+numero)\b/ },
  { category: "golpe", weight: 30, reason: "Alguém da família pedindo dinheiro por mensagem.", re: /\b(mae|mamae|pai|painho|tia|tio|vo|vovo)\b[^.\n]{0,80}\b(pix|dinheiro|transfer|pagar|conta)\b/ },
  { category: "golpe", weight: 35, reason: "Pede Pix com urgência.", re: /\b(faca|fazer|mande|manda|envie)\s+(um\s+)?pix\b[^.\n]{0,60}\b(urgente|agora|rapido|hoje|devolver|engano)\b/ },
  { category: "golpe", weight: 30, reason: "Fala de Pix feito por engano (golpe do comprovante).", re: /\bpix\b[^.\n]{0,60}\b(por\s+engano|errado)\b/ },
  { category: "golpe", weight: 30, reason: "Diz que sua conta/cartão/CPF foi bloqueado.", re: /\b(conta|cartao|cpf)\s+(foi\s+)?(bloquead|suspens|irregular|cancelad)/ },
  { category: "golpe", weight: 30, reason: "Pede para atualizar ou confirmar dados.", re: /\b(atualize|confirme|regularize)\s+(seus?|sua)\s+(dados|cadastro|conta|cpf|senha)\b/ },
  { category: "golpe", weight: 25, reason: "Promete prêmio ou sorteio.", re: /\b(voce\s+(ganhou|foi\s+sorteado|foi\s+selecionado)|resgate\s+(seu|ja))\b/ },
  { category: "golpe", weight: 25, reason: "Promete lucro garantido.", re: /\b(investimento\s+garantido|lucro\s+garantido|dobrar\s+(seu\s+)?dinheiro|renda\s+extra\s+facil)\b/ },
  { category: "golpe", weight: 20, reason: "Manda clicar em um link.", re: /\b(clique|acesse|entre)\s+(no|nesse|neste|pelo)\s+(link|site)\b/ },
  { category: "golpe", weight: 20, reason: "Diz ser do banco/suporte/central.", re: /\b(sou|aqui\s+e)\s+(do|da|o|a)?\s*(banco|suporte|central|setor|departamento)\b/ },

  // Extorsão
  { category: "extorsao", weight: 60, reason: "Ameaça vazar suas fotos, vídeos ou conversas.", re: /\bvou\s+(vazar|postar|divulgar|expor|espalhar)\s+(suas?|seus?|as?|os?)?\s*(fotos?|videos?|conversas?|prints?|nudes?)\b/ },
  { category: "extorsao", weight: 50, reason: "Exige pagamento sob ameaça.", re: /\b(paga|deposita|manda\s+(o\s+)?pix)\s+(ou|senao)\b/ },

  // Pressão por fotos
  { category: "pressao_por_fotos", weight: 50, reason: "Pede foto íntima ou sem roupa.", re: /\b(manda|envia|me\s+manda)\s+(uma?\s+)?(foto|video|nude|nudes)\s+(sem\s+roupa|pelad[oa]|intim)/ },
  { category: "pressao_por_fotos", weight: 40, reason: "Pede nudes.", re: /\bmanda\s+nudes?\b/ },
  { category: "pressao_por_fotos", weight: 40, reason: "Pede segredo dos seus pais ou de todo mundo.", re: /\b(nao\s+conta|nao\s+fala)\s+(pra|para)\s+(ninguem|seus?\s+pais|sua\s+mae|seu\s+pai)\b/ },
  { category: "pressao_por_fotos", weight: 25, reason: "Insiste em 'segredo nosso'.", re: /\bsegredo\s+(nosso|entre\s+nos)\b/ },
];

function fold(text: string): string {
  return text.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/\s+/g, " ");
}

function flagsFor(text: string): MessageFlag[] {
  const folded = fold(text);
  const flags: MessageFlag[] = [];
  for (const r of RULES) {
    if (r.re.test(folded)) flags.push({ category: r.category, weight: r.weight, reason: r.reason });
  }
  let worst = 0;
  for (const link of extractLinks(text)) {
    const res = scanLink(link);
    if (res.verdict === "perigoso") worst = Math.max(worst, 40);
    else if (res.verdict === "suspeito") worst = Math.max(worst, 15);
  }
  if (worst >= 40) flags.push({ category: "golpe", weight: 40, reason: "Um link da mensagem parece perigoso." });
  else if (worst > 0) flags.push({ category: "golpe", weight: 15, reason: "Um link da mensagem tem sinais suspeitos." });
  return flags;
}

function adviceFor(categories: Set<ThreatCategory>): string[] {
  const advice: string[] = [];
  if (categories.has("ameaca") || categories.has("perseguicao") || categories.has("extorsao")) {
    advice.push("Não apague as mensagens: tire prints e guarde, elas servem de prova.");
    advice.push("Não confronte nem responda no calor do momento. Avise alguém de confiança.");
    advice.push("Se houver risco imediato, ligue 190 (Polícia Militar). Em caso de violência contra a mulher, ligue 180.");
  }
  if (categories.has("golpe")) {
    advice.push("Não passe códigos, senhas nem faça Pix. Confirme ligando para a pessoa ou para o banco pelos canais oficiais.");
  }
  if (categories.has("pressao_por_fotos")) {
    advice.push("Não envie fotos íntimas. Se envolver menor de idade ou parecer aliciamento, avise um responsável e denuncie (Disque 100 ou SaferNet).");
  }
  if (categories.has("controle")) {
    advice.push("Pedir senha ou vigiar seu celular não é cuidado. Converse com alguém de confiança sobre isso.");
  }
  return advice;
}

function build(flags: MessageFlag[]): GuardResult {
  const unique = new Map<string, MessageFlag>();
  for (const f of flags) if (!unique.has(f.reason)) unique.set(f.reason, f);
  const list = Array.from(unique.values()).sort((a, b) => b.weight - a.weight);
  const score = Math.min(100, list.reduce((s, f) => s + f.weight, 0));
  const level: GuardLevel = score >= 50 ? "alerta" : score >= 20 ? "atencao" : "ok";
  return {
    level,
    score,
    flags: list,
    advice: level === "ok" ? [] : adviceFor(new Set(list.map((f) => f.category))),
    notes: ["Análise por padrões de texto: pode errar. Use como aviso, não como prova."],
  };
}

export function analyzeMessage(text: string): GuardResult {
  return build(flagsFor(text));
}

/** Analisa uma conversa inteira; só as mensagens de "outro" entram na checagem. */
export function analyzeConversation(messages: ChatMessage[]): GuardResult {
  const flags: MessageFlag[] = [];
  let streak = 0;
  let insistent = false;
  for (const m of messages) {
    if (m.from === "outro") {
      flags.push(...flagsFor(m.text));
      streak++;
      if (streak >= 8) insistent = true;
    } else {
      streak = 0;
    }
  }
  if (insistent) {
    flags.push({ category: "perseguicao", weight: 25, reason: "Muitas mensagens seguidas sem resposta (insistência)." });
  }
  return build(flags);
}
