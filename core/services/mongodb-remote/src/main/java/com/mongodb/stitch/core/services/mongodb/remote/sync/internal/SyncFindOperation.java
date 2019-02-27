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
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.internal.Operation;
import java.util.Collection;
import org.bson.BsonDocument;

class  SyncFindOperation<T> implements Operation<Collection<T>> {

  private final MongoNamespace namespace;
  private final Class<T> resultClass;
  private final DataSynchronizer dataSynchronizer;

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
      final Class<T> resultClass,
      final DataSynchronizer dataSynchronizer
  ) {
    this.namespace = namespace;
    this.resultClass = resultClass;
    this.dataSynchronizer = dataSynchronizer;
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
    return this.dataSynchronizer
        .find(namespace, filter, limit, projection, sort, resultClass, service.getCodecRegistry());
  }
}
