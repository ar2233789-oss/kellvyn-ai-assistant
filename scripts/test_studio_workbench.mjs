import assert from "node:assert/strict";
import { chromium } from "playwright";

const browser = await chromium.launch({ headless: true, args: ["--no-sandbox"] });
const page = await browser.newPage({ viewport: { width: 1360, height: 980 }, acceptDownloads: true });
const pageErrors = [];
page.on("pageerror", (error) => pageErrors.push(String(error)));
try {
  await page.goto("http://127.0.0.1:4173/studio/index.html", { waitUntil: "domcontentloaded", timeout: 20000 });
  await page.locator("#page-home.active").waitFor({ state: "visible", timeout: 5000 });
  assert.match(await page.title(), /Studio de Produção/);
  assert.equal(await page.locator("#page-home.active").count(), 1);

  // Screenshot comparison: test equal-dimension pixel diff and all comparison modes.
  await page.locator('[data-page="compare"]').click();
  const images = await page.evaluate(() => {
    const make = (color) => {
      const canvas = document.createElement("canvas");
      canvas.width = 48;
      canvas.height = 32;
      const context = canvas.getContext("2d");
      context.fillStyle = color;
      context.fillRect(0, 0, canvas.width, canvas.height);
      context.fillStyle = "#ffffff";
      context.fillRect(8, 8, 12, 10);
      return canvas.toDataURL("image/png").split(",")[1];
    };
    return { web: make("#102030"), native: make("#304050") };
  });
  await page.locator("#fileA").setInputFiles({ name: "web-reference.png", mimeType: "image/png", buffer: Buffer.from(images.web, "base64") });
  await page.locator("#fileB").setInputFiles({ name: "native-screen.png", mimeType: "image/png", buffer: Buffer.from(images.native, "base64") });
  await page.waitForFunction(() => document.querySelector("#compStatus")?.textContent === "Mesmo tamanho", null, { timeout: 5000 });
  assert.notEqual(await page.locator("#changed").textContent(), "—");
  await page.locator("#compMode").selectOption("diff");
  await page.locator("#compMode").selectOption("side");
  await page.locator("#compMode").selectOption("overlay");
  const diffDownloadPromise = page.waitForEvent("download");
  await page.locator("#downloadCompare").click();
  assert.equal((await diffDownloadPromise).suggestedFilename(), "poco-f6-difference.png");
  const overlayDownloadPromise = page.waitForEvent("download");
  await page.locator("#downloadOverlay").click();
  assert.equal((await overlayDownloadPromise).suggestedFilename(), "poco-f6-overlay.png");

  // Palette updates a color and preserves export behavior.
  await page.locator('[data-page="palette"]').click();
  await page.locator("#hexInput").fill("#FF0088");
  await page.locator("#applyHex").click();
  assert.equal(await page.locator("#hexLabel").textContent(), "#FF0088");
  await page.locator("#addSwatch").click();
  assert.ok((await page.locator("#swatchList").innerText()).includes("#FF0088"));
  const paletteDownloadPromise = page.waitForEvent("download");
  await page.locator("#exportPalette").click();
  assert.equal((await paletteDownloadPromise).suggestedFilename(), "kellvyn-palette.json");

  // 2D draw/text/export path.
  await page.locator('[data-page="art"]').click();
  await page.locator("#artText").fill("LAB");
  await page.locator("#addText").click();
  assert.equal(await page.locator("#artCount").textContent(), "1 objetos");
  const svgDownloadPromise = page.waitForEvent("download");
  await page.locator("#saveSvg").click();
  assert.equal((await svgDownloadPromise).suggestedFilename(), "studio-2d.svg");
  const pngDownloadPromise = page.waitForEvent("download");
  await page.locator("#savePng").click();
  assert.equal((await pngDownloadPromise).suggestedFilename(), "studio-2d.png");

  // 3D transforms and 4D tesseract controls.
  await page.locator('[data-page="space"]').click();
  await page.locator("#rotX").evaluate((el) => { el.value = "63"; el.dispatchEvent(new Event("input", { bubbles: true })); });
  assert.match(await page.locator("#cube").evaluate((el) => el.style.transform), /rotateX\(63deg\)/);
  await page.locator("#animate4d").click();
  assert.equal(await page.locator("#animate4d").textContent(), "Parar animação");
  await page.waitForTimeout(100);
  await page.locator("#animate4d").click();
  assert.equal(await page.locator("#animate4d").textContent(), "Animar rotação");
  const tesseractDownloadPromise = page.waitForEvent("download");
  await page.locator("#save4d").click();
  assert.equal((await tesseractDownloadPromise).suggestedFilename(), "tesseract-4d-projection.png");

  // Animation timeline and JSON export.
  await page.locator('[data-page="motion"]').click();
  await page.locator("#addKey").click();
  assert.ok((await page.locator("#keySummary").textContent()).includes("1 keyframe"));
  await page.locator("#playMotion").click();
  assert.equal(await page.locator("#playMotion").textContent(), "Ⅱ Pausar");
  await page.waitForTimeout(100);
  await page.locator("#playMotion").click();
  const motionDownloadPromise = page.waitForEvent("download");
  await page.locator("#exportMotion").click();
  assert.equal((await motionDownloadPromise).suggestedFilename(), "studio-animation.json");

  // Error and dependency extraction, then diagnostic export.
  await page.locator('[data-page="logs"]').click();
  await page.locator("#loadLogSample").click();
  assert.ok(Number(await page.locator("#logErrorN").textContent()) >= 1);
  assert.ok(Number(await page.locator("#logDepN").textContent()) >= 1);
  const logDownloadPromise = page.waitForEvent("download");
  await page.locator("#exportLog").click();
  assert.equal((await logDownloadPromise).suggestedFilename(), "kellvyn-log-diagnostic.json");

  // JSON report ingest and session export.
  await page.locator('[data-page="report"]').click();
  const report = Buffer.from(JSON.stringify({ status: "captures_compared", paletteComparison: "compared", method: "pixel-aligned", results: [{ name: "main", meanAbsoluteError: 0 }] }));
  await page.locator("#reportFile").setInputFiles({ name: "parity-metrics.json", mimeType: "application/json", buffer: report });
  await page.locator("#loadReport").click();
  await page.waitForFunction(() => document.querySelector("#reportSummary")?.textContent.includes("captures_compared"), null, { timeout: 5000 });
  const sessionDownloadPromise = page.waitForEvent("download");
  await page.locator("#exportSession").click();
  assert.equal((await sessionDownloadPromise).suggestedFilename(), "kellvyn-studio-session.json");

  assert.deepEqual(pageErrors, [], "Studio should not raise browser runtime errors");
  console.log(JSON.stringify({
    status: "PASS",
    checks: [
      "page initialization",
      "screenshot image loading, pixel metrics, overlay/diff/side-by-side modes and exports",
      "palette editing and JSON export",
      "2D artboard text and PNG/SVG exports",
      "3D transform and 4D tesseract animation/export",
      "animation keyframes and JSON export",
      "log errors/dependencies and diagnostic export",
      "report ingestion and session export",
    ],
    pageErrors,
  }, null, 2));
} finally {
  await browser.close();
}
