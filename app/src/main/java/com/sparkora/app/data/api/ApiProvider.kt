package com.sparkora.app.data.api

import com.sparkora.app.BuildConfig
import com.sparkora.app.data.SessionManager
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds (and caches) a Retrofit instance for the currently configured server.
 * The base URL is user-configurable from the login screen, so the instance is
 * rebuilt whenever it changes.
 */
class ApiProvider(private val session: SessionManager) {

    @OptIn(ExperimentalSerializationApi::class)
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    @Volatile
    private var cached: Pair<String, SparkoraApi>? = null

    fun api(): SparkoraApi {
        val baseUrl = session.cachedBaseUrl
        cached?.let { if (it.first == baseUrl) return it.second }
        synchronized(this) {
            cached?.let { if (it.first == baseUrl) return it.second }

            val clientBuilder = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val token = session.cachedToken
                    val request = if (token.isNullOrBlank()) {
                        chain.request()
                    } else {
                        chain.request().newBuilder()
                            .header("Authorization", "Bearer $token")
                            .build()
                    }
                    chain.proceed(request)
                }

            if (BuildConfig.DEBUG) {
                clientBuilder.addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    }
                )
            }

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(clientBuilder.build())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

            val api = retrofit.create(SparkoraApi::class.java)
            cached = baseUrl to api
            return api
        }
    }
}
