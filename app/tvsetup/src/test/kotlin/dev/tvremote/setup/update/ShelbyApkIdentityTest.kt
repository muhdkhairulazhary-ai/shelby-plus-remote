package dev.tvremote.setup.update

import com.android.apksig.ApkVerifier
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ShelbyApkIdentityTest {
    private val apk get() = File(System.getProperty("tvkeys.dir"), "debug/tvkeys.apk")
    private fun pin(): String = ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(21).build().verify().signerCertificates.single().let {
        MessageDigest.getInstance("SHA-256").digest(it.encoded).joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
    }
    @Test fun actualSignedApkAcceptsOnlyItsCertificate() {
        assertEquals(listOf(pin()), ShelbyApkIdentity.signers(apk, pin()))
        assertTrue(runCatching { ShelbyApkIdentity.signers(apk, "0".repeat(64)) }.isFailure)
    }
    @Test fun repackedUnsignedAndTamperedApksAreRejected() {
        for (strip in listOf(false,true)) {
            val modified = File.createTempFile("shelby-invalid-", ".apk")
            try {
                ZipOutputStream(modified.outputStream()).use { output -> ZipFile(apk).use { input ->
                    for (entry in input.entries()) {
                        if (strip && entry.name.startsWith("META-INF/")) continue
                        output.putNextEntry(ZipEntry(entry.name))
                        val data = input.getInputStream(entry).use { it.readBytes() }
                        output.write(if (entry.name == "classes.dex" && !strip) data+byteArrayOf(1) else data)
                        output.closeEntry()
                    }
                } }
                assertTrue("Unverified APK accepted",runCatching { ShelbyApkIdentity.signers(modified,pin()) }.isFailure)
            } finally { modified.delete() }
        }
    }
}
