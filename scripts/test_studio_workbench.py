#!/usr/bin/env python3
"""Static structural and JavaScript syntax checks for the offline Studio workbench."""
from __future__ import annotations

import re
import subprocess
import tempfile
from collections import Counter
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "studio" / "index.html"

class StudioParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.ids: list[str] = []
        self.script_parts: list[str] = []
        self.in_script = False
        self.external_resources: list[str] = []
        self.has_viewport = False

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attrs_map = dict(attrs)
        if attrs_map.get("id"):
            self.ids.append(str(attrs_map["id"]))
        if tag == "meta" and attrs_map.get("name") == "viewport":
            self.has_viewport = True
        if tag == "script":
            self.in_script = True
            if attrs_map.get("src"):
                self.external_resources.append("script src=" + str(attrs_map["src"]))
        if tag == "link" and attrs_map.get("href", "").startswith(("http://", "https://")):
            self.external_resources.append("stylesheet/link " + str(attrs_map["href"]))

    def handle_endtag(self, tag: str) -> None:
        if tag == "script":
            self.in_script = False

    def handle_data(self, data: str) -> None:
        if self.in_script:
            self.script_parts.append(data)

def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit("FAIL: " + message)

def main() -> int:
    require(PAGE.is_file(), "studio/index.html is missing")
    source = PAGE.read_text(encoding="utf-8")
    parser = StudioParser()
    parser.feed(source)
    parser.close()

    duplicates = [name for name, count in Counter(parser.ids).items() if count > 1]
    require(not duplicates, "duplicate HTML ids: " + ", ".join(duplicates))
    require(parser.has_viewport, "mobile viewport metadata is missing")
    require(not parser.external_resources, "workbench must not depend on external resources: " + ", ".join(parser.external_resources))

    required_ids = {
        "page-home", "page-compare", "page-motion-audit", "page-palette", "page-art", "page-space",
        "page-motion", "page-logs", "page-report", "compareCanvas", "artCanvas",
        "tesseract", "logInput", "reportFile", "exportSession", "channelThreshold",
        "maxChangedPercent", "exportParityReport", "videoWebFile", "videoNativeFile",
        "videoAuditChart", "analyzeEffectsBtn", "exportVideoAudit",
    }
    missing_ids = sorted(required_ids - set(parser.ids))
    require(not missing_ids, "missing required Studio sections/tools: " + ", ".join(missing_ids))

    script = "\n".join(parser.script_parts)
    require(script.strip(), "inline JavaScript was not found")
    literal_refs = set(re.findall(r'\$\("([A-Za-z0-9_-]+)"\)', script))
    missing_refs = sorted(literal_refs - set(parser.ids))
    require(not missing_refs, "JavaScript references unknown element ids: " + ", ".join(missing_refs))

    must_contain = [
        "Comparar pixels", "Prancheta 720 × 440", "Tesserato", "Linha do tempo",
        "Logs / saída do build", "Exportar JSON", "Erro médio absoluto",
        "Nenhuma alteração automática no app", "Aprovação rigorosa", "Analisador de efeitos",
        "Gravação de referência", "Exigir flashes nesta captura", "Movimento entre quadros",
    ]
    for marker in must_contain:
        require(marker.casefold() in source.casefold(), "missing workbench feature marker: " + marker)

    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".js", delete=False) as js_file:
        js_file.write(script)
        js_path = Path(js_file.name)
    try:
        result = subprocess.run(
            ["node", "--check", str(js_path)],
            capture_output=True,
            text=True,
            timeout=20,
            check=False,
        )
        require(result.returncode == 0, "JavaScript syntax error:\n" + result.stderr)
    finally:
        js_path.unlink(missing_ok=True)

    print("PASS: offline Studio HTML structure, unique IDs, internal DOM references, feature sections and JavaScript syntax.")
    print("PASS: no external script or stylesheet dependencies.")
    print(f"Studio size: {len(source.encode('utf-8'))} bytes; {len(parser.ids)} unique element IDs.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
