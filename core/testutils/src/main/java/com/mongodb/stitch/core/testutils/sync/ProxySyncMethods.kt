package com.mongodb.stitch.core.testutils.sync

import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncDeleteResult
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncInsertManyResult
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncInsertOneResult
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateOptions
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateResult
import org.bson.BsonDocument
import org.bson.BsonValue
import org.bson.Document
import org.bson.conversions.Bson

/**
 * A set of platform independent methods related to
 * [com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSync].
 */
interface ProxySyncMethods {
    fun configure(
        conflictResolver: ConflictHandler<Document?>,
        changeEventListener: ChangeEventListener<Document>?,
        exceptionListener: ExceptionListener?
    ): Void?

    fun syncMany(vararg id: BsonValue)

    fun syncOne(id: BsonValue): Void?

    fun desyncOne(id: BsonValue): Void?

    fun getSyncedIds(): Set<BsonValue>

    fun find(filter: Bson = BsonDocument()): Iterable<Document?>

    fun aggregate(pipeline: List<Bson>): Iterable<Document?>

    fun count(filter: Bson = BsonDocument()): Long

    fun updateOne(
        filter: Bson,
        update: Bson,
        updateOptions: SyncUpdateOptions = SyncUpdateOptions()
    ): SyncUpdateResult

    fun updateMany(
        filter: Bson,
        update: Bson,
        updateOptions: SyncUpdateOptions = SyncUpdateOptions()
    ): SyncUpdateResult

    fun insertOne(document: Document): SyncInsertOneResult

    fun insertMany(documents: List<Document>): SyncInsertManyResult

    fun deleteOne(filter: Bson): SyncDeleteResult

    fun deleteMany(filter: Bson): SyncDeleteResult

    fun getPausedDocumentIds(): Set<BsonValue>

    fun resumeSyncForDocument(documentId: BsonValue): Boolean
}
