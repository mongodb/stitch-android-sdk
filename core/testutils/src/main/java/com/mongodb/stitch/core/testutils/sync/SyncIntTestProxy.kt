package com.mongodb.stitch.core.testutils.sync

import com.mongodb.MongoNamespace
import com.mongodb.stitch.core.admin.create
import com.mongodb.stitch.core.admin.remove
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.admin.services.rules.rule
import com.mongodb.stitch.core.internal.common.Callback
import com.mongodb.stitch.core.internal.common.OperationResult
import com.mongodb.stitch.core.services.mongodb.remote.ChangeEvent
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import com.mongodb.stitch.core.services.mongodb.remote.OperationType
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateOptions
import org.bson.BsonBoolean
import org.bson.BsonDocument
import org.bson.BsonElement
import org.bson.BsonObjectId
import org.bson.BsonValue
import org.bson.Document
import org.bson.types.Binary
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test
import java.lang.Exception
import java.util.UUID
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test harness for Sync integration tests.
 *
 * Each @Test method in this interface reflects a test
 * that must be implemented to properly test Sync.
 *
 * The tests should be proxied from a [SyncIntTestRunner] implementor.
 * [SyncIntTestProxy] and [SyncIntTestRunner] should be in sync
 * on the these test methods.
 *
 * @param syncTestRunner a runner that contains the necessary properties
 *                       to run the tests
 */
@Ignore
class SyncIntTestProxy(private val syncTestRunner: SyncIntTestRunner) {
    private fun measure(title: String, block: () -> Unit) {
        val now = System.currentTimeMillis()
        log("starting $title at $now")

        block()

        log("ended $title at ${System.currentTimeMillis()}")
        log("$title took ${(System.currentTimeMillis() - now)/1000} seconds")
    }

    private fun log(msg: String) {
        println("\$_performance_metrics: $msg")
    }

    @Test
    fun testInitSyncPerf() {
        val remoteMethods = syncTestRunner.remoteMethods()
        val syncMethods = syncTestRunner.syncMethods()

        syncMethods.configure(
            ConflictHandler { _, _, _ -> null },
            ChangeEventListener { _, _ -> },
            ExceptionListener { _, _ -> })

        val array: List<Byte> = (0..1899).map { 0.toByte() }
        val docs = (0..29999).map { Document("bin", Binary(array.toByteArray())) }

        var i = 0
        val ids = docs.chunked(1000).map {
            log("inserting batch $i")
            i++
            remoteMethods.insertMany(it).insertedIds.map { it.value }
        }.flatten()

        this.measure("sync many") {
            syncMethods.syncMany(*ids.toTypedArray())
        }

        this.measure("sync pass") {
            syncTestRunner.dataSynchronizer.doSyncPass()
        }

        log("all done")
    }

    @Test
    fun testSync() {
        val remoteMethods = syncTestRunner.remoteMethods()
        val syncOperations = syncTestRunner.syncMethods()

        val doc1 = Document("hello", "world")
        val doc2 = Document("hello", "friend")
        doc2["proj"] = "field"
        remoteMethods.insertMany(listOf(doc1, doc2))

        // get the document
        val doc = remoteMethods.find(doc1).first()!!
        val doc1Id = BsonObjectId(doc.getObjectId("_id"))
        val doc1Filter = Document("_id", doc1Id)

        // start watching it and always set the value to hello world in a conflict
        syncOperations.configure(ConflictHandler { id: BsonValue, localEvent: ChangeEvent<Document>, remoteEvent: ChangeEvent<Document> ->
            // ensure that there is no version information on the documents in the conflict handler
            assertNoVersionFieldsInDoc(localEvent.fullDocument)
            assertNoVersionFieldsInDoc(remoteEvent.fullDocument)

            if (id == doc1Id) {
                val merged = localEvent.fullDocument.getInteger("foo") +
                    remoteEvent.fullDocument.getInteger("foo")
                val newDocument = Document(HashMap<String, Any>(remoteEvent.fullDocument))
                newDocument["foo"] = merged
                newDocument
            } else {
                Document("hello", "world")
            }
        }, null, null)

        // sync on the remote document
        syncOperations.syncOne(doc1Id)
        streamAndSync()

        // 1. updating a document remotely should not be reflected until coming back online.
        goOffline()
        val doc1Update = Document("\$inc", Document("foo", 1))
        // document should successfully update locally.
        // then sync
        val result = remoteMethods.updateOne(doc1Filter, doc1Update)
        assertEquals(1, result.matchedCount)
        streamAndSync()
        // because we are offline, the remote doc should not have updated
        Assert.assertEquals(doc, syncOperations.find(documentIdFilter(doc1Id)).firstOrNull())
        // go back online, and sync
        // the remote document should now equal our expected update
        goOnline()
        streamAndSync()
        val expectedDocument = Document(doc)
        expectedDocument["foo"] = 1
        assertEquals(expectedDocument, syncOperations.find(documentIdFilter(doc1Id)).firstOrNull())

        // 2. insertOne should work offline and then sync the document when online.
        goOffline()
        val doc3 = Document("so", "syncy")
        val insResult = syncOperations.insertOne(doc3)
        Assert.assertEquals(
            doc3,
            syncOperations.find(documentIdFilter(insResult.insertedId)).firstOrNull()!!)
        streamAndSync()
        Assert.assertNull(remoteMethods.find(Document("_id", doc3["_id"])).firstOrNull())
        goOnline()
        streamAndSync()
        Assert.assertEquals(doc3, withoutSyncVersion(remoteMethods.find(Document("_id", doc3["_id"])).first()!!))

        // 3. updating a document locally that has been updated remotely should invoke the conflict
        // resolver.
        val sem = watchForEvents(syncTestRunner.namespace)
        val result2 = remoteMethods.updateOne(
            doc1Filter,
            withNewSyncVersionSet(doc1Update))
        sem.acquire()
        Assert.assertEquals(1, result2.matchedCount)
        expectedDocument["foo"] = 2
        Assert.assertEquals(expectedDocument, withoutSyncVersion(remoteMethods.find(doc1Filter).first()!!))
        val result3 = syncOperations.updateOne(
            documentIdFilter(doc1Id),
            doc1Update)
        Assert.assertEquals(1, result3.matchedCount)
        expectedDocument["foo"] = 2
        Assert.assertEquals(
            expectedDocument,
            syncOperations.find(documentIdFilter(doc1Id)).firstOrNull()!!)
        // first pass will invoke the conflict handler and update locally but not remotely yet
        streamAndSync()
        Assert.assertEquals(expectedDocument, withoutSyncVersion(remoteMethods.find(doc1Filter).first()!!))
        expectedDocument["foo"] = 4
        expectedDocument.remove("fooOps")
        Assert.assertEquals(expectedDocument, syncOperations.find(doc1Filter).first()!!)
        // second pass will update with the ack'd version id
        streamAndSync()
        Assert.assertEquals(expectedDocument, syncOperations.find(doc1Filter).first()!!)
        Assert.assertEquals(expectedDocument, withoutSyncVersion(remoteMethods.find(doc1Filter).first()!!))
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testUpdateConflicts() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        val docToInsert = Document("hello", "world")
        remoteColl.insertOne(docToInsert)

        val doc = remoteColl.find(docToInsert).first()!!
        val doc1Id = BsonObjectId(doc.getObjectId("_id"))
        val doc1Filter = Document("_id", doc1Id)

        coll.configure(ConflictHandler { _: BsonValue, localEvent: ChangeEvent<Document>, remoteEvent: ChangeEvent<Document> ->
            val merged = Document(localEvent.fullDocument)
            remoteEvent.fullDocument.forEach {
                if (localEvent.fullDocument.containsKey(it.key)) {
                    return@forEach
                }
                merged[it.key] = it.value
            }
            merged
        }, null, null)
        coll.syncOne(doc1Id)
        streamAndSync()

        // Update remote
        val remoteUpdate = withNewSyncVersionSet(Document("\$set", Document("remote", "update")))
        val sem = watchForEvents(syncTestRunner.namespace)
        var result = remoteColl.updateOne(doc1Filter, remoteUpdate)
        sem.acquire()
        assertEquals(1, result.matchedCount)
        val expectedRemoteDocument = Document(doc)
        expectedRemoteDocument["remote"] = "update"
        assertEquals(expectedRemoteDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))

        // Update local
        val localUpdate = Document("\$set", Document("local", "updateWow"))
        result = coll.updateOne(doc1Filter, localUpdate)
        assertEquals(1, result.matchedCount)
        val expectedLocalDocument = Document(doc)
        expectedLocalDocument["local"] = "updateWow"
        assertEquals(expectedLocalDocument, coll.find(doc1Filter).first()!!)

        // first pass will invoke the conflict handler and update locally but not remotely yet
        streamAndSync()
        assertEquals(expectedRemoteDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        expectedLocalDocument["remote"] = "update"
        assertEquals(expectedLocalDocument, coll.find(doc1Filter).first()!!)

        // second pass will update with the ack'd version id
        streamAndSync()
        assertEquals(expectedLocalDocument, coll.find(doc1Filter).first()!!)
        assertEquals(expectedLocalDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testUpdateRemoteWins() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        // insert a new document remotely
        val docToInsert = Document("hello", "world")
        docToInsert["foo"] = 1
        remoteColl.insertOne(docToInsert)

        // find the document we've just inserted
        val doc = remoteColl.find(docToInsert).first()!!
        val doc1Id = BsonObjectId(doc.getObjectId("_id"))
        val doc1Filter = Document("_id", doc1Id)

        // configure Sync to resolve conflicts with remote winning,
        // synchronize the document, and stream events and do a sync pass
        coll.configure(DefaultSyncConflictResolvers.remoteWins(), null, null)
        coll.syncOne(doc1Id)
        streamAndSync()

        // update the document remotely while watching for an update
        val expectedDocument = Document(doc)
        val sem = watchForEvents(syncTestRunner.namespace)
        var result = remoteColl.updateOne(doc1Filter, withNewSyncVersionSet(Document("\$inc", Document("foo", 2))))
        // once the event has been stored,
        // fetch the remote document and assert that it has properly updated
        sem.acquire()
        assertEquals(1, result.matchedCount)
        expectedDocument["foo"] = 3
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))

        // update the local collection.
        // the count field locally should be 2
        // the count field remotely should be 3
        result = coll.updateOne(doc1Filter, Document("\$inc", Document("foo", 1)))
        assertEquals(1, result.matchedCount)
        expectedDocument["foo"] = 2
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)

        // sync the collection. the remote document should be accepted
        // and this resolution should be reflected locally and remotely
        streamAndSync()
        expectedDocument["foo"] = 3
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)
        streamAndSync()
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testUpdateLocalWins() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        // insert a new document remotely
        val docToInsert = Document("hello", "world")
        docToInsert["foo"] = 1
        remoteColl.insertOne(docToInsert)

        // find the document we just inserted
        val doc = remoteColl.find(docToInsert).first()!!
        val doc1Id = BsonObjectId(doc.getObjectId("_id"))
        val doc1Filter = Document("_id", doc1Id)

        // configure Sync to resolve conflicts with local winning,
        // synchronize the document, and stream events and do a sync pass
        coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)
        coll.syncOne(doc1Id)
        streamAndSync()

        // update the document remotely while watching for an update
        val expectedDocument = Document(doc)
        val sem = watchForEvents(syncTestRunner.namespace)
        var result = remoteColl.updateOne(doc1Filter, withNewSyncVersionSet(Document("\$inc", Document("foo", 2))))
        // once the event has been stored,
        // fetch the remote document and assert that it has properly updated
        sem.acquire()
        assertEquals(1, result.matchedCount)
        expectedDocument["foo"] = 3
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))

        // update the local collection.
        // the count field locally should be 2
        // the count field remotely should be 3
        result = coll.updateOne(doc1Filter, Document("\$inc", Document("foo", 1)))
        assertEquals(1, result.matchedCount)
        expectedDocument["foo"] = 2
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)

        // sync the collection. the local document should be accepted
        // and this resolution should be reflected locally and remotely
        streamAndSync()
        expectedDocument["foo"] = 2
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)
        streamAndSync()
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testDeleteOneByIdNoConflict() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        // insert a document remotely
        val docToInsert = Document("hello", "world")
        remoteColl.insertOne(docToInsert)

        // find the document we just inserted
        var doc = remoteColl.find(docToInsert).first()!!
        val doc1Id = BsonObjectId(doc.getObjectId("_id"))
        val doc1Filter = Document("_id", doc1Id)

        // configure Sync to fail this test if a conflict occurs.
        // sync on the id, and do a sync pass
        coll.configure(failingConflictHandler, null, null)
        coll.syncOne(doc1Id)
        streamAndSync()

        // update the document so it has a sync version (if we don't do this, then deleting
        // the document will result in a conflict because a remote document with no version
        // and a local document with no version are treated as documents with different
        // versions)
        coll.updateOne(doc1Filter, Document("\$set", Document("hello", "universe")))
        streamAndSync()
        doc = remoteColl.find(doc1Filter).first()!!

        // go offline to avoid processing events.
        // delete the document locally
        goOffline()
        val result = coll.deleteOne(doc1Filter)
        assertEquals(1, result.deletedCount)

        // assert that, while the remote document remains
        val expectedDocument = withoutSyncVersion(Document(doc))
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        Assert.assertNull(coll.find(doc1Filter).firstOrNull())

        // go online to begin the syncing process.
        // when syncing, our local delete will be synced to the remote.
        // assert that this is reflected remotely and locally
        goOnline()
        streamAndSync()
        Assert.assertNull(remoteColl.find(doc1Filter).firstOrNull())
        Assert.assertNull(coll.find(doc1Filter).firstOrNull())
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testDeleteOneByIdConflict() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        // insert a document remotely
        val docToInsert = Document("hello", "world")
        remoteColl.insertOne(docToInsert)

        // find the document we just inserted
        val doc = remoteColl.find(docToInsert).first()!!
        val doc1Id = BsonObjectId(doc.getObjectId("_id"))
        val doc1Filter = Document("_id", doc1Id)

        // configure Sync to resolve a custom document on conflict.
        // sync on the id, and do a sync pass
        coll.configure(ConflictHandler { _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
            Document("well", "shoot")
        }, null, null)
        coll.syncOne(doc1Id)
        streamAndSync()

        // update the document remotely
        val doc1Update = Document("\$inc", Document("foo", 1))
        assertEquals(1, remoteColl.updateOne(
            doc1Filter,
            withNewSyncVersionSet(doc1Update)).matchedCount)

        // go offline, and delete the document locally
        goOffline()
        val result = coll.deleteOne(doc1Filter)
        assertEquals(1, result.deletedCount)

        // assert that the remote document has not been deleted,
        // while the local document has been
        val expectedDocument = Document(doc)
        expectedDocument["foo"] = 1
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        Assert.assertNull(coll.find(doc1Filter).firstOrNull())

        // go back online and sync. assert that the remote document has been updated
        // while the local document reflects the resolution of the conflict
        goOnline()
        streamAndSync()
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        expectedDocument.remove("hello")
        expectedDocument.remove("foo")
        expectedDocument["well"] = "shoot"
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testInsertThenUpdateThenSync() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        // configure Sync to fail this test if there is a conflict.
        // insert and sync the new document locally
        val docToInsert = Document("hello", "world")
        coll.configure(failingConflictHandler, null, null)
        val insertResult = coll.insertOne(docToInsert)

        // find the local document we just inserted
        val doc = coll.find(documentIdFilter(insertResult.insertedId)).first()!!
        val doc1Id = BsonObjectId(doc.getObjectId("_id"))
        val doc1Filter = Document("_id", doc1Id)

        // update the document locally
        val doc1Update = Document("\$inc", Document("foo", 1))
        assertEquals(1, coll.updateOne(doc1Filter, doc1Update).matchedCount)

        // assert that nothing has been inserting remotely
        val expectedDocument = Document(doc)
        expectedDocument["foo"] = 1
        Assert.assertNull(remoteColl.find(doc1Filter).firstOrNull())
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)

        // go online (in case we weren't already). sync.
        goOnline()
        streamAndSync()

        // assert that the local insertion reflects remotely
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testInsertThenSyncUpdateThenUpdate() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        // configure Sync to fail this test if there is a conflict.
        // insert and sync the new document locally
        val docToInsert = Document("hello", "world")
        coll.configure(failingConflictHandler, null, null)
        val insertResult = coll.insertOne(docToInsert)

        // find the document we just inserted
        val doc = coll.find(documentIdFilter(insertResult.insertedId)).first()!!
        val doc1Id = BsonObjectId(doc.getObjectId("_id"))
        val doc1Filter = Document("_id", doc1Id)

        // go online (in case we weren't already). sync.
        // assert that the local insertion reflects remotely
        goOnline()
        streamAndSync()
        val expectedDocument = Document(doc)
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)

        // update the document locally
        val doc1Update = Document("\$inc", Document("foo", 1))
        assertEquals(1, coll.updateOne(doc1Filter, doc1Update).matchedCount)

        // assert that this update has not been reflected remotely, but has locally
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        expectedDocument["foo"] = 1
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)

        // sync. assert that our update is reflected locally and remotely
        streamAndSync()
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testInsertThenSyncThenRemoveThenInsertThenUpdate() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        // configure Sync to fail this test if there is a conflict.
        // insert and sync the new document locally. sync.
        val docToInsert = Document("hello", "world")
        coll.configure(failingConflictHandler, null, null)
        val insertResult = coll.insertOne(docToInsert)
        streamAndSync()

        // assert the sync'd document is found locally and remotely
        val doc = coll.find(documentIdFilter(insertResult.insertedId)).first()!!
        val doc1Id = BsonObjectId(doc.getObjectId("_id"))
        val doc1Filter = Document("_id", doc1Id)
        val expectedDocument = Document(doc)
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)

        // delete the doc locally, then re-insert it.
        // assert the document is still the same locally and remotely
        assertEquals(1, coll.deleteOne(doc1Filter).deletedCount)
        coll.insertOne(doc)
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)

        // update the document locally
        val doc1Update = Document("\$inc", Document("foo", 1))
        assertEquals(1, coll.updateOne(doc1Filter, doc1Update).matchedCount)

        // assert that the document has not been updated remotely yet,
        // but has locally
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        expectedDocument["foo"] = 1
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)

        // sync. assert that the update has been reflected remotely and locally
        streamAndSync()
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testRemoteDeletesLocalNoConflict() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        // insert a new document remotely
        val docToInsert = Document("hello", "world")
        remoteColl.insertOne(docToInsert)

        // find the document we just inserted
        val doc = remoteColl.find(docToInsert).first()!!
        val doc1Id = BsonObjectId(doc.getObjectId("_id"))
        val doc1Filter = Document("_id", doc1Id)

        // configure Sync with a conflict handler that fails this test
        // in the event of conflict. sync the document, and sync.
        coll.configure(failingConflictHandler, null, null)
        coll.syncOne(doc1Id)
        streamAndSync()
        assertEquals(coll.getSyncedIds().size, 1)

        // do a remote delete. wait for the event to be stored. sync.
        val sem = watchForEvents(syncTestRunner.namespace)
        remoteColl.deleteOne(doc1Filter)
        sem.acquire()
        streamAndSync()

        // assert that the remote deletion is reflected locally
        Assert.assertNull(remoteColl.find(doc1Filter).firstOrNull())
        Assert.assertNull(coll.find(doc1Filter).firstOrNull())

        // sync. this should not re-sync the document
        streamAndSync()

        // insert the document again. sync.
        remoteColl.insertOne(doc)
        streamAndSync()

        // assert that the remote insertion is NOT reflected locally
        assertEquals(doc, remoteColl.find(doc1Filter).first())
        Assert.assertNull(coll.find(doc1Filter).firstOrNull())
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testRemoteDeletesLocalConflict() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        // insert a new document remotely
        val docToInsert = Document("hello", "world")
        remoteColl.insertOne(docToInsert)

        // find the document we just inserted
        val doc = remoteColl.find(docToInsert).first()!!
        val doc1Id = BsonObjectId(doc.getObjectId("_id"))
        val doc1Filter = Document("_id", doc1Id)

        // configure Sync to resolve a custom document on conflict.
        // sync on the id, do a sync pass, and assert that the remote
        // insertion has been reflected locally
        coll.configure(ConflictHandler { _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
            Document("hello", "world")
        }, null, null)
        coll.syncOne(doc1Id)
        streamAndSync()
        assertEquals(doc, coll.find(doc1Filter).firstOrNull())
        Assert.assertNotNull(coll.find(doc1Filter))

        // go offline.
        // delete the document remotely.
        // update the document locally.
        goOffline()
        remoteColl.deleteOne(doc1Filter)
        assertEquals(1, coll.updateOne(doc1Filter, Document("\$inc", Document("foo", 1))).matchedCount)

        // go back online and sync. assert that the document remains deleted remotely,
        // but has not been reflected locally yet
        goOnline()
        streamAndSync()
        Assert.assertNull(remoteColl.find(doc1Filter).firstOrNull())
        Assert.assertNotNull(coll.find(doc1Filter).firstOrNull())

        // sync again. assert that the resolution is reflected locally and remotely
        streamAndSync()
        Assert.assertNotNull(remoteColl.find(doc1Filter).firstOrNull())
        Assert.assertNotNull(coll.find(doc1Filter).firstOrNull())
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testRemoteInsertsLocalUpdates() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        // insert a new document remotely
        val docToInsert = Document("hello", "world")
        remoteColl.insertOne(docToInsert)

        // find the document we just inserted
        val doc = remoteColl.find(docToInsert).first()!!
        val doc1Id = BsonObjectId(doc.getObjectId("_id"))
        val doc1Filter = Document("_id", doc1Id)

        // configure Sync to resolve a custom document on conflict.
        // sync on the id, do a sync pass, and assert that the remote
        // insertion has been reflected locally
        coll.configure(ConflictHandler { _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
            Document("hello", "again")
        }, null, null)
        coll.syncOne(doc1Id)
        streamAndSync()
        assertEquals(doc, coll.find(doc1Filter).firstOrNull())
        Assert.assertNotNull(coll.find(doc1Filter).firstOrNull())

        // delete the document remotely, then reinsert it.
        // wait for the events to stream
        val wait = watchForEvents(syncTestRunner.namespace, 2)
        remoteColl.deleteOne(doc1Filter)
        remoteColl.insertOne(withNewSyncVersion(doc))
        wait.acquire()

        // update the local document concurrently. sync.
        assertEquals(1, coll.updateOne(doc1Filter, Document("\$inc", Document("foo", 1))).matchedCount)
        streamAndSync()

        // assert that the remote doc has not reflected the update.
        // assert that the local document has received the resolution
        // from the conflict handled
        assertEquals(doc, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        val expectedDocument = Document("_id", doc1Id.value)
        expectedDocument["hello"] = "again"
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)

        // do another sync pass. assert that the local and remote docs are in sync
        streamAndSync()
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testRemoteInsertsWithVersionLocalUpdates() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        // insert a document remotely
        val docToInsert = Document("hello", "world")
        remoteColl.insertOne(withNewSyncVersion(docToInsert))

        // find the document we just inserted
        val doc = remoteColl.find(docToInsert).first()!!
        val doc1Id = BsonObjectId(doc.getObjectId("_id"))
        val doc1Filter = Document("_id", doc1Id)

        // configure Sync to fail this test if there is a conflict.
        // sync the document, and do a sync pass.
        // assert the remote insertion is reflected locally.
        coll.configure(failingConflictHandler, null, null)
        coll.syncOne(doc1Id)
        streamAndSync()
        assertEquals(withoutSyncVersion(doc), coll.find(doc1Filter).firstOrNull())

        // update the document locally. sync.
        assertEquals(1, coll.updateOne(doc1Filter, Document("\$inc", Document("foo", 1))).matchedCount)
        streamAndSync()

        // assert that the local update has been reflected remotely.
        val expectedDocument = Document(withoutSyncVersion(doc))
        expectedDocument["foo"] = 1
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testResolveConflictWithDelete() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        // insert a new document remotely
        val docToInsert = Document("hello", "world")
        remoteColl.insertOne(withNewSyncVersion(docToInsert))

        // find the document we just inserted
        val doc = remoteColl.find(docToInsert).first()!!
        val doc1Id = BsonObjectId(doc.getObjectId("_id"))
        val doc1Filter = Document("_id", doc1Id)

        // configure Sync to resolve null when conflicted, effectively deleting
        // the conflicted document.
        // sync the docId, and do a sync pass.
        // assert the remote insert is reflected locally
        coll.configure(ConflictHandler { _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
            null
        }, null, null)
        coll.syncOne(doc1Id)
        streamAndSync()
        assertEquals(withoutSyncVersion(doc), coll.find(doc1Filter).firstOrNull())
        Assert.assertNotNull(coll.find(doc1Filter).firstOrNull())

        // update the document remotely. wait for the update event to store.
        val sem = watchForEvents(syncTestRunner.namespace)
        assertEquals(1, remoteColl.updateOne(doc1Filter, withNewSyncVersionSet(Document("\$inc", Document("foo", 1)))).matchedCount)
        sem.acquire()

        // update the document locally.
        assertEquals(1, coll.updateOne(doc1Filter, Document("\$inc", Document("foo", 1))).matchedCount)

        // sync. assert that the remote document has received that update,
        // but locally the document has resolved to deletion
        streamAndSync()
        val expectedDocument = Document(withoutSyncVersion(doc))
        expectedDocument["foo"] = 1
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        goOffline()
        Assert.assertNull(coll.find(doc1Filter).firstOrNull())

        // go online and sync. the deletion should be reflected remotely and locally now
        goOnline()
        streamAndSync()
        Assert.assertNull(remoteColl.find(doc1Filter).firstOrNull())
        Assert.assertNull(coll.find(doc1Filter).firstOrNull())
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testTurnDeviceOffAndOn() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        // insert a document remotely
        val docToInsert = Document("hello", "world")
        docToInsert["foo"] = 1
        remoteColl.insertOne(docToInsert)

        // find the document we just inserted
        val doc = remoteColl.find(docToInsert).first()!!
        val doc1Id = BsonObjectId(doc.getObjectId("_id"))
        val doc1Filter = Document("_id", doc1Id)

        // reload our configuration
        powerCycleDevice()

        // configure Sync to resolve conflicts with a local win.
        // sync the docId
        coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)
        coll.syncOne(doc1Id)

        // reload our configuration again.
        // reconfigure sync and the same way. do a sync pass.
        powerCycleDevice()
        coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)
        val sem = watchForEvents(syncTestRunner.namespace)
        streamAndSync()

        // update the document remotely. assert the update is reflected remotely.
        // reload our configuration again. reconfigure Sync again.
        val expectedDocument = Document(doc)
        var result = remoteColl.updateOne(doc1Filter, withNewSyncVersionSet(Document("\$inc", Document("foo", 2))))
        assertTrue(sem.tryAcquire(10, TimeUnit.SECONDS))
        assertEquals(1, result.matchedCount)
        expectedDocument["foo"] = 3
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        powerCycleDevice()
        coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)

        // update the document locally. assert its success, after reconfiguration.
        result = coll.updateOne(doc1Filter, Document("\$inc", Document("foo", 1)))
        assertEquals(1, result.matchedCount)
        expectedDocument["foo"] = 2
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)

        // reconfigure again.
        powerCycleDevice()
        coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)

        // sync.
        streamAndSync() // does nothing with no conflict handler

        // assert we are still synced on one id.
        // reconfigure again.
        assertEquals(1, coll.getSyncedIds().size)
        coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)
        streamAndSync() // resolves the conflict

        // assert the update was reflected locally. reconfigure again.
        expectedDocument["foo"] = 2
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)
        powerCycleDevice()
        coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)

        // sync. assert that the update was reflected remotely
        streamAndSync()
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testDesync() {
        val coll = syncTestRunner.syncMethods()

        // insert and sync a new document.
        // configure Sync to fail this test if there is a conflict.
        val docToInsert = Document("hello", "world")
        coll.configure(failingConflictHandler, null, null)
        val doc1Id = coll.insertOne(docToInsert).insertedId

        // assert the document exists locally. desync it.
        assertEquals(docToInsert, coll.find(documentIdFilter(doc1Id)).first()!!)
        coll.desyncOne(doc1Id)

        // sync. assert that the desync'd document no longer exists locally
        streamAndSync()
        Assert.assertNull(coll.find(documentIdFilter(doc1Id)).firstOrNull())
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testInsertInsertConflict() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        // insert a new document remotely
        val docToInsert = Document()
        remoteColl.insertOne(docToInsert)

        // configure Sync to resolve a custom document when handling a conflict
        // insert and sync the same document locally, creating a conflict
        coll.configure(ConflictHandler { _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
            Document("friend", "welcome")
        }, null, null)
        val doc1Id = coll.insertOne(docToInsert).insertedId
        val doc1Filter = Document("_id", doc1Id)

        // sync. assert that the resolution is reflected locally,
        // but not yet remotely.
        streamAndSync()
        val expectedDocument = Document(docToInsert)
        expectedDocument["friend"] = "welcome"
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)
        assertEquals(docToInsert, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))

        // sync again. assert that the resolution is reflected
        // locally and remotely.
        streamAndSync()
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testConfigure() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        // insert a document locally
        val docToInsert = Document("hello", "world")
        val insertedId = coll.insertOne(docToInsert).insertedId

        var hasConflictHandlerBeenInvoked = false
        var hasChangeEventListenerBeenInvoked = false

        // configure Sync, each entry with flags checking
        // that the listeners/handlers have been called
        val changeEventListenerSemaphore = Semaphore(0)
        coll.configure(
            ConflictHandler { _: BsonValue, _: ChangeEvent<Document>, remoteEvent: ChangeEvent<Document> ->
                hasConflictHandlerBeenInvoked = true
                assertEquals(remoteEvent.fullDocument["fly"], "away")
                remoteEvent.fullDocument
            },
            ChangeEventListener { _: BsonValue, _: ChangeEvent<Document> ->
                hasChangeEventListenerBeenInvoked = true
                changeEventListenerSemaphore.release()
            },
                ExceptionListener { _, _ -> }
        )

        waitForAllStreamsOpen()

        // insert a document remotely
        remoteColl.insertOne(Document("_id", insertedId).append("fly", "away"))

        // sync. assert that the conflict handler and
        // change event listener have been called
        streamAndSync()

        assertTrue(changeEventListenerSemaphore.tryAcquire(10, TimeUnit.SECONDS))
        Assert.assertTrue(hasConflictHandlerBeenInvoked)
        Assert.assertTrue(hasChangeEventListenerBeenInvoked)
    }

    @Test
    fun testSyncVersioningScheme() {
        val coll = syncTestRunner.syncMethods()

        val remoteColl = syncTestRunner.remoteMethods()

        val docToInsert = Document("hello", "world")

        coll.configure(failingConflictHandler, null, null)
        val insertResult = coll.insertOne(docToInsert)

        val doc = coll.find(documentIdFilter(insertResult.insertedId)).first()
        val doc1Id = BsonObjectId(doc?.getObjectId("_id"))
        val doc1Filter = Document("_id", doc1Id)

        goOnline()
        streamAndSync()
        val expectedDocument = Document(doc)

        // the remote document after an initial insert should have a fresh instance ID, and a
        // version counter of 0
        val firstRemoteDoc = remoteColl.find(doc1Filter).first()!!
        assertEquals(expectedDocument, withoutSyncVersion(firstRemoteDoc))

        assertEquals(0, versionCounterOf(firstRemoteDoc))

        assertEquals(expectedDocument, coll.find(doc1Filter).firstOrNull())

        // the remote document after a local update, but before a sync pass, should have the
        // same version as the original document, and be equivalent to the unupdated document
        val doc1Update = Document("\$inc", Document("foo", 1))
        assertEquals(1, coll.updateOne(doc1Filter, doc1Update).matchedCount)

        val secondRemoteDocBeforeSyncPass = remoteColl.find(doc1Filter).first()!!
        assertEquals(expectedDocument, withoutSyncVersion(secondRemoteDocBeforeSyncPass))
        assertEquals(versionOf(firstRemoteDoc), versionOf(secondRemoteDocBeforeSyncPass))

        expectedDocument["foo"] = 1
        assertEquals(expectedDocument, coll.find(doc1Filter).firstOrNull())

        // the remote document after a local update, and after a sync pass, should have a new
        // version with the same instance ID as the original document, a version counter
        // incremented by 1, and be equivalent to the updated document.
        streamAndSync()
        val secondRemoteDoc = remoteColl.find(doc1Filter).first()!!
        assertEquals(expectedDocument, withoutSyncVersion(secondRemoteDoc))
        assertEquals(instanceIdOf(firstRemoteDoc), instanceIdOf(secondRemoteDoc))
        assertEquals(1, versionCounterOf(secondRemoteDoc))

        assertEquals(expectedDocument, coll.find(doc1Filter).firstOrNull())

        // the remote document after a local delete and local insert, but before a sync pass,
        // should have the same version as the previous document
        assertEquals(1, coll.deleteOne(doc1Filter).deletedCount)
        coll.insertOne(doc!!)

        val thirdRemoteDocBeforeSyncPass = remoteColl.find(doc1Filter).first()!!
        assertEquals(expectedDocument, withoutSyncVersion(thirdRemoteDocBeforeSyncPass))

        expectedDocument.remove("foo")
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)

        // the remote document after a local delete and local insert, and after a sync pass,
        // should have the same instance ID as before and a version count, since the change
        // events are coalesced into a single update event
        streamAndSync()

        val thirdRemoteDoc = remoteColl.find(doc1Filter).first()!!
        assertEquals(expectedDocument, withoutSyncVersion(thirdRemoteDoc))
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)

        assertEquals(instanceIdOf(secondRemoteDoc), instanceIdOf(thirdRemoteDoc))
        assertEquals(2, versionCounterOf(thirdRemoteDoc))

        // the remote document after a local delete, a sync pass, a local insert, and after
        // another sync pass should have a new instance ID, with a version counter of zero,
        // since the change events are not coalesced
        assertEquals(1, coll.deleteOne(doc1Filter).deletedCount)
        streamAndSync()
        coll.insertOne(doc)
        streamAndSync()

        val fourthRemoteDoc = remoteColl.find(doc1Filter).first()!!
        assertEquals(expectedDocument, withoutSyncVersion(thirdRemoteDoc))
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)

        Assert.assertNotEquals(instanceIdOf(secondRemoteDoc), instanceIdOf(fourthRemoteDoc))
        assertEquals(0, versionCounterOf(fourthRemoteDoc))
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testUnsupportedSpvFails() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        val docToInsert = withNewUnsupportedSyncVersion(Document("hello", "world"))

        val errorEmittedSem = Semaphore(0)
        coll.configure(
            failingConflictHandler,
            null,
                ExceptionListener { _, _ -> errorEmittedSem.release() })

        remoteColl.insertOne(docToInsert)

        val doc = remoteColl.find(docToInsert).first()!!
        val doc1Id = BsonObjectId(doc.getObjectId("_id"))
        coll.syncOne(doc1Id)

        assertTrue(coll.getSyncedIds().contains(doc1Id))

        // syncing on this document with an unsupported spv should cause the document to desync
        goOnline()
        streamAndSync()

        assertFalse(coll.getSyncedIds().contains(doc1Id))

        // an error should also have been emitted
        assertTrue(errorEmittedSem.tryAcquire(10, TimeUnit.SECONDS))
    }

    @Test
    fun testStaleFetchSingle() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        // insert a new document
        val doc1 = Document("hello", "world")
        remoteColl.insertOne(doc1)

        // find the document we just inserted
        val doc = remoteColl.find(doc1).first()!!
        val doc1Id = BsonObjectId(doc.getObjectId("_id"))

        // configure Sync with a conflict handler that will freeze a document.
        // sync the document
        coll.configure(failingConflictHandler, null, null)
        coll.syncOne(doc1Id)

        // sync. assert the document has been synced.
        streamAndSync()
        Assert.assertNotNull(coll.find(documentIdFilter(doc1Id)).firstOrNull())

        // update the document locally.
        coll.updateOne(documentIdFilter(doc1Id), Document("\$inc", Document("i", 1)))

        // sync. assert the document still exists
        streamAndSync()
        Assert.assertNotNull(coll.find(documentIdFilter(doc1Id)).firstOrNull())

        // sync. assert the document still exists
        streamAndSync()
        Assert.assertNotNull(coll.find(documentIdFilter(doc1Id)))
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testStaleFetchSingleDeleted() {
        val coll = syncTestRunner.syncMethods()

        val remoteColl = syncTestRunner.remoteMethods()

        val doc1 = Document("hello", "world")
        remoteColl.insertOne(doc1)

        // get the document
        val doc = remoteColl.find(doc1).first()!!
        val doc1Id = BsonObjectId(doc.getObjectId("_id"))
        val doc1Filter = Document("_id", doc1Id)

        coll.configure(ConflictHandler { _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
            throw IllegalStateException("failure")
        }, null, null)
        coll.syncOne(doc1Id)

        streamAndSync()
        Assert.assertNotNull(coll.find(doc1Filter).firstOrNull())

        coll.updateOne(doc1Filter, Document("\$inc", Document("i", 1)))
        streamAndSync()
        Assert.assertNotNull(coll.find(doc1Filter).firstOrNull())

        assertEquals(1, remoteColl.deleteOne(doc1Filter).deletedCount)
        powerCycleDevice()
        coll.configure(ConflictHandler { _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
            throw IllegalStateException("failure")
        }, null, null)

        streamAndSync()
        Assert.assertNull(coll.find(doc1Filter).firstOrNull())
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testStaleFetchMultiple() {
        val coll = syncTestRunner.syncMethods()

        val remoteColl = syncTestRunner.remoteMethods()

        val insertResult =
            remoteColl.insertMany(listOf(
                Document("hello", "world"),
                Document("hello", "friend")))

        // get the document
        val doc1Id = insertResult.insertedIds[0]
        val doc2Id = insertResult.insertedIds[1]

        coll.configure(ConflictHandler { _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
            throw IllegalStateException("failure")
        }, null, null)
        coll.syncOne(doc1Id!!)

        streamAndSync()
        Assert.assertNotNull(coll.find(documentIdFilter(doc1Id)).firstOrNull())

        coll.updateOne(documentIdFilter(doc1Id), Document("\$inc", Document("i", 1)))
        streamAndSync()
        Assert.assertNotNull(coll.find(documentIdFilter(doc1Id)))

        coll.syncOne(doc2Id!!)
        streamAndSync()
        Assert.assertNotNull(coll.find(documentIdFilter(doc1Id)).firstOrNull())
        Assert.assertNotNull(coll.find(documentIdFilter(doc2Id)).firstOrNull())
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testShouldUpdateUsingUpdateDescription() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        val docToInsert = Document(
            mapOf(
                "i_am" to "the walrus",
                "they_are" to "the egg men",
                "members" to listOf(
                    "paul", "john", "george", "pete"
                ),
                "where_to_be" to mapOf(
                    "under_the_sea" to mapOf(
                        "octopus_garden" to "in the shade"
                    ),
                    "the_land_of_submarines" to mapOf(
                        "a_yellow_submarine" to "a yellow submarine"
                    )
                ),
                "year" to 1960
            ))
        val docAfterUpdate = Document.parse("""
            {
                "i_am": "the egg men",
                "they_are": "the egg men",
                "members": [ "paul", "john", "george", "ringo" ],
                "where_to_be": {
                    "under_the_sea": {
                        "octopus_garden": "near a cave"
                    },
                    "the_land_of_submarines": {
                        "a_yellow_submarine": "a yellow submarine"
                    }
                }
            }
        """)
        val updateDoc = BsonDocument.parse("""
            {
                "${'$'}set": {
                   "i_am": "the egg men",
                   "members": [ "paul", "john", "george", "ringo" ],
                   "where_to_be.under_the_sea.octopus_garden": "near a cave"
                },
                "${'$'}unset": {
                    "year": true
                }
            }
        """)

        remoteColl.insertOne(docToInsert)
        val doc = remoteColl.find(docToInsert).first()!!
        val doc1Id = BsonObjectId(doc.getObjectId("_id"))
        val doc1Filter = Document("_id", doc1Id)

        val eventSemaphore = Semaphore(0)
        coll.configure(failingConflictHandler, ChangeEventListener { _, event ->
            // ensure that there is no version information in the event document.
            assertNoVersionFieldsInDoc(event.fullDocument)

            if (event.operationType == OperationType.UPDATE &&
                !event.hasUncommittedWrites()) {
                assertEquals(
                    updateDoc["\$set"],
                    event.updateDescription.updatedFields)
                assertEquals(
                    updateDoc["\$unset"],
                    BsonDocument(
                        event.updateDescription.removedFields.map { BsonElement(it, BsonBoolean(true)) }))
                eventSemaphore.release()
            }
        }, null)
        coll.syncOne(doc1Id)
        streamAndSync()

        // because the "they_are" field has already been added, set
        // a rule that prevents writing to the "they_are" field that we've added.
        // a full replace would therefore break our rule, preventing validation.
        // only an actual update document (with $set and $unset)
        // can work for the rest of this test
        syncTestRunner.mdbService.rules.rule(syncTestRunner.mdbRule._id).remove()
        val result = coll.updateOne(doc1Filter, updateDoc)
        assertEquals(1, result.matchedCount)
        assertEquals(docAfterUpdate, withoutId(coll.find(doc1Filter).first()!!))

        // set they_are to unwriteable. the update should only update i_am
        // setting i_am to false and they_are to true would fail this test
        syncTestRunner.mdbService.rules.create(
            RuleCreator.MongoDb(
                database = syncTestRunner.namespace.databaseName,
                collection = syncTestRunner.namespace.collectionName,
                roles = listOf(
                    RuleCreator.MongoDb.Role(
                        fields = Document(
                            "i_am", Document("write", true)
                        ).append(
                            "they_are", Document("write", false)
                        ).append(
                            "where_to_be.the_land_of_submarines", Document("write", false)
                        )
                    )
                ),
                schema = RuleCreator.MongoDb.Schema()
            )
        )

        streamAndSync()
        assertEquals(docAfterUpdate, withoutId(coll.find(doc1Filter).first()!!))
        assertEquals(docAfterUpdate, withoutId(withoutSyncVersion(remoteColl.find(doc1Filter).first()!!)))
        assertTrue(eventSemaphore.tryAcquire(10, TimeUnit.SECONDS))
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testResumeSyncForDocumentResumesSync() {
        val testSync = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()
        var errorEmitted = false

        var conflictCounter = 0

        testSync.configure(
            ConflictHandler { _: BsonValue, _: ChangeEvent<Document>, remoteEvent: ChangeEvent<Document> ->
                if (conflictCounter == 0) {
                    conflictCounter++
                    errorEmitted = true
                    throw Exception("ouch")
                }
                remoteEvent.fullDocument
            },
            ChangeEventListener { _: BsonValue, _: ChangeEvent<Document> ->
            },
                ExceptionListener { _, _ ->
                })

        // insert an initial doc
        val testDoc = Document("hello", "world")
        val result = testSync.insertOne(testDoc)

        // do a sync pass, synchronizing the doc
        streamAndSync()

        Assert.assertNotNull(remoteColl.find(Document("_id", testDoc["_id"])).first())

        // update the doc
        val expectedDoc = Document("hello", "computer")
        testSync.updateOne(documentIdFilter(result.insertedId), Document("\$set", expectedDoc))

        // create a conflict
        var sem = watchForEvents(syncTestRunner.namespace)
        remoteColl.updateOne(Document("_id", result.insertedId), withNewSyncVersionSet(Document("\$inc", Document("foo", 2))))
        sem.acquire()

        // do a sync pass, and throw an error during the conflict resolver
        // freezing the document
        streamAndSync()
        Assert.assertTrue(errorEmitted)
        assertEquals(result.insertedId, testSync.getPausedDocumentIds().first())

        // update the doc remotely
        val nextDoc = Document("hello", "friend")

        sem = watchForEvents(syncTestRunner.namespace)
        remoteColl.updateOne(Document("_id", result.insertedId), nextDoc)
        sem.acquire()
        streamAndSync()

        // it should not have updated the local doc, as the local doc should be paused
        assertEquals(
            withoutId(expectedDoc),
            withoutId(testSync.find(Document("_id", result.insertedId)).first()!!))

        // resume syncing here
        assertTrue(testSync.resumeSyncForDocument(result.insertedId))
        streamAndSync()

        // update the doc remotely
        val lastDoc = Document("good night", "computer")

        sem = watchForEvents(syncTestRunner.namespace)
        remoteColl.updateOne(Document("_id", result.insertedId), withNewSyncVersion(lastDoc))
        sem.acquire()

        // now that we're sync'd and resumed, it should be reflected locally
        streamAndSync()

        assertTrue(testSync.getPausedDocumentIds().isEmpty())
        assertEquals(
            withoutId(lastDoc),
            withoutId(testSync.find(Document("_id", result.insertedId)).first()!!))
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testReadsBeforeAndAfterSync() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        coll.configure(failingConflictHandler, null, null)

        val doc1 = Document("hello", "world")
        val doc2 = Document("hello", "friend")
        val doc3 = Document("hello", "goodbye")

        val insertResult = remoteColl.insertMany(listOf(doc1, doc2, doc3))
        assertEquals(3, insertResult.insertedIds.size)

        assertEquals(0, coll.count())
        assertEquals(0, coll.find().toList().size)
        assertEquals(0, coll.aggregate(listOf(Document(mapOf(
            "\$match" to mapOf("_id" to mapOf("\$in" to insertResult.insertedIds.map {
                it.value }))
        )))).toList().size)

        insertResult.insertedIds.forEach { coll.syncOne(it.value) }
        streamAndSync()

        assertEquals(3, coll.count())
        assertEquals(3, coll.find().toList().size)
        assertEquals(3, coll.aggregate(listOf(Document(mapOf(
            "\$match" to mapOf("_id" to mapOf("\$in" to insertResult.insertedIds.map {
                it.value }))
        )))).toList().size)

        insertResult.insertedIds.forEach { coll.desyncOne(it.value) }
        streamAndSync()

        assertEquals(0, coll.count())
        assertEquals(0, coll.find().toList().size)
        assertEquals(0, coll.aggregate(listOf(Document(mapOf(
            "\$match" to mapOf("_id" to mapOf("\$in" to insertResult.insertedIds.map {
                it.value }))
        )))).toList().size)
    }

    @Test
    fun testInsertManyNoConflicts() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        coll.configure(failingConflictHandler, null, null)

        val doc1 = Document("hello", "world")
        val doc2 = Document("hello", "friend")
        val doc3 = Document("hello", "goodbye")

        val insertResult = coll.insertMany(listOf(doc1, doc2, doc3))
        assertEquals(3, insertResult.insertedIds.size)

        assertEquals(3, coll.count())
        assertEquals(3, coll.find().toList().size)
        assertEquals(3, coll.aggregate(listOf(Document(mapOf(
            "\$match" to mapOf("_id" to mapOf("\$in" to insertResult.insertedIds.map {
                it.value }))
        )))).toList().size)

        assertEquals(0, remoteColl.find(Document()).toList().size)
        streamAndSync()

        assertEquals(3, remoteColl.find(Document()).toList().size)
        assertEquals(doc1, withoutSyncVersion(remoteColl.find(Document("_id", doc1["_id"])).first()!!))
        assertEquals(doc2, withoutSyncVersion(remoteColl.find(Document("_id", doc2["_id"])).first()!!))
        assertEquals(doc3, withoutSyncVersion(remoteColl.find(Document("_id", doc3["_id"])).first()!!))
    }

    @Test
    fun testUpdateManyNoConflicts() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        coll.configure(failingConflictHandler, null, null)

        var updateResult = coll.updateMany(
            Document(mapOf(
                "fish" to listOf("one", "two", "red", "blue")
            )),
            Document("\$set", mapOf(
                "fish" to listOf("black", "blue", "old", "new")
            )))

        assertEquals(0, updateResult.modifiedCount)
        assertEquals(0, updateResult.matchedCount)
        assertNull(updateResult.upsertedId)

        updateResult = coll.updateMany(
            Document(mapOf(
                "fish" to listOf("one", "two", "red", "blue")
            )),
            Document("\$set", mapOf(
                "fish" to listOf("black", "blue", "old", "new")
            )),
            SyncUpdateOptions().upsert(true))

        assertEquals(0, updateResult.modifiedCount)
        assertEquals(0, updateResult.matchedCount)
        assertNotNull(updateResult.upsertedId)

        val doc1 = Document(mapOf(
            "hello" to "world",
            "fish" to listOf("one", "two", "red", "blue")
        ))
        val doc2 = Document("hello", "friend")
        val doc3 = Document("hello", "goodbye")

        val insertResult = coll.insertMany(listOf(doc1, doc2, doc3))
        assertEquals(3, insertResult.insertedIds.size)

        streamAndSync()

        assertEquals(4, remoteColl.find(Document()).toList().size)

        updateResult = coll.updateMany(
            Document("fish", Document("\$exists", true)),
            Document("\$set", Document("fish", listOf("trout", "mackerel", "cod", "hake")))
        )

        assertEquals(2, updateResult.modifiedCount)
        assertEquals(2, updateResult.matchedCount)
        assertNull(updateResult.upsertedId)

        assertEquals(4, coll.count())

        var localFound = coll.find(Document("fish", Document("\$exists", true)))
        assertEquals(2, localFound.toList().size)
        localFound.forEach { assertEquals(listOf("trout", "mackerel", "cod", "hake"), it!!["fish"]) }

        streamAndSync()

        val remoteFound = remoteColl.find(Document("fish", Document("\$exists", true)))
        localFound = coll.find(Document("fish", Document("\$exists", true)))

        assertEquals(2, localFound.toList().size)
        assertEquals(2, remoteFound.toList().size)
        localFound.forEach { assertEquals(listOf("trout", "mackerel", "cod", "hake"), it!!["fish"]) }
        remoteFound.forEach { assertEquals(listOf("trout", "mackerel", "cod", "hake"), it!!["fish"]) }
    }

    @Test
    fun testDeleteManyNoConflicts() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        coll.configure(failingConflictHandler, null, null)

        val doc1 = Document("hello", "world")
        val doc2 = Document("hello", "friend")
        val doc3 = Document("hello", "goodbye")

        val insertResult = coll.insertMany(listOf(doc1, doc2, doc3))
        assertEquals(3, insertResult.insertedIds.size)

        assertEquals(3, coll.count())
        assertEquals(3, coll.find().toList().size)
        assertEquals(3, coll.aggregate(listOf(Document(mapOf(
            "\$match" to mapOf("_id" to mapOf("\$in" to insertResult.insertedIds.map {
                it.value }))
        )))).toList().size)

        assertEquals(0, remoteColl.find(Document()).toList().size)
        streamAndSync()

        assertEquals(3, remoteColl.find(Document()).toList().size)
        coll.deleteMany(Document(mapOf(
            "_id" to mapOf("\$in" to insertResult.insertedIds.map {
                it.value }))))

        assertEquals(3, remoteColl.find(Document()).toList().size)
        assertEquals(0, coll.find(Document()).toList().size)

        streamAndSync()

        assertEquals(0, remoteColl.find(Document()).toList().size)
        assertEquals(0, coll.find(Document()).toList().size)
    }

    @Test
    fun testSyncVersionFieldNotEditable() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        // configure Sync to fail this test if there is a conflict.

        // 0. insert with bad version
        // insert and sync a new document locally with a bad version field, and make sure it
        // doesn't exist after the insert
        val badVersionDoc = Document("bad", "version")
        val docToInsert = Document("hello", "world")
                .append("__stitch_sync_version", badVersionDoc)
        coll.configure(failingConflictHandler, null, null)
        val insertResult = coll.insertOne(docToInsert)
        val localDocBeforeSync0 = coll.find(documentIdFilter(insertResult.insertedId)).first()!!
        assertNoVersionFieldsInDoc(localDocBeforeSync0)

        streamAndSync()

        // assert the sync'd document is found locally and remotely, and that the version
        // doesn't exist locally, and isn't the bad version doc remotely
        val localDocAfterSync0 = coll.find(documentIdFilter(insertResult.insertedId)).first()!!
        val docId = BsonObjectId(localDocAfterSync0.getObjectId("_id"))
        val docFilter = Document("_id", docId)

        val remoteDoc0 = remoteColl.find(docFilter).first()!!
        val remoteVersion0 = versionOf(remoteDoc0)

        val expectedDocument0 = Document(localDocAfterSync0)
        assertEquals(expectedDocument0, withoutSyncVersion(remoteDoc0))
        assertEquals(expectedDocument0, localDocAfterSync0)
        assertNotEquals(badVersionDoc, remoteVersion0)
        assertEquals(0, versionCounterOf(remoteDoc0))

        // 1. $set bad version counter

        // update the document, setting the version counter to 10, and a future version that
        // we'll try to maliciously set but verify that before and after syncing, there is no
        // version on the local doc, and that the version on the remote doc after syncing is
        // correctly incremented by only one.
        coll.updateOne(
                docFilter,
                Document("\$set",
                        Document()
                                .append("__stitch_sync_version.v", 10)
                                .append("futureVersion", badVersionDoc)))

        val localDocBeforeSync1 = coll.find(documentIdFilter(insertResult.insertedId)).first()!!
        assertNoVersionFieldsInDoc(localDocBeforeSync1)
        streamAndSync()

        val localDocAfterSync1 = coll.find(documentIdFilter(insertResult.insertedId)).first()!!
        val remoteDoc1 = remoteColl.find(docFilter).first()!!
        val expectedDocument1 = Document(localDocAfterSync1)
        assertEquals(expectedDocument1, withoutSyncVersion(remoteDoc1))
        assertEquals(expectedDocument1, localDocAfterSync1)

        // verify the version only got incremented once
        assertEquals(1, versionCounterOf(remoteDoc1))

        // 2. $rename bad version doc

        // update the document, renaming our bad "futureVersion" field to
        // "__stitch_sync_version", and assert that there is no version on the local doc, and
        // that the version on the remote doc after syncing is correctly not incremented
        coll.updateOne(
                docFilter,
                Document("\$rename", Document("futureVersion", "__stitch_sync_version")))

        val localDocBeforeSync2 = coll.find(documentIdFilter(insertResult.insertedId)).first()!!
        assertNoVersionFieldsInDoc(localDocBeforeSync2)
        streamAndSync()

        val localDocAfterSync2 = coll.find(documentIdFilter(insertResult.insertedId)).first()!!
        val remoteDoc2 = remoteColl.find(docFilter).first()!!

        // the expected doc is the doc without the futureVersion field (localDocAfterSync0)
        assertEquals(localDocAfterSync0, withoutSyncVersion(remoteDoc2))
        assertEquals(localDocAfterSync0, localDocAfterSync2)

        // verify the version did get incremented
        assertEquals(2, versionCounterOf(remoteDoc2))

        // 3. unset

        // update the document, unsetting "__stitch_sync_version", and assert that there is no
        // version on the local doc, and that the version on the remote doc after syncing
        // is correctly not incremented because is basically a noop.
        coll.updateOne(
                docFilter,
                Document("\$unset", Document("__stitch_sync_version", 1)))

        val localDocBeforeSync3 = coll.find(documentIdFilter(insertResult.insertedId)).first()!!
        assertNoVersionFieldsInDoc(localDocBeforeSync3)
        streamAndSync()

        val localDocAfterSync3 = coll.find(documentIdFilter(insertResult.insertedId)).first()!!
        val remoteDoc3 = remoteColl.find(docFilter).first()!!

        // the expected doc is the doc without the futureVersion field (localDocAfterSync0)
        assertEquals(localDocAfterSync0, withoutSyncVersion(remoteDoc3))
        assertEquals(localDocAfterSync0, localDocAfterSync3)

        // verify the version did not get incremented, because this update was a noop
        assertEquals(2, versionCounterOf(remoteDoc3))
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testConflictForEmptyVersionDocuments() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        // insert a document remotely
        val docToInsert = Document("hello", "world")
        remoteColl.insertOne(docToInsert)

        // find the document we just inserted
        var doc = remoteColl.find(docToInsert).first()!!
        val doc1Id = BsonObjectId(doc.getObjectId("_id"))
        val doc1Filter = Document("_id", doc1Id)

        // configure Sync to have local documents win conflicts
        var conflictRaised = false
        coll.configure(
                ConflictHandler { _, localEvent, _ ->
                    conflictRaised = true
                    localEvent.fullDocument
                }, null, null)
        coll.syncOne(doc1Id)
        streamAndSync()

        // go offline to avoid processing events.
        // delete the document locally
        goOffline()
        val result = coll.deleteOne(doc1Filter)
        assertEquals(1, result.deletedCount)

        // assert that the remote document remains
        val expectedDocument = withoutSyncVersion(Document(doc))
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        Assert.assertNull(coll.find(doc1Filter).firstOrNull())

        // go online to begin the syncing process. When doing R2L first, a conflict should have
        // occurred because both the local and remote instance of this document have no version
        // information, meaning that the sync pass was forced to raise a conflict. our local
        // delete should be synced to the remote, because we set up the conflict handler to
        // have local always win. assert that this is reflected remotely and locally.
        // (As a historical note, when we used to permit L2R first, no conflict would be raised
        // since we didn't get a chance to fetch stale documents, potentially resulting in the
        // loss of events
        goOnline()
        // do one sync pass to get the local delete to happen via conflict resolution
        streamAndSync()
        // do another sync pass to get the local delete resolution committed to the remote
        streamAndSync()

        // make sure that a conflict was raised
        Assert.assertTrue(conflictRaised)

        Assert.assertNull(coll.find(doc1Filter).firstOrNull())
        Assert.assertNull(remoteColl.find(doc1Filter).firstOrNull())
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())
    }

    @Test
    fun testMultiUserSupport() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        // insert documents that correspond to different users
        val docToInsertUser1 = Document("hello", "world")
        val docToInsertUser2 = Document("hola", "mundo")
        val docToInsertUser3 = Document("hallo", "welt")

        // configure Sync
        coll.configure(ConflictHandler { _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
            fail()
            Document()
        }, null, null)

        val doc1Id = coll.insertOne(docToInsertUser1).insertedId
        val doc1Filter = Document("_id", doc1Id)

        // sync. assert that the resolution is reflected locally,
        // but not yet remotely.
        streamAndSync()
        var expectedDocument = Document(docToInsertUser1)

        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)
        assertEquals(docToInsertUser1, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))

        // sync again. assert that the resolution is reflected
        // locally and remotely.
        streamAndSync()
        assertEquals(expectedDocument, coll.find(doc1Filter).first()!!)
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())

        // switch to the other user. this function chain
        // is the simplest way to do this for now
        syncTestRunner.switchUser(syncTestRunner.userId2)

        assertNull(coll.find(doc1Filter).firstOrNull())

        // sync again. since the configurations have been reset, nothing should exist locally
        streamAndSync()
        assertNull(coll.find(doc1Filter).firstOrNull())
        // assert nothing has changed remotely
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())

        val doc2Id = coll.insertOne(docToInsertUser2).insertedId
        val doc2Filter = Document("_id", doc2Id)

        streamAndSync()

        expectedDocument = Document(docToInsertUser2)
        assertEquals(expectedDocument, coll.find(doc2Filter).first()!!)
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc2Filter).first()!!))
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())

        // switch back to the previous user
        syncTestRunner.switchUser(syncTestRunner.userId3)

        assertNull(coll.find(doc1Filter).firstOrNull())
        assertNull(coll.find(doc2Filter).firstOrNull())

        // sync again. since the configurations have been reset, nothing should exist locally
        streamAndSync()
        assertNull(coll.find(doc1Filter).firstOrNull())
        // assert nothing has changed remotely
        assertEquals(docToInsertUser1, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        assertEquals(docToInsertUser2, withoutSyncVersion(remoteColl.find(doc2Filter).first()!!))
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())

        val doc3Id = coll.insertOne(docToInsertUser3).insertedId
        val doc3Filter = Document("_id", doc3Id)

        streamAndSync()

        expectedDocument = Document(docToInsertUser3)
        assertEquals(expectedDocument, coll.find(doc3Filter).first()!!)
        assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc3Filter).first()!!))
        assertNoVersionFieldsInLocalColl(syncTestRunner.syncMethods())

        syncTestRunner.switchUser(syncTestRunner.userId1)

        // assert docs are still intact
        assertEquals(docToInsertUser1, coll.find(doc1Filter).first()!!)
        assertNull(coll.find(doc2Filter).firstOrNull())
        assertNull(coll.find(doc3Filter).firstOrNull())

        syncTestRunner.switchUser(syncTestRunner.userId2)

        // assert docs are still intact
        assertNull(coll.find(doc1Filter).firstOrNull())
        assertEquals(docToInsertUser2, coll.find(doc2Filter).firstOrNull())
        assertNull(coll.find(doc3Filter).firstOrNull())

        syncTestRunner.removeUser(syncTestRunner.userId2)

        syncTestRunner.reloginUser2()

        assertNull(coll.find(doc1Filter).firstOrNull())
        assertNull(coll.find(doc2Filter).firstOrNull())
        assertNull(coll.find(doc3Filter).firstOrNull())
    }

    private fun watchForEvents(namespace: MongoNamespace, n: Int = 1): Semaphore {
        println("watching for $n change event(s) ns=$namespace")
        val waitFor = AtomicInteger(n)
        val sem = Semaphore(0)
        syncTestRunner.dataSynchronizer.addWatcher(namespace, object : Callback<ChangeEvent<BsonDocument>, Any> {
            override fun onComplete(result: OperationResult<ChangeEvent<BsonDocument>, Any>) {
                if (result.isSuccessful && result.geResult() != null) {
                    println("change event of operation ${result.geResult().operationType} ns=$namespace found!")
                }
                if (waitFor.decrementAndGet() == 0) {
                    syncTestRunner.dataSynchronizer.removeWatcher(namespace, this)
                    sem.release()
                }
            }
        })
        return sem
    }

    private fun waitForAllStreamsOpen() {
        while (!syncTestRunner.dataSynchronizer.areAllStreamsOpen()) {
            println("waiting for all streams to open before doing sync pass")
            Thread.sleep(1000)
        }
    }

    private fun streamAndSync() {
        if (syncTestRunner.testNetworkMonitor.connectedState) {
            waitForAllStreamsOpen()
        }
        syncTestRunner.dataSynchronizer.doSyncPass()
    }

    private fun powerCycleDevice() {
        syncTestRunner.dataSynchronizer.wipeInMemorySettings()
    }

    private fun goOffline() {
        println("going offline")
        syncTestRunner.testNetworkMonitor.connectedState = false
    }

    private fun goOnline() {
        println("going online")
        syncTestRunner.testNetworkMonitor.connectedState = true
    }

    private fun withoutId(document: Document): Document {
        val newDoc = Document(document)
        newDoc.remove("_id")
        return newDoc
    }

    private fun withoutSyncVersion(document: Document): Document {
        val newDoc = Document(document)
        newDoc.remove("__stitch_sync_version")
        return newDoc
    }

    private fun withNewSyncVersionSet(document: Document): Document {
        return appendDocumentToKey(
            "\$set",
            document,
            Document("__stitch_sync_version", freshSyncVersionDoc()))
    }

    private fun withNewSyncVersion(document: Document): Document {
        val newDocument = Document(java.util.HashMap(document))
        newDocument["__stitch_sync_version"] = freshSyncVersionDoc()

        return newDocument
    }

    private fun withNewUnsupportedSyncVersion(document: Document): Document {
        val newDocument = Document(java.util.HashMap(document))
        val badVersion = freshSyncVersionDoc()
        badVersion.remove("spv")
        badVersion.append("spv", 2)

        newDocument["__stitch_sync_version"] = badVersion

        return newDocument
    }

    private fun freshSyncVersionDoc(): Document {
        return Document("spv", 1).append("id", UUID.randomUUID().toString()).append("v", 0L)
    }

    private fun versionOf(document: Document): Document {
        return document["__stitch_sync_version"] as Document
    }

    private fun versionCounterOf(document: Document): Long {
        return versionOf(document)["v"] as Long
    }

    private fun instanceIdOf(document: Document): String {
        return versionOf(document)["id"] as String
    }

    private fun appendDocumentToKey(key: String, on: Document, toAppend: Document): Document {
        val newDocument = Document(HashMap(on))
        var found = false
        newDocument.forEach {
            if (it.key != key) {
                return@forEach
            }
            found = true
            val valueAtKey = (it.value as Document)
            toAppend.forEach { doc ->
                valueAtKey[doc.key] = doc.value
            }
        }
        if (!found) {
            newDocument[key] = toAppend
        }
        return newDocument
    }

    private fun documentIdFilter(documentId: BsonValue) =
        BsonDocument("_id", documentId)

    private val failingConflictHandler = ConflictHandler { _: BsonValue, event1: ChangeEvent<Document>, event2: ChangeEvent<Document> ->
        val localEventDescription = when (event1.operationType == OperationType.DELETE) {
            true -> "delete"
            false -> event1.fullDocument.toJson()
        }

        val remoteEventDescription = when (event2.operationType == OperationType.DELETE) {
            true -> "delete"
            false -> event2.fullDocument.toJson()
        }

        println("conflict local event: $localEventDescription")
        println("conflict remote event: $remoteEventDescription")
        Assert.fail("did not expect a conflict")
        throw IllegalStateException("unreachable")
    }

    private fun assertNoVersionFieldsInLocalColl(coll: ProxySyncMethods) {
        coll.find().forEach { assertFalse(it!!.containsKey("__stitch_sync_version")) }
    }

    private fun assertNoVersionFieldsInDoc(doc: Document) {
        assertFalse(doc.containsKey("__stitch_sync_version"))
    }
}
