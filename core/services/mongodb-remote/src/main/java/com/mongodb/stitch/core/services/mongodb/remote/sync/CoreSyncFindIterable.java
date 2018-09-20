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

package com.mongodb.stitch.core.services.mongodb.remote.sync;

import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoIterable;

import javax.annotation.Nullable;

import org.bson.conversions.Bson;

/**
 * Iterable for find in the context of sync.
 *
 * @param <ResultT> The type of the result.
 */
public interface CoreSyncFindIterable<ResultT> extends CoreRemoteMongoIterable<ResultT> {

  /**
   * Sets the query filter to apply to the query.
   *
   * @param filter the filter, which may be null.
   * @return this
   */
  CoreSyncFindIterable<ResultT> filter(@Nullable final Bson filter);

  /**
   * Sets the limit to apply.
   *
   * @param limit the limit, which may be 0
   * @return this
   */
  CoreSyncFindIterable<ResultT> limit(final int limit);

  /**
   * Sets a document describing the fields to return for all matching documents.
   *
   * @param projection the project document, which may be null.
   * @return this
   */
  CoreSyncFindIterable<ResultT> projection(@Nullable final Bson projection);

  /**
   * Sets the sort criteria to apply to the query.
   *
   * @param sort the sort criteria, which may be null.
   * @return this
   */
  CoreSyncFindIterable<ResultT> sort(@Nullable final Bson sort);
}
