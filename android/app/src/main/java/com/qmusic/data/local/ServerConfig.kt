package com.qmusic.data.local

import android.content.Context
import com.qmusic.BuildConfig
import com.qmusic.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** 音乐服务地址配置（SharedPreferences 持久化，支持真机切换局域网地址） */
object ServerConfig {
    private const val PREFS = "qmusic_server"
    private const val KEY_BASE_URL = "base_url"

    /** debug 包默认：配合 `adb reverse tcp:3300 tcp:3300` 真机调试，无需知道电脑 IP */
    private const val DEBUG_BASE_URL = "http://127.0.0.1:3300/"

    /** release 包默认：Android 模拟器访问宿主机的特殊地址 */
    private const val RELEASE_BASE_URL = "http://10.0.2.2:3300/"

    /**
     * 线上环境地址：Cloudflare Quick Tunnel 指向本机 :3000 网关，
     * 手机走移动网络/任意 WiFi 均可访问（HTTPS，无需配置 network_security_config）。
     *
     * ⚠️ Quick Tunnel 域名随 cloudflared 进程重启而变化，更换后需同步更新此常量；
     * 用户亦可在「我的 → 服务器地址」手动粘贴新域名。
     */
    const val ONLINE_BASE_URL = "https://passengers-territories-faces-salvation.trycloudflare.com/"

    /**
     * 默认服务地址（按构建类型区分）。
     *
     * 调试统一走 debug 包，其默认地址指向 127.0.0.1 —— 只要在电脑上执行一次
     * `adb reverse tcp:3000 tcp:3000`，手机就能直接访问电脑上的音乐服务，
     * 不依赖局域网 IP、换 WiFi 也不用重设。
     * release 包保留模拟器地址，真机使用时在「我的 → 服务器地址」里改成电脑局域网 IP。
     */
    val DEFAULT_BASE_URL: String
        get() = if (BuildConfig.DEBUG) DEBUG_BASE_URL else RELEASE_BASE_URL

    fun getBaseUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, DEFAULT_BASE_URL)
            ?: DEFAULT_BASE_URL

    fun setBaseUrl(context: Context, url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, normalized)
            .apply()
        ApiClient.updateBaseUrl(normalized)
    }

    /** 线上地址的远程配置（换隧道域名后只需改仓库里的 json，无需发版） */
    private const val ONLINE_CONFIG_URL =
        "https://raw.githubusercontent.com/hequnsky/qMusic/main/server-address.json"

    /**
     * 解析当前线上地址：优先读远程配置，失败（无网络/被墙/超时）回退内置常量。
     *
     * 背景：Cloudflare Quick Tunnel 每次重启都会换域名，把域名写死在安装包里
     * 会导致「一换域名就要发版」。改为运行时拉取后，服务端换地址只需要改一行 json。
     */
    suspend fun resolveOnlineBaseUrl(): String = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(ONLINE_CONFIG_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            Regex("\"online\"\\s*:\\s*\"([^\"]+)\"").find(text)
                ?.groupValues?.get(1)
                ?.takeIf { it.startsWith("http") }
                ?: ONLINE_BASE_URL
        } catch (e: Exception) {
            ONLINE_BASE_URL
        }
    }

    /** 一键切换到线上环境（先取远程配置，失败回退内置地址），返回实际使用的地址 */
    suspend fun useOnline(context: Context): String {
        val url = resolveOnlineBaseUrl()
        setBaseUrl(context, url)
        return url
    }

    /** 当前是否正在使用线上环境 */
    fun isOnline(context: Context): Boolean =
        getBaseUrl(context).startsWith("https://")
}
