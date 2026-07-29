#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 path/to/app-demo.apk" >&2
  exit 2
fi

apk_path=$1
if [[ ! -f "$apk_path" ]]; then
  echo "Demo APK not found" >&2
  exit 2
fi

secret_value=${H9_TBOX_PASSWORD:-}
if [[ -n ${H9_TBOX_PASSWORD_FILE:-} ]]; then
  if [[ ! -f "$H9_TBOX_PASSWORD_FILE" ]]; then
    echo "TBOX password file not found" >&2
    exit 2
  fi
  IFS= read -r secret_value < "$H9_TBOX_PASSWORD_FILE" || true
elif [[ -n ${H9_TBOX_PASSWORD_PROPERTIES_FILE:-} ]]; then
  if [[ ! -f "$H9_TBOX_PASSWORD_PROPERTIES_FILE" ]]; then
    echo "Gradle properties file not found" >&2
    exit 2
  fi
  secret_value=$(
    awk -F= '
      /^[[:space:]]*H9_TBOX_PASSWORD[[:space:]]*=/ {
        sub(/^[^=]*=/, "")
        sub(/^[[:space:]]+/, "")
        sub(/[[:space:]]+$/, "")
        print
        exit
      }
    ' "$H9_TBOX_PASSWORD_PROPERTIES_FILE"
  )
fi

if [[ -n "$secret_value" ]]; then
  DEMO_APK_PATH="$apk_path" \
    DEMO_SECRET_CHECK_VALUE="$secret_value" \
    python3 - <<'PY'
import os
from pathlib import Path

apk = Path(os.environ["DEMO_APK_PATH"]).read_bytes()
secret = os.environ["DEMO_SECRET_CHECK_VALUE"].encode("utf-8")
if secret in apk:
    raise SystemExit("Demo APK contains the configured plain TBOX password")
PY
fi

android_sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
if [[ -z "$android_sdk_root" ]]; then
  echo "ANDROID_SDK_ROOT or ANDROID_HOME must be set" >&2
  exit 2
fi

dexdump_path=
while IFS= read -r candidate; do
  dexdump_path=$candidate
done < <(find "$android_sdk_root/build-tools" -maxdepth 2 -type f -name dexdump | sort -V)

if [[ -z "$dexdump_path" ]]; then
  echo "Android dexdump tool not found" >&2
  exit 2
fi

temp_dir=$(mktemp -d "${TMPDIR:-/tmp}/h9-demo-secret-check.XXXXXX")
trap 'rm -rf "$temp_dir"' EXIT
chmod 700 "$temp_dir"

unzip -q "$apk_path" 'classes*.dex' -d "$temp_dir"
dump_path="$temp_dir/dexdump.txt"
touch "$dump_path"
chmod 600 "$dump_path"

found_dex=false
for dex_path in "$temp_dir"/classes*.dex; do
  if [[ -f "$dex_path" ]]; then
    found_dex=true
    "$dexdump_path" "$dex_path" >> "$dump_path"
  fi
done
if [[ "$found_dex" != true ]]; then
  echo "Demo APK contains no DEX files" >&2
  exit 1
fi

DEMO_DEXDUMP_PATH="$dump_path" python3 - <<'PY'
import os
import re
from pathlib import Path

dump = Path(os.environ["DEMO_DEXDUMP_PATH"]).read_text(
    encoding="utf-8",
    errors="replace",
)
build_config_blocks = [
    block
    for block in re.split(r"(?=Class #\d+\s+-)", dump)
    if "Class descriptor  : 'Lnet/adminrunet/h9cluster/BuildConfig;'" in block
]
if len(build_config_blocks) != 1:
    raise SystemExit("Unable to identify exactly one app BuildConfig in Demo APK")

block = build_config_blocks[0]

def static_string(field_name: str) -> str:
    match = re.search(
        rf"name\s+: '{re.escape(field_name)}'.*?"
        r'value\s+: "(.*?)"',
        block,
        flags=re.DOTALL,
    )
    if match is None:
        raise SystemExit(f"Demo BuildConfig field {field_name} not found")
    return match.group(1)

mask = static_string("TBOX_SECRET_MASK")
data = static_string("TBOX_SECRET_DATA")
if mask or data:
    raise SystemExit("Demo APK contains non-empty TBOX secret material")
PY

echo "Demo APK secret verification passed"
