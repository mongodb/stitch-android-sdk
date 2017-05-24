package com.mongodb.stitch.android.services.mongodb;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.StitchClient;
import com.mongodb.stitch.android.PipelineStage;

import org.bson.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MongoClient provides a simple wrapper around pipelines to enable CRUD usage of
 * a MongoDB service.
 */
public class MongoClient {
    private static final String TAG = "Stitch-MongoDB";

    private final StitchClient _stitchClient;
    private final String _service;

    /**
     * @param stitchClient The client to execute with.
     * @param service    The name of the MongoDB service.
     */
    public MongoClient(final StitchClient stitchClient, final String service) {
        _stitchClient = stitchClient;
        _service = service;
    }

    /**
     * Gets a database.
     *
     * @param name The name of the database.
     * @return A reference to the database.
     */
    public Database getDatabase(final String name) {
        return new Database(this, name);
    }

    /**
     * Database represents a reference to a MongoDB database accessed through Stitch.
     */
    public static class Database {
        private final MongoClient _client;
        private final String _dbName;

        /**
         * @param client The client to which this database is referenced by.
         * @param dbName The name of the database.
         */
        public Database(final MongoClient client, final String dbName) {
            _client = client;
            _dbName = dbName;
        }

        /**
         * Gets a collection in this database.
         *
         * @param name The name of the collection.
         * @return A reference to the collection.
         */
        public Collection getCollection(final String name) {
            return new Collection(this, name);
        }
    }

    /**
     * Collection represents a reference to a MongoDB collection accessed through Stitch.
     */
    public static class Collection {
        private final Database _database;
        private final String _collName;

        /**
         * @param database The database to which this collection is contained in.
         * @param collName The name of the collection.
         */
        public Collection(final Database database, final String collName) {
            _database = database;
            _collName = collName;
        }

        /**
         * Makes a stage that executes a find on the collection.
         *
         * @param query      The query specifier.
         * @param projection The projection document.
         * @param limit      The maximum amount of matching documents to accept.
         * @param count      Whether or not to output a count of documents matching the query.
         * @return A stage representing this CRUD action.
         */
        public PipelineStage makeFindStage(
                final Document query,
                final Document projection,
                final Integer limit,
                final Boolean count
        ) {
            final Map<String, Object> args = new HashMap<>();
            args.put(Parameters.DATABASE, _database._dbName);
            args.put(Parameters.COLLECTION, _collName);
            args.put(Parameters.QUERY, query);

            if (projection != null) {
                args.put(Parameters.PROJECT, projection);
            }
            if (limit != null) {
                args.put(Parameters.LIMIT, limit);
            }
            if (count != null) {
                args.put(Parameters.COUNT, count);
            }

            return new PipelineStage(
                    Stages.FIND,
                    _database._client._service,
                    args);
        }

        /**
         * Makes a stage that executes an update on the collection.
         *
         * @param query  The query specifier.
         * @param update The update specifier.
         * @param upsert Whether or not to upsert if the query matches no document.
         * @param multi  Whether or not to update multiple documents.
         * @return A stage representing this CRUD action.
         */
        public PipelineStage makeUpdateStage(
                final Document query,
                final Document update,
                final boolean upsert,
                final boolean multi
        ) {
            final Map<String, Object> args = new HashMap<>();
            args.put(Parameters.DATABASE, _database._dbName);
            args.put(Parameters.COLLECTION, _collName);
            args.put(Parameters.QUERY, query);
            args.put(Parameters.UPDATE, update);
            args.put(Parameters.UPSERT, upsert);
            args.put(Parameters.MULTI, multi);
            return new PipelineStage(
                    Stages.UPDATE,
                    _database._client._service,
                    args);
        }

        /**
         * Makes a series of stages that execute an insert on the collection.
         *
         * @param documents The set of documents to insert.
         * @return The stages representing this CRUD action.
         */
        public List<PipelineStage> makeInsertStage(
                final List<Document> documents
        ) {
            final Map<String, Object> literalArgs = new HashMap<>();
            literalArgs.put(PipelineStage.LiteralStage.PARAMETER_ITEMS, documents);

            final Map<String, Object> insertArgs = new HashMap<>();
            insertArgs.put(Parameters.DATABASE, _database._dbName);
            insertArgs.put(Parameters.COLLECTION, _collName);

            final List<PipelineStage> pipelineStages = new ArrayList<>();
            pipelineStages.add(new PipelineStage(
                    PipelineStage.LiteralStage.NAME,
                    literalArgs));
            pipelineStages.add(new PipelineStage(
                    Stages.INSERT,
                    _database._client._service,
                    insertArgs));

            return pipelineStages;
        }

        /**
         * Makes a stage that executes a delete on the collection.
         *
         * @param query     The query specifier.
         * @param singleDoc Whether or not to delete only a single matched document.
         * @return A stage representing this CRUD action.
         */
        public PipelineStage makeDeleteStage(
                final Document query,
                final boolean singleDoc
        ) {
            final Map<String, Object> args = new HashMap<>();
            args.put(Parameters.DATABASE, _database._dbName);
            args.put(Parameters.COLLECTION, _collName);
            args.put(Parameters.QUERY, query);
            args.put(Parameters.SINGLE_DOCUMENT, singleDoc);
            return new PipelineStage(
                    Stages.DELETE,
                    _database._client._service,
                    args);
        }

        /**
         * Finds documents matching a query.
         *
         * @param query The query specifier.
         * @return A task containing the matched documents that can be resolved upon completion
         * of the request.
         */
        public Task<List<Document>> find(final Document query) {
            return convertToDocuments(_database._client._stitchClient.executePipeline(makeFindStage(query, null, null, null)));
        }

        /**
         * Finds documents matching a query up to the specified limit.
         *
         * @param query      The query specifier.
         * @param limit      The maximum amount of matching documents to accept.
         * @return A task containing the matched documents that can be resolved upon completion
         * of the request.
         */
        public Task<List<Document>> find(final Document query, final Integer limit) {
            return convertToDocuments(_database._client._stitchClient.executePipeline(makeFindStage(query, null, limit, null)));
        }

        /**
         * Finds and projects documents matching a query.
         *
         * @param query      The query specifier.
         * @param projection The projection document.
         * @return A task containing the matched and projected documents that can be resolved upon completion
         * of the request.
         */
        public Task<List<Document>> find(final Document query, final Document projection) {
            return convertToDocuments(_database._client._stitchClient.executePipeline(makeFindStage(query, projection, null, null)));
        }

        /**
         * Finds and projects documents matching a query up to the specified limit.
         *
         * @param query      The query specifier.
         * @param projection The projection document.
         * @param limit      The maximum amount of matching documents to accept.
         * @return A task containing the matched and projected documents that can be resolved upon completion
         * of the request.
         */
        public Task<List<Document>> find(final Document query, final Document projection, final Integer limit) {
            return convertToDocuments(_database._client._stitchClient.executePipeline(makeFindStage(query, projection, limit, null)));
        }

        /**
         * Counts the number of documents matching a query.
         *
         * @param query      The query specifier.
         * @return A task containing the number of matched documents that can be resolved upon completion
         * of the request.
         */
        public Task<Integer> count(final Document query) {
            return _database._client._stitchClient.executePipeline(makeFindStage(query, null, null, true)).continueWith(new Continuation<List<Object>, Integer>() {
                @Override
                public Integer then(@NonNull final Task<List<Object>> task) throws Exception {
                    if (task.isSuccessful()) {
                        return (Integer) task.getResult().get(0);
                    } else {
                        Log.d(
                                TAG,
                                "Error getting pipeline results",
                                task.getException()
                        );
                        throw task.getException();
                    }
                }
            });
        }

        /**
         * Counts the number of documents matching a query up to the specified limit.
         *
         * @param query      The query specifier.
         * @param limit      The maximum amount of matching documents to accept.
         * @return A task containing the number of matched documents that can be resolved upon completion
         * of the request.
         */
        public Task<Integer> count(final Document query, final Integer limit) {
            return _database._client._stitchClient.executePipeline(makeFindStage(query, null, limit, true)).continueWith(new Continuation<List<Object>, Integer>() {
                @Override
                public Integer then(@NonNull final Task<List<Object>> task) throws Exception {
                    if (task.isSuccessful()) {
                        return (Integer) task.getResult().get(0);
                    } else {
                        Log.d(
                                TAG,
                                "Error getting pipeline results",
                                task.getException()
                        );
                        throw task.getException();
                    }
                }
            });
        }

        /**
         * Updates a single document matching a query
         *
         * @param query  The query specifier.
         * @param update The update specifier.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Void> updateOne(final Document query, final Document update) {
            return updateOne(query, update, false);
        }

        /**
         * Updates a single document matching the query specifier.
         *
         * @param query  The query specifier.
         * @param update The update specifier.
         * @param upsert Whether or not to upsert if the query matches no documents.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Void> updateOne(final Document query, final Document update, final boolean upsert) {
            return _database._client._stitchClient.executePipeline(makeUpdateStage(query, update, upsert, false)).continueWith(new Continuation<List<Object>, Void>() {
                @Override
                public Void then(@NonNull final Task<List<Object>> task) throws Exception {
                    if (task.isSuccessful()) {
                        return null;
                    }
                    Log.d(
                            TAG,
                            "Error upserting single document",
                            task.getException()
                    );
                    throw task.getException();
                }
            });
        }

        /**
         * Updates many documents matching a query specifier.
         *
         * @param query  The query specifier.
         * @param update The update specifier.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Void> updateMany(final Document query, final Document update) {
            return updateMany(query, update, false);
        }

        /**
         * Updates many documents matching a query specifier.
         *
         * @param query  The query specifier.
         * @param update The update specifier.
         * @param upsert Whether or not to upsert if the query matches no documents.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Void> updateMany(final Document query, final Document update, final boolean upsert) {
            return _database._client._stitchClient.executePipeline(makeUpdateStage(query, update, upsert, true)).continueWith(new Continuation<List<Object>, Void>() {
                @Override
                public Void then(@NonNull final Task<List<Object>> task) throws Exception {
                    if (task.isSuccessful()) {
                        return null;
                    }
                    Log.d(
                            TAG,
                            "Error updating many documents",
                            task.getException()
                    );
                    throw task.getException();
                }
            });
        }

        /**
         * Inserts a single document.
         *
         * @param document The document to insert.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Void> insertOne(final Document document) {
            return _database._client._stitchClient.executePipeline(makeInsertStage(Collections.singletonList(document))).continueWith(new Continuation<List<Object>, Void>() {
                @Override
                public Void then(@NonNull final Task<List<Object>> task) throws Exception {
                    if (task.isSuccessful()) {
                        return null;
                    }
                    Log.d(
                            TAG,
                            "Error inserting single document",
                            task.getException()
                    );
                    throw task.getException();
                }
            });
        }

        /**
         * Inserts many documents.
         *
         * @param documents The list of documents to insert.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Void> insertMany(final List<Document> documents) {
            return _database._client._stitchClient.executePipeline(makeInsertStage(documents)).continueWith(new Continuation<List<Object>, Void>() {
                @Override
                public Void then(@NonNull final Task<List<Object>> task) throws Exception {
                    if (task.isSuccessful()) {
                        return null;
                    }
                    Log.d(
                            TAG,
                            "Error inserting multiple documents",
                            task.getException()
                    );
                    throw task.getException();
                }
            });
        }

        /**
         * Deletes a single document matching a query specifier.
         *
         * @param query The query specifier.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Void> deleteOne(final Document query) {
            return _database._client._stitchClient.executePipeline(makeDeleteStage(query, true)).continueWith(new Continuation<List<Object>, Void>() {
                @Override
                public Void then(@NonNull final Task<List<Object>> task) throws Exception {
                    if (task.isSuccessful()) {
                        return null;
                    }
                    Log.d(
                            TAG,
                            "Error deleting single document",
                            task.getException()
                    );
                    throw task.getException();
                }
            });
        }

        /**
         * Deletes many document matching a query specifier.
         *
         * @param query The query specifier.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Void> deleteMany(final Document query) {
            return _database._client._stitchClient.executePipeline(makeDeleteStage(query, false)).continueWith(new Continuation<List<Object>, Void>() {
                @Override
                public Void then(@NonNull final Task<List<Object>> task) throws Exception {
                    if (task.isSuccessful()) {
                        return null;
                    }
                    Log.d(
                            TAG,
                            "Error deleting many documents",
                            task.getException()
                    );
                    throw task.getException();
                }
            });
        }

        /**
         * Converts a series of documents into their concrete {@link Document} format.
         *
         * @param pipelineResult The task representing the pipeline result
         * @return A task containing the converted documents that can be resolved
         * upon completion of the pipeline result.
         */
        private Task<List<Document>> convertToDocuments(final Task<List<Object>> pipelineResult) {
            return pipelineResult.continueWith(new Continuation<List<Object>, List<Document>>() {
                @Override
                public List<Document> then(@NonNull final Task<List<Object>> task) throws Exception {
                    if (task.isSuccessful()) {
                        final List<Object> objects = task.getResult();
                        final List<Document> docs = new ArrayList<>(objects.size());
                        for (final Object obj : objects) {
                            docs.add((Document) obj);
                        }
                        return docs;
                    } else {
                        Log.d(
                                TAG,
                                "Error getting pipeline results",
                                task.getException()
                        );
                        throw task.getException();
                    }
                }
            });
        }

        private static class Stages {
            private static final String FIND = "find";
            private static final String UPDATE = "update";
            private static final String INSERT = "insert";
            private static final String DELETE = "delete";

        }

        private static class Parameters {
            private static final String DATABASE = "database";
            private static final String COLLECTION = "collection";
            private static final String QUERY = "query";
            private static final String UPDATE = "update";
            private static final String UPSERT = "upsert";
            private static final String MULTI = "multi";
            private static final String PROJECT = "project";
            private static final String SINGLE_DOCUMENT = "singleDoc";
            private static final String LIMIT = "limit";
            private static final String COUNT = "count";
        }
    }
}
