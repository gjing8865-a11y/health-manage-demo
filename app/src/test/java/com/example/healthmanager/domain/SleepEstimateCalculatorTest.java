package com.example.healthmanager.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class SleepEstimateCalculatorTest {

    @Test
    public void buildReturnsHighScoreForStableVitalsAndLowMovement() {
        long start = 1_700_000_000_000L;
        List<SleepSignalSample> samples = Arrays.asList(
                new SleepSignalSample(start, 62, 97, 1000, true),
                new SleepSignalSample(start + 2 * 60_000L, 61, 97, 1001, true),
                new SleepSignalSample(start + 4 * 60_000L, 60, 98, 1001, true),
                new SleepSignalSample(start + 6 * 60_000L, 61, 97, 1002, true),
                new SleepSignalSample(start + 8 * 60_000L, 62, 98, 1002, true),
                new SleepSignalSample(start + 10 * 60_000L, 61, 97, 1003, true)
        );

        SleepEstimate estimate = SleepEstimateCalculator.INSTANCE.build(samples);

        assertTrue(estimate.getScore() >= 85);
        assertEquals(7, estimate.getDataPoints().size());
        assertEquals("监测中", estimate.getDetails().getWakeTime());
        assertTrue(estimate.getDetails().getDeepSleepMinutes() >= 0);
    }

    @Test
    public void buildExplainsWhenStepSignalIsMissing() {
        long start = 1_700_000_000_000L;
        List<SleepSignalSample> samples = Arrays.asList(
                new SleepSignalSample(start, 70, 96, 0, false),
                new SleepSignalSample(start + 2 * 60_000L, 69, 96, 0, false),
                new SleepSignalSample(start + 4 * 60_000L, 68, 97, 0, false),
                new SleepSignalSample(start + 6 * 60_000L, 69, 97, 0, false),
                new SleepSignalSample(start + 8 * 60_000L, 70, 96, 0, false),
                new SleepSignalSample(start + 10 * 60_000L, 69, 96, 0, false)
        );

        SleepEstimate estimate = SleepEstimateCalculator.INSTANCE.build(samples);

        assertTrue(estimate.getAdvice().contains("未回传步数"));
        assertEquals(7, estimate.getDataPoints().size());
    }
}
