package com.mongodb.stitch.core.internal.net

import okhttp3.Call
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class OkHttpEventStreamUnitTests {
    @Test
    fun testOkHttpEventStream() {
        val odds = "never tell me the odds"
        val treason = "it's treason then"

        val dataOdds = "data: $odds"
        val dataTreason = "data: $treason"

        val buffer = Mockito.spy(Buffer())

        buffer.write("$dataOdds\n".toByteArray())
        buffer.write("$dataTreason\n\n".toByteArray())

        val stream = OkHttpEventStream(null, buffer, Mockito.mock(Call::class.java))

        assert(stream.isActive)

        assertEquals(dataOdds, stream.readLine())

        assert(stream.isOpen)

        assertEquals(treason, stream.nextEvent().data.split("\n")[0])

        stream.close()

        // just check if close was called on the buffer
        // these buffers do not actually close in test
        Mockito.verify(buffer).close()
    }
}