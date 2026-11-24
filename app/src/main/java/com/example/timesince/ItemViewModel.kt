package com.example.timesince

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timesince.data.AppDatabase
import com.example.timesince.data.Item
import com.example.timesince.data.ItemDao
import com.example.timesince.util.CsvSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDate

class ItemViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context = application.applicationContext
    private val operationMutex = Mutex()
    private val dao: ItemDao = AppDatabase.get(context).itemDao()

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        dao.observeAll()
            .onEach { _items.value = it }
            .catch { e -> _message.value = "Could not read items: ${e.message ?: "unknown error"}" }
            .launchIn(viewModelScope)
    }

    fun addItem(title: String, startDate: LocalDate) {
        val trimmed = title.trim()
        if (trimmed.isEmpty() || startDate.isAfter(LocalDate.now())) return
        viewModelScope.launch {
            operationMutex.withLock {
                dao.insert(Item.create(title = trimmed, startDate = startDate))
            }
        }
    }

    fun updateItem(item: Item, title: String, startDate: LocalDate) {
        val trimmed = title.trim()
        if (trimmed.isEmpty() || startDate.isAfter(LocalDate.now())) return
        viewModelScope.launch {
            operationMutex.withLock {
                dao.update(item.copy(title = trimmed, startDateEpochDay = startDate.toEpochDay()))
            }
        }
    }

    fun deleteItem(item: Item) {
        viewModelScope.launch {
            operationMutex.withLock {
                dao.delete(item)
            }
        }
    }

    fun exportItems(uri: Uri) {
        viewModelScope.launch {
            if (!_busy.compareAndSet(false, true)) return@launch
            try {
                val csv = operationMutex.withLock {
                    CsvSerializer.encode(dao.snapshotAll())
                }
                writeTextToUri(uri, csv)
                _message.value = "CSV exported"
            } catch (e: Exception) {
                _message.value = "Export failed: ${e.message ?: "unknown error"}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun importItems(uri: Uri) {
        viewModelScope.launch {
            if (!_busy.compareAndSet(false, true)) return@launch
            val tempFile = File.createTempFile("timesince-import", ".csv", context.cacheDir)
            try {
                copyUriToFile(uri, tempFile)
                val imported = CsvSerializer.decode(tempFile.readText(Charsets.UTF_8))
                operationMutex.withLock {
                    dao.replaceAll(imported)
                }
                _message.value = "CSV imported"
            } catch (e: Exception) {
                _message.value = "Import failed: ${e.message ?: "unknown error"}"
            } finally {
                tempFile.delete()
                _busy.value = false
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private suspend fun writeTextToUri(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("Could not open destination")
        output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    private suspend fun copyUriToFile(uri: Uri, target: File) = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open selected file")
        input.use { stream ->
            FileOutputStream(target).use { output -> stream.copyTo(output) }
        }
    }
}
