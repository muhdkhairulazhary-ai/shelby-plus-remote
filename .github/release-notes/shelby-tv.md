Shelby+ Remote — Your TV. Your Control.

This production APK uses the permanent Shelby signing key. The TV app and its bundled keys add-on share the same certificate. Read SIGNING-CERTIFICATE.txt and UPDATE-COMPATIBILITY.txt before installation.

Changes in 0.53.4:
- Fix release-only OTA verification: preserve apksig's reflectively parsed ASN.1 schemas through R8 shrinking. Previously valid signed APKs could be rejected with a JAR signature parse error.
- Add safe stage diagnostics for discovery/metadata, HTTPS status/host, size/SHA-256, installed and APK certificates, package/version and Android installer session/results. Signed URL queries, tokens and credentials are never logged.
- Keep signature/hash/package/version checks strict; preserve the permanent signing key, package IDs, pairing, authentication, settings and user-confirmed Android installation.

Recovery for production 0.53.2/0.53.3: their verifier bug is inside the installed client, so publishing a new APK alone cannot repair that client. Once this corrected release has been approved and published, install the verified same-key 0.53.4 APK through Downloader/Android's normal installer and choose Update. This is an in-place update. Never uninstall or clear data. If Android rejects it, retain the current installation and investigate the certificate/package/version diagnostics. Validation-only APKs cannot be used for this recovery.

Install the attached shelby-plus-remote-tv.apk using Android TV Downloader. The stable URL follows the latest published release:
https://github.com/muhdkhairulazhary-ai/shelby-plus-remote/releases/latest/download/shelby-plus-remote-tv.apk

Use the TV's physical remote for installation, then open the TV-displayed address on port 8765 in iPhone Safari on the same trusted Wi-Fi. Existing pairing, keyboard, remote actions and Safari layout remain intact.

This is an in-place update from the published production 0.53.2/0.53.3 signed with the same permanent certificate. Keep the app and its matching add-on installed. Do not uninstall or clear app data; pairing, authentication and settings remain in their existing storage. If Android rejects the update, stop and retain the current installation while the certificate/package/version issue is investigated. Do not work around rejection by uninstalling.

If download or installation fails, keep the current app, check Wi-Fi/storage and retry Check for updates or use the verified tagged release APK through Downloader. Android cannot install a lower versionCode as a recovery update; publish a corrected higher version using the same permanent key. See INSTALL.md and SIGNING.md.

Automated browser/emulator/build checks do not verify a real iPhone, PRISM+ hardware, physical-TV keyboard input or the complete production self-update flow. No such hardware verification is claimed.

Based on Hoang Tran's TV Remote: https://github.com/HoangTran0410/tv-remote
Copyright © 2026 Hoang Tran. Original MIT license preserved in LICENSE and inside the APK.
