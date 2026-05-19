package top.e404.statimg.test

import org.junit.jupiter.api.Test
import top.e404.statimg.ProxyConfig
import java.net.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestProxyConfig {
    @Test
    fun testParseSupportedProxyTypes() {
        assertEquals(Proxy.Type.HTTP, ProxyConfig("http").toProxyType())
        assertEquals(Proxy.Type.SOCKS, ProxyConfig("socks").toProxyType())
        assertEquals(Proxy.Type.SOCKS, ProxyConfig("socket").toProxyType())
        assertEquals(Proxy.Type.DIRECT, ProxyConfig("direct").toProxyType())
    }

    @Test
    fun testRejectUnsupportedProxyType() {
        assertFailsWith<IllegalStateException> {
            ProxyConfig("unknown").toProxyType()
        }
    }
}
