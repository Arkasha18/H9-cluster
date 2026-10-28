#!/usr/bin/env python3
"""Synchronize public design ZIPs without changing the legacy Demo application."""

from __future__ import annotations

import argparse
import io
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

from build_forum_design_template import ROOT, TEMPLATE_DIR, TEMPLATE_FILES

DOCS = ROOT / "docs"
TEMPLATE_ZIP = DOCS / "H9_Cluster_Neutral_Design_Template_1920x720.zip"
DEMO_ZIP = DOCS / "H9_Cluster_Dashboard_Demo_Base_Source_v3.zip"
KIT_ZIP = DOCS / "H9_Cluster_Codex_Forum_Kit_v3.zip"
DEMO_PREFIX = "H9_Cluster_Dashboard_Demo_v8/"
FIXED_DATE = (2026, 10, 28, 0, 0, 0)


def read_archive(path: Path) -> tuple[dict[str, bytes], dict[str, int]]:
    with ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise ValueError(f"Invalid ZIP: {path}")
        files = [entry for entry in archive.infolist() if not entry.is_dir()]
        if len({entry.filename for entry in files}) != len(files):
            raise ValueError(f"Duplicate ZIP entries: {path}")
        return ({entry.filename: archive.read(entry) for entry in files},
                {entry.filename: entry.external_attr for entry in files})


def sync_archive(path: Path, expected: dict[str, bytes], check: bool,
                 attributes: dict[str, int] | None = None) -> None:
    if check:
        actual, _ = read_archive(path)
        changed = sorted(name for name in actual.keys() | expected.keys()
                         if actual.get(name) != expected.get(name))
        if changed:
            raise ValueError(f"Stale {path.name}: {', '.join(changed)}")
        return
    buffer = io.BytesIO()
    with ZipFile(buffer, "w", compression=ZIP_DEFLATED, compresslevel=9) as archive:
        for name, content in sorted(expected.items()):
            info = ZipInfo(name, date_time=FIXED_DATE)
            info.create_system = 3
            info.external_attr = (attributes or {}).get(name, 0o100644 << 16)
            info.compress_type = ZIP_DEFLATED
            archive.writestr(info, content, compresslevel=9)
    path.write_bytes(buffer.getvalue())


def package(check: bool = False) -> None:
    # Explicit allowlist excludes ignored drafts, APKs and any local/private files.
    template = {name: (TEMPLATE_DIR / name).read_bytes() for name in TEMPLATE_FILES}
    template_prefix = f"docs/{TEMPLATE_DIR.name}/"
    sync_archive(TEMPLATE_ZIP, {template_prefix + name: data for name, data in template.items()}, check)

    # The legacy app is deliberately not reconstructed or upgraded here. Preserve
    # every non-document entry byte-for-byte, including wrapper and font licences.
    demo, attributes = read_archive(DEMO_ZIP)
    updates = {
        "README_RU.md": ROOT / "tools/assets/demo_legacy_readme_ru.md",
        "THREE_STAGE_WORKFLOW_RU.md": DOCS / "FORUM_DESIGN_PROMPTS_RU.md",
        "CODEX_LOCAL_SETUP_RU.md": DOCS / "CODEX_LOCAL_SETUP_RU.md",
    }
    for name, source in updates.items():
        if DEMO_PREFIX + name not in demo:
            raise ValueError(f"Missing legacy Demo entry: {name}")
        demo[DEMO_PREFIX + name] = source.read_bytes()
    sync_archive(DEMO_ZIP, demo, check, attributes)

    kit = {name: data for name, data in template.items() if name != "README_RU.txt"}
    kit["TEMPLATE_README_RU.txt"] = template["README_RU.txt"]
    for name in ("CODEX_LOCAL_SETUP_RU.md", "FORUM_DESIGN_PROMPTS_RU.md"):
        kit[name] = (DOCS / name).read_bytes()
    kit[TEMPLATE_ZIP.name] = TEMPLATE_ZIP.read_bytes()
    kit[DEMO_ZIP.name] = DEMO_ZIP.read_bytes()
    sync_archive(KIT_ZIP, kit, check)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="compare uncompressed contents without writing")
    args = parser.parse_args()
    package(args.check)
    print("Design archives are synchronized." if args.check else "Updated all three design archives.")


if __name__ == "__main__":
    main()
