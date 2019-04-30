package com.mongodb.stitch.core.services.mongodb.remote.sync.internal

import com.mongodb.stitch.core.services.mongodb.remote.ChangeEvent
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers
import org.bson.BsonDocument
import org.bson.BsonValue
import org.bson.codecs.BsonDocumentCodec
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.TimeUnit

class SyncConfigurationUnitTests {
    @Test
    fun testSyncConfigurationBuilder() {
        val conflictHandler = DefaultSyncConflictResolvers.remoteWins<BsonDocument>()
        val syncFrequency = SyncFrequency.scheduled(100, TimeUnit.MINUTES, true)
        val exceptionListener = ExceptionListener {
            _, error -> System.out.println(error.localizedMessage)
        }
        val changeEventListener = CustomEventListener()
        val codec = BsonDocumentCodec()

        val builder = SyncConfiguration.Builder()
        builder.withConflictHandler(conflictHandler)
        builder.withSyncFrequency(syncFrequency)
        builder.withExceptionListener(exceptionListener)
        builder.withChangeEventListener(changeEventListener)
        builder.withCodec(codec)
        var config = builder.build()

        Assert.assertEquals(config.conflictHandler, conflictHandler)
        Assert.assertEquals(config.syncFrequency.type, SyncFrequency.SyncFrequencyType.SCHEDULED)
        Assert.assertEquals(config.exceptionListener, exceptionListener)
        Assert.assertEquals(config.changeEventListener, changeEventListener)
        Assert.assertEquals(config.codec, codec)
    }

    @Test
    fun testSyncConfigurationBuilderDefaults() {
        val conflictHandler = DefaultSyncConflictResolvers.remoteWins<BsonDocument>()

        val builder = SyncConfiguration.Builder()
        builder.withConflictHandler(conflictHandler)
        var config = builder.build()

        Assert.assertEquals(config.conflictHandler, conflictHandler)
        Assert.assertEquals(config.syncFrequency.type, SyncFrequency.SyncFrequencyType.REACTIVE)
    }

    @Test
    fun testSyncConfigurationBuilderRequired() {
        try {
            val builder = SyncConfiguration.Builder()
            val _ = builder.build()
        } catch (ex: SyncConfigurationException) {
            return
        }
        Assert.fail()
    }

    private inner class CustomEventListener : ChangeEventListener<BsonDocument> {
        override fun onEvent(documentId: BsonValue, event: ChangeEvent<BsonDocument>) {
            return
        }
    }
}
