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

package com.mongodb.stitch.core.services.mongodb.sync;

import com.mongodb.stitch.core.services.mongodb.sync.internal.ChangeEvent;

import org.bson.BsonValue;

/**
 * SyncConflictResolver describes how to resolve a conflict between a local and remote event.
 * @param <DocumentT> the type of document involved in the conflict.
 */
public interface SyncConflictResolver<DocumentT> {

  /**
   * Returns a resolution to the conflict between the given local and remote {@link ChangeEvent}s.
   *
   * @param documentId the document _id that has the conflict.
   * @param localEvent the conflicting local event.
   * @param remoteEvent the conflicting remote event.
   * @return a resolution to the conflict between the given local and remote {@link ChangeEvent}s.
   */
  DocumentT resolveConflict(
      final BsonValue documentId,
      final ChangeEvent<DocumentT> localEvent,
      final ChangeEvent<DocumentT> remoteEvent);
}
