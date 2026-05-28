package com.example.network

import android.content.Context
import com.example.db.VaultDatabase
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import android.util.Log

object VaultNetworkClient {
    private const val TAG = "VaultNetworkClient"

    // Default mock URL if no server configured. Secure HTTPS.
    var baseUrl: String = "https://api.secure-vault-sync.cloud/"
        set(value) {
            val formatted = if (value.endsWith("/")) value else "$value/"
            field = if (formatted.startsWith("https://")) formatted else "https://$formatted"
            Log.d(TAG, "Base URL updated: $field")
            rebuildService()
        }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private var cachedService: VaultApiService? = null

    // Setup network client
    private fun buildOkHttpClient(context: Context): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // Interceptor to inject device headers dynamically from DB
        val deviceHeaderInterceptor = Interceptor { chain ->
            val database = VaultDatabase.getDatabase(context)
            var deviceId = "UNKNOWN_DEVICE"
            try {
                // Read from DB synchronously for OkHttp thread
                runBlocking {
                    val reg = database.vaultDao().getDeviceRegistration()
                    if (reg != null) {
                        deviceId = reg.deviceId
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching device ID for interception", e)
            }

            val request = chain.request().newBuilder()
                .header("X-Device-ID", deviceId)
                .header("device_id", deviceId) // Flutter/Dio compatible variant
                .build()

            // Enforcement of HTTPS only
            if (!request.url.isHttps) {
                Log.w(TAG, "WARNING: Blocking unsecure HTTP request to: ${request.url}")
                throw SecurityException("Insecure connections are forbidden. Use HTTPS only.")
            }

            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(deviceHeaderInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    private fun buildRetrofit(context: Context, url: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(url)
            .client(buildOkHttpClient(context))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Synchronized
    fun getApiService(context: Context): VaultApiService {
        if (cachedService == null) {
            cachedService = buildRetrofit(context.applicationContext, baseUrl).create(VaultApiService::class.java)
        }
        return cachedService!!
    }

    @Synchronized
    private fun rebuildService() {
        // Force garbage collection of previous instances so it gets remade with the updated URL
        cachedService = null
    }
}
