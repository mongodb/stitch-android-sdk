package com.mongodb.stitch.services.mongodb

import android.support.test.runner.AndroidJUnit4
import com.mongodb.stitch.StitchTestCase
import com.mongodb.stitch.admin.create
import com.mongodb.stitch.admin.services.ServiceConfigWrapper
import com.mongodb.stitch.admin.services.ServiceConfigs
import com.mongodb.stitch.admin.services.service
import com.mongodb.stitch.android.services.mongodb.MongoClient
import com.mongodb.stitch.assertThat
import com.mongodb.stitch.await
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFails

@RunWith(AndroidJUnit4::class)
class AndroidMongoClientTest: StitchTestCase() {
    companion object {
        val TEST_DB = ObjectId().toString()
        val TEST_COLLECTION = ObjectId().toString()
    }

    private var collection: MongoClient.Collection? = null

    override fun setup() {
        super.setup()
        registerAndLogin()
        val mongodbService = await(
                harness.app.services.create(data = ServiceConfigWrapper(
                        name = "mdb",
                        type = "mongodb",
                        config = ServiceConfigs.Mongo(uri = "mongodb://localhost:26000"))
                )
        )
        await(harness.app.services.service(mongodbService.id).rules.create(
                Document(mapOf(
                        "name" to "testRule",
                        "namespace" to "$TEST_DB.$TEST_COLLECTION",
                        "read" to mapOf("%%true" to true),
                        "write" to mapOf("%%true" to true),
                        "valid" to mapOf("%%true" to true),
                        "fields" to mapOf<String, Map<String, String>>(
                                "_id" to emptyMap(),
                                "owner_id" to emptyMap(),
                                "a" to emptyMap(),
                                "b" to emptyMap(),
                                "c" to emptyMap(),
                                "d" to emptyMap()
                        )
                ))
        ))

        val mongoClient = MongoClient(this.stitchClient, "mdb")
        collection = mongoClient.getDatabase(TEST_DB).getCollection(TEST_COLLECTION)
    }

    @Test
    fun testFind() {
        await(collection!!.insertOne(
                Document(mapOf("a" to 1, "b" to 2, "c" to 3, "owner_id" to stitchClient.userId))
        ))
        var docs = await(collection!!.find(Document("owner_id", stitchClient.userId), 10))
        assertThat(docs.size == 1)

        docs = await(collection!!.find(
                Document("owner_id", stitchClient.userId),
                Document("<array>.$", 1),
                10)
        )
        assertThat(docs.size == 1)
        assertFails {
            await(MongoClient.Collection(
                    MongoClient.Database(MongoClient(stitchClient, "mdb"), "not_a_db"),
                    "not_a_coll"
            ).find(
                    Document("owner_id", stitchClient.userId),
                    Document("<array>.$", 1),
                    10
            ))
        }
    }

    @Test
    fun testCount() {
        assertThat(await(collection!!.count(Document("owner_id", stitchClient.userId))) == 0L)
        await(collection!!.insertOne(
                Document(mapOf("a" to 1, "b" to 2, "c" to 3, "owner_id" to stitchClient.userId))
        ))
        assertThat(await(collection!!.count(Document("owner_id", stitchClient.userId))) == 1L)

        assertThat(await(collection!!.count(
                Document("owner_id", stitchClient.userId),
                Document("<array>.$", 1))
        ) == 1L)
        assertFails {
            await(MongoClient.Collection(
                    MongoClient.Database(MongoClient(stitchClient, "mdb"), "not_a_db"),
                    "not_a_coll"
            ).count(
                    Document("owner_id", stitchClient.userId)
            ))
        }
    }

    @Test
    fun testUpdateOne() {
        await(collection!!.insertOne(
                Document(mapOf("a" to 1, "b" to 2, "c" to 3, "owner_id" to stitchClient.userId))
        ))

        await(collection!!.updateOne(
                Document("owner_id", stitchClient.userId),
                Document("\$set", mapOf("a" to 2)))
        )
        val doc = await(collection!!.find(Document("owner_id", stitchClient.userId), 10))
        assertThat(doc.first()["a"] == 2)
        assertThat(doc.first()["b"] == 2)

        await(collection!!.updateOne(
                Document("owner_id", stitchClient.userId), Document("\$set", mapOf("a" to 2)), true)
        )
        assertFails {
            await(MongoClient.Collection(
                    MongoClient.Database(MongoClient(stitchClient, "mdb"), "not_a_db"),
                    "not_a_coll"
            ).updateOne(
                    Document("owner_id", stitchClient.userId),
                    Document("\$set", mapOf("a" to 2))
            ))
        }
    }

    @Test
    fun testUpdateMany() {
        await(collection!!.insertMany(
                listOf(
                    Document(mapOf("a" to 1, "b" to 2, "c" to 3, "owner_id" to stitchClient.userId)),
                    Document(mapOf("a" to 3, "b" to 2, "c" to 1, "owner_id" to stitchClient.userId)),
                    Document(mapOf("a" to 2, "b" to 1, "c" to 0, "owner_id" to stitchClient.userId))
                )
        ))

        await(collection!!.updateMany(
                Document("owner_id", stitchClient.userId),
                Document("\$set", mapOf("a" to 2)))
        )
        val doc = await(collection!!.find(Document("owner_id", stitchClient.userId), 10))
        assertThat(doc.first()["a"] == 2)
        assertThat(doc.first()["b"] == 2)

        await(collection!!.updateMany(
                Document("owner_id", stitchClient.userId), Document("a", 3), true)
        )
        assertFails {
            await(MongoClient.Collection(
                    MongoClient.Database(MongoClient(stitchClient, "mdb"), "not_a_db"),
                    "not_a_coll"
            ).updateMany(
                    Document("owner_id", stitchClient.userId),
                    Document("\$set", mapOf("a" to 2))
            ))
        }
    }

    @Test
    fun testInsertOne() {
        await(collection!!.insertOne(
                Document(mapOf("a" to 1, "b" to 2, "c" to 3, "owner_id" to stitchClient.userId))
        ))

        val doc = await(collection!!.find(Document("owner_id", stitchClient.userId), 10))
        assertThat(doc.first()["a"] == 1)
        assertThat(doc.first()["b"] == 2)
        assertFails {
            await(MongoClient.Collection(
                    MongoClient.Database(MongoClient(stitchClient, "mdb"), "not_a_db"),
                    "not_a_coll"
            ).insertOne(
                    Document("owner_id", stitchClient.userId)
            ))
        }
    }

    @Test
    fun testInsertMany() {
        await(collection!!.insertMany(
                listOf(
                        Document(mapOf("a" to 1, "b" to 2, "c" to 3, "owner_id" to stitchClient.userId)),
                        Document(mapOf("a" to 3, "b" to 2, "c" to 1, "owner_id" to stitchClient.userId)),
                        Document(mapOf("a" to 2, "b" to 1, "c" to 0, "owner_id" to stitchClient.userId))
                )
        ))

        val docs = await(collection!!.find(Document("owner_id", stitchClient.userId), 10))
        assertThat(docs[0]["a"] == 1)
        assertThat(docs[1]["a"] == 3)
        assertThat(docs[2]["a"] == 2)

        assertFails {
            await(MongoClient.Collection(
                    MongoClient.Database(MongoClient(stitchClient, "mdb"), "not_a_db"),
                    "not_a_coll"
            ).insertMany(
                    listOf(Document("owner_id", stitchClient.userId))
            ))
        }
    }

    @Test
    fun testDeleteOne() {
        await(collection!!.insertOne(
                Document(mapOf("a" to 1, "b" to 2, "c" to 3, "owner_id" to stitchClient.userId))
        ))
        assertThat(await(collection!!.count(Document("owner_id", stitchClient.userId))) == 1L)
        await(collection!!.deleteOne(Document("owner_id", stitchClient.userId)))
        assertThat(await(collection!!.count(Document("owner_id", stitchClient.userId))) == 0L)
        assertFails {
            await(MongoClient.Collection(
                    MongoClient.Database(MongoClient(stitchClient, "mdb"), "not_a_db"),
                    "not_a_coll"
            ).deleteOne(
                    Document("owner_id", stitchClient.userId)
            ))
        }
    }

    @Test
    fun testDeleteMany() {
        await(collection!!.insertMany(
                listOf(
                        Document(mapOf("a" to 1, "b" to 2, "c" to 3, "owner_id" to stitchClient.userId)),
                        Document(mapOf("a" to 3, "b" to 2, "c" to 1, "owner_id" to stitchClient.userId)),
                        Document(mapOf("a" to 2, "b" to 1, "c" to 0, "owner_id" to stitchClient.userId))
                )
        ))
        assertThat(await(collection!!.count(Document("owner_id", stitchClient.userId))) == 3L)
        await(collection!!.deleteMany(Document("owner_id", stitchClient.userId)))
        assertThat(await(collection!!.count(Document("owner_id", stitchClient.userId))) == 0L)
        assertFails {
            await(MongoClient.Collection(
                    MongoClient.Database(MongoClient(stitchClient, "mdb"), "not_a_db"),
                    "not_a_coll"
            ).deleteMany(
                    Document("owner_id", stitchClient.userId)
            ))
        }
    }
}
