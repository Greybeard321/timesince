package com.example.timesince

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timesince.data.AppDatabase
import com.example.timesince.data.Item
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class ItemViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.get(application).itemDao()

    val items: StateFlow<List<Item>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addItem(title: String, startDate: LocalDate) {
        val trimmed = title.trim()
        if (trimmed.isEmpty() || startDate.isAfter(LocalDate.now())) return
        viewModelScope.launch {
            dao.insert(Item.create(title = trimmed, startDate = startDate))
        }
    }

    fun updateItem(item: Item, title: String, startDate: LocalDate) {
        val trimmed = title.trim()
        if (trimmed.isEmpty() || startDate.isAfter(LocalDate.now())) return
        viewModelScope.launch {
            dao.update(item.copy(title = trimmed, startDateEpochDay = startDate.toEpochDay()))
        }
    }

    fun deleteItem(item: Item) {
        viewModelScope.launch { dao.delete(item) }
    }
}
