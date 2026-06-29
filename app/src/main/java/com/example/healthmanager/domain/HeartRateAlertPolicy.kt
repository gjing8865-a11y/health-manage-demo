package com.example.healthmanager.domain

data class HeartRateAlertState(
    val highRateStartedAtMillis: Long = 0L,
    val visible: Boolean = false
)

data class HeartRateAlertDecision(
    val state: HeartRateAlertState,
    val shouldVibrate: Boolean
)

object HeartRateAlertPolicy {
    const val DEFAULT_HIGH_RATE_THRESHOLD_BPM = 120
    const val DEFAULT_SUSTAINED_DURATION_MS = 10 * 60 * 1000L

    fun onSample(
        rateBpm: Int,
        nowMillis: Long,
        currentState: HeartRateAlertState,
        highRateThresholdBpm: Int = DEFAULT_HIGH_RATE_THRESHOLD_BPM,
        sustainedDurationMillis: Long = DEFAULT_SUSTAINED_DURATION_MS
    ): HeartRateAlertDecision {
        if (rateBpm <= highRateThresholdBpm) {
            return HeartRateAlertDecision(
                state = HeartRateAlertState(),
                shouldVibrate = false
            )
        }

        val startedAt = if (currentState.highRateStartedAtMillis == 0L) {
            nowMillis
        } else {
            currentState.highRateStartedAtMillis
        }
        val shouldShow = nowMillis - startedAt > sustainedDurationMillis
        val shouldVibrate = shouldShow && !currentState.visible

        return HeartRateAlertDecision(
            state = HeartRateAlertState(
                highRateStartedAtMillis = startedAt,
                visible = shouldShow || currentState.visible
            ),
            shouldVibrate = shouldVibrate
        )
    }

    fun dismiss(currentState: HeartRateAlertState, nowMillis: Long): HeartRateAlertState {
        return currentState.copy(
            highRateStartedAtMillis = nowMillis,
            visible = false
        )
    }
}
