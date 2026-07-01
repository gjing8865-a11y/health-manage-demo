package com.example.healthmanager.device;

import org.junit.Test;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Stm32ConnectionMessageFormatterTest {
    @Test
    public void formatErrorMapsCommonHardwareConnectionProblems() {
        assertEquals(
                "\u7cfb\u7edf\u62e6\u622a\u4e86\u672c\u5730 HTTP\uff0c\u8bf7\u5b89\u88c5\u65b0\u7248\u540e\u91cd\u8bd5",
                Stm32ConnectionMessageFormatter.INSTANCE.formatError(
                        new IllegalStateException("CLEARTEXT communication not permitted")
                )
        );
        assertEquals(
                "20 \u79d2\u5185\u6ca1\u6709\u6536\u5230\u624b\u73af\u63a8\u9001\u7684\u6709\u6548 JSON\uff1b\u8bf7\u786e\u8ba4\u624b\u73af\u505c\u7559\u5728\u5fc3\u7387\u9875\u3001\u5df2\u70b9\u51fb Survey\uff0c\u5e76\u4e14 App \u5df2\u8fde\u63a5 HRB_AP",
                Stm32ConnectionMessageFormatter.INSTANCE.formatError(
                        new IllegalStateException("20 \u79d2\u5185\u6ca1\u6709\u6536\u5230\u624b\u73af\u63a8\u9001")
                )
        );
        assertEquals(
                "\u5df2\u6536\u5230\u8fc7\u6570\u636e\uff0c\u4f46\u540e\u7eed 15 \u79d2\u6ca1\u6709\u65b0\u63a8\u9001\uff1bApp \u6b63\u5728\u91cd\u8fde\u6570\u636e\u901a\u9053",
                Stm32ConnectionMessageFormatter.INSTANCE.formatError(
                        new IllegalStateException("\u8fde\u7eed 15 \u79d2\u6ca1\u6709\u6536\u5230\u65b0\u7684\u5fc3\u7387\u63a8\u9001")
                )
        );
    }

    @Test
    public void formatErrorHandlesSocketTimeoutsAndFallbackMessages() {
        assertEquals(
                "\u8fde\u63a5 ESP-01S TCP \u6570\u636e\u7aef\u53e3\u8d85\u65f6\uff0c\u8bf7\u786e\u8ba4\u5355\u7247\u673a\u5df2\u542f\u52a8 AT+CIPSERVER=1,8080",
                Stm32ConnectionMessageFormatter.INSTANCE.formatError(new SocketTimeoutException("timeout"))
        );
        assertEquals(
                "\u70ed\u70b9\u8fde\u4e0a\u4e86\uff0c\u4f46 ESP-01S \u6ca1\u6709\u4e3b\u52a8\u63a8\u9001 JSON\uff1b\u8bf7\u786e\u8ba4\u624b\u73af\u5fc3\u7387\u9875\u6b63\u5728 Survey \u6d4b\u91cf",
                Stm32ConnectionMessageFormatter.INSTANCE.formatError(
                        new IllegalStateException("wrapped", new SocketTimeoutException("timeout"))
                )
        );
        assertEquals(
                "\u70ed\u70b9\u8fde\u4e0a\u4e86\uff0c\u4f46 ESP-01S \u7684 TCP 8080 \u6570\u636e\u7aef\u53e3\u6ca1\u6709\u6253\u5f00",
                Stm32ConnectionMessageFormatter.INSTANCE.formatError(new IllegalStateException("ECONNREFUSED"))
        );
        assertEquals(
                "custom failure",
                Stm32ConnectionMessageFormatter.INSTANCE.formatError(new IllegalStateException("custom failure"))
        );
    }

    @Test
    public void summarizeAttemptsLimitsLongUrlLists() {
        List<String> urls = new ArrayList<>();
        for (int index = 1; index <= 12; index++) {
            urls.add("tcp://192.168.4." + index + ":8080");
        }

        String summary = Stm32ConnectionMessageFormatter.INSTANCE.summarizeAttempts(urls, 10);

        assertTrue(summary.contains("tcp://192.168.4.1:8080"));
        assertTrue(summary.contains("tcp://192.168.4.10:8080"));
        assertTrue(summary.endsWith("\u7b49 12 \u4e2a\u5730\u5740"));
    }

    @Test
    public void summarizeAttemptsKeepsShortUrlListsComplete() {
        String summary = Stm32ConnectionMessageFormatter.INSTANCE.summarizeAttempts(
                Arrays.asList("tcp://192.168.4.1:8080", "http://192.168.4.1/data"),
                10
        );

        assertEquals("tcp://192.168.4.1:8080, http://192.168.4.1/data", summary);
    }
}
