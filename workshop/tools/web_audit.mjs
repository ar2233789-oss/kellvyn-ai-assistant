import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";

const base = process.env.KELLVYN_LAB_URL || "http://127.0.0.1:4173/";
const out = process.env.LAB_OUTPUT_DIR || "out/web";
fs.mkdirSync(out, { recursive: true });
const browser = await chromium.launch({ headless: true, args: ["--no-sandbox"] });
const page = await browser.newPage({
  viewport: { width: 444, height: 913 },
  deviceScaleFactor: 2.75,
  isMobile: true,
  hasTouch: true,
});
page.setDefaultTimeout(7000);
const pageErrors = [];
page.on("pageerror", e => pageErrors.push(String(e)));
const checks = [];
const check = (name, ok, details = "") => checks.push({ name, status: ok ? "PASS" : "FAIL", details });
const capture = async name => page.screenshot({ path: path.join(out, name), fullPage: false });
const box = async locator => {
  const r = await locator.boundingBox();
  return r ? Object.fromEntries(["x", "y", "width", "height"].map(k => [k, Math.round(r[k] * 100) / 100])) : null;
};

try {
  await page.goto(base, { waitUntil: "networkidle", timeout: 60000 });
  await capture("web-reference.png");
  const metrics = {
    url: page.url(),
    title: await page.title(),
    viewport: { width: 444, height: 913, deviceScaleFactor: 2.75 },
    controls: [],
    pageErrors,
  };
  for (const label of ["Histórico de conversas", "Configurações", "Anexos e Web", "Ditado por voz", "Enviar"]) {
    const locator = page.getByRole("button", { name: label, exact: true });
    const count = await locator.count().catch(() => 0);
    check("header/composer control: " + label, count > 0, "count=" + count);
    if (count) metrics.controls.push({ label, count, rect: await box(locator.first()) });
  }

  const settingsButton = page.getByRole("button", { name: "Configurações", exact: true });
  if (await settingsButton.count()) {
    await settingsButton.click();
    await page.getByRole("heading", { name: "CONFIGURAÇÕES DO KELLVYN" }).waitFor({ state: "visible" });
    await page.waitForTimeout(350);
    await capture("web-settings.png");
    const categories = ["CORES", "EFEITO NA TELA", "ATALHOS", "MODO DE JOGO", "SEGURANÇA"];
    for (const category of categories) {
      const found = await page.getByText(category, { exact: true }).count().catch(() => 0);
      check("settings category: " + category, found > 0, "count=" + found);
    }
    for (const effect of ["Chuva", "Raios", "Nenhum"]) {
      const found = await page.getByRole("button", { name: effect, exact: true }).count().catch(() => 0);
      check("effect control: " + effect, found > 0, "count=" + found);
    }

    // Use a fresh browser context and restore preferences after exercising both effects.
    const original = await page.evaluate(() => localStorage.getItem("kv.settings"));
    const rain = page.getByRole("button", { name: "Chuva", exact: true });
    const lightning = page.getByRole("button", { name: "Raios", exact: true });
    const none = page.getByRole("button", { name: "Nenhum", exact: true });
    if (await none.count() && await rain.count() && await lightning.count()) {
      await none.click();
      await rain.click();
      await lightning.click();
      const active = await page.evaluate(() => {
        try { return JSON.parse(localStorage.getItem("kv.settings") || "{}").effect || []; }
        catch { return []; }
      });
      check("rain and lightning can coexist", Array.isArray(active) && active.includes("chuva") && active.includes("raios"), JSON.stringify(active));
      await page.evaluate(value => value === null ? localStorage.removeItem("kv.settings") : localStorage.setItem("kv.settings", value), original);
      await page.reload({ waitUntil: "networkidle" });
    }
    const closer = page.getByRole("button", { name: "Fechar", exact: true });
    if (await closer.count()) await closer.last().click();
  }

  const plus = page.getByRole("button", { name: "Anexos e Web", exact: true });
  if (await plus.count()) {
    await plus.click();
    for (const item of ["Imagem ou arquivo", "Plugins", "Acesso à Web"]) {
      const found = await page.getByText(item, { exact: true }).count().catch(() => 0);
      check("plus menu: " + item, found > 0, "count=" + found);
    }
    await page.keyboard.press("Escape").catch(() => {});
  }
  await page.evaluate(() => {
    const box = el => {
      if (!el) return null;
      const r = el.getBoundingClientRect();
      return { x:r.x, y:r.y, width:r.width, height:r.height };
    };
    return {
      header: box(document.querySelector("header")),
      main: box(document.querySelector("main")),
      composer: box(document.querySelector("footer")),
      bottomNav: box(document.querySelector("nav")),
      buttons: [...document.querySelectorAll("header button, footer button, nav button")].map(el => ({
        label: el.getAttribute("aria-label") || el.title || (el.innerText || "").trim().replace(/\s+/g, " ").slice(0, 80),
        rect: box(el),
        disabled: el.disabled || false,
      })),
    };
  }).then(layout => { metrics.layout = layout; });
  metrics.pageErrors = pageErrors;
  metrics.checks = checks;
  fs.writeFileSync(path.join(out, "web-audit.json"), JSON.stringify(metrics, null, 2));
  fs.writeFileSync(path.join(out, "web-checks.json"), JSON.stringify({ checks, pageErrors }, null, 2));
  console.log(JSON.stringify({ checks, pageErrors }, null, 2));
  if (checks.some(c => c.status === "FAIL") || pageErrors.length) process.exitCode = 1;
} finally {
  await browser.close();
}
