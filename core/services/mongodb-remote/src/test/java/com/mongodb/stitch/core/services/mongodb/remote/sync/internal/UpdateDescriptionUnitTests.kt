package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.client.MongoCollection
import com.mongodb.stitch.core.services.mongodb.remote.UpdateDescription
import org.bson.BsonDocument
import org.bson.BsonElement
import org.bson.BsonInt32
import org.bson.BsonString
import org.bson.types.ObjectId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import java.lang.IllegalArgumentException

class UpdateDescriptionUnitTests {
    @Test
    fun testUpdateDescriptionApplyToDocument() {
        val originalDoc = BsonDocument()
            .append("a", BsonInt32(1))
            .append("b", BsonInt32(2))
            .append("c", BsonInt32(3))

        val updateDescription = UpdateDescription(
            BsonDocument()
                .append("a", BsonInt32(11))
                .append("d", BsonInt32(44)),
            HashSet(hashSetOf("c"))
        )

        val newDoc = updateDescription.applyToBsonDocument(originalDoc)

        assertEquals(
            BsonDocument()
                .append("a", BsonInt32(11))
                .append("b", BsonInt32(2))
                .append("d", BsonInt32(44)),
            newDoc
        )
    }

    @Test
    fun testUpdateDescriptionMerge() {
        val ud1 = UpdateDescription(BsonDocument("hi", BsonString("there")), setOf("meow", "bark"))
        val ud2 = UpdateDescription(BsonDocument("bye", BsonString("there")), setOf("prr", "woof"))

        ud1.merge(ud2)

        assertEquals(
            ud1.removedFields,
            setOf("meow", "bark", "prr", "woof"))
        assertEquals(
            ud1.updatedFields,
            BsonDocument("hi", BsonString("there")).append("bye", BsonString("there")))

        val ud3 = UpdateDescription(BsonDocument("hi", BsonString("bye")), setOf())
        ud1.merge(ud3)

        assertEquals(
            ud1.removedFields,
            setOf("meow", "bark", "prr", "woof"))
        assertEquals(
            ud1.updatedFields,
            BsonDocument("hi", BsonString("bye")).append("bye", BsonString("there")))

        ud1.merge(null)
        assertEquals(
            ud1.removedFields,
            setOf("meow", "bark", "prr", "woof"))
        assertEquals(
            ud1.updatedFields,
            BsonDocument("hi", BsonString("bye")).append("bye", BsonString("there")))

        assertEquals(
            ud2.removedFields,
            setOf("prr", "woof"))
        assertEquals(
            ud2.updatedFields,
            BsonDocument("bye", BsonString("there")))

        val ud4 = UpdateDescription(BsonDocument("woof", BsonString("hello")), setOf("bye"))

        ud2.merge(ud4)

        assertEquals(ud2.removedFields, setOf("prr", "bye"))
        assertEquals(ud2.updatedFields, BsonDocument("woof", BsonString("hello")))
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
        val removedFields = setOf("meow", "bark")

        val updateDoc = UpdateDescription(
            updatedFields,
            removedFields
        ).toUpdateDocument()

        assertEquals(updatedFields, updateDoc["\$set"])
        assertEquals(removedFields, updateDoc["\$unset"]?.asDocument()?.entries?.map { it.key }?.toSet())
    }

    private fun testDiff(
        collection: MongoCollection<BsonDocument>,
        beforeDocument: BsonDocument,
        expectedUpdateDocument: BsonDocument,
        afterDocument: BsonDocument
    ) {
        // create an update description via diff'ing the two documents.
        val updateDescription = UpdateDescription.diff(withoutId(beforeDocument), withoutId(afterDocument))

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
