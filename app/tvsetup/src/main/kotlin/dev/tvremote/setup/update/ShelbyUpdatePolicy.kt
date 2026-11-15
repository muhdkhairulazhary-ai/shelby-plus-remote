package dev.tvremote.setup.update

import org.json.JSONObject
import java.net.URI

/** Public release metadata is untrusted until its URL, version and pinned APK signer are checked. */
internal object ShelbyUpdatePolicy {
    const val REPO = "muhdkhairulazhary-ai/shelby-plus-remote"
    const val PACKAGE = "dev.tvremote.setup"
    const val ASSET = "shelby-plus-remote-tv.apk"
    const val MAX_APK = 64L * 1024 * 1024
    data class Candidate(val version: String, val code: Long, val url: String, val sha256: String, val bytes: Long, val signer: String)

    fun fingerprint(value: String): String = value.replace(":", "").lowercase().also {
        require(it.matches(Regex("[0-9a-f]{64}"))) { "Invalid certificate fingerprint" }
    }

    fun parse(json: String, tag: String, current: Long, pin: String): Candidate? {
        require(json.toByteArray(Charsets.UTF_8).size <= 32768) { "Oversized update metadata" }
        require(tag.matches(Regex("shelby-tv-v[0-9]+\\.[0-9]+\\.[0-9]+-[0-9]+"))) { "Invalid release tag" }
        val o = JSONObject(json)
        require(o.getInt("schema") == 1 && o.getString("packageName") == PACKAGE) { "Wrong update package" }
        val version = o.getString("version")
        require(version.matches(Regex("[0-9]{1,2}\\.[0-9]{1,2}\\.[0-9]{1,2}"))) { "Invalid version" }
        require(tag.startsWith("shelby-tv-v$version-")) { "Tag/version mismatch" }
        val parts = version.split('.').map { it.toLong() }
        val code = o.getLong("versionCode")
        require(code == 100000 + parts[0] * 10000 + parts[1] * 100 + parts[2]) { "Version code mismatch" }
        val url = o.getString("apkUrl")
        require(url == "https://github.com/$REPO/releases/download/$tag/$ASSET") { "Untrusted APK URL" }
        val sha = o.getString("sha256").lowercase()
        require(sha.matches(Regex("[0-9a-f]{64}"))) { "Invalid checksum" }
        val bytes = o.getLong("bytes")
        require(bytes in 1..MAX_APK) { "Invalid APK size" }
        val signer = fingerprint(o.getString("certificateSha256"))
        require(signer == fingerprint(pin)) { "Update signer changed" }
        return if (code <= current) null else Candidate(version, code, url, sha, bytes, signer)
    }

    fun archive(candidate: Candidate, packageName: String, code: Long, version: String?, signers: List<String>) {
        require(packageName == PACKAGE && code == candidate.code && version == candidate.version) { "APK metadata differs from release" }
        require(signers.size == 1 && fingerprint(signers.single()) == candidate.signer) { "APK signer is not trusted" }
    }

    fun allowedTransport(url: String): Boolean = try {
        val u = URI(url)
        u.scheme == "https" && u.userInfo == null && u.fragment == null && (u.port == -1 || u.port == 443) &&
            u.host in setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    } catch (e: Exception) { false }
}
