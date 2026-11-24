package com.example.timesince.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startDateEpochDay: Long,
) {
    val startDate: LocalDate
        get() = LocalDate.ofEpochDay(startDateEpochDay)

    companion object {
        fun create(title: String, startDate: LocalDate, id: Long = 0): Item =
            Item(id = id, title = title.trim(), startDateEpochDay = startDate.toEpochDay())
    }
}
