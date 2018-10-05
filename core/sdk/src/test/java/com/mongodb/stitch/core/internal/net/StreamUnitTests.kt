package com.mongodb.stitch.core.internal.net

import org.bson.Document
import org.bson.codecs.DocumentCodec
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamUnitTests {
    class TestEventStreamReader : EventStream, EventStreamReader() {
        var testStream = emptyArray<String>().iterator()

        override fun nextEvent(): Event {
            return this.processEvent()
        }

        override fun close() {
            testStream.forEachRemaining { }
        }

        override fun isOpen(): Boolean {
            return testStream.hasNext()
        }

        override fun readLine(): String {
            return testStream.next()
        }
    }

    @Test
    fun testNextEvent() {
        val testEventStreamReader = TestEventStreamReader()

        val doc = Document(
                "article",
                mapOf("title" to "how to write tests",
                        "author" to "best author")
        )

        testEventStreamReader.testStream = "data: ${doc.toJson()}\n".split("\n").iterator()

        val stream = Stream(testEventStreamReader, DocumentCodec())

        assert(stream.isOpen)

        val event = stream.nextEvent()

        assertEquals(doc, event.data)

        assert(!stream.isOpen)
    }
}
