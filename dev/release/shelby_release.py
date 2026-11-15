#!/usr/bin/env python3
"""Verify public signing evidence and prepare pinned update metadata. No private keys are handled here."""
import hashlib
import json
import os
from pathlib import Path
import re


def certificate(text):
    found = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$', text, re.M)
    if len(found) != 1:
        raise ValueError('Exactly one verified APK signer is required')
    return found[0].lower()


def identity(main, addon, pin, previous, previous_permanent, migration_approved):
    pin = pin.replace(':', '').lower()
    if not re.fullmatch('[0-9a-f]{64}', pin):
        raise ValueError('Expected public signing fingerprint is required')
    if certificate(main) != pin or certificate(addon) != pin:
        raise ValueError('APK and bundled add-on must match the configured permanent signing certificate')
    for scheme in ('v1 scheme (JAR signing)', 'v2 scheme (APK Signature Scheme v2)'):
        if f'Verified using {scheme}: true' not in main:
            raise ValueError('Required APK signature scheme missing')
    same = certificate(previous) == pin
    if not same and (previous_permanent or not migration_approved):
        raise ValueError('Signing continuity failed; only an explicitly approved first migration from a temporary release is allowed')
    return same


def badging(text):
    found = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", text)
    if not found:
        raise ValueError('APK package/version metadata is unavailable')
    return found[1], int(found[2]), found[3]


def metadata(apk, info, previous_info, version, tag, repo, pin):
    package, code, actual_version = badging(info)
    _, old_code, _ = badging(previous_info)
    if package != 'dev.tvremote.setup' or version != actual_version or code <= old_code:
        raise ValueError('Stable applicationId and an increased versionCode are required')
    parts = version.split('.')
    if len(parts) != 3 or any(not re.fullmatch(r'\d{1,2}', p) for p in parts):
        raise ValueError('Version must contain three components below 100')
    if code != 100000 + int(parts[0])*10000 + int(parts[1])*100 + int(parts[2]):
        raise ValueError('versionCode formula changed')
    if not re.fullmatch(r'shelby-tv-v' + re.escape(version) + r'-\d+', tag):
        raise ValueError('Release tag does not match APK version')
    data = Path(apk).read_bytes()
    if not 1 <= len(data) <= 64*1024*1024:
        raise ValueError('APK size is invalid')
    return dict(schema=1, packageName=package, version=version, versionCode=code,
                apkUrl=f'https://github.com/{repo}/releases/download/{tag}/shelby-plus-remote-tv.apk',
                sha256=hashlib.sha256(data).hexdigest(), bytes=len(data), certificateSha256=pin.replace(':','').lower())


def main():
    root = Path('distribution')
    temp = Path(os.environ['RUNNER_TEMP'])
    previous = json.loads((temp/'previous-release.json').read_text())
    permanent = any(a['name']=='shelby-update.json' for a in previous['assets'])
    pin = os.environ['SHELBY_RELEASE_CERT_SHA256']
    same = identity((root/'SIGNING-CERTIFICATE.txt').read_text(), (temp/'addon-certificate.txt').read_text(), pin,
                    (temp/'previous-certificate.txt').read_text(), permanent,
                    os.environ.get('SHELBY_INITIAL_MIGRATION_APPROVED') == 'true')
    (root/'UPDATE-COMPATIBILITY.txt').write_text('Previous release: '+previous['tag_name']+'\nPermanent signer SHA-256: '+pin+'\n'+
        ('Same signer; compatible package and higher versionCode verified. Existing app data is retained by Android updates.\n' if same else
         'Approved one-time migration from a temporary signer: in-place update is impossible. A fresh install loses local settings and pairings. Future releases must retain this permanent key.\n'))
    output = metadata(root/'shelby-plus-remote-tv.apk', (root/'APK-INFO.txt').read_text(), (temp/'previous-apk-info.txt').read_text(),
                      Path('app/tvsetup/VERSION').read_text().strip(), os.environ['GITHUB_REF_NAME'], os.environ['GH_REPO'], pin)
    (root/'shelby-update.json').write_text(json.dumps(output, indent=2)+'\n')
    print('Permanent APK/add-on signer, versionCode, migration policy and update metadata: verified')


if __name__ == '__main__':
    main()
