package com.example.timesince.util

import com.example.timesince.data.Item
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object CsvSerializer {
    private const val HEADER = "title,startDate"
    private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE

    fun encode(items: List<Item>): String = buildString {
        append(HEADER).append('\n')
        items
            .sortedWith(compareBy<Item> { it.startDateEpochDay }.thenBy { it.id })
            .forEach { item ->
                append(escape(item.title))
                append(',')
                append(item.startDate.format(DATE_FORMAT))
                append('\n')
            }
    }

    fun decode(text: String, today: LocalDate = LocalDate.now()): List<Item> {
        val rows = parseRows(text.removePrefix("\uFEFF"))
        if (rows.isEmpty()) throw IllegalArgumentException("CSV file is empty")

        val header = rows.first()
        if (header.size != 2 ||
            !header[0].trim().equals("title", ignoreCase = true) ||
            !header[1].trim().equals("startDate", ignoreCase = true)
        ) {
            throw IllegalArgumentException("not a TimeSince CSV file")
        }

        return rows.drop(1)
            .filterNot { it.size == 1 && it[0].isBlank() }
            .mapIndexed { index, row ->
                val lineNumber = index + 2
                if (row.size != 2) {
                    throw IllegalArgumentException("invalid row $lineNumber: expected 2 columns")
                }

                val title = row[0].trim()
                if (title.isEmpty()) {
                    throw IllegalArgumentException("empty title in row $lineNumber")
                }

                val startDate = try {
                    LocalDate.parse(row[1].trim(), DATE_FORMAT)
                } catch (e: DateTimeParseException) {
                    throw IllegalArgumentException("invalid date in row $lineNumber: ${row[1]}")
                }

                if (startDate.isAfter(today)) {
                    throw IllegalArgumentException("future date in row $lineNumber: $startDate")
                }

                Item.create(title = title, startDate = startDate)
            }
    }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    private fun parseRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        fun finishField() {
            row.add(field.toString())
            field.setLength(0)
        }

        fun finishRow() {
            finishField()
            rows.add(row.toList())
            row.clear()
        }

        while (i < text.length) {
            val char = text[i]
            when {
                inQuotes -> when (char) {
                    '"' -> {
                        if (i + 1 < text.length && text[i + 1] == '"') {
                            field.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    }
                    else -> field.append(char)
                }

                char == '"' && field.isEmpty() -> inQuotes = true

                char == ',' -> finishField()

                char == '\n' -> {
                    finishRow()
                    if (i + 1 < text.length && text[i + 1] == '\r') i++
                }

                char == '\r' -> {
                    finishRow()
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                }

                else -> field.append(char)
            }
            i++
        }

        if (field.isNotEmpty() || row.isNotEmpty()) finishRow()
        return rows
    }
}
