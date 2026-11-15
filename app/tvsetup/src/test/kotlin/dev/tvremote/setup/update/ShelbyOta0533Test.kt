package dev.tvremote.setup.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Discovery contract for the already shipped 0.53.2 updater. No network, signing secret or installation is used. */
class ShelbyOta0533Test {
    // Public certificate from production shelby-tv-v0.53.2-1, not a private signing key.
    private val pin = "fc3d3e8fc28694813921f70ece534eae1d2c00913cc8bdb711c5f4da0ef2ece5"
    private val tag = "shelby-tv-v0.53.3-1"
    private val metadataUrl = "https://github.com/${ShelbyUpdatePolicy.REPO}/releases/download/$tag/shelby-update.json"
    private fun metadata() = JSONObject().put("schema",1).put("channel","production")
        .put("packageName","dev.tvremote.setup").put("version","0.53.3").put("versionCode",105303)
        .put("apkUrl","https://github.com/${ShelbyUpdatePolicy.REPO}/releases/download/$tag/${ShelbyUpdatePolicy.ASSET}")
        .put("sha256","b".repeat(64)).put("bytes",1234).put("certificateSha256",pin)
        .put("releaseNotes","OTA Test v0.53.3").put("releaseDate","2026-11-15T00:00:00Z")
    private fun release(prerelease: Boolean = false) = JSONObject().put("draft",false).put("prerelease",prerelease)
        .put("tag_name",tag).put("assets",JSONArray().put(JSONObject().put("name",ShelbyUpdatePolicy.ASSET)).put(JSONObject().put("name","shelby-update.json")))

    @Test fun shippedUpdaterDiscoversAndValidates0533ForAnInPlaceUpdate() {
        val requested = mutableListOf<String>()
        fun fetch(url: String, limit: Int): String {
            requested += url
            return when (url) {
                "https://api.github.com/repos/${ShelbyUpdatePolicy.REPO}/releases/latest" -> release().toString()
                metadataUrl -> metadata().toString()
                else -> error("Unexpected update endpoint")
            }.also { assertTrue(it.toByteArray().size <= limit) }
        }
        val candidate = ShelbyUpdateTransfer.latest(105302,pin,::fetch)!!
        assertEquals("0.53.3",candidate.version); assertEquals(105303L,candidate.code)
        assertEquals("OTA Test v0.53.3",candidate.notes); assertEquals(pin,candidate.signer)
        assertEquals(listOf("https://api.github.com/repos/${ShelbyUpdatePolicy.REPO}/releases/latest",metadataUrl),requested)
        ShelbyUpdatePolicy.archive(candidate,"dev.tvremote.setup",105303,"0.53.3",listOf(pin))
        assertNull(ShelbyUpdateTransfer.latest(105303,pin,::fetch))
        assertTrue(runCatching { ShelbyUpdatePolicy.archive(candidate,"wrong.package",105303,"0.53.3",listOf(pin)) }.isFailure)
        assertTrue(runCatching { ShelbyUpdatePolicy.archive(candidate,"dev.tvremote.setup",105303,"0.53.3",listOf("c".repeat(64))) }.isFailure)
    }
    @Test fun prereleaseIsNotAnOtaShortcut() {
        assertTrue(runCatching { ShelbyUpdateTransfer.latest(105302,pin) { _, _ -> release(true).toString() } }.isFailure)
    }
}
