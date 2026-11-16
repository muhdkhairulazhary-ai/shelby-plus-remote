package dev.tvremote.setup.update

import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Shared production check/download policy, with I/O supplied so failures can be tested without Android. */
internal object ShelbyUpdateTransfer {
    class MissingMetadata : Exception()
    class NoSpace : IOException()
    class Cancelled : Exception()
    class InvalidDownload(val reason: String = "APK_INTEGRITY_INVALID", val facts: String = "") : Exception()
    class InstallationFailure : Exception()

    fun latest(current: Long, pin: String, text: (String, Int) -> String): ShelbyUpdatePolicy.Candidate? = latest(current, pin, {}, text)

    fun latest(current: Long, pin: String, stage: (String) -> Unit, text: (String, Int) -> String): ShelbyUpdatePolicy.Candidate? {
        stage("RELEASE_DISCOVERY")
        val latest = JSONObject(text("https://api.github.com/repos/${ShelbyUpdatePolicy.REPO}/releases/latest", 65536))
        require(!latest.getBoolean("draft") && !latest.getBoolean("prerelease"))
        val tag = latest.getString("tag_name")
        require(tag.matches(Regex("shelby-tv-v[0-9]+\\.[0-9]+\\.[0-9]+-[0-9]+")))
        val assets = latest.getJSONArray("assets")
        val names = (0 until assets.length()).map { assets.getJSONObject(it).getString("name") }
        if ("shelby-update.json" !in names || ShelbyUpdatePolicy.ASSET !in names) throw MissingMetadata()
        stage("METADATA_HTTPS")
        val json = text("https://github.com/${ShelbyUpdatePolicy.REPO}/releases/download/$tag/shelby-update.json", 32768)
        stage("METADATA_VALIDATION")
        return ShelbyUpdatePolicy.parse(json, tag, current, pin)
    }

    fun copy(input: InputStream, output: OutputStream, candidate: ShelbyUpdatePolicy.Candidate, free: Long,
             active: () -> Boolean, progress: (Int) -> Unit) {
        // The private cache and PackageInstaller session both need a copy, plus headroom.
        if (free < candidate.bytes * 2 + 8L * 1024 * 1024) throw NoSpace()
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        var count = 0L
        while (true) {
            if (!active()) throw Cancelled()
            val n = input.read(buffer); if (n < 0) break
            count += n
            if (count > candidate.bytes || count > ShelbyUpdatePolicy.MAX_APK) throw InvalidDownload("APK_SIZE_LIMIT")
            output.write(buffer, 0, n); digest.update(buffer, 0, n)
            progress((count * 100 / candidate.bytes).toInt())
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        if (count != candidate.bytes) throw InvalidDownload("APK_LENGTH_MISMATCH", "expected=${candidate.bytes} actual=$count")
        if (hash != candidate.sha256) throw InvalidDownload("APK_SHA256_MISMATCH", "expected=${candidate.sha256} actual=$hash")
    }

    /** PackageInstaller status codes; no platform calls in this policy. */
    fun installationFailure(status: Int): String? = when (status) {
        -1, 0 -> null // pending normal Android confirmation, or success
        3 -> "shelby_update.cancelled"
        6 -> "shelby_update.no_space"
        2 -> "shelby_update.installer_blocked"
        else -> "shelby_update.install_failed"
    }

    fun errorKey(error: Exception): String = when (error) {
        is MissingMetadata -> "shelby_update.missing"
        is NoSpace -> "shelby_update.no_space"
        is Cancelled -> "shelby_update.cancelled"
        is InvalidDownload -> "shelby_update.tampered"
        is InstallationFailure -> "shelby_update.install_failed"
        is IOException -> "shelby_update.network"
        else -> "shelby_update.rejected"
    }
}
