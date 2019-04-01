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

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.services.mongodb.remote.ChangeEvent;

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
   * Returns the namespace of the document being synchronized.
   *
   * @return the namespace of the document being synchronized.
   */
  MongoNamespace getNamespace();

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
   * Returns the last uncommitted change event for this document (i.e. pending write).
   *
   * @return the last uncommitted change event.
   */
  ChangeEvent<BsonDocument> getLastUncommittedChangeEvent();

  /**
   * Returns whether or not this document is paused for synchronization.
   *
   * @return true if the document is paused, false otherwise.
   */
  boolean isPaused();

  /**
   * Pauses synchronization for the document represented by this configuration.
   * @param paused state to set the paused flag.
   */
  void setPaused(boolean paused);

  /**
   * Returns whether or not this document is known to be stale.
   *
   * @return true if the document is stale, false otherwise.
   */
  boolean isStale();

  /**
   * Marks the local document represented by this configuration as stale.
   * @param stale state to set the stale flag.
   */
  void setStale(boolean stale);

  /**
   * Returns the document configuration lock so that synchronization processes can prevent
   * concurrent changes while relying on its state.
   */
  ReadWriteLock getLock();

  /**
   * Stages a pending write to the document configuration in memory.
   *
   * @param atTime      logical time at which the writes occurred.
   * @param atVersion   the version of the document.
   * @param changeEvent the {@link ChangeEvent} representing the actual change.
   */
  void setSomePendingWrites(final long atTime,
                            final BsonDocument atVersion,
                            final ChangeEvent<BsonDocument> changeEvent);

  /**
   * Stages a pending write to the document configuration and saves to the local document
   * configuration store.
   *
   * @param atTime      logical time at which the writes occurred.
   * @param atVersion   the version of the document.
   * @param changeEvent the {@link ChangeEvent} representing the actual change.
   */
  void setSomePendingWritesAndSave(final long atTime,
                                   final BsonDocument atVersion,
                                   final ChangeEvent<BsonDocument> changeEvent);

  /**
   * Stages a pending write to the document configuration and saves to the local document
   * configuration store. This variant maintains the existing version on the document sync config.
   *
   * @param atTime      logical time at which the writes occured.
   * @param changeEvent the {@link ChangeEvent} representing the actual change.
   */
  void setSomePendingWritesAndSave(final long atTime,
                                   final ChangeEvent<BsonDocument> changeEvent);

  /**
   * Indicates that a pending write was successful as of a particular document version.
   *
   * @param atVersion new version of the document.
   */
  void setPendingWritesComplete(BsonDocument atVersion);
}
