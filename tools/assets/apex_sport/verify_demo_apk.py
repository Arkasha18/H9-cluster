"""Inspect the built Demo payload without printing secrets or installing the APK."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import tempfile
import zipfile
from pathlib import Path


def verify(apk: Path, sdk: Path, output: Path) -> dict:
    tools = sdk / "build-tools" / "35.0.0"
    dexdump = tools / ("dexdump.exe" if (tools / "dexdump.exe").exists() else "dexdump")
    assert dexdump.is_file(), "Android dexdump is required"
    forbidden_payloads = (
        re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----[\r\n]+[A-Za-z0-9+/=\r\n]{64,}-----END (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
        re.compile(rb"PuTTY-User-Key-File-[^\r\n]{1,80}[\r\n]+Encryption:[^\x00]{30,20000}Private-Lines: [1-9][0-9]*[\r\n]+[A-Za-z0-9+/=\r\n]{64,}"),
    )
    with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory(prefix="apex-apk-") as temp:
        temp_path = Path(temp).resolve()
        assert temp_path.parent == Path(tempfile.gettempdir()).resolve()
        assert temp_path.name.startswith("apex-apk-")
        names = archive.namelist()
        libraries = [name for name in names if name.lower().endswith(".so")]
        assert not libraries, "Demo contains native shared libraries"
        assert not any(name.lower().endswith((".jks", ".keystore", ".p12", ".pfx", ".pem", ".key", "/.env")) for name in names), "Demo contains a key/config artifact"
        prefix = "assets/dashboard/skins/apexsport/"
        assets = sorted(name[len(prefix):] for name in names if name.startswith(prefix) and not name.endswith("/"))
        assert assets == ["NotoSans.ttf", "chrome.png", "map_black_gradient.png"], "Unexpected APEX runtime asset, possibly a review image or technical mask"
        for name in names:
            if name.endswith("/"):
                continue
            payload = archive.read(name)
            # SSH libraries legitimately contain parser marker strings. Require
            # actual multiline key material, not a header constant in DEX.
            assert not any(pattern.search(payload) for pattern in forbidden_payloads), "Private-key payload found"
        dex_names = [name for name in names if re.fullmatch(r"classes\d*\.dex", name)]
        assert dex_names, "No DEX in Demo"
        app_blocks = []
        for name in dex_names:
            dex = temp_path / name
            dex.write_bytes(archive.read(name))
            result = subprocess.run([str(dexdump), str(dex)], capture_output=True, check=True)
            dump = result.stdout.decode("utf-8", errors="replace")
            app_blocks.extend(block for block in re.split(r"(?=Class #\d+\s+-)", dump)
                              if "Class descriptor  : 'Lnet/adminrunet/h9cluster/BuildConfig;'" in block)
        assert len(app_blocks) == 1, "Expected exactly one application BuildConfig"
        block = app_blocks[0]

        def static_string(field: str) -> str:
            match = re.search(rf"name\s+: '{re.escape(field)}'.*?value\s+: \"(.*?)\"", block, re.S)
            assert match, f"Missing BuildConfig field {field}"
            return match.group(1)

        assert static_string("TBOX_SECRET_MASK") == "", "Demo secret mask is not empty"
        assert static_string("TBOX_SECRET_DATA") == "", "Demo secret data is not empty"
        assert static_string("APPLICATION_ID") == "net.adminrunet.h9cluster.demo", "Wrong application ID"
        demo_flag = re.search(r"name\s+: 'DEMO_MODE'.*?value\s+: (true|false)", block, re.S)
        assert demo_flag and demo_flag.group(1) == "true", "Not a Demo build"
        report = {
            "apk": apk.name, "bytes": apk.stat().st_size,
            "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
            "applicationId": static_string("APPLICATION_ID"),
            "versionName": static_string("VERSION_NAME"),
            "demoMode": True, "sharedLibraryCount": len(libraries),
            "tboxSecretMaskEmpty": True, "tboxSecretDataEmpty": True,
            "privateKeyArtifacts": 0, "apexAssets": assets,
            "basis": "Actual ZIP entries and disassembled DEX BuildConfig; no secret values printed",
        }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(verify(args.apk, args.sdk, args.report)))
