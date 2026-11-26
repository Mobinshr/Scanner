package com.mobin.scanner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ScanRecord(
    val id: String = UUID.randomUUID().toString(),
    val value: String,
    val format: String = "QR Code",
    val timestamp: Long = System.currentTimeMillis()
)

object ScanHistoryStore {
    private const val PREFS_NAME = "scanner_history"
    private const val ITEMS_KEY = "items"
    private const val MAX_ITEMS = 100

    fun load(context: Context): List<ScanRecord> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(ITEMS_KEY, "[]") ?: "[]"

        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val value = item.optString("value")
                    if (value.isNotBlank()) {
                        add(
                            ScanRecord(
                                id = item.optString("id", UUID.randomUUID().toString()),
                                value = value,
                                format = item.optString("format", "QR Code"),
                                timestamp = item.optLong("timestamp", System.currentTimeMillis())
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, records: List<ScanRecord>) {
        val array = JSONArray()
        records.take(MAX_ITEMS).forEach { record ->
            array.put(
                JSONObject()
                    .put("id", record.id)
                    .put("value", record.value)
                    .put("format", record.format)
                    .put("timestamp", record.timestamp)
            )
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(ITEMS_KEY, array.toString())
            .apply()
    }

    const val limit = MAX_ITEMS
}
