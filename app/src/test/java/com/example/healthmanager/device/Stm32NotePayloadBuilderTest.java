package com.example.healthmanager.device;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Stm32NotePayloadBuilderTest {
    @Test
    public void buildCreatesLineDelimitedNotePayload() throws Exception {
        String payload = Stm32NotePayloadBuilder.INSTANCE.build(
                "\u4eca\u5929\u591a\u559d\u6c34",
                "demo-account",
                1_700_000_000_000L
        );

        assertTrue(payload.endsWith("\r\n"));

        JSONObject json = new JSONObject(payload.trim());
        assertEquals("note", json.getString("type"));
        assertEquals("\u4eca\u5929\u591a\u559d\u6c34", json.getString("content"));
        assertEquals("demo-account", json.getString("account"));
        assertEquals(1_700_000_000_000L, json.getLong("timestamp"));
    }

    @Test
    public void buildEscapesSpecialCharactersInContent() throws Exception {
        String payload = Stm32NotePayloadBuilder.INSTANCE.build(
                "line1\nline2 \"quoted\"",
                "demo",
                42L
        );

        JSONObject json = new JSONObject(payload.trim());
        assertEquals("line1\nline2 \"quoted\"", json.getString("content"));
    }
}
