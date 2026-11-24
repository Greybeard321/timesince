package com.example.timesince.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY startDateEpochDay ASC, id ASC")
    fun observeAll(): Flow<List<Item>>

    @Query("SELECT * FROM items ORDER BY startDateEpochDay ASC, id ASC")
    suspend fun snapshotAll(): List<Item>

    @Insert
    suspend fun insert(item: Item)

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)

    @Query("DELETE FROM items")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(items: List<Item>) {
        deleteAll()
        items.forEach { insert(it) }
    }
}
