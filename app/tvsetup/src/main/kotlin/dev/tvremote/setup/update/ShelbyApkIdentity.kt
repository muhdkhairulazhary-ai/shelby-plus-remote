package dev.tvremote.setup.update

import com.android.apksig.ApkVerifier
import java.io.File
import java.security.MessageDigest

/** Also used by JVM tests against the actual bundled APK and modified copies, not mocked signature reports. */
internal object ShelbyApkIdentity {
    fun signers(apk: File, expected: String): List<String> {
        val verified = ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(21).build().verify()
        require(verified.isVerified && verified.isVerifiedUsingV1Scheme && verified.isVerifiedUsingV2Scheme) { "APK signature verification failed" }
        val signers = verified.signerCertificates.map { certificate ->
            MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        require(signers.size == 1 && signers.single() == ShelbyUpdatePolicy.fingerprint(expected)) { "APK signing certificate mismatch" }
        return signers
    }
}
