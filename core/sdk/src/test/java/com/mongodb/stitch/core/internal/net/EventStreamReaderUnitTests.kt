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
        var data = event.data.split("\n")

        assertEquals(event.eventName, Event.MESSAGE_EVENT)
        assertEquals(data[0], "MDB")
        assertEquals(data[1], "+2")
        assertEquals(data[2], "10")

        testEventStreamReader.testStream = errorEvent
        event = testEventStreamReader.processEvent()
        data = event.data.split("\n")

        assertEquals(event.eventName, "error")
        assertEquals(data[0], "you")
        assertEquals(data[1], "messed")
        assertEquals(data[2], "up")
    }
}
