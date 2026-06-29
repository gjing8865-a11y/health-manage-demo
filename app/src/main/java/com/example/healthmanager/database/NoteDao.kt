package com.example.healthmanager.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.healthmanager.model.NoteRecord

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE userAccount = :account ORDER BY createdAt DESC")
    suspend fun getNotesByUserAccount(account: String): List<NoteRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteRecord)

    @Query("DELETE FROM notes WHERE userAccount = :account")
    suspend fun deleteAllNotesByUserAccount(account: String)
}