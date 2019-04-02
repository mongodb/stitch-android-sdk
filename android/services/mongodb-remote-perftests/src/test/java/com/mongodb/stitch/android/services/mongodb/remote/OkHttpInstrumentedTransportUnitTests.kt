package com.mongodb.stitch.android.services.mongodb.remote

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner

/**
 * Returns Mockito.any() as nullable type to avoid java.lang.IllegalStateException when
 * null is returned.
 */
fun <T> any(): T = Mockito.any<T>()

@RunWith(MockitoJUnitRunner::class)
class OkHttpInstrumentedTransportUnitTests {
    @Test
    fun testIntercept() {
        val instrumentedTransport = OkHttpInstrumentedTransport()

        val chainMock = Mockito.mock(Interceptor.Chain::class.java)
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
