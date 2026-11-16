package dev.tvremote.setup.update

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageInstaller
import android.app.PendingIntent
import dev.tvremote.setup.ShelbyInstallReceiver
import java.io.IOException
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import dev.tvremote.core.T
import dev.tvremote.setup.R
import dev.tvremote.tvui.TvUi
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Manual fork updates only. No ADB install, silent install, data wipe or remote frontend execution. */
internal class ShelbyUpdates(private val activity: Activity, private val onVersion: (String?) -> Unit) {
    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var busy = false
    @Volatile private var cancelled = false
    private var waiting: ShelbyUpdatePolicy.Candidate? = null
    private var waitingLog: ((String) -> Unit)? = null
    private fun alive() = !activity.isFinishing && !activity.isDestroyed
    private fun say(log: (String) -> Unit, text: String) { ui.post { if (alive()) log(text) } }
    private fun diagnostics(log: (String) -> Unit) = ShelbyOtaDiagnostics { line ->
        android.util.Log.i("ShelbyOTA", line)
        say(log, T("shelby_update.diagnostic", "detail" to line))
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun ownCode(): Long {
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }
    private fun trustedPin(trace: ShelbyOtaDiagnostics): String {
        val pin = ShelbyUpdatePolicy.fingerprint(activity.getString(R.string.shelby_release_cert_sha256))
        @Suppress("DEPRECATION")
        val own = activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_SIGNATURES).signatures
        val matches = own != null && own.size == 1 && sha(own[0].toByteArray()) == pin
        trace.step("INSTALLED_CERTIFICATE", "signers=${own?.size ?: 0} pinMatch=$matches")
        require(matches) { "Installed certificate differs from configured pin" }
        return pin
    }

    fun check(log: (String) -> Unit) {
        if (busy) return
        if (activity.getString(R.string.shelby_release_cert_sha256).isBlank()) {
            say(log, T("shelby_update.not_ready")); return
        }
        busy = true
        say(log, T("tv_main.checking_for_a_new"))
        val trace = diagnostics(log)
        Thread({
            try {
                trace.step("INSTALLED_CERTIFICATE")
                val pin = trustedPin(trace)
                val candidate = ShelbyUpdateTransfer.latest(ownCode(), pin, { trace.step(it) }) { url, limit -> text(url, limit, trace) }
                trace.step("METADATA_VALIDATION", if (candidate == null) "no higher version" else "PASS version=${candidate.version} code=${candidate.code}")
                ui.post {
                    busy = false
                    if (alive()) {
                        onVersion(candidate?.version ?: "")
                        if (candidate == null) log(T("tv_main.newest_version", "v" to activity.packageManager.getPackageInfo(activity.packageName, 0).versionName.orEmpty()))
                        else TvUi.confirm(activity, R.style.TvSheet, T("tv_main.update_to", "v" to candidate.version),
                            T("shelby_update.versions", "current" to activity.packageManager.getPackageInfo(activity.packageName, 0).versionName.orEmpty(), "available" to candidate.version) + "\n" + candidate.date.take(10) + "\n\n" + candidate.notes + "\n\n" + T("shelby_update.ask"), T("page.update"), T("common.cancel")) { download(candidate, log) }
                    }
                }
            } catch (e: Exception) {
                busy = false; trace.failed(e)
            }
        }, "shelby-update-check").apply { isDaemon = true }.start()
    }

    @Suppress("DEPRECATION")
    private fun download(candidate: ShelbyUpdatePolicy.Candidate, log: (String) -> Unit) {
        if (busy || !alive()) return
        val trace = diagnostics(log)
        val allowed = Build.VERSION.SDK_INT < 26 || activity.packageManager.canRequestPackageInstalls()
        trace.step("INSTALL_PERMISSION", "allowed=$allowed")
        if (!allowed) {
            // Only after the user chooses Update; Android owns this permission decision.
            waiting = candidate; waitingLog = log
            try {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.packageName)))
                say(log, T("shelby_update.permission"))
            } catch (e: Exception) { waiting = null; waitingLog = null; say(log, T("shelby_update.installer_blocked")) }
            return
        }
        busy = true; cancelled = false
        val dialog = ProgressDialog(activity).apply {
            setTitle(T("tv_main.update_to", "v" to candidate.version)); setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100; setCancelable(true); setCanceledOnTouchOutside(false); setOnCancelListener { cancelled = true }; show()
        }
        Thread({
            val apk = File(activity.cacheDir, "shelby-verified-update.apk")
            try {
                trace.step("APK_HTTPS", "expectedBytes=${candidate.bytes}")
                val connection = open(candidate.url, trace)
                try {
                    if (connection.responseCode != 200) throw IOException("Download unavailable")
                    connection.inputStream.use { input -> apk.outputStream().use { output ->
                        ShelbyUpdateTransfer.copy(input, output, candidate, activity.cacheDir.usableSpace,
                            { !cancelled && alive() }, { percent -> ui.post { if (alive()) dialog.progress = percent } })
                    } }
                } finally { connection.disconnect() }
                trace.step("APK_CHECKSUM", "PASS bytes=${candidate.bytes} sha256=${candidate.sha256}")
                require(trustedPin(trace) == candidate.signer)
                trace.step("APK_SIGNATURE")
                val signers = ShelbyApkIdentity.signers(apk, candidate.signer)
                trace.step("APK_CERTIFICATE", "PASS signerCount=${signers.size} pinMatch=true")
                trace.step("APK_PACKAGE")
                val info = activity.packageManager.getPackageArchiveInfo(apk.path, 0) ?: error("Invalid APK")
                val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
                trace.step("APK_PACKAGE", "packageMatch=${info.packageName == ShelbyUpdatePolicy.PACKAGE}")
                require(info.packageName == ShelbyUpdatePolicy.PACKAGE)
                trace.step("APK_VERSION", "actualCode=$code expectedCode=${candidate.code} versionNameMatch=${info.versionName == candidate.version} installedCode=${ownCode()}")
                ShelbyUpdatePolicy.archive(candidate, info.packageName, code, info.versionName, signers)
                require(code > ownCode() && !cancelled && alive())
                // No uninstall: Android's compatible full-install session keeps the existing app data and asks the user.
                trace.step("INSTALLER_SESSION", "verified APK; Android user action required")
                try { install(apk, trace) } catch (e: Exception) {
                    if (activity.cacheDir.usableSpace < candidate.bytes) throw ShelbyUpdateTransfer.NoSpace()
                    throw ShelbyUpdateTransfer.InstallationFailure()
                }
                say(log, T("tv_main.installer_asks"))
            } catch (e: Exception) {
                if (!cancelled) trace.failed(if (e is IOException && activity.cacheDir.usableSpace < candidate.bytes) ShelbyUpdateTransfer.NoSpace() else e)
            } finally {
                apk.delete(); busy = false
                ui.post { if (alive()) dialog.dismiss() }
            }
        }, "shelby-update-download").apply { isDaemon = true }.start()
    }

    fun pause() { ShelbyInstallReceiver.notifyResult = null }

    fun resume(log: (String) -> Unit) {
        ShelbyInstallReceiver.notifyResult = { key ->
            activity.getSharedPreferences("shelby_update", Activity.MODE_PRIVATE).edit().remove("result").apply()
            say(log, T(key))
        }
        activity.getSharedPreferences("shelby_update", Activity.MODE_PRIVATE).getString("diagnostic", null)?.let { detail ->
            activity.getSharedPreferences("shelby_update", Activity.MODE_PRIVATE).edit().remove("diagnostic").apply()
            log(T("shelby_update.diagnostic", "detail" to detail))
        }
        activity.getSharedPreferences("shelby_update", Activity.MODE_PRIVATE).getString("result", null)?.let { key ->
            activity.getSharedPreferences("shelby_update", Activity.MODE_PRIVATE).edit().remove("result").apply()
            log(T(key))
        }
        val candidate = waiting ?: return
        val callback = waitingLog ?: log
        waiting = null; waitingLog = null
        if (Build.VERSION.SDK_INT < 26 || activity.packageManager.canRequestPackageInstalls()) {
            // Permission is not consent to a different or newly discovered version.
            TvUi.confirm(activity, R.style.TvSheet, T("tv_main.update_to", "v" to candidate.version),
                T("shelby_update.ask"), T("page.update"), T("common.cancel")) { download(candidate, callback) }
        } else log(T("shelby_update.installer_blocked"))
    }

    private fun install(apk: File, trace: ShelbyOtaDiagnostics) {
        val pi = activity.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ShelbyUpdatePolicy.PACKAGE)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        val id = pi.createSession(params)
        trace.step("INSTALLER_SESSION", "created id=$id")
        try {
            pi.openSession(id).use { session ->
                session.openWrite("apk", 0, apk.length()).use { output -> apk.inputStream().use { it.copyTo(output) }; session.fsync(output) }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val back = Intent(activity, ShelbyInstallReceiver::class.java)
                session.commit(PendingIntent.getBroadcast(activity, id, back, flags).intentSender)
                trace.step("INSTALLER_SESSION", "committed id=$id; waiting for Android confirmation/result")
            }
        } catch (e: Exception) {
            pi.abandonSession(id)
            throw e
        }
    }

    private fun text(url: String, limit: Int, trace: ShelbyOtaDiagnostics): String {
        val connection = open(url, trace)
        try {
            if (connection.responseCode == 404) throw ShelbyUpdateTransfer.MissingMetadata()
            if (connection.responseCode != 200) throw IOException("Release unavailable")
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

    private fun open(first: String, trace: ShelbyOtaDiagnostics): HttpURLConnection {
        var next = first
        repeat(6) {
            trace.step(trace.stage, "HTTPS host=${URL(next).host}")
            require(ShelbyUpdatePolicy.allowedTransport(next)) { "Untrusted update transport" }
            val connection = URL(next).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false; connection.connectTimeout = 10000; connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "Shelby-Remote-Update")
            val status = try { connection.responseCode } catch (e: Exception) { connection.disconnect(); throw e }
            trace.step(trace.stage, "HTTP $status host=${connection.url.host}")
            if (status !in setOf(301, 302, 303, 307, 308)) return connection
            val location = connection.getHeaderField("Location")
            val target = try { URL(URL(next), location ?: error("Missing redirect")).toString() } finally { connection.disconnect() }
            next = target
        }
        error("Too many update redirects")
    }
}
