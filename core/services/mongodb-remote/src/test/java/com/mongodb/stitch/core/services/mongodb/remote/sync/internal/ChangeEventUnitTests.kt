package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.MongoNamespace
import com.mongodb.client.MongoCollection
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.ChangeEvent.UpdateDescription.diff
import org.bson.BsonArray
import org.bson.BsonBoolean
import org.bson.BsonDocument
import org.bson.BsonElement
import org.bson.BsonInt32
import org.bson.BsonObjectId
import org.bson.BsonString
import org.bson.types.ObjectId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import java.lang.IllegalArgumentException

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

    @Test
    fun testUpdateDescriptionDiff() {
        val harness = SyncUnitTestHarness()
        val client = harness.freshTestContext().localClient
        val collection: MongoCollection<BsonDocument> = client
            .getDatabase("dublin")
            .getCollection("restaurants${ObjectId().toHexString()}", BsonDocument::class.java)

        // insert our original document.
        // assert that, without comparing ids, our
        // inserted document equals our original document
        val originalJson = """
             {
               "shop_name": "nkd pizza",
               "address": {
                 "street": "9 orwell rd",
                 "city": "dublin 6",
                 "county": "dublin"
               },
               "rating": 5,
               "menu": [
                 "cheese",
                 "pepperoni",
                 "veggie"
               ],
               "employees": [
                 {
                   "name": "aoife",
                   "age": 26,
                   "euro_per_hr": 18,
                   "title": "junior employee"
                 },
                 {
                   "name": "niamh",
                   "age": 27,
                   "euro_per_hr": 20,
                   "title": "chef"
                 },
               ]
             }
            """
        var beforeDocument = BsonDocument.parse(originalJson)
        collection.insertOne(beforeDocument)
        assertEquals(
            withoutId(beforeDocument),
            collection.aggregate(
                listOf(
                    BsonDocument(
                        "\$project",
                        BsonDocument("_id", BsonInt32(0))
                            .append("employees", BsonDocument("_id", BsonInt32(0)))
                    ))).first())
        var afterDocument = BsonDocument.parse("""
             {
               "shop_name": "nkd pizza",
               "address": {
                 "street": "10 orwell rd",
                 "city": "dublin 6",
                 "county": "dublin"
               },
               "menu": [
                 "cheese",
                 "veggie"
               ],
               "employees": [
                 {
                   "name": "aoife",
                   "age": 26,
                   "euro_per_hr": 18,
                   "title": "senior employee"
                 },
                 {
                   "name": "niamh",
                   "age": 27,
                   "euro_per_hr": 20,
                   "title": "chef"
                 },
                 {
                   "name": "alice",
                   "age": 29,
                   "euro_per_hr": 14,
                   "title": "cashier"
                 },
               ]
             }
            """).append("_id", beforeDocument["_id"])
        // 1. test general nested swaps
        testDiff(
            collection = collection,
            beforeDocument = beforeDocument,
            expectedUpdateDocument = BsonDocument.parse("""
                {
                    "${'$'}set": {
                        "address.street": "10 orwell rd",
                        "menu" : ["cheese", "veggie"],
                        "employees" : [
                            {
                                "name": "aoife",
                                "age": 26,
                                "euro_per_hr": 18,
                                "title" : "senior employee"
                            },
                            {
                                "name": "niamh",
                                "age": 27,
                                "euro_per_hr": 20,
                                "title": "chef"
                            },
                            {
                                "name": "alice",
                                "age": 29,
                                "euro_per_hr": 14,
                                "title": "cashier"
                            }
                        ]
                    },
                    "${'$'}unset" : {
                        "rating": true
                    }
                }
            """),
            afterDocument = afterDocument)
        // 2. test array to null
        beforeDocument = afterDocument
        afterDocument = BsonDocument.parse("""
             {
               "shop_name": "nkd pizza",
               "address": {
                 "street": "10 orwell rd",
                 "city": "dublin 6",
                 "county": "dublin"
               },
               "menu": null,
               "employees": [
                 {
                   "name": "aoife",
                   "age": 26,
                   "euro_per_hr": 18,
                   "title": "senior employee"
                 },
                 {
                   "name": "niamh",
                   "age": 27,
                   "euro_per_hr": 20,
                   "title": "chef"
                 },
                 {
                   "name": "alice",
                   "age": 29,
                   "euro_per_hr": 14,
                   "title": "cashier"
                 },
               ]
             }
            """).append("_id", beforeDocument["_id"])
        testDiff(
            collection = collection,
            beforeDocument = beforeDocument,
            expectedUpdateDocument = BsonDocument.parse("""
                { "${'$'}set" : { "menu" : null } }
            """.trimIndent()),
            afterDocument = afterDocument)

        // 3. test doc to empty doc
        beforeDocument = afterDocument
        afterDocument = BsonDocument().append("_id", beforeDocument["_id"])
        testDiff(
            collection = collection,
            beforeDocument = beforeDocument,
            expectedUpdateDocument = BsonDocument.parse("""
                {
                    "${'$'}unset" : {
                        "shop_name" : true,
                        "address" : true,
                        "menu" : true,
                        "employees" : true
                    }
                }
            """.trimIndent()),
            afterDocument = afterDocument)

        // 4. test empty to empty
        beforeDocument = afterDocument
        afterDocument = BsonDocument()
        try {
            testDiff(
                collection = collection,
                beforeDocument = beforeDocument,
                expectedUpdateDocument = BsonDocument(),
                afterDocument = afterDocument
            )
            fail("Should have thrown exception due to invalid bson")
        } catch (e: IllegalArgumentException) {
            assertNotNull(e)
        }

        harness.close()
    }

    @Test
    fun testUpdateDescriptionToUpdateDoc() {
        val updatedFields = BsonDocument("hi", BsonString("there"))
        val removedFields = listOf("meow", "bark")

        val updateDoc = ChangeEvent.UpdateDescription(
            updatedFields,
            removedFields
        ).toUpdateDocument()

        assertEquals(updatedFields, updateDoc["\$set"])
        assertEquals(removedFields, updateDoc["\$unset"]?.asDocument()?.entries?.map { it.key })
    }

    fun testDiff(
        collection: MongoCollection<BsonDocument>,
        beforeDocument: BsonDocument,
        expectedUpdateDocument: BsonDocument,
        afterDocument: BsonDocument
    ) {
        // create an update description via diff'ing the two documents.
        val updateDescription = diff(withoutId(beforeDocument), withoutId(afterDocument))

        assertEquals(
            expectedUpdateDocument,
            updateDescription.toUpdateDocument()
        )

        // create an update document from the update description.
        // update the original document with the update document
        collection.updateOne(BsonDocument("_id", beforeDocument.getObjectId("_id")), updateDescription.toUpdateDocument())

        // assert that our newly updated document reflects our expectations
        assertEquals(
            withoutId(afterDocument),
            collection.aggregate(
                listOf(
                    BsonDocument(
                        "\$project",
                        BsonDocument("_id", BsonInt32(0))
                            .append("employees", BsonDocument("_id", BsonInt32(0)))
                    ))).first())
    }

    private fun withoutId(document: BsonDocument): BsonDocument {
        val newDocument = BsonDocument(document.map { BsonElement(it.key, it.value) })
        newDocument.remove("_id")
        return newDocument
    }
}
