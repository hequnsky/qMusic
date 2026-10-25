package com.qmusic.data.remote

import com.qmusic.data.local.ServerConfig
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {

    /**
     * 音乐抓取服务地址（可通过设置页切换）。
     * - Android 模拟器访问宿主机请使用 10.0.2.2
     * - 真机调试请改为电脑局域网 IP（如 http://192.168.x.x:3300/），并确保电脑防火墙放行 3300 端口
     *
     * 对外只读暴露：界面据此判断「这次请求发出时用的地址」与「现在生效的地址」是否已经不同，
     * 从而在启动自愈切换地址后自动重试，而不是把旧地址的超时失败盖到已经修好的页面上。
     */
    @Volatile
    var baseUrl: String = ServerConfig.DEFAULT_BASE_URL
        private set

    @Volatile
    private var currentService: MusicApiService? = null

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * 地址漂移兜底重试。
     *
     * 启动自愈（或用户在设置页改地址）发生在请求飞行途中时，这批请求注定失败：
     * 它们连的是刚被判定失效的旧地址。只靠某个 ViewModel 订阅地址变更是不够的——
     * 排行榜、搜索、歌单详情各有各的请求，漏掉一处就是「那个区块永远转圈」。
     *
     * 所以在网络层统一兜住：连接类异常发生时，若地址已经被换掉，就用**新地址重试一次**。
     * 地址没变时原样抛出，不影响真正的网络故障。
     */
    private class UrlHealInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val urlAtStart = ApiClient.baseUrl
            return try {
                chain.proceed(request)
            } catch (e: IOException) {
                val current = ApiClient.baseUrl
                if (current == urlAtStart) throw e
                val base = current.toHttpUrlOrNull()
                if (base == null) throw e
                val retried = request.newBuilder()
                    .url(
                        request.url.newBuilder()
                            .scheme(base.scheme)
                            .host(base.host)
                            .port(base.port)
                            .build()
                    )
                    .build()
                chain.proceed(retried)
            }
        }
    }

    private fun buildService(url: String): MusicApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(UrlHealInterceptor())
            .addInterceptor(logging)
            // 连接超时 5s：正常服务（局域网 8ms / 公网隧道 1.3s）远低于此；
            // 而连一个失效的私有地址（移动数据下的 192.168.x.x）原本要干等 15s——
            // 启动自愈早已把地址切好，旧的超时请求却还在飞，最后把页面打成「加载失败」。
            // 这个值同时也是「地址失效 → 触发兜底重试」的最长等待，别调大。
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(url)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MusicApiService::class.java)
    }

    val service: MusicApiService
        get() {
            currentService?.let { return it }
            return synchronized(this) {
                currentService ?: buildService(baseUrl).also { currentService = it }
            }
        }

    /** 切换服务器地址（设置页保存后立即生效） */
    fun updateBaseUrl(url: String) {
        synchronized(this) {
            baseUrl = url
            currentService = buildService(url)
        }
    }
}
