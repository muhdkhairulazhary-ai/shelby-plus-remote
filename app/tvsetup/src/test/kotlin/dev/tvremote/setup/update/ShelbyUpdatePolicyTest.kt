package dev.tvremote.setup.update

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ShelbyUpdatePolicyTest {
    private val pin = "a".repeat(64)
    private val tag = "shelby-tv-v0.53.2-1"
    private fun json() = JSONObject().put("schema", 1).put("packageName", "dev.tvremote.setup")
        .put("channel", "production").put("releaseNotes", "Test release").put("releaseDate", "2026-11-15T00:00:00Z")
        .put("version", "0.53.2").put("versionCode", 105302).put("apkUrl", "https://github.com/${ShelbyUpdatePolicy.REPO}/releases/download/$tag/${ShelbyUpdatePolicy.ASSET}")
        .put("sha256", "b".repeat(64)).put("bytes", 1234).put("certificateSha256", pin)
    private fun parse(o: JSONObject = json(), current: Long = 105301) = ShelbyUpdatePolicy.parse(o.toString(), tag, current, pin)
    private fun rejected(block: () -> Unit) = assertTrue(runCatching(block).isFailure)

    @Test fun compatibleNewVersionIsOfferedButNoDowngradeIsOffered() {
        assertEquals(105302L, parse()!!.code)
        assertNull(parse(current = 105302)); assertNull(parse(current = 105400))
    }
    @Test fun metadataCannotChangeSignerPackageOrVersionCode() {
        rejected { parse(json().put("certificateSha256", "c".repeat(64))) }
        rejected { parse(json().put("packageName", "another.app")) }
        rejected { parse(json().put("versionCode", 999999)) }
        rejected { parse(json().put("version", "0.53.3")) }
        rejected { parse(json().put("sha256", "bad")) }
    }
    @Test fun assetCannotEscapePinnedRepositoryOrUseLatestRedirect() {
        rejected { parse(json().put("apkUrl", "https://evil.example/app.apk")) }
        rejected { parse(json().put("apkUrl", "https://github.com/${ShelbyUpdatePolicy.REPO}/releases/latest/download/${ShelbyUpdatePolicy.ASSET}")) }
        rejected { parse(json().put("apkUrl", json().getString("apkUrl").replace("https:", "http:"))) }
    }
    @Test fun archiveMustMatchVerifiedManifestAndHaveExactlyOnePinnedSigner() {
        val candidate = parse()!!
        ShelbyUpdatePolicy.archive(candidate, "dev.tvremote.setup", 105302, "0.53.2", listOf(pin))
        rejected { ShelbyUpdatePolicy.archive(candidate, "another.app", 105302, "0.53.2", listOf(pin)) }
        rejected { ShelbyUpdatePolicy.archive(candidate, "dev.tvremote.setup", 105301, "0.53.2", listOf(pin)) }
        rejected { ShelbyUpdatePolicy.archive(candidate, "dev.tvremote.setup", 105302, "0.53.2", listOf("c".repeat(64))) }
        rejected { ShelbyUpdatePolicy.archive(candidate, "dev.tvremote.setup", 105302, "0.53.2", listOf(pin, pin)) }
    }
    @Test fun productionMetadataAndNotesAreRequired() {
        rejected { parse(json().put("channel", "validation")) }
        rejected { parse(json().apply { remove("releaseNotes") }) }
        rejected { parse(json().put("releaseNotes", "x".repeat(6001))) }
        rejected { parse(json().put("releaseDate", "invalid")) }
    }
    @Test fun oversizedAndInvalidMetadataAreRejected() {
        rejected { parse(json().put("bytes", ShelbyUpdatePolicy.MAX_APK+1)) }
        rejected { parse(json().put("bytes", 0)) }
        rejected { ShelbyUpdatePolicy.parse("x".repeat(32769), tag, 1, pin) }
        rejected { ShelbyUpdatePolicy.parse(json().toString(), "v0.53.2", 1, pin) }
        rejected { ShelbyUpdatePolicy.parse(json().toString(), tag, 1, "") }
    }
    @Test fun redirectsRequireHttpsAndKnownGitHubHosts() {
        assertTrue(ShelbyUpdatePolicy.allowedTransport("https://release-assets.githubusercontent.com/file?signature=public-temporary-url"))
        for (url in listOf("http://github.com/file", "https://github.com.evil.test/file", "https://user@github.com/file", "https://github.com:444/file", "https://github.com/file#fragment"))
            assertFalse(url, ShelbyUpdatePolicy.allowedTransport(url))
    }
}
