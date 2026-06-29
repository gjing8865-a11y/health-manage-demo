package com.example.healthmanager.domain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HeartRateAlertPolicyTest {
    @Test
    public void normalHeartRateClearsAlertState() {
        HeartRateAlertState current = new HeartRateAlertState(1000L, true);

        HeartRateAlertDecision decision = HeartRateAlertPolicy.INSTANCE.onSample(
                80,
                2000L,
                current,
                HeartRateAlertPolicy.DEFAULT_HIGH_RATE_THRESHOLD_BPM,
                HeartRateAlertPolicy.DEFAULT_SUSTAINED_DURATION_MS
        );

        assertEquals(0L, decision.getState().getHighRateStartedAtMillis());
        assertFalse(decision.getState().getVisible());
        assertFalse(decision.getShouldVibrate());
    }

    @Test
    public void highHeartRateStartsTimerWithoutAlertingImmediately() {
        HeartRateAlertDecision decision = HeartRateAlertPolicy.INSTANCE.onSample(
                130,
                1000L,
                new HeartRateAlertState(0L, false),
                HeartRateAlertPolicy.DEFAULT_HIGH_RATE_THRESHOLD_BPM,
                HeartRateAlertPolicy.DEFAULT_SUSTAINED_DURATION_MS
        );

        assertEquals(1000L, decision.getState().getHighRateStartedAtMillis());
        assertFalse(decision.getState().getVisible());
        assertFalse(decision.getShouldVibrate());
    }

    @Test
    public void sustainedHighHeartRateShowsAlertAndVibratesOnce() {
        HeartRateAlertDecision firstAlert = HeartRateAlertPolicy.INSTANCE.onSample(
                130,
                602000L,
                new HeartRateAlertState(1000L, false),
                HeartRateAlertPolicy.DEFAULT_HIGH_RATE_THRESHOLD_BPM,
                HeartRateAlertPolicy.DEFAULT_SUSTAINED_DURATION_MS
        );

        assertTrue(firstAlert.getState().getVisible());
        assertTrue(firstAlert.getShouldVibrate());

        HeartRateAlertDecision repeatedAlert = HeartRateAlertPolicy.INSTANCE.onSample(
                130,
                603000L,
                firstAlert.getState(),
                HeartRateAlertPolicy.DEFAULT_HIGH_RATE_THRESHOLD_BPM,
                HeartRateAlertPolicy.DEFAULT_SUSTAINED_DURATION_MS
        );

        assertTrue(repeatedAlert.getState().getVisible());
        assertFalse(repeatedAlert.getShouldVibrate());
    }

    @Test
    public void dismissHidesAlertAndRestartsTimer() {
        HeartRateAlertState dismissed = HeartRateAlertPolicy.INSTANCE.dismiss(
                new HeartRateAlertState(1000L, true),
                700000L
        );

        assertEquals(700000L, dismissed.getHighRateStartedAtMillis());
        assertFalse(dismissed.getVisible());
    }
}
