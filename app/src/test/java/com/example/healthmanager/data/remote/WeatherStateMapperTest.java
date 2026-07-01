package com.example.healthmanager.data.remote;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WeatherStateMapperTest {
    @Test
    public void parseBuildsWeatherUiStateFromValidPayload() throws Exception {
        JSONObject json = new JSONObject("{" +
                "\"city\":\"\\u676d\\u5dde\\u5e02\"," +
                "\"weather\":\"\\u6674\"," +
                "\"weather_icon\":\"sun\"," +
                "\"temperature\":26," +
                "\"temp_max\":31," +
                "\"temp_min\":22," +
                "\"aqi\":42," +
                "\"aqi_category\":\"\\u4f18\"," +
                "\"feels_like\":27.5," +
                "\"visibility\":12.3," +
                "\"pressure\":1008.2," +
                "\"uv\":5.6," +
                "\"humidity\":58," +
                "\"wind_direction\":\"\\u4e1c\\u5357\\u98ce\"," +
                "\"wind_power\":\"3\\u7ea7\"," +
                "\"forecast\":[{\"week\":\"\\u5468\\u4e00\",\"temp_max\":31,\"temp_min\":22}]," +
                "\"hourly_forecast\":[{\"time\":\"09:00\",\"temperature\":26}]" +
                "}");

        WeatherUiState state = WeatherStateMapper.INSTANCE.parse(
                json,
                "\u676d\u5dde",
                "\u676d\u5dde"
        );

        assertEquals("\u676d\u5dde", state.getCity());
        assertEquals("\u6674", state.getWeather());
        assertEquals("sun", state.getWeatherIcon());
        assertEquals(26, state.getTemperature());
        assertEquals(31, state.getTempMax());
        assertEquals(22, state.getTempMin());
        assertEquals(42, state.getAqi());
        assertEquals("\u4f18", state.getAqiCategory());
        assertEquals(27.5, state.getFeelsLike(), 0.001);
        assertEquals(12.3, state.getVisibility(), 0.001);
        assertEquals(1008.2, state.getPressure(), 0.001);
        assertEquals(5.6, state.getUv(), 0.001);
        assertEquals(58, state.getHumidity());
        assertEquals("\u4e1c\u5357\u98ce", state.getWindDirection());
        assertEquals("3\u7ea7", state.getWindPower());
        JSONObject hourly = new org.json.JSONArray(state.getHourlyForecastJson()).getJSONObject(0);
        assertEquals("09:00", hourly.getString("time"));
        assertEquals(26, hourly.getInt("temperature"));

        JSONObject forecast = new org.json.JSONArray(state.getForecastJson()).getJSONObject(0);
        assertEquals("\u5468\u4e00", forecast.getString("week"));
        assertEquals(31, forecast.getInt("temp_max"));
        assertEquals(22, forecast.getInt("temp_min"));
    }

    @Test
    public void parseUsesSafeDefaultsForOptionalFields() throws Exception {
        JSONObject json = new JSONObject("{" +
                "\"city\":\"\\u676d\\u5dde\"," +
                "\"weather\":\"\\u591a\\u4e91\"," +
                "\"temperature\":18," +
                "\"aqi\":50," +
                "\"humidity\":62," +
                "\"wind_direction\":\"\\u4e1c\\u98ce\"," +
                "\"wind_power\":\"2\\u7ea7\"" +
                "}");

        WeatherUiState state = WeatherStateMapper.INSTANCE.parse(
                json,
                "\u676d\u5dde",
                "\u676d\u5dde"
        );

        assertEquals(18, state.getTempMax());
        assertEquals(18, state.getTempMin());
        assertEquals(18.0, state.getFeelsLike(), 0.001);
        assertEquals("[]", state.getForecastJson());
        assertEquals("[]", state.getHourlyForecastJson());
    }

    @Test
    public void parseRejectsInvalidAndEmptyPayloads() throws Exception {
        assertNull(WeatherStateMapper.INSTANCE.parse(
                new JSONObject("{\"weather\":\"\\u6674\"}"),
                "\u676d\u5dde",
                "\u676d\u5dde"
        ));
        assertNull(WeatherStateMapper.INSTANCE.parse(
                new JSONObject("{\"weather\":\"\\u672a\\u77e5\\u5929\\u6c14\",\"temperature\":20}"),
                "\u676d\u5dde",
                "\u676d\u5dde"
        ));
        assertNull(WeatherStateMapper.INSTANCE.parse(
                new JSONObject("{" +
                        "\"city\":\"\\u5e7f\\u4e1c\"," +
                        "\"weather\":\"\\u6674\"," +
                        "\"temperature\":25," +
                        "\"aqi\":20" +
                        "}"),
                "\u5e7f\u5dde",
                "\u5e7f\u5dde"
        ));
        assertNull(WeatherStateMapper.INSTANCE.parse(
                new JSONObject("{" +
                        "\"city\":\"\\u676d\\u5dde\"," +
                        "\"weather\":\"\\u6674\"," +
                        "\"temperature\":0," +
                        "\"temp_max\":0," +
                        "\"temp_min\":0," +
                        "\"aqi\":0," +
                        "\"aqi_category\":\"\\u672a\\u77e5\"," +
                        "\"humidity\":0" +
                        "}"),
                "\u676d\u5dde",
                "\u676d\u5dde"
        ));
    }
}
