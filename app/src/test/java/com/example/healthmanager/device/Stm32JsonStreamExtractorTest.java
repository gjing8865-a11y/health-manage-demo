package com.example.healthmanager.device;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Stm32JsonStreamExtractorTest {
    @Test
    public void extractJsonObjects_handlesMultipleObjectsInOneTcpFrame() {
        List<String> objects = Stm32JsonStreamExtractor.extractJsonObjects(
                "noise {\"hr\":72}{\"spo2\":98,\"steps\":1200} tail"
        );

        assertEquals(2, objects.size());
        assertEquals("{\"hr\":72}", objects.get(0));
        assertEquals("{\"spo2\":98,\"steps\":1200}", objects.get(1));
    }

    @Test
    public void extractJsonObjects_ignoresBracesInsideStrings() {
        List<String> objects = Stm32JsonStreamExtractor.extractJsonObjects(
                "{\"msg\":\"value with } and { braces\",\"hr\":80}"
        );

        assertEquals(1, objects.size());
        assertEquals("{\"msg\":\"value with } and { braces\",\"hr\":80}", objects.get(0));
    }

    @Test
    public void extractJsonObjects_returnsOnlyCompleteObjects() {
        List<String> objects = Stm32JsonStreamExtractor.extractJsonObjects(
                "{\"hr\":70}{\"spo2\":"
        );

        assertEquals(1, objects.size());
        assertEquals("{\"hr\":70}", objects.get(0));
        assertTrue(Stm32JsonStreamExtractor.extractJsonObjects("partial {\"hr\":70").isEmpty());
    }
}
