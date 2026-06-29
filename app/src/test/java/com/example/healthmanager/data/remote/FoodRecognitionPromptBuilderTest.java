package com.example.healthmanager.data.remote;

import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

public class FoodRecognitionPromptBuilderTest {
    @Test
    public void recognitionPromptDefinesExpectedJsonContract() {
        String prompt = FoodRecognitionPromptBuilder.INSTANCE.recognitionPrompt();

        assertTrue(prompt.contains("scene_type"));
        assertTrue(prompt.contains("foods"));
        assertTrue(prompt.contains("kcal"));
        assertTrue(prompt.contains("carbs"));
        assertTrue(prompt.contains("protein"));
        assertTrue(prompt.contains("fat"));
    }

    @Test
    public void reviewPromptIncludesInitialResultForSecondPass() throws Exception {
        JSONObject initial = new JSONObject()
                .put("scene_type", "single_dish")
                .put("foods", "[]");

        String prompt = FoodRecognitionPromptBuilder.INSTANCE.reviewPrompt(initial);

        assertTrue(prompt.contains(initial.toString()));
        assertTrue(prompt.contains("single_dish"));
        assertTrue(prompt.contains("combo_meal"));
    }

    @Test
    public void coverageAndDrinkPromptsIncludeCurrentFoodNames() {
        String coverage = FoodRecognitionPromptBuilder.INSTANCE.coveragePrompt(
                Arrays.asList("\u70ed\u72d7", "\u5976\u8336")
        );
        String drinkOnly = FoodRecognitionPromptBuilder.INSTANCE.drinkOnlyPrompt(
                Arrays.asList("\u706b\u9e21\u9762")
        );

        assertTrue(coverage.contains("[\u70ed\u72d7, \u5976\u8336]"));
        assertTrue(coverage.contains("foods"));
        assertTrue(drinkOnly.contains("[\u706b\u9e21\u9762]"));
        assertTrue(drinkOnly.contains("{\"foods\": []}"));
    }
}
