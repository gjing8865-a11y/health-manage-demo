package com.example.healthmanager.device

import com.example.healthmanager.viewmodel.SleepHardwareDetails
import org.json.JSONArray
import org.json.JSONObject

data class HardwareSleepPayload(
    val score: Int,
    val dataPoints: List<Float>,
    val details: SleepHardwareDetails = SleepHardwareDetails()
)

data class Stm32DevicePayload(
    val heartRate: Int,
    val bloodOxygen: Int,
    val steps: Int,
    val batteryLevel: Int?,
    val weeklySteps: List<Int>,
    val sleepPayload: HardwareSleepPayload?,
    val isHeartPacket: Boolean = false,
    val hasHeartRate: Boolean = false,
    val hasBloodOxygen: Boolean = false,
    val hasSteps: Boolean = false
)

object Stm32PayloadParser {
    fun parse(jsonText: String): Stm32DevicePayload =
        parse(JSONObject(jsonText))

    fun parse(json: JSONObject): Stm32DevicePayload {
        val weeklyList = json.optJSONArray("weeklySteps")?.let { weeklySteps ->
            buildList {
                for (index in 0 until weeklySteps.length()) {
                    add(weeklySteps.optInt(index, 0))
                }
            }
        }.orEmpty()
        val heartRateValue = json.optIntFromKeys("heartRate", "hr", "heart_rate")
        val bloodOxygenValue = json.optIntFromKeys("spo2", "bloodOxygen", "blood_oxygen")
        val stepsValue = json.optIntFromKeys("steps", "step", "stepCount")
        val heartRate = heartRateValue ?: 0
        val bloodOxygen = bloodOxygenValue ?: 0
        val packetType = json.optString("type").trim()
        val isHeartPacket = packetType.equals("heart", ignoreCase = true) ||
                json.has("hr") ||
                json.has("heartRate") ||
                json.has("heart_rate") ||
                json.has("spo2") ||
                json.has("bloodOxygen") ||
                json.has("blood_oxygen")

        return Stm32DevicePayload(
            heartRate = heartRate,
            bloodOxygen = bloodOxygen,
            steps = stepsValue ?: 0,
            batteryLevel = json.optIntFromKeys("batteryLevel", "battery", "power")
                ?.coerceIn(0, 100),
            weeklySteps = weeklyList,
            sleepPayload = parseHardwareSleepPayload(json),
            isHeartPacket = isHeartPacket,
            hasHeartRate = heartRateValue != null,
            hasBloodOxygen = bloodOxygenValue != null,
            hasSteps = stepsValue != null
        )
    }

    private fun parseHardwareSleepPayload(json: JSONObject): HardwareSleepPayload? {
        val nestedSleepObject = sequenceOf(
            json.optJSONObject("sleep"),
            json.optJSONObject("sleepSummary"),
            json.optJSONObject("sleepResult")
        ).firstOrNull()

        val score = nestedSleepObject?.optIntFromKeys("score", "sleepScore")
            ?: json.optIntFromKeys("sleepScore", "sleep_score", "sleepResultScore")
            ?: return null

        val dataPoints = nestedSleepObject?.optFloatListFromKeys(
            "dataPoints",
            "sleepData",
            "sleepStages",
            "stages",
            "waveform"
        ).orEmpty().ifEmpty {
            json.optFloatListFromKeys(
                "sleepData",
                "sleep_data",
                "sleepStages",
                "stages",
                "dataPoints",
                "sleepWaveform"
            )
        }

        val details = SleepHardwareDetails(
            bedTime = nestedSleepObject?.optStringFromKeys(
                "bedTime",
                "bedtime",
                "sleepTime",
                "sleepStart",
                "fallAsleepTime"
            ) ?: json.optStringFromKeys(
                "bedTime",
                "bedtime",
                "sleepTime",
                "sleepStart",
                "fallAsleepTime"
            ),
            wakeTime = nestedSleepObject?.optStringFromKeys(
                "wakeTime",
                "wakeUpTime",
                "getUpTime",
                "sleepEnd",
                "endTime"
            ) ?: json.optStringFromKeys(
                "wakeTime",
                "wakeUpTime",
                "getUpTime",
                "sleepEnd",
                "endTime"
            ),
            deepSleepMinutes = nestedSleepObject?.optIntFromKeys(
                "deepSleepMinutes",
                "deepSleepDuration",
                "deepSleep",
                "deepSleepMins"
            ) ?: json.optIntFromKeys(
                "deepSleepMinutes",
                "deepSleepDuration",
                "deepSleep",
                "deepSleepMins"
            ),
            wakeCount = nestedSleepObject?.optIntFromKeys(
                "wakeCount",
                "awakeCount",
                "awakenings",
                "awakeTimes"
            ) ?: json.optIntFromKeys(
                "wakeCount",
                "awakeCount",
                "awakenings",
                "awakeTimes"
            )
        )

        return HardwareSleepPayload(
            score = score.coerceIn(0, 100),
            dataPoints = dataPoints,
            details = details
        )
    }

    private fun JSONObject.optIntFromKeys(vararg keys: String): Int? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            when (val rawValue = opt(key)) {
                is Number -> return rawValue.toInt()
                is String -> rawValue.trim().toIntOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun JSONObject.optStringFromKeys(vararg keys: String): String? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val rawValue = optString(key).trim()
            if (rawValue.isNotEmpty()) {
                return rawValue
            }
        }
        return null
    }

    private fun JSONObject.optFloatListFromKeys(vararg keys: String): List<Float> {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val parsed = parseFloatList(opt(key))
            if (parsed.isNotEmpty()) {
                return parsed
            }
        }
        return emptyList()
    }

    private fun parseFloatList(rawValue: Any?): List<Float> {
        return when (rawValue) {
            is JSONArray -> buildList {
                for (index in 0 until rawValue.length()) {
                    when (val item = rawValue.opt(index)) {
                        is Number -> add(item.toFloat())
                        is String -> item.trim().toFloatOrNull()?.let(::add)
                    }
                }
            }

            is String -> rawValue
                .trim()
                .removePrefix("[")
                .removeSuffix("]")
                .split(',', ';', '|')
                .mapNotNull { it.trim().toFloatOrNull() }

            else -> emptyList()
        }
    }
}
