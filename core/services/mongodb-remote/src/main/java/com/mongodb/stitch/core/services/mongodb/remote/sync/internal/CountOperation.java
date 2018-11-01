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
import com.mongodb.client.model.CountOptions;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.internal.Operation;

import javax.annotation.Nullable;

import org.bson.conversions.Bson;

class CountOperation implements Operation<Long> {
  private final MongoNamespace namespace;
  private final DataSynchronizer dataSynchronizer;
  private final Bson filter;
  private final CountOptions countOptions;

  CountOperation(
      final MongoNamespace namespace,
      final DataSynchronizer dataSynchronizer,
      final Bson filter,
      final CountOptions countOptions
  ) {
    this.namespace = namespace;
    this.dataSynchronizer = dataSynchronizer;;
    this.filter = filter;
    this.countOptions = countOptions;
  }

  public Long execute(@Nullable final CoreStitchServiceClient service) {
    return this.dataSynchronizer.count(namespace, filter, countOptions);
  }
}
