package dev.tvremote.setup.update

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class ShelbyUpdateTransferTest {
    private val pin = "a".repeat(64)
    private val bytes = "verified APK contents (signature verified separately)".toByteArray()
    private val candidate = ShelbyUpdatePolicy.Candidate("0.53.3", 105303, "", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }, bytes.size.toLong(), pin)
    private fun copy(data: ByteArray, free: Long = Long.MAX_VALUE, active: () -> Boolean = { true }): ByteArray {
        val output = ByteArrayOutputStream()
        ShelbyUpdateTransfer.copy(ByteArrayInputStream(data), output, candidate, free, active) { }
        return output.toByteArray()
    }
    @Test fun correctChecksumTransfersAllBytes() = assertArrayEquals(bytes, copy(bytes))
    @Test fun tamperedTruncatedAndOversizedFilesAreRejected() {
        for (data in listOf(bytes.copyOf(bytes.size-1), bytes+byteArrayOf(1), bytes.reversedArray()))
            assertTrue(runCatching { copy(data) }.exceptionOrNull() is ShelbyUpdateTransfer.InvalidDownload)
    }
    @Test fun storageAndCancellationStopBeforeInstallation() {
        assertTrue(runCatching { copy(bytes, 1) }.exceptionOrNull() is ShelbyUpdateTransfer.NoSpace)
        assertTrue(runCatching { copy(bytes, active = { false }) }.exceptionOrNull() is ShelbyUpdateTransfer.Cancelled)
    }
    @Test fun offlineAndMidDownloadFailureHaveNetworkError() {
        val check = runCatching { ShelbyUpdateTransfer.latest(105302, pin) { _, _ -> throw IOException("offline") } }.exceptionOrNull()!!
        assertEquals("shelby_update.network", ShelbyUpdateTransfer.errorKey(check as Exception))
        val input = object : InputStream() { override fun read(): Int = throw IOException("disconnected") }
        val failed = runCatching { ShelbyUpdateTransfer.copy(input, ByteArrayOutputStream(), candidate, Long.MAX_VALUE, { true }) { } }.exceptionOrNull()!!
        assertEquals("shelby_update.network", ShelbyUpdateTransfer.errorKey(failed as Exception))
    }
    @Test fun missingMetadataCannotOfferAnUpdate() {
        val release = JSONObject().put("draft",false).put("prerelease",false).put("tag_name","shelby-tv-v0.53.3-1").put("assets",org.json.JSONArray())
        val error = runCatching { ShelbyUpdateTransfer.latest(105302,pin) { _, _ -> release.toString() } }.exceptionOrNull()
        assertTrue(error is ShelbyUpdateTransfer.MissingMetadata)
        assertEquals("shelby_update.missing", ShelbyUpdateTransfer.errorKey(error as Exception))
    }
    @Test fun installerFailureStorageAndCancellationAreActionable() {
        assertNull(ShelbyUpdateTransfer.installationFailure(0))
        assertNull(ShelbyUpdateTransfer.installationFailure(-1))
        assertEquals("shelby_update.install_failed",ShelbyUpdateTransfer.installationFailure(1))
        assertEquals("shelby_update.installer_blocked",ShelbyUpdateTransfer.installationFailure(2))
        assertEquals("shelby_update.cancelled",ShelbyUpdateTransfer.installationFailure(3))
        assertEquals("shelby_update.no_space",ShelbyUpdateTransfer.installationFailure(6))
        assertEquals("shelby_update.install_failed",ShelbyUpdateTransfer.installationFailure(999))
    }
}
