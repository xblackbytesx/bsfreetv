package com.bsfreetv.launcher.domain

import com.bsfreetv.launcher.domain.status.IpInfo
import com.bsfreetv.launcher.domain.status.IpTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IpTraceTest {
    private val sample = """
        fl=123f45
        h=1.1.1.1
        ip=100.34.42.1
        ts=1761400000.123
        visit_scheme=https
        uag=Dalvik/2.1.0
        colo=OSL
        sliver=none
        http=http/2
        loc=NO
        tls=TLSv1.3
        sni=off
        warp=off
        gateway=off
        rbi=off
        kex=X25519
    """.trimIndent()

    @Test fun `reads ip and country from a real trace`() {
        assertEquals(IpInfo("100.34.42.1", "NO"), IpTrace.parse(sample))
    }

    @Test fun `ipv6 works`() {
        assertEquals(IpInfo("2a02:1234:abcd::1", "NL"), IpTrace.parse("ip=2a02:1234:abcd::1\nloc=NL"))
    }

    @Test fun `unknown and tor pseudo countries give no country`() {
        assertEquals(IpInfo("1.2.3.4", null), IpTrace.parse("ip=1.2.3.4\nloc=XX"))
        assertEquals(IpInfo("1.2.3.4", null), IpTrace.parse("ip=1.2.3.4\nloc=T1"))
        assertEquals(IpInfo("1.2.3.4", null), IpTrace.parse("ip=1.2.3.4\nloc=Norway"))
        assertEquals(IpInfo("1.2.3.4", null), IpTrace.parse("ip=1.2.3.4"))
    }

    @Test fun `no usable ip is no result`() {
        assertNull(IpTrace.parse("loc=NO"))
        assertNull(IpTrace.parse("ip=\nloc=NO"))
        assertNull(IpTrace.parse("ip=<script>\nloc=NO"))
        assertNull(IpTrace.parse("<html>captive portal</html>"))
        assertNull(IpTrace.parse(""))
        assertNull(IpTrace.parse("ip=1.2.3.4\n" + "x".repeat(IpTrace.MAX_BYTES)))
    }
}
