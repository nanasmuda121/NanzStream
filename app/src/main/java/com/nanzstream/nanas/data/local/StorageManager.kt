package com.nanzstream.nanas.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nanzstream.nanas.data.model.ContinueWatchingItem
import com.nanzstream.nanas.data.model.WatchlistItem

class StorageManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nanzstream_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_BASE_URL = "key_base_url"
        private const val KEY_WATCHLIST = "key_watchlist"
        private const val KEY_CONTINUE = "key_continue"
        private const val KEY_READER_MODE = "key_reader_mode" // "webtoon" or "paged"
        const val DEFAULT_API_URL = "https://nanzstream-api.vercel.app/api"
    }

    var apiBaseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_API_URL) ?: DEFAULT_API_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trim().removeSuffix("/")).apply()

    var mangaReaderMode: String
        get() = prefs.getString(KEY_READER_MODE, "webtoon") ?: "webtoon"
        set(value) = prefs.edit().putString(KEY_READER_MODE, value).apply()

    fun getWatchlist(): List<WatchlistItem> {
        val json = prefs.getString(KEY_WATCHLIST, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<WatchlistItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isBookmarked(mediaId: String): Boolean {
        return getWatchlist().any { it.mediaId == mediaId }
    }

    fun toggleBookmark(item: WatchlistItem): Boolean {
        val list = getWatchlist().toMutableList()
        val index = list.indexOfFirst { it.mediaId == item.mediaId }
        val isAdded: Boolean
        if (index >= 0) {
            list.removeAt(index)
            isAdded = false
        } else {
            list.add(0, item)
            isAdded = true
        }
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_WATCHLIST, json).apply()
        return isAdded
    }

    fun getContinueWatching(): List<ContinueWatchingItem> {
        val json = prefs.getString(KEY_CONTINUE, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ContinueWatchingItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveContinueWatching(item: ContinueWatchingItem) {
        val list = getContinueWatching().toMutableList()
        list.removeAll { it.mediaId == item.mediaId }
        list.add(0, item)
        // Keep max 20 items in history
        val trimmed = if (list.size > 20) list.subList(0, 20) else list
        val json = gson.toJson(trimmed)
        prefs.edit().putString(KEY_CONTINUE, json).apply()
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_CONTINUE).apply()
    }
}
