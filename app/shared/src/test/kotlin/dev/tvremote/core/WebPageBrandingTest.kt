package dev.tvremote.core

import dev.tvremote.core.web.WebPage
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** The TV owns its branding, including legacy routes; other cores keep their own artwork. */
class WebPageBrandingTest {
    private fun newPage(core: String, stamp: String = "0.52.2-test") = WebPage({ File("web/$it").inputStream() }, core, stamp)

    @Test fun tvMetadataAndIconsAreVersionedAndServed() {
        val page = newPage("tv")
        val index = page.serve("/")!!
        assertEquals("no-store", index.headers["Cache-Control"])
        val html = String(index.body)
        assertTrue(html.contains("<title>Shelby+ Remote</title>"))
        assertTrue(html.contains("name=\"application-name\" content=\"Shelby+ Remote\""))
        assertTrue(html.contains("href=\"manifest-shelby-v1.json?v=0.52.2-test\""))
        assertTrue(html.contains("sizes=\"180x180\" href=\"icon-shelby180v1.png?v=0.52.2-test\""))
        assertTrue(html.contains("href=\"icon-shelbyfaviconv1.ico?v=0.52.2-test\""))
        assertFalse(html.contains("icon-180.png"))
        val response = page.serve("/manifest-shelby-v1.json")!!
        assertEquals("no-store", response.headers["Cache-Control"])
        val manifest = JSONObject(String(response.body))
        assertEquals("Shelby+ Remote", manifest.getString("name"))
        assertEquals("Shelby+ Remote", manifest.getString("short_name"))
        assertEquals("#080808", manifest.getString("theme_color"))
        val icons = manifest.getJSONArray("icons")
        for (i in 0 until icons.length()) {
            val src = icons.getJSONObject(i).getString("src")
            assertTrue(src.endsWith("?v=0.52.2-test"))
            val asset = src.substringBefore('?')
            val icon = page.serve("/$asset")!!
            assertEquals("image/png", icon.type)
            assertArrayEquals(File("web/$asset").readBytes(), icon.body)
        }
        assertEquals("image/x-icon", page.serve("/icon-shelbyfaviconv1.ico")!!.type)
        assertFalse(html == String(newPage("tv", "0.52.3-next").serve("/")!!.body))
    }

    @Test fun tvLegacyPathsReturnShelbyArtAndOtherCoresKeepTheirIcons() {
        val tv = newPage("tv")
        for (size in listOf(180, 192, 512)) {
            val response = tv.serve("/icon-$size.png")!!
            assertEquals("no-cache", response.headers["Cache-Control"])
            assertArrayEquals(File("web/icon-shelby${size}v1.png").readBytes(), response.body)
            for (core in listOf("phone", "pc")) {
                assertArrayEquals(File("web/icon-$size.png").readBytes(), newPage(core).serve("/icon-$size.png")!!.body)
            }
        }
        assertEquals("Shelby+ Remote", JSONObject(String(tv.serve("/manifest.json")!!.body)).getString("name"))
        assertEquals("TV Remote", JSONObject(String(newPage("phone").serve("/manifest.json")!!.body)).getString("name"))
        assertNull(tv.serve("/../icon-shelby180v1.png"))
        assertNull(tv.serve("/sw.js"))
    }
}
