#!/bin/bash
# Explicit permanent signing is required unless SHELBY_TEST_BUILD=1 is set for validation only.
set -euo pipefail
cd "$(dirname "$0")"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
if [[ "${SHELBY_TEST_BUILD:-0}" == 1 ]]; then
  signing_tmp="$(mktemp -d)"
  trap 'rm -rf "$signing_tmp"' EXIT
  export SHELBY_KEYSTORE_PATH="$signing_tmp/test-signing.jks"
  export SHELBY_STORE_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_hex(32))')"
  export SHELBY_KEY_PASSWORD="$SHELBY_STORE_PASSWORD"
  export SHELBY_KEY_ALIAS=shelby-test
  export SHELBY_RELEASE_CERT_SHA256=""
  keytool -genkeypair -storetype JKS -keystore "$SHELBY_KEYSTORE_PATH" \
    -storepass:env SHELBY_STORE_PASSWORD -keypass:env SHELBY_KEY_PASSWORD -alias "$SHELBY_KEY_ALIAS" \
    -keyalg RSA -keysize 3072 -validity 30 -dname 'CN=Shelby validation only' >/dev/null 2>&1
  echo 'Validation-only signing: this APK is not a release and cannot provide compatible updates.' >&2
else
  for name in SHELBY_KEYSTORE_PATH SHELBY_STORE_PASSWORD SHELBY_KEY_PASSWORD SHELBY_KEY_ALIAS SHELBY_RELEASE_CERT_SHA256; do
    [[ -n "${!name:-}" ]] || { echo "Missing $name; release build refused. See SIGNING.md." >&2; exit 1; }
  done
  [[ -f "$SHELBY_KEYSTORE_PATH" ]] || { echo 'Signing keystore is unavailable.' >&2; exit 1; }
fi
../gradle/gradlew -q -p ../gradle :tvsetup:assembleRelease
cp build/outputs/apk/release/tvsetup-release.apk build/tv-setup.apk
