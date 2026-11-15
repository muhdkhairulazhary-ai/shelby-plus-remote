**Shelby+ Remote — Your TV. Your Control.**

Download `shelby-plus-remote-tv.apk` directly below. On Android TV, open Downloader and enter:

https://github.com/muhdkhairulazhary-ai/shelby-plus-remote/releases/latest/download/shelby-plus-remote-tv.apk

This URL follows the latest published release. Each release also has a permanent tagged download URL. No Downloader numeric code is assigned.

Install on the TV, then open Shelby+ Remote. From iPhone Safari on the same trusted Wi-Fi, open the address shown on the TV (port 8765) and enter the six-digit pairing code. See INSTALL.md for first-time debugging and transfer requirements.

TV app 0.52.2 fixes the iPhone Home Screen branding with Shelby+ 180×180 Apple, 192×192 and 512×512 PWA icons and a favicon. The manifest, metadata, theme color and icon URLs use the official artwork and cache versioning. Legacy TV icon URLs also return Shelby+ art. Remote functionality, pairing, authentication and security are unchanged.

After installing this APK, delete the old iPhone Home Screen shortcut (this does not uninstall the TV app), open the TV's address in Safari, reload it, then Share → Add to Home Screen. Use the name Shelby+ Remote. iOS may retain icons for existing shortcuts; if re-adding still shows old art, remove the shortcut again and clear only this TV address in Safari Website Data (this removes its browser pairing), then open Safari, pair again and re-add. No real-iPhone icon verification has been performed.

The Android TV emulator check starts the real TV app's RemoteService and requests its port 8765 through ADB forwarding; it does not use a static mock server. Apple/PWA/favicon bytes, sizes, cache headers, manifest links and pairing/origin guards are checked. Emulator coverage is not physical PRISM+ or iPhone testing.

The installed release `shelby-tv-v0.52.1-1` used a discarded temporary key. Compare the attached public certificate report before attempting an in-place update. A higher versionCode does not make different signing keys compatible.

The workflow checks web/Kotlin tests, Android API lint, APK signatures (v1 and v2), the bundled add-on's matching signer, package metadata, and the packaged MIT notice. Build checks do not establish PRISM+ compatibility or keyboard operation on a real TV; these remain unverified.

Signing:

- **Temporary:** a fresh private key was generated for this run and discarded. The APK and bundled keys add-on share that key. A future build using another key cannot update this installation in place.
- **Persistent:** the repository's private `SHELBY_TV_KEYSTORE_BASE64` secret was used. Future APKs need the same signing certificate and an equal or higher Android versionCode to update in place. Back up that key securely; changing or losing it prevents compatible updates.
- Neither signing mode can update upstream TV Setup or earlier CI builds signed with another key. A fresh installation may require removing the old TV app and its keys add-on, losing local settings and pairing data. Preserve the old installation if that is undesirable.
- The persistent secret must contain base64 of the existing build's keystore format: alias `tvsetup`, store/key passwords `tvsetup`. Those upstream build constants are not private keys. The actual keystore stays private and is never a release asset or committed file.
- SHA256SUMS is an ordinary download checksum, not an upstream Ed25519 update signature. This release does not change or bypass upstream automatic-update verification or promise automatic updates from this fork.

Based on [Hoang Tran's TV Remote](https://github.com/HoangTran0410/tv-remote). Copyright © 2026 Hoang Tran. Original MIT license preserved in LICENSE and inside the APK.
