package com.example.timesince.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.timesince.data.Item
import com.example.timesince.util.ElapsedFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditorDialog(
    item: Item?,
    onDismiss: () -> Unit,
    onAdd: (title: String, startDate: LocalDate) -> Unit,
    onUpdate: (item: Item, title: String, startDate: LocalDate) -> Unit,
    onDelete: (item: Item) -> Unit,
) {
    val isNew = item == null
    val today = LocalDate.now()
    var title by rememberSaveable(item?.id) { mutableStateOf(item?.title.orEmpty()) }
    var startDate by rememberSaveable(item?.id) { mutableStateOf(item?.startDate ?: today) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var titleTouched by rememberSaveable(item?.id) { mutableStateOf(false) }

    val trimmedTitle = title.trim()
    val titleError = trimmedTitle.isEmpty() && (titleTouched || !isNew)
    val futureDate = startDate.isAfter(today)
    val canSubmit = trimmedTitle.isNotEmpty() && !futureDate

    fun commitEditIfNeeded() {
        val current = item ?: return
        if (trimmedTitle.isEmpty() || futureDate) return
        if (trimmedTitle != current.title || startDate != current.startDate) {
            onUpdate(current, trimmedTitle, startDate)
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (isNew) onDismiss() else {
                commitEditIfNeeded()
                onDismiss()
            }
        },
        title = { Text(if (isNew) "New item" else "Edit item") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleTouched = true
                        if (!isNew) {
                            val t = it.trim()
                            if (t.isNotEmpty() && !startDate.isAfter(today)) {
                                val current = item
                                if (current != null && (t != current.title || startDate != current.startDate)) {
                                    onUpdate(current, t, startDate)
                                }
                            }
                        }
                    },
                    label = { Text("Title") },
                    singleLine = true,
                    isError = titleError,
                    supportingText = {
                        if (titleError) Text("Title is required")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start date: ${ElapsedFormatter.formatDate(startDate)}")
                }
                if (futureDate) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Start date cannot be in the future",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (isNew) {
                TextButton(
                    onClick = { onAdd(trimmedTitle, startDate) },
                    enabled = canSubmit,
                ) { Text("Add") }
            } else {
                TextButton(onClick = {
                    commitEditIfNeeded()
                    onDismiss()
                }) { Text("Close") }
            }
        },
        dismissButton = {
            if (isNew) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            } else {
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )

    if (showDatePicker) {
        val initialMillis = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return !date.isAfter(today)
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        if (!picked.isAfter(today)) {
                            startDate = picked
                            if (!isNew) {
                                val t = title.trim()
                                val current = item
                                if (current != null && t.isNotEmpty() && (t != current.title || picked != current.startDate)) {
                                    onUpdate(current, t, picked)
                                }
                            }
                        }
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDeleteConfirm && item != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove item?") },
            text = { Text("This will permanently remove \"${item.title}\".") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete(item)
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}
