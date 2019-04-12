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

import org.bson.conversions.Bson;

/**
 * Contract for a class that handles failures from within a {@link WriteModelContainer}.
 */
public interface FailedOperationListener {
  /**
   * Handler for a failed update operation within a {@link WriteModelContainer}.
   *
   * @param filter the filter from the write model that failed.
   * @param update the update from the write model that failed.
   */
  default void onFailedRemoteUpdate(Bson filter, Bson update) {
    // no-op
  }

  /**
   * Handler for a failed replace operation within a {@link WriteModelContainer}.
   *
   * @param filter   the filter from the write model that failed.
   * @param document the replacemet from the write model that failed.
   */
  default void onFailedRemoteReplace(Bson filter, Bson document) {
    // no-op
  }

  /**
   * Handler for a failed insert operation within a {@link WriteModelContainer}.
   *
   * @param document the document that failed to insert.
   */
  default void onFailedRemoteInsert(Bson document) {
    // no-op
  }

  /**
   * Handler for a failed delete operation within a {@link WriteModelContainer}.
   *
   * @param filter   the filter from the write model that failed.
   */
  default void onFailedRemoteDelete(Bson filter) {
    // no-op
  }
}
