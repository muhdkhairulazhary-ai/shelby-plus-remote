**Shelby+ Remote — Your TV. Your Control.**

Download `shelby-plus-remote-tv.apk` directly below. On Android TV, open Downloader and enter:

https://github.com/muhdkhairulazhary-ai/shelby-plus-remote/releases/latest/download/shelby-plus-remote-tv.apk

This URL follows the latest published release. Each release also has a permanent tagged download URL. No Downloader numeric code is assigned.

Install on the TV, then open Shelby+ Remote. From iPhone Safari on the same trusted Wi-Fi, open the address shown on the TV (port 8765) and enter the six-digit pairing code. See INSTALL.md for first-time debugging and transfer requirements.

TV app 0.53.0 recreates the uploaded Shelby+ Liquid Glass reference as a functional PWA: CSS-built black/red ambience, translucent glass surfaces, illuminated OK/D-pad, real Power/Home/Back/Menu and volume/channel keys, installed-app shortcuts, app search, keyboard and a floating scrolling dock. More keys/media controls stay available in an expandable group. Live state supplies the TV name/status; shortcuts reflect installed apps. The official icon, pairing, API endpoints, authentication, security and MIT attribution are preserved.

Screenshot assets and GLASS-VALIDATION.json show Chromium/WebKit browser checks with **simulated PRISM+ and installed-app data**. They do not show a real TV or iPhone. Browser validation covers 320/390/430/1100 CSS-pixel layouts, minimum 44px controls, actual action payloads, hold/release/cancel, keyboard typing, app filtering/launching, all tabs, Power confirmation, pairing and view-only gates. Reduced-motion and reduced-transparency/blur fallback styles are included. The Android 11 TV emulator separately tests the actual RemoteService on port 8765.

Install the APK on the TV using its physical remote, then open its displayed address in iPhone Safari and pair. Remote, Keyboard and Apps appear first in the floating dock; swipe horizontally for Files, Health, Automation, Setup and Find. Existing Safari shortcuts can be removed and re-added if they retain old metadata. The 0.52.2-2 release used a discarded temporary signing key; consult the attached certificate comparison before attempting an update. Different keys require a fresh install and removing old apps loses local settings/pairings. No real-iPhone or PRISM+ hardware verification is claimed.

The workflow checks web/Kotlin tests, Android API lint, APK signatures (v1 and v2), the bundled add-on's matching signer, package metadata, and the packaged MIT notice. Build checks do not establish PRISM+ compatibility or keyboard operation on a real TV; these remain unverified.

Signing:

- **Temporary:** a fresh private key was generated for this run and discarded. The APK and bundled keys add-on share that key. A future build using another key cannot update this installation in place.
- **Persistent:** the repository's private `SHELBY_TV_KEYSTORE_BASE64` secret was used. Future APKs need the same signing certificate and an equal or higher Android versionCode to update in place. Back up that key securely; changing or losing it prevents compatible updates.
- Neither signing mode can update upstream TV Setup or earlier CI builds signed with another key. A fresh installation may require removing the old TV app and its keys add-on, losing local settings and pairing data. Preserve the old installation if that is undesirable.
- The persistent secret must contain base64 of the existing build's keystore format: alias `tvsetup`, store/key passwords `tvsetup`. Those upstream build constants are not private keys. The actual keystore stays private and is never a release asset or committed file.
- SHA256SUMS is an ordinary download checksum, not an upstream Ed25519 update signature. This release does not change or bypass upstream automatic-update verification or promise automatic updates from this fork.

Based on [Hoang Tran's TV Remote](https://github.com/HoangTran0410/tv-remote). Copyright © 2026 Hoang Tran. Original MIT license preserved in LICENSE and inside the APK.
