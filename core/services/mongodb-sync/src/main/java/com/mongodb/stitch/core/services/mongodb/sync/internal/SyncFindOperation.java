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

package com.mongodb.stitch.core.services.mongodb.sync.internal;

import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoCollection;
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.internal.FindOperation;
import com.mongodb.stitch.core.services.mongodb.remote.internal.Operation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import org.bson.BsonDocument;
import org.bson.BsonValue;

class SyncFindOperation<T> implements Operation<Collection<T>> {

  private final MongoNamespace namespace;
  private final FindOperation<BsonDocument> remoteFindOp;
  private final Class<T> resultClass;
  private final DataSynchronizer dataSynchronizer;
  private final NetworkMonitor networkMonitor;
  private final MongoCollection<BsonDocument> tempCollection;
  private BsonDocument filter;
  private int limit;
  private BsonDocument projection;
  private BsonDocument sort;

  /**
   * Construct a new instance.
   *
   * @param namespace the database and collection namespace for the operation.
   */
  SyncFindOperation(
      final MongoNamespace namespace,
      final FindOperation<BsonDocument> remoteFindOp,
      final Class<T> resultClass,
      final DataSynchronizer dataSynchronizer,
      final NetworkMonitor networkMonitor,
      final MongoCollection<BsonDocument> tempCollection
  ) {
    this.remoteFindOp = remoteFindOp;
    this.namespace = namespace;
    this.resultClass = resultClass;
    this.dataSynchronizer = dataSynchronizer;
    this.networkMonitor = networkMonitor;
    this.tempCollection = tempCollection;
  }

  /**
   * Sets the query filter to apply to the query.
   *
   * @param filter the filter, which may be null.
   * @return this
   */
  SyncFindOperation<T> filter(final BsonDocument filter) {
    this.filter = filter;
    return this;
  }

  /**
   * Sets the limit to apply.
   *
   * @param limit the limit, which may be null
   * @return this
   */
  SyncFindOperation<T> limit(final int limit) {
    this.limit = limit;
    return this;
  }

  /**
   * Sets a document describing the fields to return for all matching documents.
   *
   * @param projection the project document, which may be null.
   * @return this
   */
  SyncFindOperation<T> projection(final BsonDocument projection) {
    this.projection = projection;
    return this;
  }

  /**
   * Sets the sort criteria to apply to the query.
   *
   * @param sort the sort criteria, which may be null.
   * @return this
   */
  SyncFindOperation<T> sort(final BsonDocument sort) {
    this.sort = sort;
    return this;
  }

  @Override
  public Collection<T> execute(final CoreStitchServiceClient service) {
    final Collection<T> localResult = this.dataSynchronizer
        .find(namespace, filter, limit, projection, sort, resultClass, service.getCodecRegistry());
    if (!this.networkMonitor.isConnected()) {
      return localResult;
    }

    final Set<BsonValue> syncedIds =
        this.dataSynchronizer.getSynchronizedDocumentIds(namespace);
    final Collection<BsonDocument> remoteResult = remoteFindOp.projection(null).execute(service);

    try {
      for (final BsonDocument doc : remoteResult) {
        if (syncedIds.contains(BsonUtils.getDocumentId(doc))) {
          continue;
        }
        tempCollection.insertOne(doc);
      }
      final MongoCollection<T> userColl = tempCollection
          .withDocumentClass(resultClass)
          .withCodecRegistry(service.getCodecRegistry());
      for (final T localDoc : localResult) {
        userColl.insertOne(localDoc);
      }
      return userColl.find(filter)
          .limit(limit)
          .projection(projection)
          .sort(sort)
          .into(new ArrayList<T>());
    } finally {
      tempCollection.drop();
    }
  }
}
