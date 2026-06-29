package com.example.healthmanager.domain;

import com.example.healthmanager.model.ExerciseRecord;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class ExerciseSummaryCalculatorTest {
    @Test
    public void latestReturnsEmptySummaryWhenRecordIsMissing() {
        LatestExerciseSummary summary = ExerciseSummaryCalculator.INSTANCE.latest(null, "Outdoor Run");

        assertEquals("Outdoor Run", summary.getType());
        assertEquals(0f, summary.getDistanceKm(), 0.001f);
        assertEquals(0, summary.getDurationSeconds());
        assertEquals(0, summary.getCalories());
    }

    @Test
    public void weeklyAggregatesDurationDistanceAndCount() {
        ExerciseRecord first = new ExerciseRecord(
                1,
                "user-1",
                "Run",
                3.2f,
                1500,
                180,
                1000L
        );
        ExerciseRecord second = new ExerciseRecord(
                2,
                "user-1",
                "Walk",
                1.8f,
                900,
                80,
                2000L
        );

        WeeklyExerciseSummary summary = ExerciseSummaryCalculator.INSTANCE.weekly(
                Arrays.asList(first, second)
        );

        assertEquals(40, summary.getDurationMinutes());
        assertEquals(5.0f, summary.getDistanceKm(), 0.001f);
        assertEquals(2, summary.getCount());
    }

    @Test
    public void weeklyHandlesNoRecords() {
        WeeklyExerciseSummary summary = ExerciseSummaryCalculator.INSTANCE.weekly(Collections.emptyList());

        assertEquals(0, summary.getDurationMinutes());
        assertEquals(0f, summary.getDistanceKm(), 0.001f);
        assertEquals(0, summary.getCount());
    }
}
