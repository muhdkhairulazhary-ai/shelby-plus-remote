Shelby+ Remote — Your TV. Your Control.

This production APK uses the permanent Shelby signing key. The TV app and its bundled keys add-on share the same certificate. Read SIGNING-CERTIFICATE.txt and UPDATE-COMPATIBILITY.txt before installation.

Changes in 0.53.2:
- Manual Check for updates with current/available versions, release date and release notes.
- Bounded HTTPS downloads, SHA-256, APK signature/certificate, package and increasing-version checks before Android installation.
- Download progress and clear offline, storage, verification, cancellation and installer errors.
- Android installer permission is requested only after choosing Update. Android still asks before installing; no silent installation.
- Production releases require an explicit manual workflow run and reviewed version. Validation artifacts are excluded from update metadata.

Install the attached shelby-plus-remote-tv.apk using Android TV Downloader. The stable URL follows the latest published release:
https://github.com/muhdkhairulazhary-ai/shelby-plus-remote/releases/latest/download/shelby-plus-remote-tv.apk

Use the TV's physical remote for installation, then open the TV-displayed address on port 8765 in iPhone Safari on the same trusted Wi-Fi. Existing pairing, keyboard, remote actions and Safari layout remain intact.

Same package and permanent certificate with a higher versionCode supports an in-place update that keeps local pairing/settings. Do not uninstall or clear data for a compatible update. Builds signed with a discarded temporary key require a one-time fresh installation of the app and matching add-on, losing local pairing/settings. Keep your current installation until ready to accept that loss.

If download or installation fails, keep the current app, check Wi-Fi/storage and retry Check for updates or use the verified tagged release APK through Downloader. Android cannot install a lower versionCode as a recovery update; publish a corrected higher version using the same permanent key. See INSTALL.md and SIGNING.md.

Automated browser/emulator/build checks do not verify a real iPhone, PRISM+ hardware, physical-TV keyboard input or the complete production self-update flow. No such hardware verification is claimed.

Based on Hoang Tran's TV Remote: https://github.com/HoangTran0410/tv-remote
Copyright © 2026 Hoang Tran. Original MIT license preserved in LICENSE and inside the APK.
