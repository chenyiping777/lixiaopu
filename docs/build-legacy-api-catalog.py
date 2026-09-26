"""Convert ChestnutShopWX's HTTP examples into data for the static API pages.

Usage: python docs/build-legacy-api-catalog.py SOURCE.md OUTPUT.js
"""

import json
import re
import sys
from pathlib import Path
from urllib.parse import parse_qsl


GROUPS = {
    "account": set(range(1, 11)),
    "products": set(range(11, 25)) | {33, 38},
    "reviews": set(range(25, 33)),
    "admin": set(range(34, 38)) | set(range(62, 68)) | set(range(69, 73)),
    "cart": set(range(39, 48)),
    "orders": set(range(48, 61)),
    "content": {61, 68, 77},
    "service": set(range(73, 77)) | {78},
}


def parse(source: str):
    headings = list(re.finditer(r"^### \d{2}\. ", source, flags=re.M))
    chunks = [source[heading.start():headings[index + 1].start() if index + 1 < len(headings) else len(source)]
              for index, heading in enumerate(headings)]
    operations = []
    for chunk in chunks:
        heading = re.match(r"^### (\d{2})\. (.+)$", chunk, flags=re.M)
        if not heading:
            continue
        number = int(heading.group(1))
        request = re.search(r"^(GET|POST|PUT|PATCH|DELETE) \{\{baseUrl\}\}([^\s]+)$", chunk, flags=re.M)
        if not request:
            raise ValueError(f"No request for #{number}")
        route = request.group(2)
        path, _, query = route.partition("?")
        tail = chunk[request.end():]
        body = ""
        separator = re.search(r"\n\s*\n", tail)
        if separator:
            body = tail[separator.end():].strip()
        if number == 78:
            body = ""
        notes = [line[2:].strip() for line in chunk.splitlines() if line.startswith("# ")]
        groups = [name for name, numbers in GROUPS.items() if number in numbers]
        if len(groups) != 1:
            raise ValueError(f"Expected one group for #{number}: {groups}")
        operations.append({
            "id": number,
            "title": heading.group(2).strip(),
            "group": groups[0],
            "method": request.group(1),
            "path": path,
            "params": parse_qsl(query, keep_blank_values=True),
            "body": body,
            "auth": "Authorization: {{accessToken}}" in chunk and "可选登录" not in heading.group(2),
            "optionalAuth": "可选登录" in heading.group(2),
            "notes": notes,
            "upload": number == 78,
            "browserUnsupported": number == 58,
        })
    if [operation["id"] for operation in operations] != list(range(1, 79)):
        raise ValueError(f"Expected exactly the 78 documented requests, got {[operation['id'] for operation in operations]}")
    return operations


if __name__ == "__main__":
    source_path, output_path = map(Path, sys.argv[1:3])
    data = parse(source_path.read_text(encoding="utf-8"))
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text("window.LEGACY_API_CATALOG = " + json.dumps(data, ensure_ascii=False, indent=2) + ";\n", encoding="utf-8")
    print(f"Wrote {len(data)} requests to {output_path}")
