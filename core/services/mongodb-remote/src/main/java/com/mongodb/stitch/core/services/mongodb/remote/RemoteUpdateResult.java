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

package com.mongodb.stitch.core.services.mongodb.remote;

import javax.annotation.Nullable;

import org.bson.BsonValue;

public class RemoteUpdateResult {

  private final long matchedCount;
  private final BsonValue upsertedId;

  public RemoteUpdateResult(final long matchedCount, final BsonValue upsertedId) {
    this.matchedCount = matchedCount;
    this.upsertedId = upsertedId;
  }

  /**
   * Gets the number of documents matched by the query.
   *
   * @return the number of documents matched
   */
  public long getMatchedCount() {
    return matchedCount;
  }

  /**
   * If the replace resulted in an inserted document, gets the _id of the inserted document,
   * otherwise null.
   *
   * @return if the replace resulted in an inserted document, the _id of the inserted document,
   *         otherwise null.
   */
  @Nullable
  public BsonValue getUpsertedId() {
    return upsertedId;
  }
}
