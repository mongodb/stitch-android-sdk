package com.mongodb.stitch.core.testutils.sync

import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import com.mongodb.stitch.core.services.mongodb.remote.sync.ErrorListener
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
    /**
     * Set the conflict handler and and change event listener on this collection.
     * @param conflictResolver the conflict resolver to invoke when a conflict happens between local
     *                         and remote events.
     * @param changeEventListener the event listener to invoke when a change event happens for the
     *                         document.
     * @param errorListener the error listener to invoke when an irrecoverable error occurs
     */
    fun configure(
        conflictResolver: ConflictHandler<Document?>,
        changeEventListener: ChangeEventListener<Document>?,
        errorListener: ErrorListener?
    )

    /**
     * Requests that the given document _id be synchronized.
     * @param id the document _id to synchronize.
     */
    fun syncOne(id: BsonValue)

    /**
     * Stops synchronizing the given document _id. Any uncommitted writes will be lost.
     *
     * @param id the _id of the document to desynchronize.
     */
    fun desyncOne(id: BsonValue)

    /**
     * Returns the set of synchronized document ids in a namespace.
     *
     * @return the set of synchronized document ids in a namespace.
     */
    fun getSyncedIds(): Set<BsonValue>

    /**
     * Finds all documents in the collection.
     *
     * @param filter the query filter
     * @return the find iterable interface
     */
    fun find(filter: Bson = BsonDocument()): Iterable<Document?>

    /**
     * Aggregates documents that have been synchronized from the remote
     * according to the specified aggregation pipeline.
     *
     * @param pipeline the aggregation pipeline
     * @return an iterable containing the result of the aggregation operation
     */
    fun aggregate(pipeline: List<Bson>): Iterable<Document?>

    /**
     * Counts the number of documents in the collection that have been synchronized from the remote
     * according to the given options.
     *
     * @param filter the query filter
     * @return the number of documents in the collection
     */
    fun count(filter: Bson = BsonDocument()): Long

    /**
     * Updates a document by the given id. It is first searched for in the local synchronized cache
     * and if not found and there is internet connectivity, it is searched for remotely.
     *
     * @param filter the query filter
     * @param update the update specifier.
     * @return the result of the local or remote update.
     */
    fun updateOne(
        filter: Bson,
        update: Bson,
        updateOptions: SyncUpdateOptions = SyncUpdateOptions()
    ): SyncUpdateResult

    /**
     * Update all documents that have been synchronized from the remote
     * in the collection according to the specified arguments.
     *
     * @param filter a document describing the query filter, which may not be null.
     * @param update a document describing the update, which may not be null. The update to
     * apply must include only update operators.
     * @param updateOptions the options to apply to the update operation
     * @return the result of the update many operation
     */
    fun updateMany(
        filter: Bson,
        update: Bson,
        updateOptions: SyncUpdateOptions = SyncUpdateOptions()
    ): SyncUpdateResult

    /**
     * Inserts a single document and begins to synchronize it.
     *
     * @param document the document to insert and synchronize.
     * @return the result of the insertion.
     */
    fun insertOneAndSync(document: Document): SyncInsertOneResult

    /**
     * Inserts one or more documents. Begin synchronizing on the documents' ids.
     *
     * @param documents the documents to insert
     * @return the result of the insert many operation
     */
    fun insertManyAndSync(documents: List<Document>): SyncInsertManyResult

    /**
     * Deletes a single document by the given id. It is first searched for in the local synchronized
     * cache and if not found and there is internet connectivity, it is searched for remotely.
     *
     * @param filter the query filter
     * @return the result of the local or remote update.
     */
    fun deleteOne(filter: Bson): SyncDeleteResult

    /**
     * Removes all documents from the collection that have been synchronized from the remote
     * that match the given query filter.  If no documents match, the collection is not modified.
     *
     * @param filter the query filter to apply the the delete operation
     * @return the result of the remove many operation
     */
    fun deleteMany(filter: Bson): SyncDeleteResult

    /**
     * Return the set of synchronized document _ids in a namespace
     * that have been paused due to an irrecoverable error.
     *
     * @return the set of paused document _ids in a namespace
     */
    fun getPausedDocumentIds(): Set<BsonValue>

    /**
     * A document that is paused no longer has remote updates applied to it.
     * Any local updates to this document cause it to be resumed. An example of pausing a document
     * is when a conflict is being resolved for that document and the handler throws an exception.
     *
     * @param documentId the id of the document to resume syncing
     */
    fun resumeSyncForDocument(documentId: BsonValue): Boolean
}
