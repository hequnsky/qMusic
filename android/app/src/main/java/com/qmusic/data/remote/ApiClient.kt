package com.qmusic.data.remote

import com.qmusic.data.local.ServerConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    /**
     * 音乐抓取服务地址（可通过设置页切换）。
     * - Android 模拟器访问宿主机请使用 10.0.2.2
     * - 真机调试请改为电脑局域网 IP（如 http://192.168.x.x:3000/），并确保电脑防火墙放行 3000 端口
     */
    @Volatile
    private var currentBaseUrl: String = ServerConfig.DEFAULT_BASE_URL

    @Volatile
    private var currentService: MusicApiService? = null

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun buildService(baseUrl: String): MusicApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MusicApiService::class.java)
    }

    val service: MusicApiService
        get() {
            currentService?.let { return it }
            return synchronized(this) {
                currentService ?: buildService(currentBaseUrl).also { currentService = it }
            }
        }

    /** 切换服务器地址（设置页保存后立即生效） */
    fun updateBaseUrl(url: String) {
        synchronized(this) {
            currentBaseUrl = url
            currentService = buildService(url)
        }
    }
}