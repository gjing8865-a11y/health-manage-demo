package com.example.healthmanager.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.healthmanager.model.SleepRecord

@Dao
interface SleepDao {

    @Query("""
        SELECT * FROM sleep_records
        WHERE userAccount = :account
        ORDER BY updatedAt DESC
        LIMIT 1
    """)
    suspend fun getLatestSleepRecordByUser(account: String): SleepRecord?

    @Query("""
        SELECT * FROM sleep_records
        WHERE userAccount = :account
        ORDER BY updatedAt DESC
        LIMIT :limit
    """)
    suspend fun getRecentSleepRecordsByUser(account: String, limit: Int): List<SleepRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepRecord(record: SleepRecord)

    @Query("DELETE FROM sleep_records WHERE userAccount = :account")
    suspend fun deleteSleepRecordsByUser(account: String)
}
