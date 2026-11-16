package dev.tvremote.setup.update

import android.app.Activity
import android.app.Instrumentation
import android.os.Build
import android.os.Bundle
import org.json.JSONObject
import java.io.File

/** Runs real published APK verification on Android, not only the JVM. Test APK/fixtures are never shipped. */
class OtaProbeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val trace = StringBuilder("Android SDK ${Build.VERSION.SDK_INT}\n")
        val result = Bundle()
        try {
            val old = JSONObject(context.assets.open("0.53.2/shelby-update.json").bufferedReader().use { it.readText() })
            val json = context.assets.open("0.53.3/shelby-update.json").bufferedReader().use { it.readText() }
            val pin = "fc3d3e8fc28694813921f70ece534eae1d2c00913cc8bdb711c5f4da0ef2ece5" // public production baseline
            check(old.getString("certificateSha256") == pin)
            val candidate = ShelbyUpdatePolicy.parse(json,"shelby-tv-v0.53.3-1",105302,pin)!!
            trace.append("metadata PASS ${candidate.version} code=${candidate.code}\n")
            val apk = File(targetContext.cacheDir,"production-0.53.3.apk")
            context.assets.open("0.53.3/shelby-plus-remote-tv.apk").use { input -> apk.outputStream().use { output ->
                ShelbyUpdateTransfer.copy(input,output,candidate,targetContext.cacheDir.usableSpace,{ true }) { }
            } }
            trace.append("checksum/length PASS\n")
            val signers = ShelbyApkIdentity.signers(apk,candidate.signer)
            trace.append("signature/certificate PASS\n")
            val info = targetContext.packageManager.getPackageArchiveInfo(apk.path,0) ?: error("Archive missing")
            @Suppress("DEPRECATION") val code = if (Build.VERSION.SDK_INT>=28) info.longVersionCode else info.versionCode.toLong()
            ShelbyUpdatePolicy.archive(candidate,info.packageName,code,info.versionName,signers)
            trace.append("package/version PASS; installer not invoked for ARM production fixture on x86 test TV\n")
            check(runCatching { ShelbyApkIdentity.signers(apk,"0".repeat(64)) }.isFailure)
            java.io.RandomAccessFile(apk,"rw").use { file ->
                val offset = file.length()/2
                file.seek(offset); val original = file.readByte()
                file.seek(offset); file.writeByte(original.toInt() xor 1)
            }
            check(runCatching { ShelbyApkIdentity.signers(apk,candidate.signer) }.isFailure)
            trace.append("wrong certificate and tampered APK rejected PASS\n")
            result.putString("ota_status","PASS")
            apk.delete()
        } catch (error: Throwable) {
            trace.append(error.javaClass.name).append(": ").append(error.message).append('\n')
            val writer = java.io.StringWriter()
            error.printStackTrace(java.io.PrintWriter(writer))
            trace.append(writer.toString())
            result.putString("ota_status","FAIL")
        }
        result.putString("ota_trace",trace.toString())
        finish(Activity.RESULT_OK,result)
    }
}
