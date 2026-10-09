package com.qmusic.data.local

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** 搜索历史：SharedPreferences 本地持久化，最多保留 10 条 */
object SearchHistoryStore {

    private const val NAME = "qmusic_search"
    private const val KEY_HISTORY = "history"
    private const val MAX_SIZE = 10

    fun get(context: Context): List<String> {
        val raw = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, "[]") ?: "[]"
        return try {
            Json.parseToJsonElement(raw).jsonArray.map { it.jsonPrimitive.content }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(context: Context, keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        val list = get(context).filterNot { it.equals(kw, ignoreCase = true) }.toMutableList()
        list.add(0, kw)
        if (list.size > MAX_SIZE) list.removeAt(list.lastIndex)
        save(context, list)
    }

    fun remove(context: Context, keyword: String) {
        save(context, get(context).filterNot { it == keyword })
    }

    fun clear(context: Context) = save(context, emptyList())

    private fun save(context: Context, list: List<String>) {
        val json = Json.encodeToString(JsonArray.serializer(), JsonArray(list.map { JsonPrimitive(it) }))
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, json)
            .apply()
    }
}