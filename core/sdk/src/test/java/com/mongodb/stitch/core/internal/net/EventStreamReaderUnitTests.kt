package com.mongodb.stitch.core.internal.net

import org.junit.Test
import kotlin.test.assertEquals

class EventStreamReaderUnitTests {
    private val dataEvent = arrayOf(
            "data: MDB",
            "data: +2",
            "data: 10",
            ""
    ).iterator()

    private val errorEvent = arrayOf(
            "event: error",
            "data: you",
            "data: messed",
            "data: up",
            ""
    ).iterator()

    class TestEventStreamReader : EventStreamReader() {
        var testStream = emptyArray<String>().iterator()

        override fun isOpen(): Boolean {
            return testStream.hasNext()
        }

        override fun readLine(): String {
            return testStream.next()
        }
    }

    @Test
    fun testReadEvent() {
        val testEventStreamReader = TestEventStreamReader()
        testEventStreamReader.testStream = dataEvent

        var event = testEventStreamReader.processEvent()

        assertEquals(event.eventName, Event.MESSAGE_EVENT)
        assertEquals(event.data, "MDB\n+2\n10")

        testEventStreamReader.testStream = errorEvent
        event = testEventStreamReader.processEvent()

        assertEquals(event.eventName, "error")
        assertEquals(event.data, "you\nmessed\nup")
    }
}
