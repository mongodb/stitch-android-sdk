package com.mongodb.stitch.android.services.mongodb.performance

import com.mongodb.stitch.core.internal.net.OkHttpTransport
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

class OkHttpInstrumentedTransport : OkHttpTransport() {
    var bytesUploaded: Long = 0
        private set
    var bytesDownloaded: Long = 0
        private set
    val interceptor: Interceptor by lazy {
        Interceptor { chain: Interceptor.Chain ->
            val request = chain.request()
            val requestBody = request.body()
            bytesUploaded += request.headers().byteCount()
            bytesUploaded += requestBody?.contentLength() ?: 0

            val response: Response = chain.proceed(request)
            val responseBody = response.body()
            bytesDownloaded += response.headers().byteCount()
            bytesDownloaded += responseBody?.contentLength() ?: 0
            response
        }
    }

    override fun newClientBuilder(
        connectTimeout: Long,
        readTimeout: Long,
        writeTimeout: Long
    ): OkHttpClient.Builder {
        return super.newClientBuilder(connectTimeout, readTimeout, writeTimeout)
            .addNetworkInterceptor(this.interceptor)
    }
}
