package com.mongodb.stitch.android.services.mongodb.remote.internal

/*
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit


open class MockChainImpl : Interceptor.Chain {
    override fun request(): Request {
        throw NotImplementedError("method not mocked")
    }

    @Throws(IOException::class)
    override fun proceed(request: Request): Response {
        throw NotImplementedError("method not mocked")
    }

    override fun connection(): Connection? {
        throw NotImplementedError("method not mocked")
    }

    override fun call(): Call {
        throw NotImplementedError("method not mocked")
    }

    override fun connectTimeoutMillis(): Int {
        throw NotImplementedError("method not mocked")
    }

    override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain {
        throw NotImplementedError("method not mocked")
    }

    override fun readTimeoutMillis(): Int {
        throw NotImplementedError("method not mocked")
    }

    override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain {
        throw NotImplementedError("method not mocked")
    }

    override fun writeTimeoutMillis(): Int {
        throw NotImplementedError("method not mocked")
    }

    override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain {
        throw NotImplementedError("method not mocked")
    }
}

@RunWith(MockitoJUnitRunner::class)
class OkHttpInstrumentedTransportUnitTests {
    @Test
    fun testIntercept() {
        val instrumentedTransport = OkHttpInstrumentedTransport()

        val chainMock = Mockito.mock(MockChainImpl::class.java)
        val requestMock = Mockito.mock(Request::class.java)
        val requestBodyMock = Mockito.mock(RequestBody::class.java)
        val headersMock = Mockito.mock(Headers::class.java)
        val responseMock = Mockito.mock(Response::class.java)
        val responseBodyMock = Mockito.mock(ResponseBody::class.java)


        Mockito.`when`(headersMock.byteCount()).thenReturn(10)
        Mockito.`when`(requestMock.headers()).thenReturn(headersMock)
        Mockito.`when`(requestBodyMock.contentLength()).thenReturn(10)
        Mockito.`when`(requestMock.body()).thenReturn(requestBodyMock)
        Mockito.`when`(responseBodyMock.contentLength()).thenReturn(10)
        Mockito.`when`(responseMock.headers()).thenReturn(headersMock)
        Mockito.`when`(responseMock.body()).thenReturn(responseBodyMock)

        Mockito.`when`(chainMock.request()).thenReturn(requestMock)
        Mockito.`when`(chainMock.proceed(any())).thenReturn(responseMock)

        instrumentedTransport.interceptor.intercept(chainMock)

        assertEquals(20, instrumentedTransport.bytesUploaded)
        assertEquals(20, instrumentedTransport.bytesDownloaded)
    }
}
*/
