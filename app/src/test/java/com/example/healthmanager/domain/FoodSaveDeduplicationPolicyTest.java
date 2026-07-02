package com.example.healthmanager.domain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FoodSaveDeduplicationPolicyTest {
    @Test
    public void fingerprintNormalizesMealAndFoodNames() {
        assertEquals(
                "午餐|鸡胸肉|180",
                FoodSaveDeduplicationPolicy.INSTANCE.fingerprint(" 午餐 ", " 鸡胸肉 ", 180)
        );
    }

    @Test
    public void firstSaveIsAllowedAndUpdatesState() {
        FoodSaveDeduplicationDecision decision = FoodSaveDeduplicationPolicy.INSTANCE.evaluate(
                new FoodSaveDeduplicationState(),
                "早餐",
                "燕麦",
                210,
                1_000L,
                FoodSaveDeduplicationPolicy.DEFAULT_DUPLICATE_WINDOW_MS
        );

        assertTrue(decision.getShouldSave());
        assertEquals("早餐|燕麦|210", decision.getFingerprint());
        assertEquals("早餐|燕麦|210", decision.getNextState().getLastFingerprint());
        assertEquals(1_000L, decision.getNextState().getLastSavedAtMillis());
    }

    @Test
    public void duplicateWithinWindowIsRejectedWithoutChangingState() {
        FoodSaveDeduplicationState state = new FoodSaveDeduplicationState("早餐|燕麦|210", 1_000L);

        FoodSaveDeduplicationDecision decision = FoodSaveDeduplicationPolicy.INSTANCE.evaluate(
                state,
                "早餐",
                "燕麦",
                210,
                60_000L,
                FoodSaveDeduplicationPolicy.DEFAULT_DUPLICATE_WINDOW_MS
        );

        assertFalse(decision.getShouldSave());
        assertEquals(state, decision.getNextState());
    }

    @Test
    public void sameFoodAfterWindowOrDifferentFoodIsAllowed() {
        FoodSaveDeduplicationState state = new FoodSaveDeduplicationState("早餐|燕麦|210", 1_000L);

        FoodSaveDeduplicationDecision afterWindow = FoodSaveDeduplicationPolicy.INSTANCE.evaluate(
                state,
                "早餐",
                "燕麦",
                210,
                130_000L,
                FoodSaveDeduplicationPolicy.DEFAULT_DUPLICATE_WINDOW_MS
        );
        assertTrue(afterWindow.getShouldSave());

        FoodSaveDeduplicationDecision differentMeal = FoodSaveDeduplicationPolicy.INSTANCE.evaluate(
                state,
                "午餐",
                "燕麦",
                210,
                60_000L,
                FoodSaveDeduplicationPolicy.DEFAULT_DUPLICATE_WINDOW_MS
        );
        assertTrue(differentMeal.getShouldSave());

        FoodSaveDeduplicationDecision differentKcal = FoodSaveDeduplicationPolicy.INSTANCE.evaluate(
                state,
                "早餐",
                "燕麦",
                260,
                60_000L,
                FoodSaveDeduplicationPolicy.DEFAULT_DUPLICATE_WINDOW_MS
        );
        assertTrue(differentKcal.getShouldSave());
    }
}
