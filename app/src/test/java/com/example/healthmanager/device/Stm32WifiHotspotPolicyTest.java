package com.example.healthmanager.device;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Stm32WifiHotspotPolicyTest {
    @Test
    public void normalizeSsid_trimsQuotesAndUnknownValues() {
        assertEquals("HRB_AP", Stm32WifiHotspotPolicy.normalizeSsid(" \"HRB_AP\" "));
        assertEquals("", Stm32WifiHotspotPolicy.normalizeSsid("<unknown ssid>"));
        assertEquals("", Stm32WifiHotspotPolicy.normalizeSsid(null));
    }

    @Test
    public void isLikelyStm32HotspotName_matchesSupportedDevicePrefixes() {
        assertTrue(Stm32WifiHotspotPolicy.isLikelyStm32HotspotName("HRB_AP"));
        assertTrue(Stm32WifiHotspotPolicy.isLikelyStm32HotspotName("STM32SmartBand"));
        assertTrue(Stm32WifiHotspotPolicy.isLikelyStm32HotspotName("AI-THINKER_ESP"));
        assertFalse(Stm32WifiHotspotPolicy.isLikelyStm32HotspotName("Home WiFi"));
    }
}
