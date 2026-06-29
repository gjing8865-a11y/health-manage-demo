package com.example.healthmanager.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class Stm32DemoPayloadFactoryTest {

    @Test
    public void buildCreatesDeterministicReviewPayloadForGivenTime() {
        long sevenMinutes = 7L * 60_000L;

        Stm32DevicePayload payload = Stm32DemoPayloadFactory.INSTANCE.build(sevenMinutes);

        assertEquals(79, payload.getHeartRate());
        assertEquals(98, payload.getBloodOxygen());
        assertEquals(6659, payload.getSteps());
        assertEquals(Integer.valueOf(79), payload.getBatteryLevel());
        assertEquals(7, payload.getWeeklySteps().size());
        assertEquals(6659, payload.getWeeklySteps().get(6).intValue());
        assertTrue(payload.getHasHeartRate());
        assertTrue(payload.getHasBloodOxygen());
        assertTrue(payload.getHasSteps());
        assertNotNull(payload.getSleepPayload());
        assertEquals(86, payload.getSleepPayload().getScore());
    }
}
