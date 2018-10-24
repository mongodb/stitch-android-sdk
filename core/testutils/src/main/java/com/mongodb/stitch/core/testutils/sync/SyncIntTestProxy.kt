package com.mongodb.stitch.core.testutils.sync

import com.mongodb.MongoNamespace
import com.mongodb.stitch.core.internal.common.Callback
import com.mongodb.stitch.core.internal.common.OperationResult
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers
import com.mongodb.stitch.core.services.mongodb.remote.sync.ErrorListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.ChangeEvent
import org.bson.BsonDocument
import org.bson.BsonObjectId
import org.bson.BsonValue
import org.bson.Document
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    @Test
    fun testSync() {
        testSyncInBothDirections {
            val remoteMethods = syncTestRunner.remoteMethods()
            val remoteOperations = syncTestRunner.syncMethods()

            val doc1 = Document("hello", "world")
            val doc2 = Document("hello", "friend")
            doc2["proj"] = "field"
            remoteMethods.insertMany(listOf(doc1, doc2))

            // get the document
            val doc = remoteMethods.find(doc1).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            // start watching it and always set the value to hello world in a conflict
            remoteOperations.configure(ConflictHandler { id: BsonValue, localEvent: ChangeEvent<Document>, remoteEvent: ChangeEvent<Document> ->
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
            remoteOperations.syncOne(doc1Id)
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
            Assert.assertEquals(doc, remoteOperations.findOneById(doc1Id))
            // go back online, and sync
            // the remote document should now equal our expected update
            goOnline()
            streamAndSync()
            val expectedDocument = Document(doc)
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, remoteOperations.findOneById(doc1Id))

            // 2. insertOneAndSync should work offline and then sync the document when online.
            goOffline()
            val doc3 = Document("so", "syncy")
            val insResult = remoteOperations.insertOneAndSync(doc3)
            Assert.assertEquals(doc3, withoutSyncVersion(remoteOperations.findOneById(insResult.insertedId)!!))
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
            val result3 = remoteOperations.updateOneById(
                doc1Id,
                doc1Update)
            Assert.assertEquals(1, result3.matchedCount)
            expectedDocument["foo"] = 2
            Assert.assertEquals(expectedDocument, withoutSyncVersion(remoteOperations.findOneById(doc1Id)!!))
            // first pass will invoke the conflict handler and update locally but not remotely yet
            streamAndSync()
            Assert.assertEquals(expectedDocument, withoutSyncVersion(remoteMethods.find(doc1Filter).first()!!))
            expectedDocument["foo"] = 4
            expectedDocument.remove("fooOps")
            Assert.assertEquals(expectedDocument, withoutSyncVersion(remoteOperations.findOneById(doc1Id)!!))
            // second pass will update with the ack'd version id
            streamAndSync()
            Assert.assertEquals(expectedDocument, withoutSyncVersion(remoteOperations.findOneById(doc1Id)!!))
            Assert.assertEquals(expectedDocument, withoutSyncVersion(remoteMethods.find(doc1Filter).first()!!))
        }
    }

    @Test
    fun testUpdateConflicts() {
        testSyncInBothDirections {
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
            result = coll.updateOneById(doc1Id, localUpdate)
            assertEquals(1, result.matchedCount)
            val expectedLocalDocument = Document(doc)
            expectedLocalDocument["local"] = "updateWow"
            assertEquals(expectedLocalDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            // first pass will invoke the conflict handler and update locally but not remotely yet
            streamAndSync()
            assertEquals(expectedRemoteDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            expectedLocalDocument["remote"] = "update"
            assertEquals(expectedLocalDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            // second pass will update with the ack'd version id
            streamAndSync()
            assertEquals(expectedLocalDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
            assertEquals(expectedLocalDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        }
    }

    @Test
    fun testUpdateRemoteWins() {
        testSyncInBothDirections {
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
            result = coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1)))
            assertEquals(1, result.matchedCount)
            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            // sync the collection. the remote document should be accepted
            // and this resolution should be reflected locally and remotely
            streamAndSync()
            expectedDocument["foo"] = 3
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
            streamAndSync()
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        }
    }

    @Test
    fun testUpdateLocalWins() {
        testSyncInBothDirections {
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
            result = coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1)))
            assertEquals(1, result.matchedCount)
            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            // sync the collection. the local document should be accepted
            // and this resolution should be reflected locally and remotely
            streamAndSync()
            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
            streamAndSync()
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        }
    }

    @Test
    fun testDeleteOneByIdNoConflict() {
        testSyncInBothDirections {
            val coll = syncTestRunner.syncMethods()
            val remoteColl = syncTestRunner.remoteMethods()

            // insert a document remotely
            val docToInsert = Document("hello", "world")
            remoteColl.insertOne(docToInsert)

            // find the document we just inserted
            val doc = remoteColl.find(docToInsert).first()!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            // configure Sync to fail this test if a conflict occurs.
            // sync on the id, and do a sync pass
            coll.configure(failingConflictHandler, null, null)
            coll.syncOne(doc1Id)
            streamAndSync()

            // go offline to avoid processing events.
            // delete the document locally
            goOffline()
            val result = coll.deleteOneById(doc1Id)
            assertEquals(1, result.deletedCount)

            // assert that, while the remote document remains
            val expectedDocument = withoutSyncVersion(Document(doc))
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            Assert.assertNull(coll.findOneById(doc1Id))

            // go online to begin the syncing process.
            // when syncing, our local delete will be synced to the remote.
            // assert that this is reflected remotely and locally
            goOnline()
            streamAndSync()
            Assert.assertNull(remoteColl.find(doc1Filter).firstOrNull())
            Assert.assertNull(coll.findOneById(doc1Id))
        }
    }

    @Test
    fun testDeleteOneByIdConflict() {
        testSyncInBothDirections {
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
            val result = coll.deleteOneById(doc1Id)
            assertEquals(1, result.deletedCount)

            // assert that the remote document has not been deleted,
            // while the local document has been
            val expectedDocument = Document(doc)
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            Assert.assertNull(coll.findOneById(doc1Id))

            // go back online and sync. assert that the remote document has been updated
            // while the local document reflects the resolution of the conflict
            goOnline()
            streamAndSync()
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            expectedDocument.remove("hello")
            expectedDocument.remove("foo")
            expectedDocument["well"] = "shoot"
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
        }
    }

    @Test
    fun testInsertThenUpdateThenSync() {
        testSyncInBothDirections {
            val coll = syncTestRunner.syncMethods()
            val remoteColl = syncTestRunner.remoteMethods()

            // configure Sync to fail this test if there is a conflict.
            // insert and sync the new document locally
            val docToInsert = Document("hello", "world")
            coll.configure(failingConflictHandler, null, null)
            val insertResult = coll.insertOneAndSync(docToInsert)

            // find the local document we just inserted
            val doc = coll.findOneById(insertResult.insertedId)!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            // update the document locally
            val doc1Update = Document("\$inc", Document("foo", 1))
            assertEquals(1, coll.updateOneById(doc1Id, doc1Update).matchedCount)

            // assert that nothing has been inserting remotely
            val expectedDocument = withoutSyncVersion(Document(doc))
            expectedDocument["foo"] = 1
            Assert.assertNull(remoteColl.find(doc1Filter).firstOrNull())
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            // go online (in case we weren't already). sync.
            goOnline()
            streamAndSync()

            // assert that the local insertion reflects remotely
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
        }
    }

    @Test
    fun testInsertThenSyncUpdateThenUpdate() {
        testSyncInBothDirections {
            val coll = syncTestRunner.syncMethods()
            val remoteColl = syncTestRunner.remoteMethods()

            // configure Sync to fail this test if there is a conflict.
            // insert and sync the new document locally
            val docToInsert = Document("hello", "world")
            coll.configure(failingConflictHandler, null, null)
            val insertResult = coll.insertOneAndSync(docToInsert)

            // find the document we just inserted
            val doc = coll.findOneById(insertResult.insertedId)!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)

            // go online (in case we weren't already). sync.
            // assert that the local insertion reflects remotely
            goOnline()
            streamAndSync()
            val expectedDocument = withoutSyncVersion(Document(doc))
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            // update the document locally
            val doc1Update = Document("\$inc", Document("foo", 1))
            assertEquals(1, coll.updateOneById(doc1Id, doc1Update).matchedCount)

            // assert that this update has not been reflected remotely, but has locally
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            // sync. assert that our update is reflected locally and remotely
            streamAndSync()
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
        }
    }

    @Test
    fun testInsertThenSyncThenRemoveThenInsertThenUpdate() {
        testSyncInBothDirections {
            val coll = syncTestRunner.syncMethods()
            val remoteColl = syncTestRunner.remoteMethods()

            // configure Sync to fail this test if there is a conflict.
            // insert and sync the new document locally. sync.
            val docToInsert = Document("hello", "world")
            coll.configure(failingConflictHandler, null, null)
            val insertResult = coll.insertOneAndSync(docToInsert)
            streamAndSync()

            // assert the sync'd document is found locally and remotely
            val doc = coll.findOneById(insertResult.insertedId)!!
            val doc1Id = BsonObjectId(doc.getObjectId("_id"))
            val doc1Filter = Document("_id", doc1Id)
            val expectedDocument = withoutSyncVersion(Document(doc))
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            // delete the doc locally, then re-insert it.
            // assert the document is still the same locally and remotely
            assertEquals(1, coll.deleteOneById(doc1Id).deletedCount)
            coll.insertOneAndSync(doc)
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            // update the document locally
            val doc1Update = Document("\$inc", Document("foo", 1))
            assertEquals(1, coll.updateOneById(doc1Id, doc1Update).matchedCount)

            // assert that the document has not been updated remotely yet,
            // but has locally
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            // sync. assert that the update has been reflected remotely and locally
            streamAndSync()
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
        }
    }

    @Test
    fun testRemoteDeletesLocalNoConflict() {
        testSyncInBothDirections {
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
            Assert.assertNull(coll.findOneById(doc1Id))

            // sync. this should not re-sync the document
            streamAndSync()

            // insert the document again. sync.
            remoteColl.insertOne(doc)
            streamAndSync()

            // assert that the remote insertion is NOT reflected locally
            assertEquals(doc, remoteColl.find(doc1Filter).first())
            Assert.assertNull(coll.findOneById(doc1Id))
        }
    }

    @Test
    fun testRemoteDeletesLocalConflict() {
        testSyncInBothDirections {
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
            assertEquals(doc, coll.findOneById(doc1Id))
            Assert.assertNotNull(coll.findOneById(doc1Id))

            // go offline.
            // delete the document remotely.
            // update the document locally.
            goOffline()
            remoteColl.deleteOne(doc1Filter)
            assertEquals(1, coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1))).matchedCount)

            // go back online and sync. assert that the document remains deleted remotely,
            // but has not been reflected locally yet
            goOnline()
            streamAndSync()
            Assert.assertNull(remoteColl.find(doc1Filter).firstOrNull())
            Assert.assertNotNull(coll.findOneById(doc1Id))

            // sync again. assert that the resolution is reflected locally and remotely
            streamAndSync()
            Assert.assertNotNull(remoteColl.find(doc1Filter).firstOrNull())
            Assert.assertNotNull(coll.findOneById(doc1Id))
        }
    }

    @Test
    fun testRemoteInsertsLocalUpdates() {
        testSyncInBothDirections {
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
            assertEquals(doc, coll.findOneById(doc1Id))
            Assert.assertNotNull(coll.findOneById(doc1Id))

            // delete the document remotely, then reinsert it.
            // wait for the events to stream
            val wait = watchForEvents(syncTestRunner.namespace, 2)
            remoteColl.deleteOne(doc1Filter)
            remoteColl.insertOne(withNewSyncVersion(doc))
            wait.acquire()

            // update the local document concurrently. sync.
            assertEquals(1, coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1))).matchedCount)
            streamAndSync()

            // assert that the remote doc has not reflected the update.
            // assert that the local document has received the resolution
            // from the conflict handled
            assertEquals(doc, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            val expectedDocument = Document("_id", doc1Id.value)
            expectedDocument["hello"] = "again"
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            // do another sync pass. assert that the local and remote docs are in sync
            streamAndSync()
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
        }
    }

    @Test
    fun testRemoteInsertsWithVersionLocalUpdates() {
        testSyncInBothDirections {
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
            assertEquals(doc, coll.findOneById(doc1Id))

            // update the document locally. sync.
            assertEquals(1, coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1))).matchedCount)
            streamAndSync()

            // assert that the local update has been reflected remotely.
            val expectedDocument = Document(withoutSyncVersion(doc))
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
        }
    }

    @Test
    fun testResolveConflictWithDelete() {
        testSyncInBothDirections {
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
            assertEquals(doc, coll.findOneById(doc1Id))
            Assert.assertNotNull(coll.findOneById(doc1Id))

            // update the document remotely. wait for the update event to store.
            val sem = watchForEvents(syncTestRunner.namespace)
            assertEquals(1, remoteColl.updateOne(doc1Filter, withNewSyncVersionSet(Document("\$inc", Document("foo", 1)))).matchedCount)
            sem.acquire()

            // update the document locally.
            assertEquals(1, coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1))).matchedCount)

            // sync. assert that the remote document has received that update,
            // but locally the document has resolved to deletion
            streamAndSync()
            val expectedDocument = Document(withoutSyncVersion(doc))
            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            goOffline()
            Assert.assertNull(coll.findOneById(doc1Id))

            // go online and sync. the deletion should be reflected remotely and locally now
            goOnline()
            streamAndSync()
            Assert.assertNull(remoteColl.find(doc1Filter).firstOrNull())
            Assert.assertNull(coll.findOneById(doc1Id))
        }
    }

    @Test
    fun testTurnDeviceOffAndOn() {
        testSyncInBothDirections {
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
            streamAndSync()

            // update the document remotely. assert the update is reflected remotely.
            // reload our configuration again. reconfigure Sync again.
            val expectedDocument = Document(doc)
            var result = remoteColl.updateOne(doc1Filter, withNewSyncVersionSet(Document("\$inc", Document("foo", 2))))
            assertEquals(1, result.matchedCount)
            expectedDocument["foo"] = 3
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
            powerCycleDevice()
            coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)

            // update the document locally. assert its success, after reconfiguration.
            result = coll.updateOneById(doc1Id, Document("\$inc", Document("foo", 1)))
            assertEquals(1, result.matchedCount)
            expectedDocument["foo"] = 2
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

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
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
            powerCycleDevice()
            coll.configure(DefaultSyncConflictResolvers.localWins(), null, null)

            // sync. assert that the update was reflected remotely
            streamAndSync()
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        }
    }

    @Test
    fun testDesync() {
        testSyncInBothDirections {
            val coll = syncTestRunner.syncMethods()

            // insert and sync a new document.
            // configure Sync to fail this test if there is a conflict.
            val docToInsert = Document("hello", "world")
            coll.configure(failingConflictHandler, null, null)
            val doc1Id = coll.insertOneAndSync(docToInsert).insertedId

            // assert the document exists locally. desync it.
            assertEquals(docToInsert, withoutSyncVersion(coll.findOneById(doc1Id)!!))
            coll.desyncOne(doc1Id)

            // sync. assert that the desync'd document no longer exists locally
            streamAndSync()
            Assert.assertNull(coll.findOneById(doc1Id))
        }
    }

    @Test
    fun testInsertInsertConflict() {
        testSyncInBothDirections {
            val coll = syncTestRunner.syncMethods()
            val remoteColl = syncTestRunner.remoteMethods()

            // insert a new document remotely
            val docToInsert = Document("_id", "hello")
            remoteColl.insertOne(docToInsert)

            // configure Sync to resolve a custom document when handling a conflict
            // insert and sync the same document locally, creating a conflict
            coll.configure(ConflictHandler { _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
                Document("friend", "welcome")
            }, null, null)
            val doc1Id = coll.insertOneAndSync(docToInsert).insertedId
            val doc1Filter = Document("_id", doc1Id)

            // sync. assert that the resolution is reflected locally,
            // but not yet remotely.
            streamAndSync()
            val expectedDocument = Document(docToInsert)
            expectedDocument["friend"] = "welcome"
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
            assertEquals(docToInsert, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))

            // sync again. assert that the resolution is reflected
            // locally and remotely.
            streamAndSync()
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))
            assertEquals(expectedDocument, withoutSyncVersion(remoteColl.find(doc1Filter).first()!!))
        }
    }

    @Test
    fun testFrozenDocumentConfig() {
        testSyncInBothDirections {
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
                ErrorListener { _, _ ->
                })

            // insert an initial doc
            val testDoc = Document("hello", "world")
            val result = testSync.insertOneAndSync(testDoc)

            // do a sync pass, synchronizing the doc
            streamAndSync()

            Assert.assertNotNull(remoteColl.find(Document("_id", testDoc.get("_id"))).first())

            // update the doc
            val expectedDoc = Document("hello", "computer")
            testSync.updateOneById(result.insertedId, Document("\$set", expectedDoc))

            // create a conflict
            var sem = watchForEvents(syncTestRunner.namespace)
            remoteColl.updateOne(Document("_id", result.insertedId), withNewSyncVersionSet(Document("\$inc", Document("foo", 2))))
            sem.acquire()

            // do a sync pass, and throw an error during the conflict resolver
            // freezing the document
            streamAndSync()
            Assert.assertTrue(errorEmitted)

            // update the doc remotely
            val nextDoc = Document("hello", "friend")

            sem = watchForEvents(syncTestRunner.namespace)
            remoteColl.updateOne(Document("_id", result.insertedId), nextDoc)
            sem.acquire()
            streamAndSync()

            // it should not have updated the local doc, as the local doc should be frozen
            assertEquals(
                withoutId(expectedDoc),
                withoutSyncVersion(withoutId(testSync.find(Document("_id", result.insertedId)).first()!!)))

            // update the local doc. this should unfreeze the config
            testSync.updateOneById(result.insertedId, Document("\$set", Document("no", "op")))

            streamAndSync()

            // this should still be the remote doc since remote wins
            assertEquals(
                withoutId(nextDoc),
                withoutSyncVersion(withoutId(testSync.find(Document("_id", result.insertedId)).first()!!)))

            // update the doc remotely
            val lastDoc = Document("good night", "computer")

            sem = watchForEvents(syncTestRunner.namespace)
            remoteColl.updateOne(
                Document("_id", result.insertedId),
                withNewSyncVersion(lastDoc)
            )
            sem.acquire()

            // now that we're sync'd and unfrozen, it should be reflected locally
            // TODO: STITCH-1958 Possible race condition here for update listening
            streamAndSync()

            assertEquals(
                withoutId(lastDoc),
                withoutSyncVersion(
                    withoutId(testSync.find(Document("_id", result.insertedId)).first()!!)))
        }
    }

    @Test
    fun testConfigure() {
        val coll = syncTestRunner.syncMethods()
        val remoteColl = syncTestRunner.remoteMethods()

        // insert a documnet locally
        val docToInsert = Document("hello", "world")
        val insertedId = coll.insertOneAndSync(docToInsert).insertedId

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
            ErrorListener { _, _ -> }
        )

        // insert a document remotely, and wait for the event to store
        val sem = watchForEvents(syncTestRunner.namespace)
        remoteColl.insertOne(Document("_id", insertedId).append("fly", "away"))
        assertTrue(sem.tryAcquire(10, TimeUnit.SECONDS))

        // sync. assert that the conflict handler and
        // change event listener have been called
        streamAndSync()

        assertTrue(changeEventListenerSemaphore.tryAcquire(10, TimeUnit.SECONDS))
        Assert.assertTrue(hasConflictHandlerBeenInvoked)
        Assert.assertTrue(hasChangeEventListenerBeenInvoked)
    }

    @Test
    fun testSyncVersioningScheme() {
        testSyncInBothDirections {
            val coll = syncTestRunner.syncMethods()

            val remoteColl = syncTestRunner.remoteMethods()

            val docToInsert = Document("hello", "world")

            coll.configure(failingConflictHandler, null, null)
            val insertResult = coll.insertOneAndSync(docToInsert)

            val doc = coll.findOneById(insertResult.insertedId)
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

            assertEquals(expectedDocument, coll.findOneById(doc1Id))

            // the remote document after a local update, but before a sync pass, should have the
            // same version as the original document, and be equivalent to the unupdated document
            val doc1Update = Document("\$inc", Document("foo", 1))
            assertEquals(1, coll.updateOneById(doc1Id, doc1Update).matchedCount)

            val secondRemoteDocBeforeSyncPass = remoteColl.find(doc1Filter).first()!!
            assertEquals(expectedDocument, withoutSyncVersion(secondRemoteDocBeforeSyncPass))
            assertEquals(versionOf(firstRemoteDoc), versionOf(secondRemoteDocBeforeSyncPass))

            expectedDocument["foo"] = 1
            assertEquals(expectedDocument, coll.findOneById(doc1Id))

            // the remote document after a local update, and after a sync pass, should have a new
            // version with the same instance ID as the original document, a version counter
            // incremented by 1, and be equivalent to the updated document.
            streamAndSync()
            val secondRemoteDoc = remoteColl.find(doc1Filter).first()!!
            assertEquals(expectedDocument, withoutSyncVersion(secondRemoteDoc))
            assertEquals(instanceIdOf(firstRemoteDoc), instanceIdOf(secondRemoteDoc))
            assertEquals(1, versionCounterOf(secondRemoteDoc))

            assertEquals(expectedDocument, coll.findOneById(doc1Id))

            // the remote document after a local delete and local insert, but before a sync pass,
            // should have the same version as the previous document
            assertEquals(1, coll.deleteOneById(doc1Id).deletedCount)
            coll.insertOneAndSync(doc!!)

            val thirdRemoteDocBeforeSyncPass = remoteColl.find(doc1Filter).first()!!
            assertEquals(expectedDocument, withoutSyncVersion(thirdRemoteDocBeforeSyncPass))

            expectedDocument.remove("foo")
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            // the remote document after a local delete and local insert, and after a sync pass,
            // should have the same instance ID as before and a version count, since the change
            // events are coalesced into a single update event
            streamAndSync()

            val thirdRemoteDoc = remoteColl.find(doc1Filter).first()!!
            assertEquals(expectedDocument, withoutSyncVersion(thirdRemoteDoc))
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            assertEquals(instanceIdOf(secondRemoteDoc), instanceIdOf(thirdRemoteDoc))
            assertEquals(2, versionCounterOf(thirdRemoteDoc))

            // the remote document after a local delete, a sync pass, a local insert, and after
            // another sync pass should have a new instance ID, with a version counter of zero,
            // since the change events are not coalesced
            assertEquals(1, coll.deleteOneById(doc1Id).deletedCount)
            streamAndSync()
            coll.insertOneAndSync(doc)
            streamAndSync()

            val fourthRemoteDoc = remoteColl.find(doc1Filter).first()!!
            assertEquals(expectedDocument, withoutSyncVersion(thirdRemoteDoc))
            assertEquals(expectedDocument, withoutSyncVersion(coll.findOneById(doc1Id)!!))

            Assert.assertNotEquals(instanceIdOf(secondRemoteDoc), instanceIdOf(fourthRemoteDoc))
            assertEquals(0, versionCounterOf(fourthRemoteDoc))
        }
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
            ErrorListener { _, _ -> errorEmittedSem.release() })

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
        testSyncInBothDirections {
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
            Assert.assertNotNull(coll.findOneById(doc1Id))

            // update the document locally.
            coll.updateOneById(doc1Id, Document("\$inc", Document("i", 1)))

            // sync. assert the document still exists
            streamAndSync()
            Assert.assertNotNull(coll.findOneById(doc1Id))

            // sync. assert the document still exists
            streamAndSync()
            Assert.assertNotNull(coll.findOneById(doc1Id))
        }
    }

    @Test
    fun testStaleFetchSingleDeleted() {
        testSyncInBothDirections {
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
            Assert.assertNotNull(coll.findOneById(doc1Id))

            coll.updateOneById(doc1Id, Document("\$inc", Document("i", 1)))
            streamAndSync()
            Assert.assertNotNull(coll.findOneById(doc1Id))

            assertEquals(1, remoteColl.deleteOne(doc1Filter).deletedCount)
            powerCycleDevice()
            coll.configure(ConflictHandler { _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
                throw IllegalStateException("failure")
            }, null, null)

            streamAndSync()
            Assert.assertNull(coll.findOneById(doc1Id))
        }
    }

    @Test
    fun testStaleFetchMultiple() {
        testSyncInBothDirections {
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
            Assert.assertNotNull(coll.findOneById(doc1Id))

            coll.updateOneById(doc1Id, Document("\$inc", Document("i", 1)))
            streamAndSync()
            Assert.assertNotNull(coll.findOneById(doc1Id))

            coll.syncOne(doc2Id!!)
            streamAndSync()
            Assert.assertNotNull(coll.findOneById(doc1Id))
            Assert.assertNotNull(coll.findOneById(doc2Id))
        }
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

    private fun streamAndSync() {
        if (syncTestRunner.testNetworkMonitor.connectedState) {
            while (!syncTestRunner.dataSynchronizer.areAllStreamsOpen()) {
                println("waiting for all streams to open before doing sync pass")
                Thread.sleep(1000)
            }
        }
        syncTestRunner.dataSynchronizer.doSyncPass()
    }

    private fun powerCycleDevice() {
        syncTestRunner.dataSynchronizer.reloadConfig()
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

    private val failingConflictHandler = ConflictHandler { _: BsonValue, _: ChangeEvent<Document>, _: ChangeEvent<Document> ->
        Assert.fail("did not expect a conflict")
        throw IllegalStateException("unreachable")
    }

    private fun testSyncInBothDirections(testFun: () -> Unit) {
        println("running tests with L2R going first")
        syncTestRunner.dataSynchronizer.swapSyncDirection(true)
        testFun()

        syncTestRunner.teardown()
        syncTestRunner.setup()
        println("running tests with R2L going first")
        syncTestRunner.dataSynchronizer.swapSyncDirection(false)
        testFun()
    }
}
