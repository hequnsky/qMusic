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
     * 线上环境地址：Cloudflare Quick Tunnel 指向本机 :3300 网关，
     * 手机走移动网络/任意 WiFi 均可访问（HTTPS，无需配置 network_security_config）。
     *
     * ⚠️ Quick Tunnel 域名随 cloudflared 进程重启而变化，更换后需同步更新此常量；
     * 优先由远程 server-address.json 下发，此常量仅作远程配置不可达时的兜底。
     */
    const val ONLINE_BASE_URL = "https://cooperative-badly-elect-boundary.trycloudflare.com/"

    /** 局域网直连地址（手机与电脑同一 WiFi 时最快，隧道失效时的兜底） */
    private const val LAN_BASE_URL = "http://192.168.0.56:3300/"

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

    /**
     * 线上地址的远程配置源（换隧道域名后只改仓库里的 json 并 push，无需发版）。
     *
     * 顺序即优先级：jsDelivr 的 fastly / gcore 节点国内可直连
     * （cdn.jsdelivr.net 与 raw.githubusercontent.com 在部分网络下不可达，故放最后兜底）。
     * 注意 jsDelivr 对 `@main` 有缓存，紧急换域名时可访问
     * `https://purge.jsdelivr.net/gh/hequnsky/qMusic@main/server-address.json` 刷新。
     */
    private val ONLINE_CONFIG_URLS = listOf(
        "https://fastly.jsdelivr.net/gh/hequnsky/qMusic@main/server-address.json",
        "https://gcore.jsdelivr.net/gh/hequnsky/qMusic@main/server-address.json",
        "https://raw.githubusercontent.com/hequnsky/qMusic/main/server-address.json",
    )

    /** 内置候选地址（远程配置全部不可达时依次探测） */
    private val BUILTIN_CANDIDATES = listOf(ONLINE_BASE_URL, LAN_BASE_URL)

    /** 探测用轻量接口：任一可用服务都应秒回，用于判定地址是否真的连通 */
    private const val PROBE_PATH = "personalized?limit=1"

    /**
     * 解析当前线上地址。
     *
     * 关键改动：**先探测连通性再采用**。旧实现只取第一个能"下载到"的远程配置里的
     * online 字段，一旦该地址（隧道被回收 / 电脑关机）实际不可用，就会把死地址写进
     * prefs，表现为「服务明明在跑却一直加载失败」。
     *
     * 现在按 远程 candidates → 远程 online → 内置候选 的顺序合并去重，
     * 逐个发一次轻量请求，返回**第一个真实可连通**的地址；全都不通才回退首个候选。
     */
    suspend fun resolveOnlineBaseUrl(): String = withContext(Dispatchers.IO) {
        val ordered = LinkedHashSet<String>()
        fetchRemoteCandidates().forEach { ordered.add(it) }
        BUILTIN_CANDIDATES.forEach { ordered.add(it) }

        for (url in ordered) {
            if (probe(url)) return@withContext normalize(url)
        }
        normalize(ordered.first())
    }

    /** 抓取远程配置里的候选地址（candidates 数组优先，其次 online 字段） */
    private fun fetchRemoteCandidates(): List<String> {
        for (configUrl in ONLINE_CONFIG_URLS) {
            try {
                val conn = (URL(configUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                    requestMethod = "GET"
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val list = ArrayList<String>()
                // candidates: [ "https://...", "http://..." ]
                Regex("\"candidates\"\\s*:\\s*\\[([^\\]]*)\\]").find(text)
                    ?.groupValues?.get(1)
                    ?.let { body ->
                        Regex("\"(https?://[^\"]+)\"").findAll(body)
                            .forEach { list.add(it.groupValues[1]) }
                    }
                // online: "https://..."（兼容旧格式）
                Regex("\"online\"\\s*:\\s*\"(https?://[^\"]+)\"").find(text)
                    ?.groupValues?.get(1)?.let { list.add(it) }

                if (list.isNotEmpty()) return list
            } catch (_: Exception) {
                // 换下一个源
            }
        }
        return emptyList()
    }

    /** 探测地址是否真实可连通（HTTP 2xx/3xx 视为可用） */
    private fun probe(baseUrl: String): Boolean {
        val url = normalize(baseUrl) + PROBE_PATH
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
            }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..399
        } catch (_: Exception) {
            false
        }
    }

    private fun normalize(url: String): String = if (url.endsWith("/")) url else "$url/"

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
