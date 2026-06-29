package com.example.healthmanager.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONObject;
import org.junit.Test;

public class FoodRecognitionRemoteDataSourceTest {

    @Test
    public void recognizeFoodJsonFallsBackToNextModelAndExtractsJson() throws Exception {
        AtomicInteger calls = new AtomicInteger(0);
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    int call = calls.incrementAndGet();
                    String body = call == 1
                            ? "{\"error\":\"temporary\"}"
                            : "{\"choices\":[{\"message\":{\"content\":\"```json\\n{\\\"foods\\\":[{\\\"name\\\":\\\"鸡胸肉\\\",\\\"kcal\\\":180}]}\\n```\"}}]}";

                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(call == 1 ? 500 : 200)
                            .message(call == 1 ? "Server Error" : "OK")
                            .body(ResponseBody.create(body, MediaType.parse("application/json")))
                            .build();
                })
                .build();

        FoodRecognitionRemoteDataSource dataSource =
                new FoodRecognitionRemoteDataSource(client, "test-key");

        JSONObject result = dataSource.recognizeFoodJson(
                "base64-image",
                "data:image/jpeg;base64,base64-image",
                "recognize food"
        );

        assertEquals(2, calls.get());
        assertEquals("鸡胸肉", result.getJSONArray("foods").getJSONObject(0).getString("name"));
        assertEquals(180, result.getJSONArray("foods").getJSONObject(0).getInt("kcal"));
    }

    @Test
    public void hasApiKeyReflectsConfiguration() {
        assertFalse(new FoodRecognitionRemoteDataSource(new OkHttpClient(), "").getHasApiKey());
        assertTrue(new FoodRecognitionRemoteDataSource(new OkHttpClient(), "key").getHasApiKey());
    }
}
