package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.FolderItem
import org.json.JSONArray
import org.json.JSONObject

class ExamSetPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("examset_app_prefs", Context.MODE_PRIVATE)

    fun getLastActivePaperId(): Long {
        return prefs.getLong(KEY_LAST_ACTIVE_PAPER_ID, -1L)
    }

    fun setLastActivePaperId(id: Long) {
        prefs.edit().putLong(KEY_LAST_ACTIVE_PAPER_ID, id).apply()
    }

    fun getDraftRawText(): String {
        return prefs.getString(KEY_DRAFT_RAW_TEXT, "") ?: ""
    }

    fun setDraftRawText(text: String) {
        prefs.edit().putString(KEY_DRAFT_RAW_TEXT, text).apply()
    }

    fun getActiveTabIndex(): Int {
        return prefs.getInt(KEY_ACTIVE_TAB_INDEX, 0)
    }

    fun setActiveTabIndex(index: Int) {
        prefs.edit().putInt(KEY_ACTIVE_TAB_INDEX, index).apply()
    }

    fun getCustomFolders(): List<FolderItem> {
        val jsonStr = prefs.getString(KEY_CUSTOM_FOLDERS, null)
        if (jsonStr.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<FolderItem>()
            val legacyNames = setOf("Science & Emerging Tech", "UPSC Civil Services / PSC", "SSC CGL / Banking")
            val legacyIds = setOf("f-science", "f-upsc", "f-ssc")
            var hadLegacy = false
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                if (id in legacyIds || name in legacyNames) {
                    hadLegacy = true
                    continue
                }
                list.add(
                    FolderItem(
                        id = id,
                        name = name,
                        color = obj.optString("color", "#0284C7"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            if (hadLegacy) {
                saveCustomFolders(list)
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCustomFolders(folders: List<FolderItem>) {
        val arr = JSONArray()
        folders.forEach { f ->
            val obj = JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("color", f.color)
                put("createdAt", f.createdAt)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_FOLDERS, arr.toString()).apply()
    }

    fun getDailyGoalMinutes(): Int {
        return prefs.getInt(KEY_DAILY_GOAL_MINUTES, 30)
    }

    fun setDailyGoalMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_DAILY_GOAL_MINUTES, minutes.coerceIn(5, 360)).apply()
    }

    fun getFontSizeScale(): Float {
        return prefs.getFloat(KEY_FONT_SIZE_SCALE, 1.0f)
    }

    fun setFontSizeScale(scale: Float) {
        prefs.edit().putFloat(KEY_FONT_SIZE_SCALE, scale.coerceIn(0.85f, 1.6f)).apply()
    }

    fun getShuffleOptions(): Boolean {
        return prefs.getBoolean(KEY_SHUFFLE_OPTIONS, false)
    }

    fun setShuffleOptions(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHUFFLE_OPTIONS, enabled).apply()
    }

    companion object {
        private const val KEY_LAST_ACTIVE_PAPER_ID = "key_last_active_paper_id"
        private const val KEY_DRAFT_RAW_TEXT = "key_draft_raw_text"
        private const val KEY_ACTIVE_TAB_INDEX = "key_active_tab_index"
        private const val KEY_CUSTOM_FOLDERS = "key_custom_folders"
        private const val KEY_DAILY_GOAL_MINUTES = "key_daily_goal_minutes"
        private const val KEY_FONT_SIZE_SCALE = "key_font_size_scale"
        private const val KEY_SHUFFLE_OPTIONS = "key_shuffle_options"
    }
}
