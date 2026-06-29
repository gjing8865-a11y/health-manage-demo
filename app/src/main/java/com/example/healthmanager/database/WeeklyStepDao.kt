package com.example.healthmanager.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.healthmanager.model.WeeklyStepRecord

@Dao
interface WeeklyStepDao {

    @Query("""
        SELECT * FROM weekly_step_records
        WHERE userAccount = :account
        ORDER BY updatedAt DESC
        LIMIT 1
    """)
    suspend fun getLatestWeeklyRecordByUser(account: String): WeeklyStepRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeklyRecord(record: WeeklyStepRecord)

    @Query("DELETE FROM weekly_step_records WHERE userAccount = :account")
    suspend fun deleteWeeklyRecordsByUser(account: String)
}