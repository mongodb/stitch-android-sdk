package com.mongodb.stitch.core.services.mongodb.sync.internal;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteFindIterable;
import com.mongodb.stitch.core.services.mongodb.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.sync.CoreSync;
import com.mongodb.stitch.core.services.mongodb.sync.SyncConflictResolver;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

import java.util.Set;

import javax.annotation.Nullable;

public class CoreSyncImpl<DocumentT> implements CoreSync<DocumentT> {
    private final DataSynchronizer dataSynchronizer;
    private final SyncOperations<DocumentT> syncOperations;
    private final MongoNamespace namespace;
    private final CodecRegistry codecRegistry;
    private final Class<DocumentT> documentClass;
    private final CoreStitchServiceClient service;

    private SyncConflictResolver<DocumentT> conflictResolver;
    private ChangeEventListener<DocumentT> changeEventListener;

    public CoreSyncImpl(MongoNamespace namespace,
                        Class<DocumentT> documentClass,
                        DataSynchronizer dataSynchronizer,
                        CoreStitchServiceClient service,
                        SyncOperations<DocumentT> syncOperations,
                        CodecRegistry codecRegistry) {
        this.namespace = namespace;
        this.documentClass = documentClass;
        this.dataSynchronizer = dataSynchronizer;
        this.syncOperations = syncOperations;
        this.codecRegistry = codecRegistry;
        this.service = service;
    }

    @Override
    public void configure(SyncConflictResolver<DocumentT> conflictResolver,
                          ChangeEventListener<DocumentT> changeEventListener) {
        this.conflictResolver = conflictResolver;
        this.changeEventListener = changeEventListener;
    }

    @Override
    public void syncOne(BsonValue id) {
        this.dataSynchronizer.syncDocumentFromRemote(
                this.namespace, id,
                this.conflictResolver,
                this.codecRegistry.get(this.documentClass)
        );
        dataSynchronizer.watchDocument(
                namespace, id, changeEventListener, codecRegistry.get(documentClass));
    }

    @Override
    public void syncMany(BsonValue... ids) {
        for (BsonValue id: ids) {
            this.syncOne(id);
        }
    }

    @Override
    public void desyncOne(BsonValue id) {
        this.dataSynchronizer.desyncDocumentFromRemote(namespace, id);
    }

    @Override
    public void desyncMany(BsonValue... ids) {
        for (BsonValue id: ids) {
            this.desyncOne(id);
        }
    }

    @Override
    public Set<BsonValue> getSyncedIds() {
        return this.dataSynchronizer.getSynchronizedDocumentIds(namespace);
    }

    /**
     * Finds a single document by the given id. It is first searched for in the local synchronized
     * cache and if not found and there is internet connectivity, it is searched for remotely.
     *
     * @param documentId the _id of the document to search for.
     * @return the document if found locally or remotely.
     */
    @Nullable
    public DocumentT findOneById(final BsonValue documentId) {
        return findOneById(documentId, this.documentClass);
    }

    /**
     * Finds a single document by the given id. It is first searched for in the local synchronized
     * cache and if not found and there is internet connectivity, it is searched for remotely.
     *
     * @param documentId the _id of the document to search for.
     * @param resultClass the class to decode each document into
     * @param <ResultT>   the target document type of the iterable.
     * @return the document if found locally or remotely.
     */
    @Nullable
    public <ResultT> ResultT findOneById(
            final BsonValue documentId,
            final Class<ResultT> resultClass
    ) {
        return syncOperations.findOneById(documentId, resultClass).execute(service);
    }

    /**
     * Updates a document by the given id. It is first searched for in the local synchronized cache
     * and if not found and there is internet connectivity, it is searched for remotely.
     *
     * @param documentId the _id of the document to search for.
     * @param update the update specifier.
     * @return the result of the local or remote update.
     */
    public RemoteUpdateResult updateOneById(final BsonValue documentId, final Bson update) {
        return syncOperations.updateOneById(documentId, update).execute(service);
    }

    /**
     * Inserts a single document and begins to synchronize it.
     *
     * @param document the document to insert and synchronize.
     * @return the result of the insertion.
     */
    public RemoteInsertOneResult insertOneAndSync(
            final DocumentT document
    ) {
        return syncOperations
                .insertOneAndSync(document, conflictResolver, this.changeEventListener).execute(service);
    }

    /**
     * Deletes a single document by the given id. It is first searched for in the local synchronized
     * cache and if not found and there is internet connectivity, it is searched for remotely.
     *
     * @param documentId the _id of the document to search for.
     * @return the result of the local or remote update.
     */
    public RemoteDeleteResult deleteOneById(final BsonValue documentId) {
        return syncOperations.deleteOneById(documentId).execute(service);
    }

    @Override
    public CoreRemoteFindIterable<DocumentT> find() {
        return this.find(new BsonDocument(), documentClass);
    }

    @Override
    public CoreRemoteFindIterable<DocumentT> find(Bson filter) {
        return this.find(filter, documentClass);
    }

    @Override
    public <ResultT> CoreRemoteFindIterable<ResultT> find(Class<ResultT> resultClass) {
        return this.find(new BsonDocument(), resultClass);
    }

    /**
     * Finds all documents in the collection.
     *
     * @param filter      the query filter
     * @param resultClass the class to decode each document into
     * @param <ResultT>   the target document type of the iterable.
     * @return the find iterable interface
     */
    public <ResultT> CoreRemoteFindIterable<ResultT> find(
            final Bson filter,
            final Class<ResultT> resultClass
    ) {
        return createFindIterable(filter, resultClass);
    }

    private <ResultT> CoreRemoteFindIterable<ResultT> createFindIterable(
            final Bson filter,
            final Class<ResultT> resultClass
    ) {
        return new CoreSyncFindIterableImpl<>(
                filter,
                resultClass,
                service,
                syncOperations);
    }
}
