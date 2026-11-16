package dev.tvremote.setup.update

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class ShelbyOtaDiagnosticsTest {
    @Test fun stageIdentifiesSignatureFailureWithoutLeakingExceptionParameters() {
        val lines = mutableListOf<String>(); val trace = ShelbyOtaDiagnostics(lines::add)
        trace.step("APK_SIGNATURE")
        trace.failed(ShelbyApkIdentity.Rejected("APK_SIGNATURE","verified=false v1=false v2=true issues=JAR_SIG_PARSE_EXCEPTION"))
        assertTrue(lines.last().contains("v1=false v2=true"))
        assertTrue(lines.last().contains("JAR_SIG_PARSE_EXCEPTION"))
        trace.step("APK_HTTPS")
        trace.failed(IOException("https://example.invalid/file?token=PRIVATE_PAIRING_TOKEN password=secret"))
        assertEquals("OTA[APK_HTTPS] FAILED IOException",lines.last())
        assertFalse(lines.joinToString().contains("PRIVATE_PAIRING_TOKEN"))
    }
    @Test fun checksumTimeoutAndArchiveStagesHaveDistinctDiagnostics() {
        val lines = mutableListOf<String>(); val trace = ShelbyOtaDiagnostics(lines::add)
        trace.step("APK_HTTPS"); trace.failed(ShelbyUpdateTransfer.InvalidDownload("APK_SHA256_MISMATCH"))
        assertTrue(lines.last().contains("APK_CHECKSUM")); assertTrue(lines.last().contains("APK_SHA256_MISMATCH"))
        trace.step("METADATA_HTTPS"); trace.failed(SocketTimeoutException("private URL"))
        assertEquals("OTA[METADATA_HTTPS] FAILED NETWORK_TIMEOUT",lines.last())
        trace.step("APK_VERSION","actualCode=105303 expectedCode=105304")
        trace.failed(IllegalArgumentException("do not log arbitrary messages"))
        assertEquals("OTA[APK_VERSION] FAILED IllegalArgumentException",lines.last())
    }
}
