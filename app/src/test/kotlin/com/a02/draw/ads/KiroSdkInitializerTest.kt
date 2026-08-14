package com.a02.draw.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KiroSdkInitializerTest {
    @Test
    fun `base integration keeps monetization services disabled until ads plan is applied`() {
        val config = KiroSdkInitializer.createConfig(isDebug = true)

        assertTrue(config.isDebug)
        assertFalse(config.enableAds)
        assertFalse(config.trackingConfig.enableFirebase)
        assertFalse(config.trackingConfig.enableFacebook)
        assertNull(config.trackingConfig.appsFlyerDevKey)
        assertNull(config.trackingConfig.adjustAppToken)
        assertFalse(config.billingConfig.enableBilling)
    }
}
