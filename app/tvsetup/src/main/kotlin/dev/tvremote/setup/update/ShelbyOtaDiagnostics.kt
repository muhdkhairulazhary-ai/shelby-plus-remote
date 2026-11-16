package dev.tvremote.setup.update

import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import org.json.JSONException

/** Safe local breadcrumbs: no exception messages, URLs, query tokens, cookies, credentials or release-note content. */
internal class ShelbyOtaDiagnostics(private val emit: (String) -> Unit) {
    var stage = "START"; private set
    fun step(name: String, facts: String = "") {
        stage = name
        emit("OTA[$name] $facts".trimEnd())
    }
    fun failed(error: Exception) {
        val reason = when (error) {
            is ShelbyApkIdentity.Rejected -> error.code + " " + error.facts
            is ShelbyUpdateTransfer.InvalidDownload -> error.reason + " " + error.facts
            is ShelbyUpdateTransfer.MissingMetadata -> "RELEASE_METADATA_MISSING"
            is ShelbyUpdateTransfer.NoSpace -> "INSUFFICIENT_STORAGE"
            is ShelbyUpdateTransfer.Cancelled -> "CANCELLED"
            is ShelbyUpdateTransfer.InstallationFailure -> "INSTALLER_SESSION_FAILED"
            is SecurityException -> "ANDROID_PERMISSION_DENIED"
            is SocketTimeoutException -> "NETWORK_TIMEOUT"
            is SSLException -> "TLS_VALIDATION_FAILED"
            is JSONException -> "METADATA_FORMAT_INVALID"
            else -> error.javaClass.simpleName
        }
        val at = when (error) {
            is ShelbyApkIdentity.Rejected -> error.code
            is ShelbyUpdateTransfer.InvalidDownload -> "APK_CHECKSUM"
            else -> stage
        }
        emit("OTA[$at] FAILED $reason".trimEnd())
    }
}
