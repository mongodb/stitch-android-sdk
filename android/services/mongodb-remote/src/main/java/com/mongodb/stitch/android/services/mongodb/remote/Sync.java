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

package com.mongodb.stitch.android.services.mongodb.remote;

import android.support.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ErrorListener;

import java.util.Set;

import org.bson.BsonValue;
import org.bson.conversions.Bson;

/**
 * A set of synchronization related operations for a collection.
 *
 * @param <DocumentT> the type of document we are syncing on
 */
public interface Sync<DocumentT> {
  /**
   * Set the conflict handler and and change event listener on this collection.
   * @param conflictHandler the conflict resolver to invoke when a conflict happens between local
   *                         and remote events.
   * @param changeEventListener the event listener to invoke when a change event happens for the
   *                         document.
   * @param errorListener the error listener to invoke when an irrecoverable error occurs
   */
  void configure(@NonNull final ConflictHandler<DocumentT> conflictHandler,
                 final ChangeEventListener<DocumentT> changeEventListener,
                 final ErrorListener errorListener);

  /**
   * Requests that the given document _id be synchronized.
   * @param id the document _id to synchronize.
   */
  void syncOne(final BsonValue id);

  /**
   * Requests that the given document _ids be synchronized.
   * @param ids the document _ids to synchronize.
   */
  void syncMany(final BsonValue... ids);

  /**
   * Stops synchronizing the given document _id. Any uncommitted writes will be lost.
   *
   * @param id the _id of the document to desynchronize.
   */
  void desyncOne(final BsonValue id);

  /**
   * Stops synchronizing the given document _ids. Any uncommitted writes will be lost.
   *
   * @param ids the _ids of the documents to desynchronize.
   */
  void desyncMany(final BsonValue... ids);

  /**
   * Returns the set of synchronized document ids in a namespace.
   *
   * @return the set of synchronized document ids in a namespace.
   */
  Set<BsonValue> getSyncedIds();

  /**
   * Finds all documents in the collection that have been synchronized from the remote.
   *
   * @return the find iterable interface
   */
  SyncFindIterable<DocumentT> find();

  /**
   * Finds all documents in the collection that have been synchronized from the remote.
   *
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the find iterable interface
   */
  <ResultT> SyncFindIterable<ResultT> find(final Class<ResultT> resultClass);

  /**
   * Finds all documents in the collection that have been synchronized from the remote.
   *
   * @param filter the query filter
   * @return the find iterable interface
   */
  SyncFindIterable<DocumentT> find(final Bson filter);

  /**
   * Finds all documents in the collection that have been synchronized from the remote.
   *
   * @param filter      the query filter
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the find iterable interface
   */
  <ResultT> SyncFindIterable<ResultT> find(final Bson filter, final Class<ResultT> resultClass);

  /**
   * Finds a single document by the given id. It is first searched for in the local synchronized
   * cache and if not found and there is internet connectivity, it is searched for remotely.
   *
   * @param documentId the _id of the document to search for.
   * @return a task containing the document if found locally or remotely.
   */
  Task<DocumentT> findOneById(final BsonValue documentId);

  /**
   * Finds a single document by the given id. It is first searched for in the local synchronized
   * cache and if not found and there is internet connectivity, it is searched for remotely.
   *
   * @param documentId the _id of the document to search for.
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return a task containing the document if found locally or remotely.
   */
  <ResultT> Task<ResultT> findOneById(final BsonValue documentId, final Class<ResultT> resultClass);

  /**
   * Updates a document by the given id. It is first searched for in the local synchronized cache
   * and if not found and there is internet connectivity, it is searched for remotely.
   *
   * @param documentId the _id of the document to search for.
   * @param update the update specifier.
   * @return a task containing the result of the local or remote update.
   */
  Task<RemoteUpdateResult> updateOneById(final BsonValue documentId, final Bson update);

  /**
   * Inserts a single document and begins to synchronize it.
   *
   * @param document the document to insert and synchronize.
   * @return the result of the insertion.
   */
  Task<RemoteInsertOneResult> insertOneAndSync(final DocumentT document);

  /**
   * Deletes a single document by the given id. It is first searched for in the local synchronized
   * cache and if not found and there is internet connectivity, it is searched for remotely.
   *
   * @param documentId the _id of the document to search for.
   * @return a task containing the result of the local or remote update.
   */
  Task<RemoteDeleteResult> deleteOneById(final BsonValue documentId);
}
