package com.openmapsrouter.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import kotlin.math.abs

class StorageManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("openmapsrouter_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_SETTINGS = "key_settings"
        private const val KEY_HISTORY = "key_history"
        private const val MAX_HISTORY = 30
    }

    fun getSettings(): AppSettings {
        val json = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return try {
            gson.fromJson(json, AppSettings::class.java) ?: AppSettings()
        } catch (e: Exception) {
            AppSettings()
        }
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit().putString(KEY_SETTINGS, gson.toJson(settings)).apply()
    }

    fun getHistory(): List<HistoryItem> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<HistoryItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addHistoryItem(result: CoordinateResult): List<HistoryItem> {
        val current = getHistory().toMutableList()
        // Deduplicate close coordinates
        current.removeAll {
            abs(it.latitude - result.latitude) < 0.00001 &&
            abs(it.longitude - result.longitude) < 0.00001
        }

        val newItem = HistoryItem(
            id = UUID.randomUUID().toString(),
            latitude = result.latitude,
            longitude = result.longitude,
            name = result.name,
            sourceMethod = result.sourceMethod,
            originalUrl = result.originalUrl,
            timestamp = result.timestamp
        )

        current.add(0, newItem)
        val trimmed = if (current.size > MAX_HISTORY) current.subList(0, MAX_HISTORY) else current
        prefs.edit().putString(KEY_HISTORY, gson.toJson(trimmed)).apply()
        return trimmed
    }

    fun deleteHistoryItem(id: String): List<HistoryItem> {
        val current = getHistory().toMutableList()
        current.removeAll { it.id == id }
        prefs.edit().putString(KEY_HISTORY, gson.toJson(current)).apply()
        return current
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }
}
