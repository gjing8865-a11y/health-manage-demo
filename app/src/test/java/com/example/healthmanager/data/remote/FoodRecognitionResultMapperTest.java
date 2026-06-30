package com.example.healthmanager.data.remote;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FoodRecognitionResultMapperTest {
    @Test
    public void parseAndSanitizeItems_normalizesNamesAndNutritionFallbacks() throws Exception {
        JSONObject result = new JSONObject("{\"scene_type\":\"combo_meal\",\"foods\":[" +
                "{\"name\":\"1. 杯装奶茶饮料\",\"kcal\":\"160-200\",\"icon\":\"🍽️\"}," +
                "{\"name\":\"烤鱼堡\",\"kcal\":0,\"carbs\":0,\"protein\":0,\"fat\":0}," +
                "{\"name\":\"未知食物\",\"kcal\":10}" +
                "]}");

        List<RecognizedFoodItem> sanitized = FoodRecognitionResultMapper.INSTANCE.sanitize(
                FoodRecognitionResultMapper.INSTANCE.parseItems(result)
        );

        assertEquals(2, sanitized.size());
        assertEquals("奶茶", sanitized.get(0).getName());
        assertEquals(180, sanitized.get(0).getKcal());
        assertEquals("🥤", sanitized.get(0).getIcon());
        assertEquals("热狗", sanitized.get(1).getName());
        assertEquals(280, sanitized.get(1).getKcal());
        assertEquals(25, sanitized.get(1).getCarbs());
    }

    @Test
    public void sceneTypeAndReviewRules_coverCommonFoodRecognitionBranches() throws Exception {
        assertEquals(
                "mixed_bowl",
                FoodRecognitionResultMapper.INSTANCE.extractSceneType(new JSONObject("{\"sceneType\":\"mixed bowl\"}"))
        );

        List<RecognizedFoodItem> burgerOnly = Arrays.asList(
                new RecognizedFoodItem("汉堡", 400, "🍔", 40, 18, 20)
        );
        List<RecognizedFoodItem> burgerAndDrink = Arrays.asList(
                new RecognizedFoodItem("汉堡", 400, "🍔", 40, 18, 20),
                new RecognizedFoodItem("奶茶", 180, "🥤", 30, 2, 5)
        );
        List<RecognizedFoodItem> richerCandidate = Arrays.asList(
                new RecognizedFoodItem("汉堡", 400, "🍔", 40, 18, 20),
                new RecognizedFoodItem("薯条", 260, "🍔", 32, 3, 12),
                new RecognizedFoodItem("奶茶", 180, "🥤", 30, 2, 5)
        );

        assertTrue(FoodRecognitionResultMapper.INSTANCE.needsCoverageReview(burgerOnly, "combo_meal"));
        assertTrue(FoodRecognitionResultMapper.INSTANCE.shouldRunDrinkCheck(burgerOnly, "combo_meal"));
        assertFalse(FoodRecognitionResultMapper.INSTANCE.shouldRunDrinkCheck(burgerAndDrink, "combo_meal"));
        assertTrue(FoodRecognitionResultMapper.INSTANCE.shouldAcceptInitialRecognition(richerCandidate, "combo_meal"));
        assertTrue(FoodRecognitionResultMapper.INSTANCE.shouldPreferCandidateFoods(burgerOnly, richerCandidate));
    }

    @Test
    public void drinkDetectionSupportsChineseAndEnglishNames() {
        assertTrue(FoodRecognitionResultMapper.INSTANCE.isDrinkFoodName("柠檬茶"));
        assertTrue(FoodRecognitionResultMapper.INSTANCE.isDrinkFoodName("iced coffee"));
        assertFalse(FoodRecognitionResultMapper.INSTANCE.isDrinkFoodName("鸡胸肉"));
    }
}
