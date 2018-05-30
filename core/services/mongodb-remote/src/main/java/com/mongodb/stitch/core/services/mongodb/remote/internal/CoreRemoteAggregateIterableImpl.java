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

import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import java.util.Collection;
import java.util.List;
import org.bson.conversions.Bson;

class CoreRemoteAggregateIterableImpl<DocumentT, ResultT>
    extends CoreRemoteMongoIterableImpl<DocumentT, ResultT>
    implements CoreRemoteAggregateIterable<ResultT> {

  private final List<? extends Bson> pipeline;

  CoreRemoteAggregateIterableImpl(
      final List<? extends Bson> pipeline,
      final Class<ResultT> resultClass,
      final CoreStitchServiceClient service,
      final Operations<DocumentT> operations
  ) {
    super(service, resultClass, operations);
    this.pipeline = pipeline;
  }

  Operation<Collection<ResultT>> asOperation() {
    return getOperations().aggregate(pipeline, geResultClass());
  }
}
