package dev.tvremote.setup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import dev.tvremote.setup.update.ShelbyUpdateTransfer

/** Explicit, non-exported PackageInstaller callback; persists actionable results across process replacement. */
class ShelbyInstallReceiver : BroadcastReceiver() {
    companion object { @Volatile var notifyResult: ((String) -> Unit)? = null }
    override fun onReceive(c: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        var result = ShelbyUpdateTransfer.installationFailure(status)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val ask = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            try {
                require(ask != null)
                c.startActivity(ask.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) { result = "shelby_update.install_failed" }
        }
        if (result != null) c.getSharedPreferences("shelby_update", Context.MODE_PRIVATE).edit().putString("result", result).apply()
        if (result != null) notifyResult?.invoke(result)
        // No retry, uninstall, security-setting change or silent installation on failure.
    }
}
