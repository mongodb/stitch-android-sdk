package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import org.bson.BsonArray
import org.bson.BsonBoolean
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonObjectId
import org.bson.BsonString
import org.bson.types.ObjectId
import org.junit.Assert
import org.junit.Test

class HashUtilsUnitTests {
    companion object {
        const val TEST_EMPTY_BSON_DOC_HASH = -6534195273556634272L
        const val TEST_HELLO_WORLD_BSON_DOC_HASH = 3488831889965352219L
        const val TEST_NESTED_BSON_DOC_HASH = 2637880642529775697L

        val BSON_OBJECT_ID = ObjectId("5cb8a847a8d14019f59b99f0")
    }

    @Test
    fun testHashFunctionHashesEmptyBsonDocumentCorrectly() {
        Assert.assertEquals(TEST_EMPTY_BSON_DOC_HASH, HashUtils.hash(BsonDocument()))
    }

    @Test
    fun testHashFunctionHashesHelloWorldBsonDocumentCorrectly() {
        Assert.assertEquals(TEST_HELLO_WORLD_BSON_DOC_HASH,
                HashUtils.hash(BsonDocument("hello", BsonString("world"))))
    }

    @Test
    fun testHashFunctionHashesNestedBsonDocumentCorrectly() {
        Assert.assertEquals(TEST_NESTED_BSON_DOC_HASH,
                HashUtils.hash(BsonDocument()
                        .append("_id", BsonObjectId(BSON_OBJECT_ID))
                        .append("foo", BsonDocument()
                                .append("hello", BsonString("world"))
                                .append("ways to leave",
                                        BsonArray(listOf(
                                                    BsonString("make a new plan, Stan"),
                                                    BsonString("sneak out the back, Jack"),
                                                    BsonString("no need to be coy, Roy"),
                                                    BsonString("just hop on the bus, Gus"),
                                                    BsonString("drop off the key, Lee")))))
                        .append("bar", BsonInt32(42))
                        .append("baz", BsonString("metasyntactic variables rule"))
                        .append("quux", BsonBoolean(true))))
    }
}
