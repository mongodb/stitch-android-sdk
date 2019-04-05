/*
 * Copyright 2018-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import com.mongodb.MongoNamespace;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.ClientSession;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MapReduceIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.CreateIndexOptions;
import com.mongodb.client.model.DeleteOptions;
import com.mongodb.client.model.DropIndexOptions;
import com.mongodb.client.model.EstimatedDocumentCountOptions;
import com.mongodb.client.model.FindOneAndDeleteOptions;
import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.InsertOneOptions;
import com.mongodb.client.model.RenameCollectionOptions;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.lang.Nullable;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollection;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

public class WriteModelContainer<DocumentT> implements Committable {
  private final MongoCollection<DocumentT> collection;
  private final List<WriteModel<DocumentT>> bulkWriteModels = new ArrayList<>();

  WriteModelContainer(final MongoCollection<DocumentT> collection) {
    this.collection = collection;
  }

  WriteModelContainer(final CoreRemoteMongoCollection<DocumentT> collection) {
    this.collection = new MongoCollectionFacade<>(collection);
  }

  final void add(@Nullable final WriteModel<DocumentT> write) {
    if (write == null) {
      return;
    }
    this.bulkWriteModels.add(write);
  }

  final void merge(@Nullable final WriteModelContainer<DocumentT> syncWriteModelContainer) {
    if (syncWriteModelContainer == null) {
      return;
    }
    this.bulkWriteModels.addAll(syncWriteModelContainer.bulkWriteModels);
  }

  @Override
  public final void commit() {
    if (collection == null) {
      throw new IllegalStateException("cannot commit a container with no associated collection");
    }
    if (bulkWriteModels.size() > 0) {
      collection.bulkWrite(bulkWriteModels);
    }
  }

  @Override
  public final void commitAndClear() {
    this.commit();
    this.bulkWriteModels.clear();
  }

  // goes away when bulk API is available, but for now captures the union type of
  // a fully realized Mongo collection and a Stitch remote Mongo collection
  @SuppressWarnings("all")
  private static class MongoCollectionFacade<DocumentT> implements MongoCollection<DocumentT> {
    private final CoreRemoteMongoCollection<DocumentT> collection;

    MongoCollectionFacade(final CoreRemoteMongoCollection<DocumentT> collection) {
      this.collection = collection;
    }

    @Override
    public MongoNamespace getNamespace() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Class<DocumentT> getDocumentClass() {
      throw new UnsupportedOperationException();
    }

    @Override
    public CodecRegistry getCodecRegistry() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ReadPreference getReadPreference() {
      throw new UnsupportedOperationException();
    }

    @Override
    public WriteConcern getWriteConcern() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ReadConcern getReadConcern() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <NewT> MongoCollection<NewT> withDocumentClass(
        final Class<NewT> clazz) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MongoCollection<DocumentT> withCodecRegistry(final CodecRegistry codecRegistry) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MongoCollection<DocumentT> withReadPreference(final ReadPreference readPreference) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MongoCollection<DocumentT> withWriteConcern(final WriteConcern writeConcern) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MongoCollection<DocumentT> withReadConcern(final ReadConcern readConcern) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long count() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long count(final Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long count(
        final Bson filter,
        final CountOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long count(final ClientSession clientSession) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long count(
        final ClientSession clientSession,
        final Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long count(
        final ClientSession clientSession,
        final Bson filter,
        final CountOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long countDocuments() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long countDocuments(final Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long countDocuments(
        final Bson filter,
        final CountOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long countDocuments(final ClientSession clientSession) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long countDocuments(
        final ClientSession clientSession,
        final Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long countDocuments(
        final ClientSession clientSession,
        final Bson filter,
        final CountOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long estimatedDocumentCount() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long estimatedDocumentCount(final EstimatedDocumentCountOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <ResultT> DistinctIterable<ResultT> distinct(
        final String fieldName,
        final Class<ResultT> resultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <ResultT> DistinctIterable<ResultT> distinct(
        final String fieldName,
        final Bson filter,
        final Class<ResultT> resultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <ResultT> DistinctIterable<ResultT> distinct(
        final ClientSession clientSession,
        final String fieldName,
        final Class<ResultT> resultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <ResultT> DistinctIterable<ResultT> distinct(
        final ClientSession clientSession,
        final String fieldName,
        final Bson filter,
        final Class<ResultT> resultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public FindIterable<DocumentT> find() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <ResultT> FindIterable<ResultT> find(final Class<ResultT> resultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public FindIterable<DocumentT> find(final Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <ResultT> FindIterable<ResultT> find(
        final Bson filter,
        final Class<ResultT> resultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public FindIterable<DocumentT> find(final ClientSession clientSession) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <ResultT> FindIterable<ResultT> find(
        final ClientSession clientSession,
        final Class<ResultT> resultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public FindIterable<DocumentT> find(
        final ClientSession clientSession,
        final Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <ResultT> FindIterable<ResultT> find(
        final ClientSession clientSession,
        final Bson filter,
        final Class<ResultT> resultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public AggregateIterable<DocumentT> aggregate(final List<? extends Bson> pipeline) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <ResultT> AggregateIterable<ResultT> aggregate(
        final List<? extends Bson> pipeline,
        final Class<ResultT> resultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public AggregateIterable<DocumentT> aggregate(
        final ClientSession clientSession,
        final List<? extends Bson> pipeline) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <ResultT> AggregateIterable<ResultT> aggregate(
        final ClientSession clientSession,
        final List<? extends Bson> pipeline,
        final Class<ResultT> resultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ChangeStreamIterable<DocumentT> watch() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <ResultT> ChangeStreamIterable<ResultT> watch(final Class<ResultT> resultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ChangeStreamIterable<DocumentT> watch(final List<? extends Bson> pipeline) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <ResultT> ChangeStreamIterable<ResultT> watch(
        final List<? extends Bson> pipeline,
        final Class<ResultT> resultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ChangeStreamIterable<DocumentT> watch(final ClientSession clientSession) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <ResultT> ChangeStreamIterable<ResultT> watch(
        final ClientSession clientSession,
        final Class<ResultT> resultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ChangeStreamIterable<DocumentT> watch(
        final ClientSession clientSession,
        final List<? extends Bson> pipeline) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <ResultT> ChangeStreamIterable<ResultT> watch(
        final ClientSession clientSession,
        final List<? extends Bson> pipeline,
        final Class<ResultT> resultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MapReduceIterable<DocumentT> mapReduce(
        final String mapFunction,
        final String reduceFunction) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <ResultT> MapReduceIterable<ResultT> mapReduce(
        final String mapFunction,
        final String reduceFunction,
        final Class<ResultT> resultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MapReduceIterable<DocumentT> mapReduce(
        final ClientSession clientSession,
        final String mapFunction,
        final String reduceFunction) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <ResultT> MapReduceIterable<ResultT> mapReduce(
        final ClientSession clientSession,
        final String mapFunction,
        final String reduceFunction,
        final Class<ResultT> resultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public BulkWriteResult bulkWrite(
        final List<? extends WriteModel<? extends DocumentT>> requests) {
      for (final WriteModel<? extends DocumentT> write : requests) {
        if (write instanceof ReplaceOneModel) {
          final ReplaceOneModel<DocumentT> replaceModel = ((ReplaceOneModel) write);
          final RemoteUpdateResult result = this.collection.updateOne(replaceModel.getFilter(),
              (Bson) replaceModel.getReplacement());
        } else if (write instanceof UpdateOneModel) {
          final UpdateOneModel<DocumentT> updateModel = ((UpdateOneModel) write);
          final RemoteUpdateResult result = this.collection.updateOne(updateModel.getFilter(),
              updateModel.getUpdate());
        } else if (write instanceof UpdateManyModel) {
          final UpdateManyModel<DocumentT> updateModel = ((UpdateManyModel) write);
          final RemoteUpdateResult result = this.collection.updateMany(updateModel.getFilter(),
              updateModel.getUpdate());
        }
      }
      return BulkWriteResult.unacknowledged(); // unused
    }

    @Override
    public BulkWriteResult bulkWrite(
        final List<? extends WriteModel<? extends DocumentT>> requests,
        final BulkWriteOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public BulkWriteResult bulkWrite(
        final ClientSession clientSession,
        final List<? extends WriteModel<? extends DocumentT>> requests) {
      throw new UnsupportedOperationException();
    }

    @Override
    public BulkWriteResult bulkWrite(
        final ClientSession clientSession,
        final List<? extends WriteModel<? extends DocumentT>> requests,
        final BulkWriteOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void insertOne(final DocumentT documentT) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void insertOne(
        final DocumentT documentT,
        final InsertOneOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void insertOne(
        final ClientSession clientSession,
        final DocumentT documentT) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void insertOne(
        final ClientSession clientSession,
        final DocumentT documentT,
        final InsertOneOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void insertMany(final List<? extends DocumentT> documents) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void insertMany(
        final List<? extends DocumentT> documents,
        final InsertManyOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void insertMany(
        final ClientSession clientSession,
        final List<? extends DocumentT> documents) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void insertMany(
        final ClientSession clientSession,
        final List<? extends DocumentT> documents,
        final InsertManyOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DeleteResult deleteOne(final Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DeleteResult deleteOne(
        final Bson filter,
        final DeleteOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DeleteResult deleteOne(
        final ClientSession clientSession,
        final Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DeleteResult deleteOne(
        final ClientSession clientSession,
        final Bson filter,
        final DeleteOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DeleteResult deleteMany(final Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DeleteResult deleteMany(
        final Bson filter,
        final DeleteOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DeleteResult deleteMany(
        final ClientSession clientSession,
        final Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DeleteResult deleteMany(
        final ClientSession clientSession,
        final Bson filter,
        final DeleteOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult replaceOne(
        final Bson filter,
        final DocumentT replacement) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult replaceOne(
        final Bson filter,
        final DocumentT replacement,
        final UpdateOptions updateOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult replaceOne(
        final Bson filter,
        final DocumentT replacement,
        final ReplaceOptions replaceOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult replaceOne(
        final ClientSession clientSession,
        final Bson filter,
        final DocumentT replacement) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult replaceOne(
        final ClientSession clientSession,
        final Bson filter,
        final DocumentT replacement,
        final UpdateOptions updateOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult replaceOne(
        final ClientSession clientSession,
        final Bson filter,
        final DocumentT replacement,
        final ReplaceOptions replaceOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult updateOne(
        final Bson filter,
        final Bson update) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult updateOne(
        final Bson filter,
        final Bson update,
        final UpdateOptions updateOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult updateOne(
        final ClientSession clientSession,
        final Bson filter,
        final Bson update) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult updateOne(
        final ClientSession clientSession,
        final Bson filter,
        final Bson update,
        final UpdateOptions updateOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult updateMany(
        final Bson filter,
        final Bson update) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult updateMany(
        final Bson filter,
        final Bson update,
        final UpdateOptions updateOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult updateMany(
        final ClientSession clientSession,
        final Bson filter,
        final Bson update) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult updateMany(
        final ClientSession clientSession,
        final Bson filter,
        final Bson update,
        final UpdateOptions updateOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndDelete(final Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndDelete(
        final Bson filter,
        final FindOneAndDeleteOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndDelete(
        final ClientSession clientSession,
        final Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndDelete(
        final ClientSession clientSession,
        final Bson filter,
        final FindOneAndDeleteOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndReplace(
        final Bson filter,
        final DocumentT replacement) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndReplace(
        final Bson filter,
        final DocumentT replacement,
        final FindOneAndReplaceOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndReplace(
        final ClientSession clientSession,
        final Bson filter,
        final DocumentT replacement) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndReplace(
        final ClientSession clientSession,
        final Bson filter,
        final DocumentT replacement,
        final FindOneAndReplaceOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndUpdate(
        final Bson filter,
        final Bson update) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndUpdate(
        final Bson filter,
        final Bson update,
        final FindOneAndUpdateOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndUpdate(
        final ClientSession clientSession,
        final Bson filter,
        final Bson update) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndUpdate(
        final ClientSession clientSession,
        final Bson filter,
        final Bson update,
        final FindOneAndUpdateOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void drop() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void drop(final ClientSession clientSession) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String createIndex(final Bson keys) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String createIndex(
        final Bson keys,
        final IndexOptions indexOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String createIndex(
        final ClientSession clientSession,
        final Bson keys) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String createIndex(
        final ClientSession clientSession,
        final Bson keys,
        final IndexOptions indexOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<String> createIndexes(final List<IndexModel> indexes) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<String> createIndexes(
        final List<IndexModel> indexes,
        final CreateIndexOptions createIndexOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<String> createIndexes(
        final ClientSession clientSession,
        final List<IndexModel> indexes) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<String> createIndexes(
        final ClientSession clientSession,
        final List<IndexModel> indexes,
        final CreateIndexOptions createIndexOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ListIndexesIterable<Document> listIndexes() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <ResultT> ListIndexesIterable<ResultT> listIndexes(final Class<ResultT> resultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ListIndexesIterable<Document> listIndexes(final ClientSession clientSession) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <ResultT> ListIndexesIterable<ResultT> listIndexes(
        final ClientSession clientSession,
        final Class<ResultT> resultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndex(final String indexName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndex(
        final String indexName,
        final DropIndexOptions dropIndexOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndex(final Bson keys) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndex(
        final Bson keys,
        final DropIndexOptions dropIndexOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndex(
        final ClientSession clientSession,
        final String indexName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndex(
        final ClientSession clientSession,
        final Bson keys) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndex(
        final ClientSession clientSession,
        final String indexName,
        final DropIndexOptions dropIndexOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndex(
        final ClientSession clientSession,
        final Bson keys,
        final DropIndexOptions dropIndexOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndexes() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndexes(final ClientSession clientSession) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndexes(final DropIndexOptions dropIndexOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndexes(
        final ClientSession clientSession,
        final DropIndexOptions dropIndexOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void renameCollection(final MongoNamespace newCollectionNamespace) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void renameCollection(
        final MongoNamespace newCollectionNamespace,
        final RenameCollectionOptions renameCollectionOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void renameCollection(
        final ClientSession clientSession,
        final MongoNamespace newCollectionNamespace) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void renameCollection(
        final ClientSession clientSession,
        final MongoNamespace newCollectionNamespace,
        final RenameCollectionOptions renameCollectionOptions) {
      throw new UnsupportedOperationException();
    }
  }

}
