package com.mongodb.stitch.core.internal.net

import com.mongodb.stitch.core.internal.net.Headers.CONTENT_TYPE
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import org.junit.Test
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.locks.ReentrantLock

internal class RoundTripHandler: HttpHandler {
    @Throws(IOException::class)
    override fun handle(t: HttpExchange) {
        val response = "This is the response"
        t.sendResponseHeaders(200, response.length.toLong())
        val os = t.responseBody
        os.write(response.toByteArray())
        os.close()
    }
}

internal class StreamHandler: HttpHandler {
    @Throws(IOException::class)
    override fun handle(t: HttpExchange) {
        var x = 0
        t.responseHeaders.set(CONTENT_TYPE, "text/event-next")
        t.sendResponseHeaders(200, 0)

        val os = t.responseBody
        while (x < 10) {
            val response = "data: poop${Math.random()}\n\n"
            os.write(response.toByteArray())
            x += 1
            Thread.sleep((Math.random() * 100).toLong())
            os.flush()
        }
        os.close()
    }
}
class TransportIntTests {
    val lock = ReentrantLock()

    init {
        Thread {
            lock.lock()
            val server = HttpServer.create(InetSocketAddress(8000), 0)
            server.createContext("/roundTrip", RoundTripHandler())
            server.createContext("/next", StreamHandler())
            server.executor = null // creates a default executor
            server.start()
            if (lock.isLocked) {
                lock.unlock()
            }
        }.start()
    }

    @Test
    fun testRoundTrip() {
        val transport = OkHttpTransport()

        val resp = transport.roundTrip(
                Request.Builder().withUrl(
                        "http://localhost:8000/roundTrip"
                ).withMethod(Method.GET).withTimeout(1000).build())

        val inputAsString = resp.body!!.bufferedReader().use { it.readText() }  // defaults to UTF-8

        print(inputAsString)
    }

    @Test
    fun testStream() {
        val transport = OkHttpTransport()

        val stream = transport.stream(
                Request.Builder().withUrl(
                        "http://localhost:8000/next"
                ).withMethod(Method.GET).withTimeout(1000).build())

        while (stream.isOpen) {

            val event = stream.nextEvent()
            if (event.data != null)
                println(event.data!!)
        }
//        val inputAsString = resp.body!!.bufferedReader().use { it.readText() }  // defaults to UTF-8

//        print(event.data)
    }
}