package com.example.healthmanager.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.healthmanager.model.ExerciseRecord
import com.example.healthmanager.model.FoodRecord
import com.example.healthmanager.model.NoteRecord
import com.example.healthmanager.model.SleepRecord
import com.example.healthmanager.model.User
import com.example.healthmanager.model.WeeklyStepRecord

@Database(
    entities = [
        User::class,
        NoteRecord::class,
        FoodRecord::class,
        WeeklyStepRecord::class,
        SleepRecord::class,
        ExerciseRecord::class
    ],
    version = 9,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun noteDao(): NoteDao
    abstract fun foodDao(): FoodDao
    abstract fun weeklyStepDao(): WeeklyStepDao
    abstract fun sleepDao(): SleepDao
    abstract fun exerciseDao(): ExerciseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "health_manager_db"
                )
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
