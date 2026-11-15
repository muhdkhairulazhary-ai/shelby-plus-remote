# Shelby+ Remote

**Your TV. Your Control.**

Phase 1 fork of [Hoang Tran's TV Remote](https://github.com/HoangTran0410/tv-remote), copyright © 2026 Hoang Tran, under the original [MIT license](LICENSE). The notice is also bundled in the TV APK. Upstream documentation below retains upstream product names and download links; those links install upstream builds.

The TV app and the page it serves are branded Shelby+ Remote. The shared browser page uses a black and charcoal design with white text, red accents, larger D-pad and touch targets, safe-area spacing and a wrapping keyboard form. The phone and desktop apps retain their existing presentation. Package `dev.tvremote.setup`, pairing, authentication, scopes, port 8765 and control handlers remain unchanged. Phase 1 does not change the TV launcher, system packages or optimization settings.

## Install using an iPhone (no computer)

1. In Safari, sign in to GitHub and open this fork's **Actions → Shelby+ Remote TV APK**. Choose a successful run for `feat/shelby-plus-phase-1` (or the merged main branch). Download **shelby-plus-remote-tv-apk** at the bottom. Safari saves the ZIP in Files → Downloads; tap it to extract `shelby-plus-remote-tv.apk`. The ZIP also contains the MIT notice and SHA256SUMS.
2. Transfer the APK from iPhone Files to the TV using a local file-transfer app that exposes a browser upload page, or a cloud drive accessible from the TV. Open the APK with the TV's file manager. Alternatively host the APK at a download URL you control and enter that URL in the TV's Downloader app using its physical remote. GitHub artifact pages require login and are not direct Downloader APK URLs. An APK installs on the TV, not on iOS.
3. Allow installation from the chosen file manager/Downloader when Android asks. Install and open **Shelby+ Remote** using the TV's physical remote. This first installation requires a way to select the installer on the TV; an unpaired iPhone alone cannot bootstrap control.
4. Follow the existing on-TV connection instructions. If required, enable Developer options and USB/ADB debugging, and accept only the matching debugging authorization. Some Android 11 TVs expose only Wireless debugging; upstream's flow may require an additional ADB client to establish the initial connection. An iPhone-only setup on PRISM+ has **not** been verified.
5. Put iPhone and TV on the same trusted Wi-Fi. In Safari enter the address shown on the TV, for example `http://192.168.1.50:8765`. Request the code on the TV and enter its six digits. Keep port 8765 on your local network.
6. Use the D-pad/OK, Home, Back and volume controls. Tap Keyboard to open the existing text interface; typing requires the existing ADB connection and remains dependent on the focused TV app. Safari → Share → Add to Home Screen can save the remote.

CI APKs use a new temporary signing key per run, shared with the bundled keys add-on. They cannot update an upstream installation or a previous CI build with a different signing key. Removing an earlier installation loses its local settings/pairings; retain it if that is undesirable. Production updates require a private, persistent signing key, outside git. This workflow creates downloadable test artifacts, not signed upstream releases, and does not change existing automatic update or signature-verification behavior.

## Validation

Target: PRISM+ Android 11 Google TV and iPhone Safari. These are targets, **not verified device compatibility**. No real TV or physical iPhone was available during implementation; keyboard input has not been tested on a real TV. CI tests and an APK build do not establish device compatibility.

Build locally with JDK 17 and Android SDK 35: `app/tvsetup/build.sh`. Existing output remains `app/tvsetup/build/tv-setup.apk`; Actions packages it as `shelby-plus-remote-tv.apk`.
