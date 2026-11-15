# Shelby+ Remote

**Your TV. Your Control.**

Phase 1 fork of [Hoang Tran's TV Remote](https://github.com/HoangTran0410/tv-remote), copyright © 2026 Hoang Tran, under the original [MIT license](LICENSE). The notice is also bundled in the TV APK. Upstream documentation below retains upstream product names and download links; those links install upstream builds.

The TV app and the page it serves are branded Shelby+ Remote. The shared browser page uses a black and charcoal design with white text, red accents, larger D-pad and touch targets, safe-area spacing and a wrapping keyboard form. The phone and desktop apps retain their existing presentation. Package `dev.tvremote.setup`, pairing, authentication, scopes, port 8765 and control handlers remain unchanged. Phase 1 does not change the TV launcher, system packages or optimization settings.

## Install using an iPhone (no computer)

1. For a direct APK, open this fork’s Releases page and choose `shelby-plus-remote-tv.apk`. Once a release is published, Android TV Downloader can use `https://github.com/muhdkhairulazhary-ai/shelby-plus-remote/releases/latest/download/shelby-plus-remote-tv.apk`. No numeric Downloader code is assigned. For Actions test builds, in Safari, sign in to GitHub and open this fork's **Actions → Shelby+ Remote TV APK**. Choose a successful run for `feat/shelby-plus-phase-1` (or the merged main branch). Download **shelby-plus-remote-tv-apk** at the bottom. Safari saves the ZIP in Files → Downloads; tap it to extract `shelby-plus-remote-tv.apk`. The ZIP also contains the MIT notice and SHA256SUMS.
2. Transfer the APK from iPhone Files to the TV using a local file-transfer app that exposes a browser upload page, or a cloud drive accessible from the TV. Open the APK with the TV's file manager. Alternatively host the APK at a download URL you control and enter that URL in the TV's Downloader app using its physical remote. GitHub artifact pages require login and are not direct Downloader APK URLs. An APK installs on the TV, not on iOS.
3. Allow installation from the chosen file manager/Downloader when Android asks. Install and open **Shelby+ Remote** using the TV's physical remote. This first installation requires a way to select the installer on the TV; an unpaired iPhone alone cannot bootstrap control.
4. Follow the existing on-TV connection instructions. If required, enable Developer options and USB/ADB debugging, and accept only the matching debugging authorization. Some Android 11 TVs expose only Wireless debugging; upstream's flow may require an additional ADB client to establish the initial connection. An iPhone-only setup on PRISM+ has **not** been verified.
5. Put iPhone and TV on the same trusted Wi-Fi. In Safari enter the address shown on the TV, for example `http://192.168.1.50:8765`. Request the code on the TV and enter its six digits. Keep port 8765 on your local network.
6. Use the D-pad/OK, Home, Back and volume controls. Tap Keyboard to open the existing text interface; typing requires the existing ADB connection and remains dependent on the focused TV app. Safari → Share → Add to Home Screen can save the remote.

Actions test APKs use a new temporary signing key per run, shared with the bundled keys add-on. They cannot update an upstream installation or a previous CI build with a different signing key. Removing an earlier installation loses its local settings/pairings; retain it if that is undesirable. Production updates require a private, persistent signing key, outside git. This workflow creates downloadable test artifacts, not signed upstream releases, and does not change existing automatic update or signature-verification behavior.

## GitHub Release distribution

Push a tag `shelby-tv-v<APK version>-<distribution number>` from this branch, for example `shelby-tv-v0.52.0-1`. `.github/workflows/tv-release.yml` builds the existing TV APK, runs checks, verifies the app and bundled add-on signatures, stages a draft release, then publishes it with the APK and original MIT notice. Published release assets are not overwritten. This fork-specific distribution does not run the upstream multi-app release script or alter app version files.

The tag-specific URL is `https://github.com/muhdkhairulazhary-ai/shelby-plus-remote/releases/download/<tag>/shelby-plus-remote-tv.apk`. The `releases/latest/download/shelby-plus-remote-tv.apk` URL follows the latest release, so future latest releases must retain this filename. These URLs work only after their assets have been published.

For consistent release signatures, a repository administrator can configure the private Actions secret `SHELBY_TV_KEYSTORE_BASE64`: base64 of a securely backed-up keystore compatible with the existing build (alias `tvsetup`, store and key passwords `tvsetup`). Without that secret the workflow explicitly labels the release **temporary** and generates a new key. Never put a keystore in git or in release assets. The current GitHub connection cannot manage Actions secrets (HTTP 403).

Release APKs are checked with Android `apksigner` for v1/v2 signatures, and the bundled add-on must have the same signer. `SIGNING-CERTIFICATE.txt` contains only public certificate information. Update compatibility requires the same signer and an equal or higher versionCode; a different signer requires a fresh installation and may require removing the old keys add-on too. Temporary-key releases do not support reproducible update signatures across builds. Existing upstream automatic-update signature checks and sources are unchanged; these releases do not supply upstream Ed25519 update signatures.

## Validation

Target: PRISM+ Android 11 Google TV and iPhone Safari. These are targets, **not verified device compatibility**. No real TV or physical iPhone was available during implementation; keyboard input has not been tested on a real TV. CI tests and an APK build do not establish device compatibility.

Build locally with JDK 17 and Android SDK 35: `app/tvsetup/build.sh`. Existing output remains `app/tvsetup/build/tv-setup.apk`; Actions packages it as `shelby-plus-remote-tv.apk`.

## Official TV artwork (0.52.1)

The launcher icon uses the exact root upload `0EC7D146-3DDF-4716-8FAC-91805B16C55B.png` (commit `6ebd84e`). The 320×180 Android TV banner fits the complete square logo on black without stretching or cropping. The startup screen and in-app header use the same branding. Regenerate only these resources with `python3 dev/gen/shelby_art.py`; verify them with `--check` (Pillow required).

The previous release `shelby-tv-v0.52.0-1` used a discarded temporary key. Its private key is unavailable, so a differently signed 0.52.1 APK cannot update that installation in place, even though its versionCode increases. Check the public `SIGNING-CERTIFICATE.txt` assets for the exact signer fingerprints. Removing an older app and its keys add-on for a fresh install loses local state; no private keys are released.

## iPhone Home Screen icon fix (0.52.2)

The TV page now uses opaque 180×180 Apple touch artwork, 192×192 and 512×512 PWA icons, a safe-area maskable icon and favicon derived from the exact TV branding source. TV metadata uses Shelby+ Remote and #080808. New icon filenames and versioned links invalidate old browser URLs; manifests are no-store, and legacy TV icon URLs revalidate and return the new art. The TV remote registers no service worker. The separate website demo worker does not serve the TV origin.

1. Download `shelby-plus-remote-tv.apk` from this fork’s latest release and install it on the TV with the physical remote. The 0.52.1 release used a discarded temporary signing key; if the new release certificate differs, Android refuses an update. Removing the old TV app and its keys add-on for a fresh install loses local settings and pairings. Do not remove them unless you accept that loss. Never uninstall or disable TV system packages.
2. Open Shelby+ Remote on the TV and follow its existing connection/debugging instructions. Keep the TV and iPhone on the same trusted Wi-Fi.
3. Delete the old Shelby+/TV Remote shortcut from the iPhone Home Screen. This removes only the web shortcut, not the TV app.
4. In Safari open the current TV address shown on screen, for example `http://192.168.1.50:8765/`. Reload the page, complete six-digit pairing if prompted, and choose Share → Add to Home Screen. Set the name to Shelby+ Remote and tap Add.
5. If the newly added shortcut still has cached artwork, delete it, then in iPhone Settings → Apps → Safari (or Settings → Safari on older iOS) → Advanced → Website Data, remove data only for the TV’s IP address. This clears that browser’s TV pairing. Reopen the TV address in Safari, pair again, reload and add the shortcut again. Do not clear all website data.

The release workflow exercises the actual RemoteService in an Android 11 TV emulator on port 8765, with a test-only x86 build; the distributed release APK retains its ARM targets. The test verifies HTTP paths, PNG dimensions, metadata, cache rules, icon bytes and pairing/security guards. Its output is the `tv-pwa-server-checks` Actions artifact. Physical PRISM+ compatibility and Safari Add to Home Screen on a real iPhone remain unverified. Existing home-screen icons cannot be forcibly refreshed by a TV webpage.
