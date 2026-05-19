package com.formbricks.android.network

import com.formbricks.android.logger.Logger
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class FormbricksRetrofitBuilder(private val baseUrl: String, private val loggingEnabled: Boolean) {
    fun getBuilder(): Retrofit.Builder? {
        // Validate base URL is HTTPS
        if (!baseUrl.startsWith("https://", ignoreCase = true)) {
            val error = RuntimeException("Only HTTPS URLs are allowed. HTTP URLs are not permitted for security reasons. Provided URL: $baseUrl")
            Logger.e(error)
            return null
        }

        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .followSslRedirects(true)
            .addInterceptor(HttpsOnlyInterceptor())
        
        if (loggingEnabled) {
            val logging = HttpLoggingInterceptor()
            logging.setLevel(HttpLoggingInterceptor.Level.BODY)
            clientBuilder.addInterceptor(logging)
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(clientBuilder.build())
    }

    /**
     * Interceptor that ensures all requests use HTTPS protocol
     */
    private class HttpsOnlyInterceptor : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url
            
            if (!url.isHttps) {
                val error = RuntimeException("HTTP request blocked. Only HTTPS requests are allowed. Attempted URL: $url")
                Logger.e(error)
                throw IOException("HTTP request blocked. Only HTTPS requests are allowed. Attempted URL: $url")
            }
            
            return chain.proceed(request)
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 30 * 1000 // 30 seconds
        private const val READ_TIMEOUT_MS = 30 * 1000 // 30 seconds
    }
}