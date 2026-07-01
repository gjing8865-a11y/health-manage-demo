package com.example.healthmanager.domain

import com.example.healthmanager.model.WeeklyStepRecord

object WeeklyStepRecordMapper {
    const val DAYS_PER_WEEK = 7

    fun emptyWeekSteps(): List<Int> = List(DAYS_PER_WEEK) { 0 }

    fun hasCompleteWeek(steps: List<Int>): Boolean {
        return steps.size == DAYS_PER_WEEK
    }

    fun toDayList(record: WeeklyStepRecord): List<Int> {
        return listOf(
            record.day1,
            record.day2,
            record.day3,
            record.day4,
            record.day5,
            record.day6,
            record.day7
        )
    }

    fun toRecord(
        account: String,
        weekRange: String,
        steps: List<Int>,
        updatedAt: Long = System.currentTimeMillis()
    ): WeeklyStepRecord {
        require(hasCompleteWeek(steps)) {
            "Weekly step record requires exactly $DAYS_PER_WEEK day values"
        }

        return WeeklyStepRecord(
            userAccount = account,
            weekRange = weekRange,
            day1 = steps[0],
            day2 = steps[1],
            day3 = steps[2],
            day4 = steps[3],
            day5 = steps[4],
            day6 = steps[5],
            day7 = steps[6],
            updatedAt = updatedAt
        )
    }
}
