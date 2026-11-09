"""Verify the exact local car-preview APK without printing signing/TBOX secrets."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

EXPECTED_CERT = 'e7c84ad0463e88361afc4510a16b18774d4ccc6c0934ed866c93a4902940d610'
EXPECTED_CALIBRATION = 'c21978f23e6bcbcdbb255c8ee3b0599ee4494310741eeb84deb1298ff0957260'

def run(command):
    result = subprocess.run([str(x) for x in command], capture_output=True, text=True, encoding='utf-8', errors='replace')
    if result.returncode:
        raise RuntimeError(f'{Path(command[0]).name} failed ({result.returncode})')
    return result.stdout

def require(condition, message):
    if not condition:
        raise RuntimeError(message)

def config_from_apk(apk, build_tools):
    found = []
    with zipfile.ZipFile(apk) as package, tempfile.TemporaryDirectory(prefix='h9-dex-verify-') as directory:
        for name in package.namelist():
            if not re.fullmatch(r'classes\d*\.dex', name):
                continue
            dex = Path(directory) / name
            dex.write_bytes(package.read(name))
            dump = run([build_tools / 'dexdump.exe', dex])
            for block in re.split(r'(?=Class #\d+\s+-)', dump):
                if "Class descriptor  : 'Lnet/adminrunet/h9cluster/BuildConfig;'" in block:
                    found.append(block)
    require(len(found) == 1, 'Expected exactly one APK BuildConfig')
    fields = {}
    for name in ('APPLICATION_ID', 'BUILD_TYPE', 'VERSION_NAME', 'VERSION_CODE', 'DEMO_MODE', 'DEBUG', 'TBOX_SECRET_MASK', 'TBOX_SECRET_DATA'):
        match = re.search(r"name\s+: '" + name + r"'(?:(?!\n\s+name\s+:).)*?value\s+: ([^\r\n]+)", found[0], re.S)
        require(match is not None, 'Missing APK BuildConfig field: ' + name)
        fields[name] = match.group(1).strip().strip('"')
    return fields

def java_property(path, key):
    for line in path.read_text(encoding='utf-8-sig').splitlines():
        match = re.match(r'^\s*' + re.escape(key) + r'\s*=\s*(.*)$', line)
        if match:
            raw = match.group(1)
            return re.sub(r'\\u([0-9a-fA-F]{4})|\\(.)', lambda m: chr(int(m[1], 16)) if m[1] else {'t':'\t','n':'\n','r':'\r','f':'\f'}.get(m[2], m[2]), raw)
    raise RuntimeError('Required local build input is missing: ' + key)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--demo-apk', type=Path)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--properties', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--version-code', type=int, required=True)
    args = parser.parse_args()
    bt = args.sdk / 'build-tools/35.0.0'
    java = Path(os.environ['JAVA_HOME']) / 'bin/java.exe'
    signature = run([java, '-jar', bt / 'lib/apksigner.jar', 'verify', '--Werr', '--verbose', '--print-certs', args.apk])
    cert = re.search(r'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]+)', signature)
    require(cert is not None and cert[1].lower() == EXPECTED_CERT, 'APK signer differs from the production certificate')
    require('Number of signers: 1' in signature, 'Expected one production signer')
    run([bt / 'zipalign.exe', '-c', '-P', '16', '4', args.apk])
    # Windows aapt cannot open all Unicode paths; inspect an identical copy
    # under the ASCII temporary directory while hashing the delivered file.
    with tempfile.TemporaryDirectory(prefix='h9-apk-metadata-') as directory:
        metadata_apk = Path(directory) / 'package.apk'
        shutil.copyfile(args.apk, metadata_apk)
        badging = run([bt / 'aapt.exe', 'dump', 'badging', metadata_apk])
    require("name='net.adminrunet.h9cluster'" in badging, 'Wrong applicationId')
    require(f"versionCode='{args.version_code}'" in badging and f"versionName='{args.version}'" in badging, 'Unexpected APK version')
    require("sdkVersion:'28'" in badging and "targetSdkVersion:'28'" in badging, 'SDK compatibility changed')
    require('application-debuggable' not in badging, 'Release must not be debuggable')
    config = config_from_apk(args.apk, bt)
    require(config['DEMO_MODE'] == 'false' and config['DEBUG'] == 'false', 'Real-data Release flags are not correct')
    require(config['BUILD_TYPE'] == 'release', 'Wrong APK build type')
    mask = base64.b64decode(config['TBOX_SECRET_MASK'])
    data = base64.b64decode(config['TBOX_SECRET_DATA'])
    require(len(mask) > 0 and len(mask) == len(data), 'Missing TBOX build input')
    configured = java_property(args.properties, 'H9_TBOX_PASSWORD').encode('utf-8')
    decoded = bytes(a ^ b for a, b in zip(mask, data))
    require(decoded == configured, 'TBOX material does not match the configured production input')
    with zipfile.ZipFile(args.apk) as package:
        names = package.namelist()
        abis = sorted({name.split('/')[1] for name in names if re.fullmatch(r'lib/[^/]+/[^/]+\.so', name)})
        require(abis == ['arm64-v8a'], 'Wrong native ABI payload')
        prefix = 'assets/dashboard/skins/porcelainfluid/'
        for name in ('chrome.png','pearl-material.png','map_black_gradient.png','PFDisplay.ttf','PFData.ttf','PFLabel.ttf'):
            require(prefix + name in names, 'Missing theme asset: ' + name)
        require(hashlib.sha256(package.read(prefix+'map_black_gradient.png')).hexdigest() == EXPECTED_CALIBRATION, 'Calibrated map asset changed')
        require(not any('demo_system_icons' in name for name in names), 'Demo lamp assets leaked into Release')
        require(not any(re.search(r'/0[23567]_.*\.png$', name) for name in names), 'Validation masks leaked into APK')
        require(not any(configured in package.read(name) for name in names if re.fullmatch(r'classes\d*\.dex',name)), 'Plain TBOX input found in DEX')
    demo_verified = False
    if args.demo_apk:
        demo = config_from_apk(args.demo_apk, bt)
        require(demo['DEMO_MODE'] == 'true', 'Companion Demo build has wrong flag')
        require(not demo['TBOX_SECRET_MASK'] and not demo['TBOX_SECRET_DATA'], 'Demo contains production TBOX material')
        demo_verified = True
    report = {
        'apk': str(args.apk.resolve()), 'bytes': args.apk.stat().st_size,
        'sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(),
        'applicationId': config['APPLICATION_ID'], 'versionName': config['VERSION_NAME'],
        'versionCode': int(config['VERSION_CODE']), 'variant': 'release', 'DEMO_MODE': False,
        'debuggable': False, 'minSdk':28, 'targetSdk':28, 'abis':abis,
        'certificate_sha256':cert[1].lower(), 'production_certificate_matches':True,
        'zipalign_16k_passed':True, 'tbox_material_matches_configured_input':True,
        'plain_tbox_input_in_dex':False, 'calibration_sha256':EXPECTED_CALIBRATION,
        'demo_secret_exclusion_verified':demo_verified,
        'vehicle_run': 'not performed; APK supplied for user vehicle validation',
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False))

if __name__ == '__main__':
    main()
