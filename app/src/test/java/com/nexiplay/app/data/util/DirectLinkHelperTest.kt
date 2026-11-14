package com.nexiplay.app.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectLinkHelperTest {

    @Test
    fun `collectDistinctHttpUrls filters invalid and deduplicates preserving order`() {
        val urls = DirectLinkHelper.collectDistinctHttpUrls(
            "  https://a.example/offer1  ",
            "",
            "ftp://invalid.example",
            "https://b.example/offer2",
            "https://a.example/offer1",
            "http://c.example/offer3",
            null
        )
        assertEquals(
            listOf(
                "https://a.example/offer1",
                "https://b.example/offer2",
                "http://c.example/offer3"
            ),
            urls
        )
    }

    @Test
    fun `selectRotatingUrl cycles evenly across primary secondary and fallback`() {
        val candidates = listOf("https://u1.test", "https://u2.test", "https://u3.test")
        assertEquals("https://u1.test", DirectLinkHelper.selectRotatingUrl(candidates, 0))
        assertEquals("https://u2.test", DirectLinkHelper.selectRotatingUrl(candidates, 1))
        assertEquals("https://u3.test", DirectLinkHelper.selectRotatingUrl(candidates, 2))
        assertEquals("https://u1.test", DirectLinkHelper.selectRotatingUrl(candidates, 3))
        assertNull(DirectLinkHelper.selectRotatingUrl(emptyList(), 0))
    }

    @Test
    fun `shouldSkipDirectLinkGate enforces cooldown without starving expired or zero timestamps`() {
        assertFalse(DirectLinkHelper.shouldSkipDirectLinkGate(0L, 50_000L, 10_000L))
        assertTrue(DirectLinkHelper.shouldSkipDirectLinkGate(45_000L, 50_000L, 10_000L))
        assertFalse(DirectLinkHelper.shouldSkipDirectLinkGate(40_000L, 50_000L, 10_000L))
        assertFalse(DirectLinkHelper.shouldSkipDirectLinkGate(60_000L, 50_000L, 10_000L))
    }

    @Test
    fun `classifyOverlayUrl keeps http in webview and routes schemes externally`() {
        assertEquals(
            DirectLinkHelper.OverlayUrlAction.LOAD_IN_WEBVIEW,
            DirectLinkHelper.classifyOverlayUrl("https://conscientiouscabbageadrift.com/abc")
        )
        assertEquals(
            DirectLinkHelper.OverlayUrlAction.LOAD_IN_WEBVIEW,
            DirectLinkHelper.classifyOverlayUrl("http://example.org/landing")
        )
        assertEquals(
            DirectLinkHelper.OverlayUrlAction.OPEN_EXTERNAL,
            DirectLinkHelper.classifyOverlayUrl("intent://offer#Intent;scheme=https;end")
        )
        assertEquals(
            DirectLinkHelper.OverlayUrlAction.OPEN_EXTERNAL,
            DirectLinkHelper.classifyOverlayUrl("market://details?id=com.example.app")
        )
        assertEquals(
            DirectLinkHelper.OverlayUrlAction.IGNORE,
            DirectLinkHelper.classifyOverlayUrl("about:blank")
        )
        assertEquals(
            DirectLinkHelper.OverlayUrlAction.IGNORE,
            DirectLinkHelper.classifyOverlayUrl("javascript:void(0)")
        )
    }

    @Test
    fun `extractBrowserFallbackUrlFromIntentUri decodes fallback https url safely`() {
        val raw = "intent://open#Intent;scheme=https;package=com.missing.app;" +
            "S.browser_fallback_url=https%3A%2F%2Fpromo.example.com%2Fland%3Fa%3D1%26b%3D2;end"
        assertEquals(
            "https://promo.example.com/land?a=1&b=2",
            DirectLinkHelper.extractBrowserFallbackUrlFromIntentUri(raw)
        )
        assertNull(
            DirectLinkHelper.extractBrowserFallbackUrlFromIntentUri(
                "intent://open#Intent;S.browser_fallback_url=javascript%3Aalert(1);end"
            )
        )
        assertNull(DirectLinkHelper.extractBrowserFallbackUrlFromIntentUri("https://normal.example"))
    }
}
