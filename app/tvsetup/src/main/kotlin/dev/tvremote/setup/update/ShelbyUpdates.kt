package dev.tvremote.setup.update

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.android.apksig.ApkVerifier
import dev.tvremote.core.T
import dev.tvremote.setup.R
import dev.tvremote.tvui.TvUi
import dev.tvremote.update.SelfUpdate
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Manual fork updates only. No ADB install, silent install, permission change, data wipe or remote frontend execution. */
internal class ShelbyUpdates(private val activity: Activity) {
    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var busy = false
    @Volatile private var cancelled = false
    private fun alive() = !activity.isFinishing && !activity.isDestroyed
    private fun say(log: (String) -> Unit, text: String) { ui.post { if (alive()) log(text) } }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun ownCode(): Long {
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }
    private fun trustedPin(): String {
        val pin = ShelbyUpdatePolicy.fingerprint(activity.getString(R.string.shelby_release_cert_sha256))
        @Suppress("DEPRECATION")
        val own = activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_SIGNATURES).signatures
        require(own != null && own.size == 1 && sha(own[0].toByteArray()) == pin) { "Installed certificate differs from configured pin" }
        return pin
    }

    fun check(log: (String) -> Unit) {
        if (busy) return
        if (activity.getString(R.string.shelby_release_cert_sha256).isBlank()) {
            say(log, T("shelby_update.not_ready")); return
        }
        busy = true
        say(log, T("tv_main.checking_for_a_new"))
        Thread({
            try {
                val pin = trustedPin()
                val latest = JSONObject(text("https://api.github.com/repos/${ShelbyUpdatePolicy.REPO}/releases/latest", 65536))
                require(!latest.getBoolean("draft") && !latest.getBoolean("prerelease")) { "Unpublished update" }
                val tag = latest.getString("tag_name")
                require(tag.matches(Regex("shelby-tv-v[0-9]+\\.[0-9]+\\.[0-9]+-[0-9]+")))
                val metadata = "https://github.com/${ShelbyUpdatePolicy.REPO}/releases/download/$tag/shelby-update.json"
                val candidate = ShelbyUpdatePolicy.parse(text(metadata, 32768), tag, ownCode(), pin)
                ui.post {
                    busy = false
                    if (alive()) {
                        if (candidate == null) log(T("tv_main.newest_version", "v" to activity.packageManager.getPackageInfo(activity.packageName, 0).versionName.orEmpty()))
                        else TvUi.confirm(activity, R.style.TvSheet, T("tv_main.update_to", "v" to candidate.version),
                            T("shelby_update.ask"), T("page.update"), T("common.cancel")) { download(candidate, log) }
                    }
                }
            } catch (e: Exception) {
                busy = false; say(log, T("shelby_update.rejected"))
            }
        }, "shelby-update-check").apply { isDaemon = true }.start()
    }

    @Suppress("DEPRECATION")
    private fun download(candidate: ShelbyUpdatePolicy.Candidate, log: (String) -> Unit) {
        if (busy || !alive()) return
        // Do not open or change Android security settings. The usual external installer remains an alternative.
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            say(log, T("shelby_update.installer_blocked")); return
        }
        busy = true; cancelled = false
        val dialog = ProgressDialog(activity).apply {
            setTitle(T("tv_main.update_to", "v" to candidate.version)); setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100; setCancelable(true); setCanceledOnTouchOutside(false); setOnCancelListener { cancelled = true }; show()
        }
        Thread({
            val apk = File(activity.cacheDir, "shelby-verified-update.apk")
            try {
                val connection = open(candidate.url)
                val digest = MessageDigest.getInstance("SHA-256")
                try {
                    require(connection.responseCode == 200)
                    var count = 0L
                    connection.inputStream.use { input -> apk.outputStream().use { output ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            check(!cancelled && alive())
                            val n = input.read(buffer); if (n < 0) break
                            count += n; require(count <= candidate.bytes && count <= ShelbyUpdatePolicy.MAX_APK)
                            output.write(buffer, 0, n); digest.update(buffer, 0, n)
                            val percent = (count * 100 / candidate.bytes).toInt()
                            ui.post { if (alive()) dialog.progress = percent }
                        }
                    } }
                    require(count == candidate.bytes)
                } finally { connection.disconnect() }
                val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                require(actual == candidate.sha256)
                require(trustedPin() == candidate.signer)
                val verified = ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(21).build().verify()
                require(verified.isVerified)
                val info = activity.packageManager.getPackageArchiveInfo(apk.path, 0) ?: error("Invalid APK")
                val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
                ShelbyUpdatePolicy.archive(candidate, info.packageName, code, info.versionName, verified.signerCertificates.map { sha(it.encoded) })
                require(code > ownCode() && !cancelled && alive())
                // No uninstall: Android's compatible full-install session keeps the existing app data and asks the user.
                SelfUpdate.Installed.failed = { say(log, T("shelby_update.rejected")) }
                SelfUpdate.commit(activity, apk, Intent(activity, SelfUpdate.Installed::class.java), false, false)
                say(log, T("tv_main.installer_asks"))
            } catch (e: Exception) {
                if (!cancelled) say(log, T("shelby_update.rejected"))
            } finally {
                apk.delete(); busy = false
                ui.post { if (alive()) dialog.dismiss() }
            }
        }, "shelby-update-download").apply { isDaemon = true }.start()
    }

    private fun text(url: String, limit: Int): String {
        val connection = open(url)
        try {
            require(connection.responseCode == 200)
            return connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val bytes = ByteArray(4096)
                while (true) {
                    val n = input.read(bytes); if (n < 0) break
                    require(out.size() + n <= limit); out.write(bytes, 0, n)
                }
                String(out.toByteArray(), Charsets.UTF_8)
            }
        } finally { connection.disconnect() }
    }

    private fun open(first: String): HttpURLConnection {
        var next = first
        repeat(6) {
            require(ShelbyUpdatePolicy.allowedTransport(next)) { "Untrusted update transport" }
            val connection = URL(next).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false; connection.connectTimeout = 10000; connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "Shelby-Remote-Update")
            if (connection.responseCode !in setOf(301, 302, 303, 307, 308)) return connection
            val location = connection.getHeaderField("Location")
            val target = try { URL(URL(next), location ?: error("Missing redirect")).toString() } finally { connection.disconnect() }
            next = target
        }
        error("Too many update redirects")
    }
}
