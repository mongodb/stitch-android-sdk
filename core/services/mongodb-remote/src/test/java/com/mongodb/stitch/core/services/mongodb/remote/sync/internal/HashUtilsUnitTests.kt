package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import org.bson.BsonDocument
import org.bson.BsonString
import org.junit.Assert
import org.junit.Test

class HashUtilsUnitTests {
    companion object {
        const val TEST_EMPTY_BSON_DOC_HASH = -6534195273556634272L
        const val TEST_HELLO_WORLD_BSON_DOC_HASH = 3488831889965352219L
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
}
