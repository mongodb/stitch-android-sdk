/*
 * Copyright 2018-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
