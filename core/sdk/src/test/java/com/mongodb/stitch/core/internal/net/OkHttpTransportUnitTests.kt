package com.mongodb.stitch.core.internal.net

import com.mongodb.stitch.core.StitchServiceErrorCode
import com.mongodb.stitch.core.StitchServiceException
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.bson.Document
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.Exception
import java.lang.IllegalStateException
import java.net.BindException
import java.net.InetSocketAddress

class OkHttpTransportUnitTests {
    private val server: HttpServer
    private val port: Int

    init {
        var server: HttpServer? = null
        var port = 8000
        while (server == null) {
            try {
                // attempt to create the server until we find an open port
                server = HttpServer.create(InetSocketAddress(port), 0)
            } catch (be: BindException) {
                port++
                if (port > 65535) {
                    throw IllegalStateException("all ports in use")
                }
            }
        }
        this.server = server
        this.server.executor = null
        this.port = port
        this.server.start()
    }

    @Test
    fun testStream() {
        val transport = OkHttpTransport()
        var doc = Document("is_this_pod_racing", true)

        withServer("/next", HttpHandler { exchange: HttpExchange ->
            exchange.responseHeaders.set(Headers.CONTENT_TYPE, ContentTypes.TEXT_EVENT_STREAM)
            exchange.sendResponseHeaders(200, 0)

            val os = exchange.responseBody

            os.write("data: ${doc.toJson()}\n\n".toByteArray())
            os.close()
        }) {
            val stream = transport.stream(
                    Request.Builder().withUrl(
                            "http://localhost:$port/next"
                    ).withMethod(Method.GET).withTimeout(1000).build())


            assertEquals(doc, Document.parse(stream.nextEvent().data))
        }

        doc = Document(
                mapOf(
                        "error" to "does not have the high ground",
                        "error_code" to StitchServiceErrorCode.FUNCTION_INVALID.codeName
                )
        )
        withServer("/error", HttpHandler { exchange: HttpExchange ->
            exchange.responseHeaders.set(Headers.CONTENT_TYPE, ContentTypes.APPLICATION_JSON)
            exchange.sendResponseHeaders(400, 0)

            val os = exchange.responseBody

            os.write(doc.toJson().toByteArray())
            os.close()
        }) {
            try {
                transport.stream(
                        Request.Builder().withUrl(
                                "http://localhost:$port/error"
                        ).withMethod(Method.GET).withTimeout(1000).build())
            } catch (e: Exception) {
                assert(e is StitchServiceException)
                assertEquals(doc["error"], (e as StitchServiceException).message)
                assertEquals(
                        StitchServiceErrorCode.FUNCTION_INVALID,
                        e.errorCode)
            }
        }
    }

    private fun withServer(endpoint: String, withHandler: HttpHandler, block: () -> Unit) {
        server.createContext(endpoint, withHandler)
        block()
        server.removeContext(endpoint)
    }
}