package com.mongodb.stitch.core.testutils.sync

import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertManyResult
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult
import org.bson.Document
import org.bson.conversions.Bson

/**
 * A set of platform independent methods related to
 * [com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollection].
 */
interface CoreRemoteMethods {
    /**
     * Inserts the provided document. If the document is missing an identifier, the client should
     * generate one.
     *
     * @param document the document to insert
     * @return the result of the insert one operation
     */
    fun insertOne(document: Document): RemoteInsertOneResult

    /**
     * Inserts one or more documents.
     *
     * @param documents the documents to insert
     * @return the result of the insert many operation
     */
    fun insertMany(documents: List<Document>): RemoteInsertManyResult

    /**
     * Finds all documents in the collection.
     *
     * @param filter the query filter
     * @return an iterable interface containing the documents
     */
    fun find(filter: Document): Iterable<Document?>
    /**
     * Update a single document in the collection according to the specified arguments.
     *
     * @param filter a document describing the query filter, which may not be null.
     * @param updateDocument a document describing the update, which may not be null. The update to
     *                       apply must include only update operators.
     * @return the result of the update one operation
     */
    fun updateOne(filter: Document, updateDocument: Document): RemoteUpdateResult

    /**
     * Removes at most one document from the collection that matches the given filter.  If no
     * documents match, the collection is not
     * modified.
     *
     * @param filter the query filter to apply the the delete operation
     * @return the result of the remove one operation
     */
    fun deleteOne(filter: Bson): RemoteDeleteResult
}
