package com.example.healthmanager.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.healthmanager.model.User

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE account = :acc LIMIT 1")
    suspend fun getUserByAccount(acc: String): User?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun registerUser(user: User)

    @Update
    suspend fun updateUser(user: User)

    @Query("UPDATE users SET nickName = :nickName WHERE account = :acc")
    suspend fun updateNickName(acc: String, nickName: String): Unit

    @Query("UPDATE users SET signature = :signature WHERE account = :acc")
    suspend fun updateSignature(acc: String, signature: String): Unit

    @Query("UPDATE users SET avatarUri = :avatarUri WHERE account = :acc")
    suspend fun updateAvatar(acc: String, avatarUri: String): Unit

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE account = :acc AND password = :pwd)")
    suspend fun login(acc: String, pwd: String): Boolean

    @Query("SELECT * FROM users WHERE account = :acc AND password = :pwd LIMIT 1")
    suspend fun loginAndGetUser(acc: String, pwd: String): User?
}