package com.qmusic.data.local

import android.content.Context
import com.qmusic.data.model.UserDetailProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 网易云登录态存储（SharedPreferences 持久化 cookie 与用户资料）。
 */
object LoginStore {
    private const val PREFS = "qmusic_login"
    private const val KEY_COOKIE = "cookie"
    private const val KEY_USER = "user"
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class StoredUser(
        val userId: Long = 0,
        val nickname: String = "",
        val avatarUrl: String? = null,
        val follows: Long = 0,
        val followeds: Long = 0,
        val eventCount: Long = 0,
    )

    fun isLoggedIn(context: Context): Boolean = getCookie(context).isNotBlank()

    fun getCookie(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_COOKIE, "") ?: ""

    fun getUser(context: Context): StoredUser {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_USER, null)
            ?: return StoredUser()
        return try {
            json.decodeFromString<StoredUser>(raw)
        } catch (e: Exception) {
            StoredUser()
        }
    }

    fun saveLogin(context: Context, cookie: String, profile: UserDetailProfile) {
        val user = StoredUser(
            userId = profile.userId,
            nickname = profile.nickname,
            avatarUrl = profile.avatarUrl,
            follows = profile.follows,
            followeds = profile.followeds,
            eventCount = profile.eventCount,
        )
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_COOKIE, cookie)
            .putString(KEY_USER, json.encodeToString(user))
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_COOKIE)
            .remove(KEY_USER)
            .apply()
    }
}