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
import com.mongodb.Block;
import com.mongodb.Function;
import java.util.Collection;

/**
 * The RemoteMongoIterable is the results from an operation, such as a query.
 *
 * @param <ResultT> The type that this iterable will decode documents to.
 */
public interface RemoteMongoIterable<ResultT> {

  /**
   * Returns a cursor of the operation represented by this iterable.
   *
   * @return a cursor of the operation represented by this iterable.
   */
  @NonNull
  RemoteMongoCursor<ResultT> iterator();

  /**
   * Helper to return the first item in the iterator or null.
   *
   * @return a task containing the first item or null.
   */
  @NonNull
  Task<ResultT> first();

  /**
   * Maps this iterable from the source document type to the target document type.
   *
   * @param mapper a function that maps from the source to the target document type
   * @param <U> the target document type
   * @return an iterable which maps T to U
   */
  <U> RemoteMongoIterable<U> map(Function<ResultT, U> mapper);

  /**
   * Iterates over all documents in the view, applying the given block to each.
   *
   * <p>Similar to {@code map} but the function is fully encapsulated with no returned result.</p>
   *
   * @param block the block to apply to each document of type T.
   * @return a task that completes when the iteration over all documents has completed.
   */
  Task<Void> forEach(Block<? super ResultT> block);

  /**
   * Iterates over all the documents, adding each to the given target.
   *
   * @param target the collection to insert into
   * @param <A> the collection type
   * @return a task containing the target.
   */
  <A extends Collection<? super ResultT>> Task<A> into(A target);
}
