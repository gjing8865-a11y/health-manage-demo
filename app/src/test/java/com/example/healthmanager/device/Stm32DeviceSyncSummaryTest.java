package com.example.healthmanager.device;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class Stm32DeviceSyncSummaryTest {
    @Test
    public void buildIncludesCompleteHardwarePayloadFields() {
        String summary = Stm32DeviceSyncSummary.INSTANCE.build(
                76,
                98,
                8210,
                86,
                91,
                Arrays.asList(6000, 7100, 8200, 9300, 10400, 9800, 8210),
                "08:05:09"
        );

        assertEquals(
                "同步完成\n" +
                        "心率: 76 bpm\n" +
                        "血氧: 98 %\n" +
                        "更新时间: 08:05:09\n" +
                        "步数: 8210\n" +
                        "电量: 86%\n" +
                        "睡眠评分: 91\n" +
                        "周步数: 6000, 7100, 8200, 9300, 10400, 9800, 8210",
                summary
        );
    }

    @Test
    public void buildExplainsEstimatedSleepAndMissingWeeklySteps() {
        String summary = Stm32DeviceSyncSummary.INSTANCE.build(
                72,
                97,
                2400,
                null,
                null,
                Collections.singletonList(2400),
                "21:30:00"
        );

        assertEquals(
                "同步完成\n" +
                        "心率: 72 bpm\n" +
                        "血氧: 97 %\n" +
                        "更新时间: 21:30:00\n" +
                        "步数: 2400\n" +
                        "睡眠数据: 已根据心率和步数自动估算\n" +
                        "周报数据: STM32 暂未返回完整的 7 天步数",
                summary
        );
    }
}
