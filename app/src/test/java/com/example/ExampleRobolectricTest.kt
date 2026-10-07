package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.DeviceType
import com.example.scanner.MacVendorResolver
import com.example.service.DnsFilterServer
import com.example.service.HotspotProxyService
import com.example.service.IptablesController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Hotspot Manager", appName)
    }

    @Test
    fun `test mac vendor resolver for infinix and apple`() {
        val (vendorInfinix, typeInfinix) = MacVendorResolver.resolve("70:70:8B:11:22:33")
        assertTrue(vendorInfinix.contains("Infinix"))
        assertEquals(DeviceType.PHONE, typeInfinix)

        val (vendorApple, _) = MacVendorResolver.resolve("3C:06:30:AA:BB:CC")
        assertTrue(vendorApple.contains("Apple"))
    }

    @Test
    fun `test iptables command generation`() {
        val dropCmd = IptablesController.generateBlockIpCommand("192.168.43.50")
        assertTrue(dropCmd.contains("-s 192.168.43.50 -j DROP"))
        assertTrue(dropCmd.contains("-d 192.168.43.50 -j DROP"))

        val adbCmd = IptablesController.generateAdbCommand(dropCmd)
        assertTrue(adbCmd.startsWith("adb shell su -c"))
    }

    @Test
    fun `test proxy rules blocking and rate limiting`() {
        val testIp = "192.168.43.99"
        HotspotProxyService.blockIp(testIp)
        assertTrue(HotspotProxyService.isIpBlocked(testIp))

        HotspotProxyService.unblockIp(testIp)
        assertFalse(HotspotProxyService.isIpBlocked(testIp))

        HotspotProxyService.setBandwidthLimit(testIp, 512)
        assertEquals(512, HotspotProxyService.getBandwidthLimit(testIp))

        HotspotProxyService.setQuota(testIp, 1000L)
        HotspotProxyService.addBytesUsed(testIp, 1200L)
        assertTrue(HotspotProxyService.isQuotaExceeded(testIp))
    }

    @Test
    fun `test dns filter blacklist and categories`() {
        DnsFilterServer.addDomainToBlacklist("tiktok.com")
        assertTrue(DnsFilterServer.customBlacklist.contains("tiktok.com"))

        DnsFilterServer.removeDomainFromBlacklist("tiktok.com")
        assertFalse(DnsFilterServer.customBlacklist.contains("tiktok.com"))

        DnsFilterServer.blockAds.value = true
        assertTrue(DnsFilterServer.blockAds.value)
    }
}
