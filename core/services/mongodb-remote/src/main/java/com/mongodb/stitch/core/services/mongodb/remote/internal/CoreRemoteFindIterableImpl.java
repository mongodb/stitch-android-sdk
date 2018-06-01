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

package com.mongodb.stitch.core.services.mongodb.remote.internal;

import static com.mongodb.stitch.core.internal.common.Assertions.notNull;

import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteFindOptions;
import java.util.Collection;
import java.util.Iterator;
import javax.annotation.Nullable;
import org.bson.conversions.Bson;

public class CoreRemoteFindIterableImpl<DocumentT, ResultT>
    extends CoreRemoteMongoIterableImpl<DocumentT, ResultT>
    implements CoreRemoteFindIterable<ResultT> {

  private final RemoteFindOptions findOptions;

  private Bson filter;

  public CoreRemoteFindIterableImpl(
      final Bson filter,
      final Class<ResultT> resultClass,
      final CoreStitchServiceClient service,
      final Operations<DocumentT> operations
  ) {
    super(service, resultClass, operations);
    notNull("filter", filter);
    this.filter = filter;
    this.findOptions = new RemoteFindOptions();
  }

  /**
   * Sets the query filter to apply to the query.
   *
   * @param filter the filter, which may be null.
   * @return this
   */
  public CoreRemoteFindIterableImpl<DocumentT, ResultT> filter(@Nullable final Bson filter) {
    this.filter = filter;
    return this;
  }

  /**
   * Sets the limit to apply.
   *
   * @param limit the limit, which may be 0
   * @return this
   */
  public CoreRemoteFindIterableImpl<DocumentT, ResultT> limit(final int limit) {
    findOptions.limit(limit);
    return this;
  }

  /**
   * Sets a document describing the fields to return for all matching documents.
   *
   * @param projection the project document, which may be null.
   * @return this
   */
  public CoreRemoteFindIterableImpl<DocumentT, ResultT> projection(
      @Nullable final Bson projection
  ) {
    findOptions.projection(projection);
    return this;
  }

  /**
   * Sets the sort criteria to apply to the query.
   *
   * @param sort the sort criteria, which may be null.
   * @return this
   */
  public CoreRemoteFindIterableImpl<DocumentT, ResultT> sort(@Nullable final Bson sort) {
    findOptions.sort(sort);
    return this;
  }

  @Nullable
  @Override
  public ResultT first() {
    final Iterator<ResultT> iter = getOperations()
        .findFirst(filter, geResultClass(), findOptions)
        .execute(getService())
        .iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  @Override
  Operation<Collection<ResultT>> asOperation() {
    return getOperations().find(filter, geResultClass(), findOptions);
  }
}
