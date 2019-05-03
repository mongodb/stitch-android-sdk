package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.MongoNamespace
import com.mongodb.stitch.core.services.mongodb.remote.ChangeEvent
import com.mongodb.stitch.core.services.mongodb.remote.OperationType
import com.mongodb.stitch.core.services.mongodb.remote.UpdateDescription
import org.bson.BsonArray
import org.bson.BsonBoolean
import org.bson.BsonDocument
import org.bson.BsonObjectId
import org.bson.BsonString
import org.junit.Assert.assertEquals
import org.junit.Test

class ChangeEventUnitTests {
    private val namespace = MongoNamespace("foo", "bar")

    @Test
    fun testNew() {
        val expectedFullDocument = BsonDocument("foo", BsonString("bar")).append("_id", BsonObjectId())
        val expectedId = BsonDocument("_id", expectedFullDocument["_id"])
        val expectedOperationType = OperationType.INSERT
        val expectedNamespace = namespace
        val expectedDocumentKey = BsonDocument("_id", expectedFullDocument["_id"])
        val expectedUpdateDescription = UpdateDescription(
            BsonDocument("foo", BsonString("bar")),
            setOf("baz"))

        val changeEvent = ChangeEvent(
                expectedId,
                expectedOperationType,
                expectedFullDocument,
                expectedNamespace,
                expectedDocumentKey,
                expectedUpdateDescription,
                true)

        assertEquals(expectedId, changeEvent.id)
        assertEquals(expectedOperationType, changeEvent.operationType)
        assertEquals(expectedFullDocument, changeEvent.fullDocument)
        assertEquals(expectedNamespace, changeEvent.namespace)
        assertEquals(expectedDocumentKey, changeEvent.documentKey)
        assertEquals(expectedUpdateDescription, changeEvent.updateDescription)
        assertEquals(true, changeEvent.hasUncommittedWrites())
    }

    @Test
    fun testToBsonDocumentRoundTrip() {
        val expectedFullDocument = BsonDocument("foo", BsonString("bar")).append("_id", BsonObjectId())
        val expectedId = BsonDocument("_id", expectedFullDocument["_id"])
        val expectedOperationType = OperationType.INSERT
        val expectedNamespace = namespace
        val expectedDocumentKey = BsonDocument("_id", expectedFullDocument["_id"])
        val expectedUpdateDescription = UpdateDescription(
            BsonDocument("foo", BsonString("bar")),
            setOf("baz"))

        val changeEvent = ChangeEvent(
                expectedId,
                expectedOperationType,
                expectedFullDocument,
                expectedNamespace,
                expectedDocumentKey,
                expectedUpdateDescription,
                true)

        val changeEventDocument = changeEvent.toBsonDocument()

        assertEquals(expectedFullDocument, changeEventDocument["fullDocument"])
        assertEquals(expectedId, changeEventDocument["_id"])
        assertEquals(BsonString(expectedOperationType.toRemote()), changeEventDocument["operationType"])
        assertEquals(
            BsonDocument("db", BsonString(namespace.databaseName))
                .append("coll", BsonString(namespace.collectionName)),
            changeEventDocument["ns"])
        assertEquals(expectedDocumentKey, changeEventDocument["documentKey"])
        assertEquals(
            BsonDocument("updatedFields", expectedUpdateDescription.updatedFields)
                .append("removedFields", BsonArray(expectedUpdateDescription.removedFields.map { BsonString(it) })),
            changeEventDocument["updateDescription"])
        assertEquals(BsonBoolean(true), changeEventDocument["writePending"])

        val changeEventFromDocument = ChangeEvent.fromBsonDocument(changeEventDocument)

        assertEquals(expectedFullDocument, changeEventFromDocument.fullDocument)
        assertEquals(expectedId, changeEventFromDocument.id)
        assertEquals(expectedOperationType, changeEventFromDocument.operationType)
        assertEquals(expectedDocumentKey, changeEventFromDocument.documentKey)
        assertEquals(expectedUpdateDescription.updatedFields, changeEventFromDocument.updateDescription!!.updatedFields)
        assertEquals(expectedUpdateDescription.removedFields, changeEventFromDocument.updateDescription!!.removedFields)
        assertEquals(true, changeEventFromDocument.hasUncommittedWrites())
    }
}
