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

import java.util.concurrent.locks.ReadWriteLock;

import org.bson.BsonDocument;
import org.bson.BsonValue;

/**
 * The synchronization configuration for a document.
 */
public interface DocumentSynchronizationConfig {

  /**
   * Returns the _id of the document being synchronized.
   *
   * @return the _id of the document being synchronized.
   */
  BsonValue getDocumentId();

  /**
   * Returns whether or not this document has pending writes that have not yet been committed
   * remotely.
   *
   * @return whether or not this document has pending writes that have not yet been committed
   *         remotely.
   */
  boolean hasUncommittedWrites();

  /**
   * Returns the last logical time at which.
   *
   * @return the last logical resolution time.
   */
  long getLastResolution();

  /**
   * Returns the {@link BsonDocument} representing the version information for the last
   * previously seen remote change event.
   *
   * @return the last seen version information.
   */
  BsonDocument getLastKnownRemoteVersion();

  /**
   * Returns the document configuration lock so that synchronization processes can prevent
   * concurrent changes while relying on its state.
   */
  ReadWriteLock getLock();
}
