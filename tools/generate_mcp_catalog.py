"""Refresh the explicit MCP module allowlist and runtime action reference from repository sources."""
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/zszl/zszlScriptMod"
OUTPUT = ROOT / "src/main/resources/mcp"
OUTPUT.mkdir(parents=True, exist_ok=True)
modules = []
for path in sorted(JAVA.rglob("*.java")):
    if "shadowbaritone" in path.parts or "mcp" in path.parts:
        continue
    source = path.read_text(encoding="utf-8-sig")
    methods = re.findall(r"public\s+static\s+(?:synchronized\s+)?void\s+((?:load|reload)[A-Za-z]*|save[A-Za-z]*(?:Config|Configs|Settings|Warehouses)|save)\s*\(\s*\)", source)
    if not methods:
        continue
    package = re.search(r"package\s+([\w.]+);", source).group(1)
    modules.append({"id": path.stem, "class": package + "." + path.stem,
                    "reload": [x for x in methods if x.startswith(("load", "reload"))],
                    "save": [x for x in methods if x.startswith("save")]})
modules.append({"id": "PathSequenceManager", "class": "com.zszl.zszlScriptMod.path.PathSequenceManager",
                "reload": ["initializePathSequences"], "save": []})
(OUTPUT / "modules.json").write_text(json.dumps(modules, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

# Keep the actual runtime clauses, including defaults, enum handling and nested
# key accesses. This supplements editor schemas without inventing parameter types.
catalog = (JAVA / "gui/path/GuiActionEditor/library/ActionDisplayCatalog.java").read_text(encoding="utf-8")
actions = {key: [] for key in re.findall(r'ACTION_DISPLAY_KEYS.put\("([^"]+)"', catalog)}
for relative in ["path/PathSequenceManager.java", "path/LegacyActionRuntime.java",
                 "path/PathSequenceEventListener.java", "path/validation/PathConfigValidator.java"]:
    source = (JAVA / relative).read_text(encoding="utf-8-sig")
    cases = list(re.finditer(r'case\s+"([^"]+)"\s*:', source))
    for i, case in enumerate(cases):
        key = case.group(1)
        if key not in actions:
            continue
        end = cases[i + 1].start() if i + 1 < len(cases) else min(len(source), case.end() + 4000)
        clause = source[case.end():end]
        # Consecutive case labels share the following clause.
        j = i + 1
        while not clause.strip() and j < len(cases):
            end = cases[j + 1].start() if j + 1 < len(cases) else min(len(source), cases[j].end() + 4000)
            clause = source[cases[j].end():end]
            j += 1
        keys = sorted(set(re.findall(r'(?:params|actionParams)\.(?:get|has|getAsJsonObject|getAsJsonArray)\("([^"]+)"', clause)))
        if keys:
            actions[key].append({"source": relative, "line": source.count("\n", 0, case.start()) + 1,
                                 "keys": keys, "implementation": clause.strip()[:16000]})
(OUTPUT / "actions.json").write_text(json.dumps(actions, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"MCP catalogue: {len(modules)} modules, {len(actions)} action IDs")
