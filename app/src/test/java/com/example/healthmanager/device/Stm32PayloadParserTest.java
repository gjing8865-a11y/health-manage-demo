package com.example.healthmanager.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class Stm32PayloadParserTest {

    @Test
    public void parseVitalsPacketSupportsDeviceAliases() {
        String json = "{"
                + "\"type\":\"heart\","
                + "\"hr\":\"78\","
                + "\"spo2\":98,"
                + "\"stepCount\":1234,"
                + "\"battery\":88,"
                + "\"weeklySteps\":[1000,2000,3000,4000,5000,6000,7000]"
                + "}";

        Stm32DevicePayload payload = Stm32PayloadParser.INSTANCE.parse(json);

        assertEquals(78, payload.getHeartRate());
        assertEquals(98, payload.getBloodOxygen());
        assertEquals(1234, payload.getSteps());
        assertEquals(Integer.valueOf(88), payload.getBatteryLevel());
        assertEquals(7, payload.getWeeklySteps().size());
        assertEquals(7000, payload.getWeeklySteps().get(6).intValue());
        assertTrue(payload.isHeartPacket());
        assertTrue(payload.getHasHeartRate());
        assertTrue(payload.getHasBloodOxygen());
        assertTrue(payload.getHasSteps());
    }

    @Test
    public void parseNestedSleepPayloadClampsScoreAndReadsDetails() {
        String json = "{"
                + "\"heartRate\":72,"
                + "\"bloodOxygen\":97,"
                + "\"steps\":6400,"
                + "\"sleep\":{"
                + "\"sleepScore\":120,"
                + "\"sleepData\":\"[4;6|8]\","
                + "\"bedTime\":\"23:18\","
                + "\"wakeTime\":\"07:06\","
                + "\"deepSleepMinutes\":\"124\","
                + "\"wakeCount\":2"
                + "}"
                + "}";

        Stm32DevicePayload payload = Stm32PayloadParser.INSTANCE.parse(json);
        HardwareSleepPayload sleepPayload = payload.getSleepPayload();

        assertNotNull(sleepPayload);
        assertEquals(100, sleepPayload.getScore());
        assertEquals(3, sleepPayload.getDataPoints().size());
        assertEquals(4f, sleepPayload.getDataPoints().get(0), 0.001f);
        assertEquals("23:18", sleepPayload.getDetails().getBedTime());
        assertEquals("07:06", sleepPayload.getDetails().getWakeTime());
        assertEquals(Integer.valueOf(124), sleepPayload.getDetails().getDeepSleepMinutes());
        assertEquals(Integer.valueOf(2), sleepPayload.getDetails().getWakeCount());
    }
}
