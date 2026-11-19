#!/usr/bin/env node
/**
 * TypeScript/TSX semantic map powered by the repository's own TypeScript compiler.
 * It reports resolved project-local declarations, imports, calls and test references.
 * It does not write source files or claim runtime correctness.
 */
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import ts from "typescript";

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const DEFAULT_ROOT = path.resolve(SCRIPT_DIR, "../..");

function relativePath(root, fileName) {
  const relative = path.relative(root, fileName).split(path.sep).join("/");
  return relative.startsWith("../") || path.isAbsolute(relative) ? null : relative;
}

function safeType(checker, symbol, node) {
  try {
    return checker.typeToString(checker.getTypeOfSymbolAtLocation(symbol, node)).slice(0, 300);
  } catch {
    return null;
  }
}

function resolvedDeclarations(checker, symbol) {
  if (!symbol) return [];
  let resolved = symbol;
  try {
    if ((resolved.flags & ts.SymbolFlags.Alias) !== 0) {
      resolved = checker.getAliasedSymbol(resolved);
    }
  } catch {
    return [];
  }
  return (resolved.declarations ?? []).filter(Boolean);
}

function declarationKind(node) {
  return ts.isClassDeclaration(node) || ts.isClassExpression(node) ||
    ts.isFunctionDeclaration(node) || ts.isInterfaceDeclaration(node) ||
    ts.isTypeAliasDeclaration(node) || ts.isEnumDeclaration(node) ||
    ts.isVariableDeclaration(node) || ts.isMethodDeclaration(node) ||
    ts.isPropertyDeclaration(node) || ts.isGetAccessorDeclaration(node) ||
    ts.isSetAccessorDeclaration(node) || ts.isConstructorDeclaration(node) ||
    ts.isModuleDeclaration(node);
}

function hasExportModifier(node) {
  const modifiers = node.modifiers ?? node.parent?.modifiers ?? [];
  return modifiers.some(modifier =>
    modifier.kind === ts.SyntaxKind.ExportKeyword ||
    modifier.kind === ts.SyntaxKind.DefaultKeyword
  );
}

function resolveModule(root, sourceFile, specifier, options) {
  const result = ts.resolveModuleName(specifier, sourceFile.fileName, options, ts.sys).resolvedModule;
  if (!result) return { target: null, resolved: false, projectLocal: false };

  const absolutePath = path.resolve(result.resolvedFileName);
  const relative = relativePath(root, absolutePath);
  const projectLocal = Boolean(relative && !relative.startsWith("node_modules/"));
  return {
    target: relative,
    resolved: true,
    projectLocal
  };
}

export function analyzeProject(projectRoot = DEFAULT_ROOT) {
  const root = path.resolve(projectRoot);
  const configPath = ts.findConfigFile(root, ts.sys.fileExists, "tsconfig.json");
  if (!configPath) throw new Error("tsconfig.json was not found at " + root);

  const configRead = ts.readConfigFile(configPath, ts.sys.readFile);
  if (configRead.error) {
    throw new Error(ts.flattenDiagnosticMessageText(configRead.error.messageText, "\n"));
  }
  const parsed = ts.parseJsonConfigFileContent(configRead.config, ts.sys, path.dirname(configPath), {
    noEmit: true,
    incremental: false
  }, configPath);

  if (parsed.errors.length > 0) {
    const message = parsed.errors.map(error => ts.flattenDiagnosticMessageText(error.messageText, "\n")).join("\n");
    throw new Error("tsconfig parsing failed:\n" + message);
  }

  const program = ts.createProgram({
    rootNames: parsed.fileNames,
    options: { ...parsed.options, noEmit: true, incremental: false, tsBuildInfoFile: undefined }
  });
  const checker = program.getTypeChecker();
  const files = [];
  const declarations = [];
  const imports = [];
  const calls = [];
  const references = [];
  const diagnostics = ts.getPreEmitDiagnostics(program).map(diagnostic => {
    const file = diagnostic.file?.fileName ? relativePath(root, diagnostic.file.fileName) : null;
    const position = diagnostic.file && diagnostic.start !== undefined
      ? diagnostic.file.getLineAndCharacterOfPosition(diagnostic.start)
      : null;
    return {
      category: ts.DiagnosticCategory[diagnostic.category] ?? "Unknown",
      code: diagnostic.code,
      file,
      line: position ? position.line + 1 : null,
      column: position ? position.character + 1 : null,
      message: ts.flattenDiagnosticMessageText(diagnostic.messageText, "\n").slice(0, 1200)
    };
  });

  const projectFiles = new Set(
    program.getSourceFiles()
      .map(sourceFile => relativePath(root, sourceFile.fileName))
      .filter(Boolean)
  );

  for (const sourceFile of program.getSourceFiles()) {
    if (sourceFile.isDeclarationFile) continue;
    const fileName = relativePath(root, sourceFile.fileName);
    if (!fileName || fileName.startsWith("node_modules/") || !projectFiles.has(fileName)) continue;
    const sourceText = sourceFile.getFullText();
    files.push({
      file: fileName,
      sha256: crypto.createHash("sha256").update(sourceText, "utf8").digest("hex"),
      line_count: sourceText.split("\n").length
    });

    function visit(node) {
      if (declarationKind(node) && node.name && ts.isIdentifier(node.name)) {
        const symbol = checker.getSymbolAtLocation(node.name);
        if (symbol) {
          const start = sourceFile.getLineAndCharacterOfPosition(node.getStart());
          declarations.push({
            file: fileName,
            name: node.name.text,
            kind: ts.SyntaxKind[node.kind],
            line: start.line + 1,
            exported: hasExportModifier(node),
            type: safeType(checker, symbol, node)
          });
        }
      }

      if ((ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) &&
          node.moduleSpecifier && ts.isStringLiteral(node.moduleSpecifier)) {
        const specifier = node.moduleSpecifier.text;
        const resolution = resolveModule(root, sourceFile, specifier, parsed.options);
        const start = sourceFile.getLineAndCharacterOfPosition(node.getStart());
        imports.push({
          from: fileName,
          specifier,
          to: resolution.target,
          line: start.line + 1,
          resolved: resolution.resolved,
          project_local: resolution.projectLocal
        });
      }

      if (ts.isCallExpression(node) || ts.isNewExpression(node)) {
        const symbol = checker.getSymbolAtLocation(node.expression);
        for (const declaration of resolvedDeclarations(checker, symbol)) {
          const targetFile = relativePath(root, declaration.getSourceFile().fileName);
          if (!targetFile || targetFile === fileName) continue;
          const start = sourceFile.getLineAndCharacterOfPosition(node.getStart());
          calls.push({
            from: fileName,
            to: targetFile,
            symbol: symbol?.getName() ?? node.expression.getText(sourceFile),
            line: start.line + 1,
            kind: ts.isNewExpression(node) ? "construct" : "call"
          });
        }
      }

      if (ts.isIdentifier(node)) {
        const symbol = checker.getSymbolAtLocation(node);
        for (const declaration of resolvedDeclarations(checker, symbol)) {
          const targetFile = relativePath(root, declaration.getSourceFile().fileName);
          if (!targetFile || targetFile === fileName) continue;
          const start = sourceFile.getLineAndCharacterOfPosition(node.getStart());
          references.push({
            from: fileName,
            to: targetFile,
            symbol: symbol?.getName() ?? node.text,
            line: start.line + 1
          });
        }
      }
      ts.forEachChild(node, visit);
    }
    visit(sourceFile);
  }

  const tests = files.filter(item => /(?:\.test|\.spec)\.[cm]?[jt]sx?$/i.test(item.file) || /(^|\/)__tests__(\/|$)/.test(item.file));
  const testFiles = new Set(tests.map(item => item.file));
  const testReferences = references.filter(edge => testFiles.has(edge.from));

  const sortBy = (items, fields) => items.sort((a, b) => {
    for (const field of fields) {
      const left = String(a[field] ?? "");
      const right = String(b[field] ?? "");
      const comparison = left.localeCompare(right);
      if (comparison) return comparison;
    }
    return 0;
  });

  return {
    schema: "kellvyn-typescript-semantic-map/v1",
    generated_at_utc: new Date().toISOString(),
    root: root,
    typescript_version: ts.version,
    compiler_options: {
      noEmit: true,
      strict: parsed.options.strict ?? false,
      jsx: parsed.options.jsx === undefined ? null : (ts.JsxEmit[parsed.options.jsx] ?? parsed.options.jsx),
      target: parsed.options.target === undefined ? null : (ts.ScriptTarget[parsed.options.target] ?? parsed.options.target),
      module: parsed.options.module === undefined ? null : (ts.ModuleKind[parsed.options.module] ?? parsed.options.module),
      moduleResolution: parsed.options.moduleResolution === undefined
        ? null
        : (ts.ModuleResolutionKind[parsed.options.moduleResolution] ?? parsed.options.moduleResolution),
      allowJs: parsed.options.allowJs ?? false,
      resolveJsonModule: parsed.options.resolveJsonModule ?? false,
      skipLibCheck: parsed.options.skipLibCheck ?? false
    },
    summary: {
      files: files.length,
      declarations: declarations.length,
      imports: imports.length,
      resolved_imports: imports.filter(item => item.resolved).length,
      calls: calls.length,
      cross_file_references: references.length,
      test_files: tests.length,
      test_references: testReferences.length,
      diagnostics: diagnostics.length,
      error_diagnostics: diagnostics.filter(item => item.category === "Error").length
    },
    files: sortBy(files, ["file"]),
    declarations: sortBy(declarations, ["file", "line", "name"]),
    imports: sortBy(imports, ["from", "line", "specifier"]),
    calls: sortBy(calls, ["from", "line", "symbol", "to"]),
    references: sortBy(references, ["from", "line", "symbol", "to"]),
    test_files: tests.map(item => item.file).sort(),
    test_references: sortBy(testReferences, ["from", "line", "symbol", "to"]),
    diagnostics: sortBy(diagnostics, ["file", "line", "code"])
  };
}

function parseArgs(argv) {
  const args = { root: DEFAULT_ROOT, out: "", failOnDiagnostics: false };
  for (let index = 0; index < argv.length; index += 1) {
    const value = argv[index];
    if (value === "--root" && argv[index + 1]) args.root = path.resolve(argv[++index]);
    else if (value === "--out" && argv[index + 1]) args.out = path.resolve(argv[++index]);
    else if (value === "--fail-on-diagnostics") args.failOnDiagnostics = true;
    else throw new Error("unknown or incomplete argument: " + value);
  }
  return args;
}

async function main(argv = process.argv.slice(2)) {
  const args = parseArgs(argv);
  const report = analyzeProject(args.root);
  const json = JSON.stringify(report, null, 2) + "\n";
  if (args.out) {
    fs.mkdirSync(path.dirname(args.out), { recursive: true });
    fs.writeFileSync(args.out, json, "utf8");
  } else {
    process.stdout.write(json);
  }
  process.stderr.write(JSON.stringify(report.summary) + "\n");
  if (args.failOnDiagnostics && report.summary.error_diagnostics > 0) process.exitCode = 1;
}

const invokedPath = process.argv[1] ? path.resolve(process.argv[1]) : "";
if (invokedPath && pathToFileURL(invokedPath).href === import.meta.url) {
  main().catch(error => {
    process.stderr.write("TYPESCRIPT_SEMANTIC_MAP_ERROR: " + error.message + "\n");
    process.exitCode = 2;
  });
}
