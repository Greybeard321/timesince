package com.example.timesince

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timesince.data.AppDatabase
import com.example.timesince.data.Item
import com.example.timesince.data.ItemDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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

    private var dao: ItemDao = AppDatabase.get(context).itemDao()
    private var observerJob: Job? = null

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        startObserving()
    }

    private fun startObserving() {
        observerJob = dao.observeAll()
            .onEach { _items.value = it }
            .catch { e -> _message.value = "Could not read items: ${e.message ?: "unknown error"}" }
            .launchIn(viewModelScope)
    }

    private suspend fun pauseObserving() {
        observerJob?.cancelAndJoin()
        observerJob = null
    }

    private fun reopenDatabase() {
        dao = AppDatabase.get(context).itemDao()
        startObserving()
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

    fun exportDatabase(uri: Uri) {
        viewModelScope.launch {
            if (!_busy.compareAndSet(false, true)) return@launch
            try {
                operationMutex.withLock {
                    pauseObserving()
                    try {
                        checkpointDatabase()
                        AppDatabase.closeAndClear()
                        copyDatabaseToUri(uri)
                    } finally {
                        reopenDatabase()
                    }
                }
                _message.value = "Database exported"
            } catch (e: Exception) {
                _message.value = "Export failed: ${e.message ?: "unknown error"}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun importDatabase(uri: Uri) {
        viewModelScope.launch {
            if (!_busy.compareAndSet(false, true)) return@launch
            val tempFile = File.createTempFile("timesince-import", ".db", context.cacheDir)
            try {
                operationMutex.withLock {
                    copyUriToFile(uri, tempFile)
                    validateDatabaseFile(tempFile)

                    pauseObserving()
                    try {
                        AppDatabase.closeAndClear()
                        replaceDatabaseWith(tempFile)
                    } finally {
                        reopenDatabase()
                    }
                }
                _message.value = "Database imported"
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

    private fun checkpointDatabase() {
        val file = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        if (!file.exists()) return
        val database = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
                cursor.moveToFirst()
            }
        } finally {
            database.close()
        }
    }

    private suspend fun copyDatabaseToUri(uri: Uri) = withContext(Dispatchers.IO) {
        val source = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        if (!source.exists()) throw IOException("Database file not found")
        val output = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("Could not open destination")
        output.use { out ->
            source.inputStream().use { input -> input.copyTo(out) }
        }
    }

    private suspend fun copyUriToFile(uri: Uri, target: File) = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open selected file")
        input.use { stream ->
            FileOutputStream(target).use { output -> stream.copyTo(output) }
        }
    }

    private fun validateDatabaseFile(file: File) {
        if (!file.exists() || file.length() == 0L) throw IOException("Selected file is empty")

        val database = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val version = database.rawQuery("PRAGMA user_version", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else -1
            }
            if (version != AppDatabase.DATABASE_VERSION) {
                throw IllegalArgumentException("unsupported database version: $version")
            }

            val columns = mutableSetOf<String>()
            database.rawQuery("PRAGMA table_info(items)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) {
                    columns.add(cursor.getString(nameIndex))
                }
            }
            val required = setOf("id", "title", "startDateEpochDay")
            if (!columns.containsAll(required)) {
                throw IllegalArgumentException("not a TimeSince database")
            }
        } finally {
            database.close()
        }
    }

    private suspend fun replaceDatabaseWith(source: File) = withContext(Dispatchers.IO) {
        val target = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        target.parentFile?.mkdirs()

        val staging = File(target.parentFile, "${target.name}.new")
        try {
            source.copyTo(staging, overwrite = true)
            File("${target.path}-wal").delete()
            File("${target.path}-shm").delete()
            File("${target.path}-journal").delete()
            if (target.exists() && !target.delete()) {
                throw IOException("Could not remove existing database")
            }
            if (!staging.renameTo(target)) {
                staging.copyTo(target, overwrite = true)
            }
        } finally {
            staging.delete()
        }
    }
}
