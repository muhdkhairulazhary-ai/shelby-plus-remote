package dev.tvremote.setup.update

import com.android.apksig.ApkVerifier
import java.io.File
import java.security.MessageDigest

/** Also used by JVM tests against the actual bundled APK and modified copies, not mocked signature reports. */
internal object ShelbyApkIdentity {
    class Rejected(val code: String, val facts: String) : Exception(code)

    fun signers(apk: File, expected: String): List<String> {
        val verified = ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(21).build().verify()
        if (!verified.isVerified || !verified.isVerifiedUsingV1Scheme || !verified.isVerifiedUsingV2Scheme) {
            // Issue names only: verifier parameters may contain untrusted archive data.
            val issues = verified.allErrors.take(6).joinToString(",") { it.issue.name }
            throw Rejected("APK_SIGNATURE", "verified=${verified.isVerified} v1=${verified.isVerifiedUsingV1Scheme} v2=${verified.isVerifiedUsingV2Scheme} issues=$issues")
        }
        val signers = verified.signerCertificates.map { certificate ->
            MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        if (signers.size != 1 || signers.single() != ShelbyUpdatePolicy.fingerprint(expected)) {
            throw Rejected("APK_CERTIFICATE", "signerCount=${signers.size} pinMatch=false")
        }
        return signers
    }
}
