**Shelby+ Remote — Your TV. Your Control.**

Download `shelby-plus-remote-tv.apk` directly below. On Android TV, open Downloader and enter:

https://github.com/muhdkhairulazhary-ai/shelby-plus-remote/releases/latest/download/shelby-plus-remote-tv.apk

This URL follows the latest published release. Each release also has a permanent tagged download URL. No Downloader numeric code is assigned.

Install on the TV, then open Shelby+ Remote. From iPhone Safari on the same trusted Wi-Fi, open the address shown on the TV (port 8765) and enter the six-digit pairing code. See INSTALL.md for first-time debugging and transfer requirements.

TV app 0.53.1 recreates the uploaded Shelby+ Liquid Glass reference as a functional PWA: CSS-built black/red ambience, translucent glass surfaces, illuminated OK/D-pad, real Power/Home/Back/Menu and volume/channel keys, installed-app shortcuts, app search, keyboard and a floating scrolling dock. More keys/media controls stay available in an expandable group. Live state supplies the TV name/status; shortcuts reflect installed apps. The official icon, pairing, API endpoints, authentication, security and MIT attribution are preserved.

Screenshot assets and GLASS-VALIDATION.json show Chromium/WebKit browser checks with **simulated PRISM+ and installed-app data**. They do not show a real TV or iPhone. Browser validation covers 320/375/390/430/1100 CSS-pixel layouts, minimum 44px controls, actual action payloads, hold/release/cancel, keyboard typing, app filtering/launching, all tabs, Power confirmation, pairing and view-only gates. Reduced-motion and reduced-transparency/blur fallback styles are included. The Android 11 TV emulator separately tests the actual RemoteService on port 8765.

Install the APK on the TV using its physical remote, then open its displayed address in iPhone Safari and pair. Remote, Keyboard and Apps appear first in the floating dock; swipe horizontally for Files, Health, Automation, Setup and Find. Existing Safari shortcuts can be removed and re-added if they retain old metadata. The 0.52.2-2 release used a discarded temporary signing key; consult the attached certificate comparison before attempting an update. Different keys require a fresh install and removing old apps loses local settings/pairings. No real-iPhone or PRISM+ hardware verification is claimed.

The workflow checks web/Kotlin tests, Android API lint, APK signatures (v1 and v2), the bundled add-on's matching signer, package metadata, and the packaged MIT notice. Build checks do not establish PRISM+ compatibility or keyboard operation on a real TV; these remain unverified.

Signing:

- Releases require the protected permanent signing key and pinned certificate; there is no temporary-key release fallback. See SIGNING.md.
- Read UPDATE-COMPATIBILITY.txt and SIGNING-CERTIFICATE.txt before installing. Compatible updates require the same certificate, stable package ID and a strictly higher versionCode; they preserve local data.
- Earlier temporary-key builds cannot be updated by a different permanent key. A one-time fresh install loses local settings and pairings. Keep the existing installation until ready to accept that migration.
- The native Shelby update checker is manual and user-confirmed. It verifies HTTPS origin, package, increasing version, size, SHA-256 and the embedded signing certificate before invoking Android's installer. Private keys and passwords are never published.

Based on [Hoang Tran's TV Remote](https://github.com/HoangTran0410/tv-remote). Copyright © 2026 Hoang Tran. Original MIT license preserved in LICENSE and inside the APK.
