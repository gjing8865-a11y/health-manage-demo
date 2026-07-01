package com.example.healthmanager.domain;

import com.example.healthmanager.model.SleepRecord;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SleepPresentationMapperTest {
    @Test
    public void parseStagePointsIgnoresBlankAndInvalidValues() {
        assertTrue(SleepPresentationMapper.INSTANCE.parseStagePoints("").isEmpty());

        List<Float> points = SleepPresentationMapper.INSTANCE.parseStagePoints("4.0, bad, 6, ,8.5");

        assertEquals(3, points.size());
        assertEquals(4.0f, points.get(0), 0.001f);
        assertEquals(6.0f, points.get(1), 0.001f);
        assertEquals(8.5f, points.get(2), 0.001f);
    }

    @Test
    public void adviceForScoreMatchesSleepScreenBuckets() {
        assertEquals(
                "\u7761\u7720\u8d28\u91cf\u6781\u4f73\uff0c\u7ee7\u7eed\u4fdd\u6301\uff01",
                SleepPresentationMapper.INSTANCE.adviceForScore(95)
        );
        assertEquals(
                "\u7761\u5f97\u8fd8\u4e0d\u9519\uff0c\u5efa\u8bae\u65e9\u70b9\u4f11\u606f\u3002",
                SleepPresentationMapper.INSTANCE.adviceForScore(75)
        );
        assertEquals(
                "\u7761\u7720\u8f83\u6d45\uff0c\u7761\u524d\u8bd5\u8bd5\u653e\u4e0b\u624b\u673a\u3002",
                SleepPresentationMapper.INSTANCE.adviceForScore(60)
        );
    }

    @Test
    public void toTrendSortsRecordsAndFormatsDateLabels() {
        List<SleepRecord> records = Arrays.asList(
                new SleepRecord(0, "demo", "2026-07-01", 82, "4,5,6", 300L),
                new SleepRecord(0, "demo", "2026-06-29", 76, "3,4,5", 100L),
                new SleepRecord(0, "demo", "2026-06-30", 91, "5,6,7", 200L)
        );

        List<SleepTrendPoint> trend = SleepPresentationMapper.INSTANCE.toTrend(records);

        assertEquals(3, trend.size());
        assertEquals("06/29", trend.get(0).getLabel());
        assertEquals(76, trend.get(0).getScore());
        assertEquals("06/30", trend.get(1).getLabel());
        assertEquals(91, trend.get(1).getScore());
        assertEquals("07/01", trend.get(2).getLabel());
        assertEquals(82, trend.get(2).getScore());
    }
}
