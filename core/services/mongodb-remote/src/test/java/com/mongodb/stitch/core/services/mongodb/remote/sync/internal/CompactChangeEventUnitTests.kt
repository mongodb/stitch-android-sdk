package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.stitch.core.services.mongodb.remote.CompactChangeEvent
import com.mongodb.stitch.core.services.mongodb.remote.OperationType
import com.mongodb.stitch.core.services.mongodb.remote.UpdateDescription
import org.bson.BsonBoolean
import org.bson.BsonDocument
import org.bson.BsonInt64
import org.bson.BsonObjectId
import org.bson.BsonString
import org.junit.Test
import org.junit.Assert.assertEquals

class CompactChangeEventUnitTests {
    private val expectedOperationType = OperationType.INSERT
    private val expectedFullDocument = BsonDocument("foo", BsonString("bar"))
        .append("_id", BsonObjectId())
    private val expectedDocumentKey = BsonDocument("_id", expectedFullDocument.getObjectId("_id"))
    private val expectedUpdateDescription = UpdateDescription(
        BsonDocument("foo", BsonString("bar")),
        listOf("blah")
    )
    private val expectedDocumentVersion = DocumentVersionInfo.Version(
        1,
        BsonObjectId().toString(),
        0
    )
    private val expectedDocumentHash = 12345L
    private val expectedHasUncommittedWrites = true

    @Test
    fun testToBsonDocument() {
        val event = CompactChangeEvent(
            expectedOperationType,
            expectedFullDocument,
            expectedDocumentKey,
            expectedUpdateDescription,
            expectedDocumentVersion,
            expectedDocumentHash,
            expectedHasUncommittedWrites
        )

        val eventDoc = event.toBsonDocument()

        assertEquals(eventDoc.getString("ot").value, expectedOperationType.toRemote())
        assertEquals(eventDoc.getDocument("fd"), expectedFullDocument)
        assertEquals(eventDoc.getDocument("dk"), expectedDocumentKey)
        assertEquals(
            UpdateDescription.fromBsonDocument(eventDoc.getDocument("ud")),
            expectedUpdateDescription
        )
        assertEquals(eventDoc.getDocument("sdv"), expectedDocumentVersion.toBsonDocument())
        assertEquals(eventDoc.getInt64("sdh").value, expectedDocumentHash)
        assertEquals(
            eventDoc.getBoolean("writePending").value,
            expectedHasUncommittedWrites
        )
    }

    @Test
    fun testFromBsonDocument() {
        val eventDoc = BsonDocument()
            .append("ot", BsonString(expectedOperationType.toRemote()))
            .append("fd", expectedFullDocument)
            .append("dk", expectedDocumentKey)
            .append("ud", expectedUpdateDescription.toBsonDocument())
            .append("sdv", expectedDocumentVersion.toBsonDocument())
            .append("sdh", BsonInt64(expectedDocumentHash))
            .append("writePending", BsonBoolean(expectedHasUncommittedWrites))

        val event = CompactChangeEvent.fromBsonDocument(eventDoc)

        assertEquals(event.operationType, expectedOperationType)
        assertEquals(event.fullDocument, expectedFullDocument)
        assertEquals(event.documentKey, expectedDocumentKey)
        assertEquals(event.updateDescription, expectedUpdateDescription)
        assertEquals(
            event.stitchDocumentVersion!!.toBsonDocument(),
            expectedDocumentVersion.toBsonDocument()
        )
        assertEquals(event.stitchDocumentHash, expectedDocumentHash)
        assertEquals(event.hasUncommittedWrites(), expectedHasUncommittedWrites)
    }

    @Test
    fun testWithoutUncommittedWrites() {
        val event = CompactChangeEvent(
            expectedOperationType,
            expectedFullDocument,
            expectedDocumentKey,
            expectedUpdateDescription,
            expectedDocumentVersion,
            expectedDocumentHash,
            true
        )

        assertEquals(event.hasUncommittedWrites(), true)
        assertEquals(event.withoutUncommittedWrites().hasUncommittedWrites(), false)
    }
}