package dev.tvremote.setup.update

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dev.tvremote.setup.MainActivity
import dev.tvremote.setup.link.Paired
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Synthetic data and disposable signatures on an isolated emulator only. No production endpoint/key override. */
internal class OtaUpgradeProbe(private val runner: Instrumentation, private val trace: StringBuilder) {
    private val context get() = runner.targetContext
    private val prefs get() = context.getSharedPreferences("ota_probe",Context.MODE_PRIVATE)
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    @Suppress("DEPRECATION") private fun installed() = context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNATURES)

    fun handoff() {
        val info=installed()
        check(info.versionCode==105304 && info.packageName=="dev.tvremote.setup")
        val pin=sha(info.signatures!!.single().toByteArray())
        val apk=File(context.cacheDir,"ota-test-0535.apk")
        context.assets.open("candidate.apk").use { input -> apk.outputStream().use { input.copyTo(it) } }
        val hash=apk.inputStream().use { stream ->
            val digest=MessageDigest.getInstance("SHA-256"); val buffer=ByteArray(65536)
            while(true) { val n=stream.read(buffer); if(n<0) break; digest.update(buffer,0,n) }
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        val tag="shelby-tv-v0.53.5-1"
        val url="https://github.com/${ShelbyUpdatePolicy.REPO}/releases/download/$tag/${ShelbyUpdatePolicy.ASSET}"
        val metadata=JSONObject().put("schema",1).put("channel","production").put("packageName",context.packageName)
            .put("version","0.53.5").put("versionCode",105305).put("apkUrl",url).put("sha256",hash).put("bytes",apk.length())
            .put("certificateSha256",pin).put("releaseNotes","OTA Test v0.53.5").put("releaseDate","2026-11-16T00:00:00Z")
        val release=JSONObject().put("draft",false).put("prerelease",false).put("tag_name",tag)
            .put("assets",JSONArray().put(JSONObject().put("name",ShelbyUpdatePolicy.ASSET)).put(JSONObject().put("name","shelby-update.json")))
        val candidate=ShelbyUpdateTransfer.latest(105304,pin) { endpoint, _ ->
            when(endpoint) {
                "https://api.github.com/repos/${ShelbyUpdatePolicy.REPO}/releases/latest" -> release.toString()
                "https://github.com/${ShelbyUpdatePolicy.REPO}/releases/download/$tag/shelby-update.json" -> metadata.toString()
                else -> error("Unexpected endpoint")
            }
        }!!
        val checked=File(context.cacheDir,"ota-checked-0535.apk")
        apk.inputStream().use { input -> checked.outputStream().use { output ->
            ShelbyUpdateTransfer.copy(input,output,candidate,context.cacheDir.usableSpace,{true}) { }
        } }
        val signers=ShelbyApkIdentity.signers(checked,pin)
        val archive=context.packageManager.getPackageArchiveInfo(checked.path,0)!!
        ShelbyUpdatePolicy.archive(candidate,archive.packageName,archive.versionCode.toLong(),archive.versionName,signers)
        check(candidate.code>info.versionCode)
        trace.append("0.53.4->0.53.5 discovery/checksum/signature/package/code PASS (test key, mocked metadata)\n")
        val token=Paired.add(context,"web","OTA emulator fixture","127.0.0.1",false,"remote")
        check(Paired.check(context,token,false)?.entry?.scope=="remote")
        check(prefs.edit().putString("token",token).putString("pin",pin).putInt("uid",android.os.Process.myUid()).commit())
        check(context.getSharedPreferences("remote",Context.MODE_PRIVATE).edit().putBoolean("ota_probe_setting",true).commit())
        File(context.filesDir,"ota-probe-marker.txt").writeText("retained across confirmed update")
        val activity=runner.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val updater=ShelbyUpdates(activity) { }
        val method=ShelbyUpdates::class.java.getDeclaredMethod("install",File::class.java,ShelbyOtaDiagnostics::class.java)
        method.isAccessible=true
        method.invoke(updater,checked,ShelbyOtaDiagnostics { trace.append(it).append('\n') })
        val until=System.currentTimeMillis()+10000
        var pending=false
        while(System.currentTimeMillis()<until) {
            val detail=context.getSharedPreferences("shelby_update",Context.MODE_PRIVATE).getString("diagnostic","").orEmpty()
            if(detail.contains("status=-1 ")) { pending=true;break }
            Thread.sleep(100)
        }
        check(pending) { "Android did not request user confirmation" }
        check(installed().versionCode==105304) { "Update installed before confirmation" }
        trace.append("real PackageInstaller handoff/user-confirmation-required PASS; version still105304\n")
    }

    fun persistence() {
        val info=installed()
        check(info.versionCode==105305 && info.versionName=="0.53.5" && info.packageName=="dev.tvremote.setup")
        check(android.os.Process.myUid()==prefs.getInt("uid",-1))
        check(sha(info.signatures!!.single().toByteArray())==prefs.getString("pin",null))
        val token=prefs.getString("token",null) ?: error("Pairing token fixture lost")
        check(Paired.check(context,token,false)?.entry?.scope=="remote")
        check(context.getSharedPreferences("remote",Context.MODE_PRIVATE).getBoolean("ota_probe_setting",false))
        check(File(context.filesDir,"ota-probe-marker.txt").readText()=="retained across confirmed update")
        trace.append("confirmed105305: UID/certificate/pairing authentication/scope/preferences/private file retained PASS\n")
    }
}
