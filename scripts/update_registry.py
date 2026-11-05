#!/usr/bin/env python3
"""把一个插件构建产物写入中央索引。由 Publish Plugin 工作流调用。"""
import argparse, json, os

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--file", default="registry/index.json")
    ap.add_argument("--kind", choices=["ui", "dex"], required=True)
    ap.add_argument("--id", required=True)
    ap.add_argument("--name", required=True)
    ap.add_argument("--version", required=True)
    ap.add_argument("--url", required=True)
    ap.add_argument("--sha256", required=True)
    ap.add_argument("--size", type=int, required=True)
    ap.add_argument("--entry-class", default="")
    ap.add_argument("--api-version", type=int, default=5)
    ap.add_argument("--min-host-api", type=int, default=5)
    ap.add_argument("--description", default="")
    ap.add_argument("--changelog", default="")
    ap.add_argument("--repo", default="")
    ap.add_argument("--author", default="")
    ap.add_argument("--tags", default="")
    ap.add_argument("--updated-at", default="")
    a = ap.parse_args()

    path = a.file
    data = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {}
    data.setdefault("plugins", [])
    data.setdefault("uiPlugins", [])
    key = "uiPlugins" if a.kind == "ui" else "plugins"
    entry = {
        "id": a.id,
        "name": a.name,
        "version": a.version,
        "url": a.url,
        "sha256": a.sha256,
        "size": a.size,
        "minHostApi": a.min_host_api,
        "description": a.description,
        "changelog": a.changelog,
        "repo": a.repo,
        "author": a.author,
        "tags": [t.strip() for t in a.tags.split(",") if t.strip()],
        "updatedAt": a.updated_at,
    }
    if a.kind == "ui":
        entry["entryClass"] = a.entry_class
    else:
        entry["apiVersion"] = a.api_version
        entry["entryClass"] = a.entry_class
    data[key] = [x for x in data[key] if x.get("id") != a.id] + [entry]
    open(path, "w", encoding="utf-8").write(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    print(f"updated {key}: {a.id}@{a.version}")

if __name__ == "__main__":
    main()
