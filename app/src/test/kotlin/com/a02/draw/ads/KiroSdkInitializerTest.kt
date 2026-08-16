package com.a02.draw.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KiroSdkInitializerTest {
    @Test
    fun `base integration enables ads while keeping tracking and billing disabled`() {
        val config = KiroSdkInitializer.createConfig(isDebug = true)

        assertTrue(config.isDebug)
        assertTrue(config.enableAds)
        assertFalse(config.trackingConfig.enableFirebase)
        assertFalse(config.trackingConfig.enableFacebook)
        assertNull(config.trackingConfig.appsFlyerDevKey)
        assertNull(config.trackingConfig.adjustAppToken)
        assertFalse(config.billingConfig.enableBilling)
    }
}
