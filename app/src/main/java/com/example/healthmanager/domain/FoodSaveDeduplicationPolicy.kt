package com.example.healthmanager.domain

data class FoodSaveDeduplicationState(
    val lastFingerprint: String? = null,
    val lastSavedAtMillis: Long = 0L
)

data class FoodSaveDeduplicationDecision(
    val fingerprint: String,
    val shouldSave: Boolean,
    val nextState: FoodSaveDeduplicationState
)

object FoodSaveDeduplicationPolicy {
    const val DEFAULT_DUPLICATE_WINDOW_MS = 2 * 60 * 1000L

    fun fingerprint(
        mealType: String,
        foodName: String,
        kcal: Int
    ): String = "${mealType.trim()}|${foodName.trim()}|$kcal"

    fun evaluate(
        state: FoodSaveDeduplicationState,
        mealType: String,
        foodName: String,
        kcal: Int,
        nowMillis: Long,
        duplicateWindowMillis: Long = DEFAULT_DUPLICATE_WINDOW_MS
    ): FoodSaveDeduplicationDecision {
        val nextFingerprint = fingerprint(mealType, foodName, kcal)
        val isDuplicate = state.lastFingerprint == nextFingerprint &&
                nowMillis - state.lastSavedAtMillis < duplicateWindowMillis

        return if (isDuplicate) {
            FoodSaveDeduplicationDecision(
                fingerprint = nextFingerprint,
                shouldSave = false,
                nextState = state
            )
        } else {
            FoodSaveDeduplicationDecision(
                fingerprint = nextFingerprint,
                shouldSave = true,
                nextState = FoodSaveDeduplicationState(
                    lastFingerprint = nextFingerprint,
                    lastSavedAtMillis = nowMillis
                )
            )
        }
    }
}
