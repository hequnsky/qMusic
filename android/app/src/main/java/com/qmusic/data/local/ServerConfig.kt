package com.qmusic.data.local

import android.content.Context
import com.qmusic.BuildConfig
import com.qmusic.data.remote.ApiClient

/** 音乐服务地址配置（SharedPreferences 持久化，支持真机切换局域网地址） */
object ServerConfig {
    private const val PREFS = "qmusic_server"
    private const val KEY_BASE_URL = "base_url"

    /** debug 包默认：配合 `adb reverse tcp:3000 tcp:3000` 真机调试，无需知道电脑 IP */
    private const val DEBUG_BASE_URL = "http://127.0.0.1:3000/"

    /** release 包默认：Android 模拟器访问宿主机的特殊地址 */
    private const val RELEASE_BASE_URL = "http://10.0.2.2:3000/"

    /**
     * 线上环境地址：Cloudflare Quick Tunnel 指向本机 :3000 网关，
     * 手机走移动网络/任意 WiFi 均可访问（HTTPS，无需配置 network_security_config）。
     *
     * ⚠️ Quick Tunnel 域名随 cloudflared 进程重启而变化，更换后需同步更新此常量；
     * 用户亦可在「我的 → 服务器地址」手动粘贴新域名。
     */
    const val ONLINE_BASE_URL = "https://albums-quite-grass-harvest.trycloudflare.com/"

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

    /** 一键切换到线上环境（Cloudflare 隧道） */
    fun useOnline(context: Context) = setBaseUrl(context, ONLINE_BASE_URL)

    /** 当前是否正在使用线上环境 */
    fun isOnline(context: Context): Boolean =
        getBaseUrl(context).startsWith("https://")
}
