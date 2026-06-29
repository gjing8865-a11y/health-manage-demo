package com.example.healthmanager.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class Stm32EndpointResolverTest {
    @Test
    public void normalizeEndpointDefaultsPlainHostToTcp() {
        assertEquals(
                "tcp://192.168.4.9:8080",
                Stm32EndpointResolver.INSTANCE.normalizeEndpoint("192.168.4.9")
        );
        assertEquals(
                "tcp://192.168.4.9:9000",
                Stm32EndpointResolver.INSTANCE.normalizeEndpoint("tcp://192.168.4.9:9000/")
        );
    }

    @Test
    public void normalizeEndpointAddsDataPathForHttpRoot() {
        assertEquals(
                "http://192.168.4.9/data",
                Stm32EndpointResolver.INSTANCE.normalizeEndpoint("http://192.168.4.9")
        );
        assertEquals(
                "http://192.168.4.9/custom",
                Stm32EndpointResolver.INSTANCE.normalizeEndpoint("192.168.4.9/custom")
        );
        assertNull(Stm32EndpointResolver.INSTANCE.normalizeEndpoint("   "));
    }

    @Test
    public void buildDataUrlsDeduplicatesManualAndDiscoveredHosts() {
        List<String> urls = Stm32EndpointResolver.INSTANCE.buildDataUrls(
                "192.168.4.1",
                Arrays.asList("0.0.0.0", "192.168.4.1", "192.168.4.2", "bad-host")
        );

        assertEquals(2, urls.size());
        assertEquals("tcp://192.168.4.1:8080", urls.get(0));
        assertEquals("tcp://192.168.4.2:8080", urls.get(1));
    }

    @Test
    public void ipv4HelpersRejectInvalidHostsAndFormatDhcpAddress() {
        assertTrue(Stm32EndpointResolver.INSTANCE.isUsableIpv4Host("192.168.4.1"));
        assertFalse(Stm32EndpointResolver.INSTANCE.isUsableIpv4Host("0.0.0.0"));
        assertFalse(Stm32EndpointResolver.INSTANCE.isUsableIpv4Host("192.168.4.999"));
        assertFalse(Stm32EndpointResolver.INSTANCE.isUsableIpv4Host("stm32.local"));

        assertEquals("192.168.4.1", Stm32EndpointResolver.INSTANCE.formatIpv4Address(0x0104A8C0));
    }
}
