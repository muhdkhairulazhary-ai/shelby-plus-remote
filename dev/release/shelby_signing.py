#!/usr/bin/env python3
"""Fail-closed permanent signing preparation. Prints only public certificate information."""
import base64
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys

REQUIRED = ('SHELBY_TV_KEYSTORE_BASE64', 'SHELBY_STORE_PASSWORD', 'SHELBY_KEY_PASSWORD',
            'SHELBY_KEY_ALIAS', 'SHELBY_RELEASE_CERT_SHA256')


def prepare(env=os.environ):
    missing = [name for name in REQUIRED if not env.get(name)]
    if missing:
        raise ValueError('Persistent signing is not configured: ' + ', '.join(missing))
    pin = env['SHELBY_RELEASE_CERT_SHA256'].replace(':', '').lower()
    if not re.fullmatch('[0-9a-f]{64}', pin):
        raise ValueError('Expected signing certificate SHA-256 is invalid')
    path = Path(env['RUNNER_TEMP']) / 'shelby-release.jks'
    try:
        raw = base64.b64decode(env['SHELBY_TV_KEYSTORE_BASE64'], validate=True)
        if not 100 <= len(raw) <= 1024 * 1024:
            raise ValueError('Invalid keystore size')
        with path.open('wb') as file:
            os.chmod(path, 0o600)
            file.write(raw)
        result = subprocess.run(['keytool', '-exportcert', '-keystore', str(path), '-alias', env['SHELBY_KEY_ALIAS'],
                                 '-storepass:env', 'SHELBY_STORE_PASSWORD'], env=dict(env), capture_output=True)
        if result.returncode != 0:
            raise ValueError('Unable to open signing certificate; check the protected signing configuration')
        actual = hashlib.sha256(result.stdout).hexdigest()
        if actual != pin:
            raise ValueError('Signing certificate does not match the expected public fingerprint')
        with Path(env['GITHUB_ENV']).open('a') as output:
            output.write(f'SHELBY_KEYSTORE_PATH={path}\nSHELBY_RELEASE_CERT_SHA256={pin}\nSIGNING_MODE=persistent\n')
        print('Verified permanent signing certificate SHA-256:', actual)
    except Exception:
        path.unlink(missing_ok=True)
        raise


if __name__ == '__main__':
    try:
        prepare()
    except Exception as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
