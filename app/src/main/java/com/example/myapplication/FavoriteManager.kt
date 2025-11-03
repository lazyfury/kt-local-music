package com.example.myapplication

import android.content.Context

/**
 * 简单的收藏管理器：支持多个收藏列表，使用 SharedPreferences 持久化。
 * 每个列表保存为一个 String Set，元素为 Track 的唯一 id（字符串形式）。
 */
class FavoriteManager(context: Context) {
    private val prefs = context.getSharedPreferences("favorites_store", Context.MODE_PRIVATE)

    private val KEY_LIST_NAMES = "fav_list_names"
    private fun keyForList(name: String) = "fav_list_" + name

    fun getAllLists(): Set<String> {
        // 返回副本，避免外部修改原始集合导致异常
        return HashSet(prefs.getStringSet(KEY_LIST_NAMES, emptySet()) ?: emptySet())
    }

    fun createList(name: String): Boolean {
        if (name.isBlank()) return false
        val lists = getAllLists().toMutableSet()
        val added = lists.add(name)
        if (added) {
            prefs.edit()
                .putStringSet(KEY_LIST_NAMES, lists)
                .putStringSet(keyForList(name), emptySet())
                .apply()
        }
        return added
    }

    fun deleteList(name: String) {
        val lists = getAllLists().toMutableSet()
        if (lists.remove(name)) {
            prefs.edit()
                .putStringSet(KEY_LIST_NAMES, lists)
                .remove(keyForList(name))
                .apply()
        }
    }

    fun getListTrackIds(name: String): Set<Long> {
        val raw = HashSet(prefs.getStringSet(keyForList(name), emptySet()) ?: emptySet())
        return raw.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun addTrackToList(name: String, trackId: Long) {
        val key = keyForList(name)
        val current = HashSet(prefs.getStringSet(key, emptySet()) ?: emptySet())
        current.add(trackId.toString())
        prefs.edit().putStringSet(key, current).apply()
    }

    fun removeTrackFromList(name: String, trackId: Long) {
        val key = keyForList(name)
        val current = HashSet(prefs.getStringSet(key, emptySet()) ?: emptySet())
        current.remove(trackId.toString())
        prefs.edit().putStringSet(key, current).apply()
    }

    fun isTrackInList(name: String, trackId: Long): Boolean {
        val key = keyForList(name)
        val current = prefs.getStringSet(key, emptySet()) ?: emptySet()
        return trackId.toString() in current
    }

    fun isTrackInAnyList(trackId: Long): Boolean {
        val lists = getAllLists()
        return lists.any { isTrackInList(it, trackId) }
    }
}