package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.MongoNamespace
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
        val expectedOperationType = ChangeEvent.OperationType.INSERT
        val expectedNamespace = namespace
        val expectedDocumentKey = BsonDocument("_id", expectedFullDocument["_id"])
        val expectedUpdateDescription = ChangeEvent.UpdateDescription(
            BsonDocument("foo", BsonString("bar")),
            listOf("baz"))

        val changeEvent = ChangeEvent(
            expectedId,
            expectedOperationType,
            expectedFullDocument,
            expectedNamespace,
            expectedDocumentKey,
            expectedUpdateDescription,
            true
        )

        assertEquals(expectedId, changeEvent.id)
        assertEquals(expectedOperationType, changeEvent.operationType)
        assertEquals(expectedFullDocument, changeEvent.fullDocument)
        assertEquals(expectedNamespace, changeEvent.namespace)
        assertEquals(expectedDocumentKey, changeEvent.documentKey)
        assertEquals(expectedUpdateDescription, changeEvent.updateDescription)
        assertEquals(true, changeEvent.hasUncommittedWrites())
    }

    @Test
    fun testOperationTypeFromRemote() {
        assertEquals(
            ChangeEvent.OperationType.INSERT,
            ChangeEvent.OperationType.fromRemote("insert"))

        assertEquals(
            ChangeEvent.OperationType.UPDATE,
            ChangeEvent.OperationType.fromRemote("update"))

        assertEquals(
            ChangeEvent.OperationType.REPLACE,
            ChangeEvent.OperationType.fromRemote("replace"))

        assertEquals(
            ChangeEvent.OperationType.DELETE,
            ChangeEvent.OperationType.fromRemote("delete"))

        assertEquals(
            ChangeEvent.OperationType.UNKNOWN,
            ChangeEvent.OperationType.fromRemote("bad"))
    }

    @Test
    fun testOperationTypeToRemote() {
        assertEquals("insert", ChangeEvent.OperationType.INSERT.toRemote())
        assertEquals("update", ChangeEvent.OperationType.UPDATE.toRemote())
        assertEquals("replace", ChangeEvent.OperationType.REPLACE.toRemote())
        assertEquals("delete", ChangeEvent.OperationType.DELETE.toRemote())
        assertEquals("unknown", ChangeEvent.OperationType.UNKNOWN.toRemote())
    }

    @Test
    fun testToBsonDocumentRoundTrip() {
        val expectedFullDocument = BsonDocument("foo", BsonString("bar")).append("_id", BsonObjectId())
        val expectedId = BsonDocument("_id", expectedFullDocument["_id"])
        val expectedOperationType = ChangeEvent.OperationType.INSERT
        val expectedNamespace = namespace
        val expectedDocumentKey = BsonDocument("_id", expectedFullDocument["_id"])
        val expectedUpdateDescription = ChangeEvent.UpdateDescription(
            BsonDocument("foo", BsonString("bar")),
            listOf("baz"))

        val changeEvent = ChangeEvent(
            expectedId,
            expectedOperationType,
            expectedFullDocument,
            expectedNamespace,
            expectedDocumentKey,
            expectedUpdateDescription,
            true)

        val changeEventDocument = ChangeEvent.toBsonDocument(changeEvent)

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
        assertEquals(expectedUpdateDescription.updatedFields, changeEventFromDocument.updateDescription.updatedFields)
        assertEquals(expectedUpdateDescription.removedFields, changeEventFromDocument.updateDescription.removedFields)
        assertEquals(true, changeEventFromDocument.hasUncommittedWrites())
    }
}
