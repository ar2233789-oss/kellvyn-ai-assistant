import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterEach, test } from "node:test";
import { analyzeProject } from "../typescript_semantic_map.mjs";

const temporaryRoots = [];

function fixture() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "kellvyn-ts-map-"));
  temporaryRoots.push(root);
  fs.mkdirSync(path.join(root, "src"));
  fs.writeFileSync(path.join(root, "tsconfig.json"), JSON.stringify({
    compilerOptions: {
      target: "ES2022",
      module: "ESNext",
      moduleResolution: "Bundler",
      strict: true,
      noEmit: true
    },
    include: ["src/**/*.ts"]
  }, null, 2));
  fs.writeFileSync(path.join(root, "src", "agent.ts"), `
export interface AgentTask { id: string }
export class AgentEngine {
  run(task: AgentTask): string {
    return task.id;
  }
}
`);
  fs.writeFileSync(path.join(root, "src", "agent.test.ts"), `
import { AgentEngine, type AgentTask } from "./agent";
const task: AgentTask = { id: "probe" };
new AgentEngine().run(task);
`);
  return root;
}

afterEach(() => {
  for (const root of temporaryRoots.splice(0)) {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test("semantic map resolves exported symbols, local imports, calls and test references", () => {
  const report = analyzeProject(fixture());
  assert.equal(report.summary.error_diagnostics, 0, JSON.stringify(report.diagnostics));
  assert.ok(report.declarations.some(item => item.name === "AgentEngine" && item.exported));
  assert.ok(report.declarations.some(item => item.name === "AgentTask" && item.exported));
  assert.ok(report.imports.some(item => item.specifier === "./agent" && item.resolved && item.project_local && item.to === "src/agent.ts"));
  assert.ok(report.calls.some(item => item.symbol === "AgentEngine" || item.symbol === "run"));
  assert.ok(report.test_files.includes("src/agent.test.ts"));
  assert.ok(report.test_references.some(item => item.symbol === "AgentEngine" && item.to === "src/agent.ts"));
});

test("semantic map distinguishes a resolved external import from a missing import", () => {
  const root = fixture();
  fs.writeFileSync(path.join(root, "src", "agent.test.ts"), `
import { AgentEngine } from "./agent";
import type { MissingType } from "./not-present";
new AgentEngine();
let item: MissingType;
void item;
`);
  const report = analyzeProject(root);
  const local = report.imports.find(item => item.specifier === "./agent");
  const missing = report.imports.find(item => item.specifier === "./not-present");
  assert.equal(local?.resolved, true);
  assert.equal(local?.project_local, true);
  assert.equal(missing?.resolved, false);
  assert.equal(missing?.to, null);
  assert.ok(report.summary.error_diagnostics > 0);
});
