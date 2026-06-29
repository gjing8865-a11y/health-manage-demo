package com.example.healthmanager.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.healthmanager.model.ExerciseRecord

@Dao
interface ExerciseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExerciseRecord(record: ExerciseRecord)

    @Query("""
        SELECT * FROM exercise_records
        WHERE userAccount = :account
        ORDER BY createdAt DESC
        LIMIT 1
    """)
    suspend fun getLatestExerciseRecordByUser(account: String): ExerciseRecord?

    @Query("""
        SELECT * FROM exercise_records
        WHERE userAccount = :account
        ORDER BY createdAt DESC
    """)
    suspend fun getExerciseRecordsByUser(account: String): List<ExerciseRecord>

    @Query("""
    SELECT * FROM exercise_records
    WHERE userAccount = :account
    AND createdAt BETWEEN :startTime AND :endTime
    ORDER BY createdAt DESC
""")
    suspend fun getExerciseRecordsByUserInRange(
        account: String,
        startTime: Long,
        endTime: Long
    ): List<ExerciseRecord>
}