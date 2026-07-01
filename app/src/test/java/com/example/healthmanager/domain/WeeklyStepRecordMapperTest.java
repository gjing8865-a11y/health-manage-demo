package com.example.healthmanager.domain;

import com.example.healthmanager.model.WeeklyStepRecord;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WeeklyStepRecordMapperTest {
    @Test
    public void emptyWeekStepsReturnsSevenZeros() {
        List<Integer> steps = WeeklyStepRecordMapper.INSTANCE.emptyWeekSteps();

        assertEquals(7, steps.size());
        assertEquals(Arrays.asList(0, 0, 0, 0, 0, 0, 0), steps);
    }

    @Test
    public void hasCompleteWeekChecksSevenDayPayloads() {
        assertTrue(WeeklyStepRecordMapper.INSTANCE.hasCompleteWeek(Arrays.asList(1, 2, 3, 4, 5, 6, 7)));
        assertFalse(WeeklyStepRecordMapper.INSTANCE.hasCompleteWeek(Arrays.asList(1, 2, 3)));
        assertFalse(WeeklyStepRecordMapper.INSTANCE.hasCompleteWeek(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8)));
    }

    @Test
    public void mapsWeeklyStepRecordToDayList() {
        WeeklyStepRecord record = new WeeklyStepRecord(
                0,
                "demo",
                "7\u67086\u65e5 - 7\u670812\u65e5",
                1000,
                2000,
                3000,
                4000,
                5000,
                6000,
                7000,
                123L
        );

        assertEquals(
                Arrays.asList(1000, 2000, 3000, 4000, 5000, 6000, 7000),
                WeeklyStepRecordMapper.INSTANCE.toDayList(record)
        );
    }

    @Test
    public void buildsWeeklyStepRecordFromCompleteDayList() {
        WeeklyStepRecord record = WeeklyStepRecordMapper.INSTANCE.toRecord(
                "demo",
                "7\u67086\u65e5 - 7\u670812\u65e5",
                Arrays.asList(1100, 2200, 3300, 4400, 5500, 6600, 7700),
                456L
        );

        assertEquals("demo", record.getUserAccount());
        assertEquals("7\u67086\u65e5 - 7\u670812\u65e5", record.getWeekRange());
        assertEquals(1100, record.getDay1());
        assertEquals(7700, record.getDay7());
        assertEquals(456L, record.getUpdatedAt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void toRecordRejectsIncompleteDayLists() {
        WeeklyStepRecordMapper.INSTANCE.toRecord(
                "demo",
                "7\u67086\u65e5 - 7\u670812\u65e5",
                Arrays.asList(1000, 2000, 3000),
                456L
        );
    }
}
