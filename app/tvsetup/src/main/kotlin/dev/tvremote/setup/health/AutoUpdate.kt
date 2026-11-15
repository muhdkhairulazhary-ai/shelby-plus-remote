package dev.tvremote.setup.health

import android.app.usage.UsageEvents
import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.tvremote.core.Words
import dev.tvremote.core.adb.Adb
import dev.tvremote.core.ontv.Channels
import dev.tvremote.core.ontv.Games
import dev.tvremote.core.ontv.Home
import dev.tvremote.core.ontv.TvApps
import dev.tvremote.core.ontv.VoiceAddon
import dev.tvremote.home.HomeConfig
import dev.tvremote.setup.rules.Usage
import dev.tvremote.update.UpdatePlan

/** Keeps separately opted-in ecosystem app updates unchanged; Shelby itself uses the manual pinned fork checker. */
internal class AutoUpdate(private val c: Context, private val version: String, private val adb: () -> Adb?, private val busy: () -> String?) {
    companion object {
        private const val PREFS = "remote"
        fun on(c: Context): Boolean = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("auto_update", true)
        fun set(c: Context, on: Boolean) { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("auto_update", on).apply() }
        /** One TV app's own switch (its menu in TV Setup's More TV apps row, the user 2026-11-03); never set = as TV Setup's own [on]. */
        fun on(c: Context, pkg: String): Boolean = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("auto_update:$pkg", on(c))
        fun set(c: Context, pkg: String, on: Boolean) { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("auto_update:$pkg", on).apply() }
        /** The TV apps it updates, each by its own switch. */
        val APPS get() = listOf(HomeConfig.PKG, Channels.PKG, Games.PKG, TvApps.PKG) + VoiceAddon.LANGS.map { VoiceAddon.pkg(it) }
        private const val MIN = 60_000L
    }

    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var stopped = false
    private var appsAt = 0L
    private val dir by lazy { c.getExternalFilesDir("update") }       // shared storage: the shell, which installs it, can read it

    private val tick = object : Runnable {
        override fun run() {
            if (stopped) return
            Thread({
                val next = try { step() } catch (e: Exception) { 30 * MIN }   // offline, GitHub busy: later
                if (!stopped) ui.postDelayed(this, next)
            }, "auto-update").apply { isDaemon = true }.start()
        }
    }

    fun start() {
        val old = System.currentTimeMillis() - 3600_000              // left from an update that is over by now
        dir?.listFiles()?.forEach { if (it.lastModified() < old) it.delete() }
        ui.postDelayed(tick, 10 * MIN)                                // not while the TV is just starting up
    }

    fun stop() { stopped = true; ui.removeCallbacks(tick) }

    /** One step towards an update; returns how long until the next is worth trying. */
    private fun step(): Long {
        val apps = APPS.any { on(c, it) }
        if (!on(c) && !apps) return 60 * MIN
        val now = System.currentTimeMillis()
        if (apps && now - appsAt >= UpdatePlan.CHECK) adb()?.let { a -> appsAt = if (ownApps(a)) now else now - UpdatePlan.CHECK + UpdatePlan.RETRY }   // one had to wait: again soon
        // Shelby's own APK is updated only by its pinned, user-confirmed fork checker.
        // Keep the separately opted-in upstream ecosystem updates above unchanged.
        return 20 * MIN
    }

    /**
     * TV Home, TV Channels and TV Games put up to date when installed and a newer one is out (each checked against the signed
     * SHA256SUMS, their data kept), so that they rarely need to ask for themselves (SelfUpdate waits for this, UpdatePlan.quiet).
     * An install closes the app, so it goes in only when nobody is in it ([UpdatePlan.mayInstall]): in standby anything, awake TV
     * Home when it is not on top, TV Channels / TV Games when not on top and not in front within the last 30 minutes either
     * (one checked as not on the screen was opened during the push and install and closed under the person: so the file is
     * downloaded first and the check made again right before the install, [free]). Awake installs also wait for [busy] (the remote
     * or a session in use). False when one had to wait.
     */
    private fun ownApps(a: Adb): Boolean {
        val sh: (String) -> String = { a.shell(it) }
        var waited = false
        fun top(pkg: String) = sh("dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'").contains("$pkg/")
        fun free(pkg: String, careful: Boolean): Boolean {
            val now = System.currentTimeMillis()
            val awake = sh("dumpsys power | grep -m1 mWakefulness=").contains("Awake")
            val ok = !awake || ((!careful || busy() == null) && UpdatePlan.mayInstall(true, top(pkg), if (careful) lastUsed(pkg, now) else null, now, careful))
            if (!ok) waited = true
            return ok
        }
        Channels.refresh(); Games.refresh(); TvApps.refresh(); VoiceAddon.refresh()                           // asked again now, not from the 6 h the pages keep
        for ((pkg, state, install) in listOf(
            Triple(Channels.PKG, { Channels.state(sh) }, { Channels.install(a, dir, Words.lang) { free(Channels.PKG, true) } }),
            Triple(Games.PKG, { Games.state(sh) }, { Games.install(a, dir, Words.lang) { free(Games.PKG, true) } }),
            Triple(TvApps.PKG, { TvApps.state(sh) }, { TvApps.install(a, dir, Words.lang) { free(TvApps.PKG, true) } }),
            *VoiceAddon.LANGS.map { l -> Triple(VoiceAddon.pkg(l), { VoiceAddon.state(sh, l) }, { VoiceAddon.install(a, dir, Words.lang, { free(VoiceAddon.pkg(l), false) }, voice = l) }) }.toTypedArray())) {   // TV Setup Voice, each language on the TV; no screen: only "not in use now"
            if (!on(c, pkg)) continue                                 // its own switch off
            try {
                val s = state()
                if (s.optBoolean("installed") && s.has("latest")) {
                    if (top(pkg)) waited = true else install()        // on screen: no download wasted; install() asks free() again once it has the file
                }
            } catch (e: Exception) { android.util.Log.w("AutoUpdate", pkg, e) }   // next time
        }
        try { if (on(c, HomeConfig.PKG) && free(HomeConfig.PKG, false)) Home.update(a, dir, Words.lang) { free(HomeConfig.PKG, false) } } catch (e: Exception) { }   // not on this TV ("home:missing"), offline: next time
        return !waited
    }

    /** The last time [pkg] came to or left the front within a day, or null when the usage log is empty (no usage access: nothing to go by). Events only; cheap. */
    private fun lastUsed(pkg: String, now: Long): Long? = try {
        var any = false
        var last = 0L
        Usage.events(c, now - 86_400_000L, now) { e ->
            any = true
            if (e.packageName == pkg && (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || e.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND)) last = e.timeStamp
        }
        if (any) last else null                                       // 0: not seen today, long ago enough
    } catch (e: Exception) { null }
}
