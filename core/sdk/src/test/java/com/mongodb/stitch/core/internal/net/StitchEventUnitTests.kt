package com.mongodb.stitch.core.internal.net

import com.mongodb.stitch.core.StitchServiceErrorCode
import com.mongodb.stitch.core.StitchServiceException
import org.bson.Document
import org.bson.codecs.DocumentCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StitchEventUnitTests {
    @Test
    fun testFromEvent() {
        var doc = Document(
                "article",
                mapOf("title" to "this deal's getting worse all the time",
                        "author" to "billy dee")
        )

        var stitchEvent = StitchEvent.fromEvent(
                Event.Builder().withData(doc.toJson()).build(), DocumentCodec())

        assertEquals(doc, stitchEvent.data)
        assertEquals(Event.MESSAGE_EVENT, stitchEvent.eventName)
        assertNull(stitchEvent.error)

        val error = StitchServiceException("that was never a condition of our arrangement",
                StitchServiceErrorCode.UNKNOWN)

        doc = Document(
                "error", error.message
        ).append("error_code", "i have altered the deal")

        stitchEvent = StitchEvent.fromEvent(
                Event.Builder().withData(doc.toJson()).withEventName("error").build(),
                DocumentCodec())

        assertEquals(error.message, stitchEvent.error!!.message)
        assertEquals("error", stitchEvent.eventName)
        assertNull(stitchEvent.data)

        stitchEvent = StitchEvent.fromEvent(
                Event.Builder().withData(doc.toJson()).withEventName("unreal").build(),
                DocumentCodec())

        assertNull(stitchEvent.error)
        assertNull(stitchEvent.data)
    }
}
