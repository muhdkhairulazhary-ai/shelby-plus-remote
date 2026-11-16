Shelby+ Remote — Your TV. Your Control.

This production APK uses the permanent Shelby signing key. The TV app and its bundled keys add-on share the same certificate. Read SIGNING-CERTIFICATE.txt and UPDATE-COMPATIBILITY.txt before installation.

Changes in 0.53.5:
- Minimal production OTA test based on 0.53.4: visible “OTA Test v0.53.5” label on the TV screen.
- versionName 0.53.5 and versionCode 105305. The fixed minified APK verifier, permanent signing configuration, package IDs, pairing/settings storage and Android compatibility are unchanged.
- On 0.53.4, choose Check for updates, review the version/notes, choose Update, and confirm Android's standard installation prompt. Reopen Shelby+ Remote and confirm the new label. Do not uninstall or clear app data.

This test is a compatible in-place update from published production 0.53.4 using the same permanent certificate. Keep the existing app and matching add-on installed. If identity/version verification fails, retain the current installation and investigate; never uninstall or clear data as a workaround. The corrected 0.53.4 updater can discover this only after it is approved and published as the latest regular production release; drafts/prereleases are excluded.

Install the attached shelby-plus-remote-tv.apk using Android TV Downloader. The stable URL follows the latest published release:
https://github.com/muhdkhairulazhary-ai/shelby-plus-remote/releases/latest/download/shelby-plus-remote-tv.apk

Use the TV's physical remote for installation, then open the TV-displayed address on port 8765 in iPhone Safari on the same trusted Wi-Fi. Existing pairing, keyboard, remote actions and Safari layout remain intact.

This is an in-place update from published production 0.53.4 signed with the same permanent certificate. Keep the app and its matching add-on installed. Do not uninstall or clear app data; pairing, authentication and settings remain in their existing storage. If Android rejects the update, stop and retain the current installation while the certificate/package/version issue is investigated. Do not work around rejection by uninstalling.

If download or installation fails, keep the current app, check Wi-Fi/storage and retry Check for updates or use the verified tagged release APK through Downloader. Android cannot install a lower versionCode as a recovery update; publish a corrected higher version using the same permanent key. See INSTALL.md and SIGNING.md.

Automated browser/emulator/build checks do not verify a real iPhone, PRISM+ hardware, physical-TV keyboard input or the complete production self-update flow. No such hardware verification is claimed.

Based on Hoang Tran's TV Remote: https://github.com/HoangTran0410/tv-remote
Copyright © 2026 Hoang Tran. Original MIT license preserved in LICENSE and inside the APK.
