package com.example.healthmanager.domain;

import com.example.healthmanager.model.FoodRecord;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class FoodStatsCalculatorTest {
    @Test
    public void buildCalculatesTodayNutritionAndOverallCalories() {
        FoodRecord breakfast = food(
                1,
                "user-1",
                "Oatmeal",
                320,
                "6\u670829\u65e5",
                45,
                12,
                8
        );
        FoodRecord lunch = food(
                2,
                "user-1",
                "Chicken Rice",
                560,
                "6\u670829\u65e5",
                70,
                32,
                14
        );
        FoodRecord yesterday = food(
                3,
                "user-1",
                "Noodles",
                480,
                "6\u670828\u65e5",
                80,
                18,
                10
        );

        FoodNutritionSummary summary = FoodStatsCalculator.INSTANCE.build(
                Arrays.asList(yesterday, breakfast, lunch),
                "6\u670829\u65e5"
        );

        assertEquals(1360, summary.getTotalKcal());
        assertEquals(880, summary.getTodayTotalKcal());
        assertEquals(115, summary.getTodayCarbs());
        assertEquals(44, summary.getTodayProtein());
        assertEquals(22, summary.getTodayFat());
        assertEquals(2, summary.getDailyKcalStats().size());
        assertEquals("6\u670828\u65e5", summary.getDailyKcalStats().get(0).getDate());
        assertEquals(480, summary.getDailyKcalStats().get(0).getTotalKcal());
        assertEquals("6\u670829\u65e5", summary.getDailyKcalStats().get(1).getDate());
        assertEquals(880, summary.getDailyKcalStats().get(1).getTotalKcal());
    }

    @Test
    public void parseMonthDayToSortableKeyHandlesInvalidLabels() {
        assertEquals(629, FoodStatsCalculator.INSTANCE.parseMonthDayToSortableKey("6\u670829\u65e5"));
        assertEquals(0, FoodStatsCalculator.INSTANCE.parseMonthDayToSortableKey("bad-date"));
    }

    private static FoodRecord food(
            int id,
            String user,
            String name,
            int kcal,
            String date,
            int carbs,
            int protein,
            int fat
    ) {
        return new FoodRecord(
                id,
                user,
                name,
                kcal,
                "",
                "08:00",
                date,
                "Meal",
                carbs,
                protein,
                fat
        );
    }
}
