package com.example.healthmanager.data.repository

import com.example.healthmanager.database.ExerciseDao
import com.example.healthmanager.database.FoodDao
import com.example.healthmanager.database.NoteDao
import com.example.healthmanager.database.SleepDao
import com.example.healthmanager.database.UserDao
import com.example.healthmanager.database.WeeklyStepDao
import com.example.healthmanager.model.ExerciseRecord
import com.example.healthmanager.model.FoodRecord
import com.example.healthmanager.model.NoteRecord
import com.example.healthmanager.model.SleepRecord
import com.example.healthmanager.model.User
import com.example.healthmanager.model.WeeklyStepRecord

class UserRepository(private val userDao: UserDao) {
    suspend fun getUserByAccount(account: String): User? =
        userDao.getUserByAccount(account)

    suspend fun registerUser(user: User) =
        userDao.registerUser(user)

    suspend fun loginAndGetUser(account: String, password: String): User? =
        userDao.loginAndGetUser(account, password)

    suspend fun updateNickName(account: String, nickName: String) =
        userDao.updateNickName(account, nickName)

    suspend fun updateSignature(account: String, signature: String) =
        userDao.updateSignature(account, signature)

    suspend fun updateAvatar(account: String, avatarUri: String) =
        userDao.updateAvatar(account, avatarUri)
}

class NoteRepository(private val noteDao: NoteDao) {
    suspend fun getNotesByUserAccount(account: String): List<NoteRecord> =
        noteDao.getNotesByUserAccount(account)

    suspend fun insertNote(note: NoteRecord) =
        noteDao.insertNote(note)
}

class FoodRepository(private val foodDao: FoodDao) {
    suspend fun getFoodRecordsByUser(account: String): List<FoodRecord> =
        foodDao.getFoodRecordsByUser(account)

    suspend fun insertFoodRecord(record: FoodRecord) =
        foodDao.insertFoodRecord(record)

    suspend fun deleteFoodRecord(record: FoodRecord) =
        foodDao.deleteFoodRecord(record)
}

class WeeklyStepRepository(private val weeklyStepDao: WeeklyStepDao) {
    suspend fun getLatestWeeklyRecordByUser(account: String): WeeklyStepRecord? =
        weeklyStepDao.getLatestWeeklyRecordByUser(account)

    suspend fun insertWeeklyRecord(record: WeeklyStepRecord) =
        weeklyStepDao.insertWeeklyRecord(record)
}

class SleepRepository(private val sleepDao: SleepDao) {
    suspend fun getLatestSleepRecordByUser(account: String): SleepRecord? =
        sleepDao.getLatestSleepRecordByUser(account)

    suspend fun getRecentSleepRecordsByUser(account: String, limit: Int): List<SleepRecord> =
        sleepDao.getRecentSleepRecordsByUser(account, limit)

    suspend fun insertSleepRecord(record: SleepRecord) =
        sleepDao.insertSleepRecord(record)
}

class ExerciseRepository(private val exerciseDao: ExerciseDao) {
    suspend fun insertExerciseRecord(record: ExerciseRecord) =
        exerciseDao.insertExerciseRecord(record)

    suspend fun getLatestExerciseRecordByUser(account: String): ExerciseRecord? =
        exerciseDao.getLatestExerciseRecordByUser(account)

    suspend fun getExerciseRecordsByUserInRange(
        account: String,
        startTime: Long,
        endTime: Long
    ): List<ExerciseRecord> =
        exerciseDao.getExerciseRecordsByUserInRange(account, startTime, endTime)
}
