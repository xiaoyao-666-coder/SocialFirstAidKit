package com.socialfirstaidkit.core.network

import com.socialfirstaidkit.core.network.imagegen.ImageGenerationClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @Named("default_okhttp")
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // HTTP/2 多路复用。
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            // 复用连接以减少 TCP 握手。
            .connectionPool(
                ConnectionPool(
                    maxIdleConnections = 10,  // 最大空闲连接数
                    keepAliveDuration = 5,    // 连接保持时间(分钟)
                    timeUnit = TimeUnit.MINUTES
                )
            )
            // 禁用自动重试以避免重复请求。
            .retryOnConnectionFailure(true)
            // 启用响应缓存。
            .cache(null)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    @Provides
    @Singleton
    @Named("ocr_okhttp")
    fun provideOcrOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            // OCR 客户端使用独立的超时配置。
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            // HTTP/2 多路复用。
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            // 连接池支持并发 OCR 请求。
            .connectionPool(
                ConnectionPool(
                    maxIdleConnections = 20,
                    keepAliveDuration = 10,
                    timeUnit = TimeUnit.MINUTES
                )
            )
            .retryOnConnectionFailure(true)
            // 关闭详细日志。
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.NONE
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideImageGenerationClient(
        @Named("default_okhttp") okHttpClient: OkHttpClient,
        @Named("vivo_aigc_api_key") apiKey: String,
        @Named("agnes_base_url") agnesBaseUrl: String,
        @Named("agnes_api_key") agnesApiKey: String
    ): ImageGenerationClient {
        return ImageGenerationClient(okHttpClient, apiKey, agnesBaseUrl, agnesApiKey)
    }
}
