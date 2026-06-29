package com.example.healthmanager.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONObject;
import org.junit.Test;

public class WeatherRemoteDataSourceTest {

    @Test
    public void fetchWeatherJsonEncodesCityAndParsesResponse() throws Exception {
        final String[] requestedUrl = new String[1];
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    requestedUrl[0] = chain.request().url().toString();
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create(
                                    "{\"weather\":\"晴\",\"temperature\":26}",
                                    MediaType.parse("application/json")
                            ))
                            .build();
                })
                .build();

        WeatherRemoteDataSource dataSource = new WeatherRemoteDataSource(client, "weather-key");

        JSONObject json = dataSource.fetchWeatherJson("杭州");

        assertEquals("晴", json.getString("weather"));
        assertEquals(26, json.getInt("temperature"));
        assertTrue(requestedUrl[0].contains("city=%E6%9D%AD%E5%B7%9E"));
        assertTrue(requestedUrl[0].contains("key=weather-key"));
    }

    @Test
    public void fetchWeatherJsonRejectsHtmlResponses() {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create("<html></html>", MediaType.parse("text/html")))
                        .build())
                .build();

        WeatherRemoteDataSource dataSource = new WeatherRemoteDataSource(client, "weather-key");

        assertThrows(IllegalStateException.class, () -> dataSource.fetchWeatherJson("杭州"));
    }

    @Test
    public void hasApiKeyReflectsConfiguration() {
        assertFalse(new WeatherRemoteDataSource(new OkHttpClient(), "").getHasApiKey());
        assertTrue(new WeatherRemoteDataSource(new OkHttpClient(), "key").getHasApiKey());
    }
}
