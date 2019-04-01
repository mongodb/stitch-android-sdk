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

import java.util.ArrayList;
import java.util.List;

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

    MongoCollectionFacade(CoreRemoteMongoCollection<DocumentT> collection) {
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
    public <NewTDocument> MongoCollection<NewTDocument> withDocumentClass(Class<NewTDocument> clazz) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MongoCollection<DocumentT> withCodecRegistry(CodecRegistry codecRegistry) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MongoCollection<DocumentT> withReadPreference(ReadPreference readPreference) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MongoCollection<DocumentT> withWriteConcern(WriteConcern writeConcern) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MongoCollection<DocumentT> withReadConcern(ReadConcern readConcern) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long count() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long count(Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long count(
        Bson filter,
        CountOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long count(ClientSession clientSession) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long count(
        ClientSession clientSession,
        Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long count(
        ClientSession clientSession,
        Bson filter,
        CountOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long countDocuments() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long countDocuments(Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long countDocuments(
        Bson filter,
        CountOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long countDocuments(ClientSession clientSession) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long countDocuments(
        ClientSession clientSession,
        Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long countDocuments(
        ClientSession clientSession,
        Bson filter,
        CountOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long estimatedDocumentCount() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long estimatedDocumentCount(EstimatedDocumentCountOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <TResult> DistinctIterable<TResult> distinct(
        String fieldName,
        Class<TResult> tResultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <TResult> DistinctIterable<TResult> distinct(
        String fieldName,
        Bson filter,
        Class<TResult> tResultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <TResult> DistinctIterable<TResult> distinct(
        ClientSession clientSession,
        String fieldName,
        Class<TResult> tResultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <TResult> DistinctIterable<TResult> distinct(
        ClientSession clientSession,
        String fieldName,
        Bson filter,
        Class<TResult> tResultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public FindIterable<DocumentT> find() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <TResult> FindIterable<TResult> find(Class<TResult> tResultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public FindIterable<DocumentT> find(Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <TResult> FindIterable<TResult> find(
        Bson filter,
        Class<TResult> tResultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public FindIterable<DocumentT> find(ClientSession clientSession) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <TResult> FindIterable<TResult> find(
        ClientSession clientSession,
        Class<TResult> tResultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public FindIterable<DocumentT> find(
        ClientSession clientSession,
        Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <TResult> FindIterable<TResult> find(
        ClientSession clientSession,
        Bson filter,
        Class<TResult> tResultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public AggregateIterable<DocumentT> aggregate(List<? extends Bson> pipeline) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <TResult> AggregateIterable<TResult> aggregate(
        List<? extends Bson> pipeline,
        Class<TResult> tResultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public AggregateIterable<DocumentT> aggregate(
        ClientSession clientSession,
        List<? extends Bson> pipeline) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <TResult> AggregateIterable<TResult> aggregate(
        ClientSession clientSession,
        List<? extends Bson> pipeline,
        Class<TResult> tResultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ChangeStreamIterable<DocumentT> watch() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(Class<TResult> tResultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ChangeStreamIterable<DocumentT> watch(List<? extends Bson> pipeline) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(
        List<? extends Bson> pipeline,
        Class<TResult> tResultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ChangeStreamIterable<DocumentT> watch(ClientSession clientSession) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(
        ClientSession clientSession,
        Class<TResult> tResultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ChangeStreamIterable<DocumentT> watch(
        ClientSession clientSession,
        List<? extends Bson> pipeline) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(
        ClientSession clientSession,
        List<? extends Bson> pipeline,
        Class<TResult> tResultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MapReduceIterable<DocumentT> mapReduce(
        String mapFunction,
        String reduceFunction) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <TResult> MapReduceIterable<TResult> mapReduce(
        String mapFunction,
        String reduceFunction,
        Class<TResult> tResultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MapReduceIterable<DocumentT> mapReduce(
        ClientSession clientSession,
        String mapFunction,
        String reduceFunction) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <TResult> MapReduceIterable<TResult> mapReduce(
        ClientSession clientSession,
        String mapFunction,
        String reduceFunction,
        Class<TResult> tResultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public BulkWriteResult bulkWrite(List<? extends WriteModel<? extends DocumentT>> requests) {
      for (WriteModel<? extends DocumentT> write : requests) {
        if (write instanceof ReplaceOneModel) {
          ReplaceOneModel<DocumentT> replaceModel = ((ReplaceOneModel) write);
          RemoteUpdateResult result = this.collection.updateOne(replaceModel.getFilter(),
              (Bson) replaceModel.getReplacement());
        } else if (write instanceof UpdateOneModel) {
          UpdateOneModel<DocumentT> updateModel = ((UpdateOneModel) write);
          RemoteUpdateResult result = this.collection.updateOne(updateModel.getFilter(),
              updateModel.getUpdate());
        } else if (write instanceof UpdateManyModel) {
          UpdateManyModel<DocumentT> updateModel = ((UpdateManyModel) write);
          RemoteUpdateResult result = this.collection.updateMany(updateModel.getFilter(),
              updateModel.getUpdate());
        }
      }
      return null; // no one using this result
    }

    @Override
    public BulkWriteResult bulkWrite(
        List<? extends WriteModel<? extends DocumentT>> requests,
        BulkWriteOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public BulkWriteResult bulkWrite(
        ClientSession clientSession,
        List<? extends WriteModel<? extends DocumentT>> requests) {
      throw new UnsupportedOperationException();
    }

    @Override
    public BulkWriteResult bulkWrite(
        ClientSession clientSession,
        List<? extends WriteModel<? extends DocumentT>> requests,
        BulkWriteOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void insertOne(DocumentT documentT) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void insertOne(
        DocumentT documentT,
        InsertOneOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void insertOne(
        ClientSession clientSession,
        DocumentT documentT) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void insertOne(
        ClientSession clientSession,
        DocumentT documentT,
        InsertOneOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void insertMany(List<? extends DocumentT> documentTS) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void insertMany(
        List<? extends DocumentT> documentTS,
        InsertManyOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void insertMany(
        ClientSession clientSession,
        List<? extends DocumentT> documentTS) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void insertMany(
        ClientSession clientSession,
        List<? extends DocumentT> documentTS,
        InsertManyOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DeleteResult deleteOne(Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DeleteResult deleteOne(
        Bson filter,
        DeleteOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DeleteResult deleteOne(
        ClientSession clientSession,
        Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DeleteResult deleteOne(
        ClientSession clientSession,
        Bson filter,
        DeleteOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DeleteResult deleteMany(Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DeleteResult deleteMany(
        Bson filter,
        DeleteOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DeleteResult deleteMany(
        ClientSession clientSession,
        Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DeleteResult deleteMany(
        ClientSession clientSession,
        Bson filter,
        DeleteOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult replaceOne(
        Bson filter,
        DocumentT replacement) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult replaceOne(
        Bson filter,
        DocumentT replacement,
        UpdateOptions updateOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult replaceOne(
        Bson filter,
        DocumentT replacement,
        ReplaceOptions replaceOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult replaceOne(
        ClientSession clientSession,
        Bson filter,
        DocumentT replacement) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult replaceOne(
        ClientSession clientSession,
        Bson filter,
        DocumentT replacement,
        UpdateOptions updateOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult replaceOne(
        ClientSession clientSession,
        Bson filter,
        DocumentT replacement,
        ReplaceOptions replaceOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult updateOne(
        Bson filter,
        Bson update) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult updateOne(
        Bson filter,
        Bson update,
        UpdateOptions updateOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult updateOne(
        ClientSession clientSession,
        Bson filter,
        Bson update) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult updateOne(
        ClientSession clientSession,
        Bson filter,
        Bson update,
        UpdateOptions updateOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult updateMany(
        Bson filter,
        Bson update) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult updateMany(
        Bson filter,
        Bson update,
        UpdateOptions updateOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult updateMany(
        ClientSession clientSession,
        Bson filter,
        Bson update) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UpdateResult updateMany(
        ClientSession clientSession,
        Bson filter,
        Bson update,
        UpdateOptions updateOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndDelete(Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndDelete(
        Bson filter,
        FindOneAndDeleteOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndDelete(
        ClientSession clientSession,
        Bson filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndDelete(
        ClientSession clientSession,
        Bson filter,
        FindOneAndDeleteOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndReplace(
        Bson filter,
        DocumentT replacement) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndReplace(
        Bson filter,
        DocumentT replacement,
        FindOneAndReplaceOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndReplace(
        ClientSession clientSession,
        Bson filter,
        DocumentT replacement) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndReplace(
        ClientSession clientSession,
        Bson filter,
        DocumentT replacement,
        FindOneAndReplaceOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndUpdate(
        Bson filter,
        Bson update) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndUpdate(
        Bson filter,
        Bson update,
        FindOneAndUpdateOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndUpdate(
        ClientSession clientSession,
        Bson filter,
        Bson update) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocumentT findOneAndUpdate(
        ClientSession clientSession,
        Bson filter,
        Bson update,
        FindOneAndUpdateOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void drop() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void drop(ClientSession clientSession) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String createIndex(Bson keys) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String createIndex(
        Bson keys,
        IndexOptions indexOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String createIndex(
        ClientSession clientSession,
        Bson keys) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String createIndex(
        ClientSession clientSession,
        Bson keys,
        IndexOptions indexOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<String> createIndexes(List<IndexModel> indexes) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<String> createIndexes(
        List<IndexModel> indexes,
        CreateIndexOptions createIndexOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<String> createIndexes(
        ClientSession clientSession,
        List<IndexModel> indexes) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<String> createIndexes(
        ClientSession clientSession,
        List<IndexModel> indexes,
        CreateIndexOptions createIndexOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ListIndexesIterable<Document> listIndexes() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <TResult> ListIndexesIterable<TResult> listIndexes(Class<TResult> tResultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ListIndexesIterable<Document> listIndexes(ClientSession clientSession) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <TResult> ListIndexesIterable<TResult> listIndexes(
        ClientSession clientSession,
        Class<TResult> tResultClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndex(String indexName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndex(
        String indexName,
        DropIndexOptions dropIndexOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndex(Bson keys) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndex(
        Bson keys,
        DropIndexOptions dropIndexOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndex(
        ClientSession clientSession,
        String indexName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndex(
        ClientSession clientSession,
        Bson keys) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndex(
        ClientSession clientSession,
        String indexName,
        DropIndexOptions dropIndexOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndex(
        ClientSession clientSession,
        Bson keys,
        DropIndexOptions dropIndexOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndexes() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndexes(ClientSession clientSession) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndexes(DropIndexOptions dropIndexOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dropIndexes(
        ClientSession clientSession,
        DropIndexOptions dropIndexOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void renameCollection(MongoNamespace newCollectionNamespace) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void renameCollection(
        MongoNamespace newCollectionNamespace,
        RenameCollectionOptions renameCollectionOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void renameCollection(
        ClientSession clientSession,
        MongoNamespace newCollectionNamespace) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void renameCollection(
        ClientSession clientSession,
        MongoNamespace newCollectionNamespace,
        RenameCollectionOptions renameCollectionOptions) {
      throw new UnsupportedOperationException();
    }
  }

}
