package com.example.healthmanager.device;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Stm32WifiScanSummaryTest {
    @Test
    public void countLikelyDeviceHotspotsUsesSharedHotspotPolicy() {
        List<WifiAccessPoint> accessPoints = Arrays.asList(
                accessPoint("Home WiFi"),
                accessPoint("HRB_AP"),
                accessPoint("AI-THINKER_ESP"),
                accessPoint("Coffee Shop")
        );

        assertEquals(2, Stm32WifiScanSummary.INSTANCE.countLikelyDeviceHotspots(accessPoints));
    }

    @Test
    public void buildMessageExplainsEmptyScanResult() {
        String message = Stm32WifiScanSummary.INSTANCE.buildMessage(
                Collections.emptyList(),
                false
        );

        assertTrue(message.contains("\u6ca1\u6709\u626b\u63cf\u5230\u4efb\u4f55\u70ed\u70b9"));
        assertTrue(message.contains("\u624b\u52a8\u8f93\u5165\u70ed\u70b9\u540d"));
    }

    @Test
    public void buildMessageExplainsNoLikelyDeviceInFreshScan() {
        String message = Stm32WifiScanSummary.INSTANCE.buildMessage(
                Arrays.asList(accessPoint("Home WiFi"), accessPoint("Office WiFi")),
                false
        );

        assertEquals(
                "\u5df2\u626b\u63cf\u5230 2 \u4e2a\u70ed\u70b9\uff0c\u4f46\u6ca1\u6709\u660e\u663e\u7684 STM32 \u70ed\u70b9\uff0c\u8bf7\u68c0\u67e5 STM32 \u70ed\u70b9\u540d\u79f0\uff0c\u6216\u76f4\u63a5\u624b\u52a8\u8f93\u5165\u70ed\u70b9\u540d\u8fde\u63a5\u3002",
                message
        );
    }

    @Test
    public void buildMessageExplainsCachedScanResult() {
        String message = Stm32WifiScanSummary.INSTANCE.buildMessage(
                Arrays.asList(accessPoint("Home WiFi"), accessPoint("HRB_AP")),
                true
        );

        assertEquals(
                "\u7cfb\u7edf\u9650\u5236\u4e86\u672c\u6b21\u4e3b\u52a8\u626b\u63cf\uff0c\u5df2\u5c55\u793a\u6700\u8fd1\u4e00\u6b21\u626b\u63cf\u7ed3\u679c\uff0c\u5e76\u53d1\u73b0 1 \u4e2a\u7591\u4f3c\u8bbe\u5907\u70ed\u70b9\u3002",
                message
        );
    }

    @Test
    public void buildMessageCountsFreshLikelyDevices() {
        String message = Stm32WifiScanSummary.INSTANCE.buildMessage(
                Arrays.asList(accessPoint("STM32SmartBand"), accessPoint("HRB_AP")),
                false
        );

        assertEquals(
                "\u5df2\u53d1\u73b0 2 \u4e2a\u7591\u4f3c\u8bbe\u5907\u70ed\u70b9\uff0c\u70b9\u51fb\u5217\u8868\u9879\u5373\u53ef\u5c1d\u8bd5\u8fde\u63a5\u3002",
                message
        );
    }

    private static WifiAccessPoint accessPoint(String ssid) {
        return new WifiAccessPoint(ssid, ssid + "-bssid", -50, "[WPA2]");
    }
}
