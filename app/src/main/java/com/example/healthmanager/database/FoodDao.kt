package com.example.healthmanager.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.healthmanager.model.FoodRecord

@Dao
interface FoodDao {

    @Query("""
        SELECT * FROM food_records
        WHERE userAccount = :account
        ORDER BY id DESC
    """)

    suspend fun getFoodRecordsByUser(account: String): List<FoodRecord>
    @Delete
    suspend fun deleteFoodRecord(record: FoodRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodRecord(record: FoodRecord)

    @Query("DELETE FROM food_records WHERE userAccount = :account")
    suspend fun deleteAllFoodRecordsByUser(account: String)
}