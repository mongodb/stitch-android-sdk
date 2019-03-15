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

import com.mongodb.Block;
import com.mongodb.Function;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;

import java.util.Collection;
import java.util.Iterator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class CoreRemoteMongoIterableImpl<DocumentT, ResultT>
    implements CoreRemoteMongoIterable<ResultT> {

  private final CoreStitchServiceClient service;
  private final Class<ResultT> resultClass;
  private final Operations<DocumentT> operations;

  public CoreRemoteMongoIterableImpl(
      final CoreStitchServiceClient service,
      final Class<ResultT> resultClass,
      final Operations<DocumentT> operations
  ) {
    this.service = service;
    this.resultClass = resultClass;
    this.operations = operations;
  }

  abstract Operation<Iterator<ResultT>> asOperation();

  CoreStitchServiceClient getService() {
    return service;
  }

  Class<ResultT> getResultClass() {
    return resultClass;
  }

  Operations<DocumentT> getOperations() {
    return operations;
  }

  @Override
  @Nonnull
  public CoreRemoteMongoCursor<ResultT> iterator() {
    return new CoreRemoteMongoCursorImpl<>(asOperation().execute(service));
  }

  /**
   * Helper to return the first item in the iterator or null.
   *
   * @return T the first item or null.
   */
  @Nullable
  public ResultT first() {
    final CoreRemoteMongoCursor<ResultT> cursor = iterator();
    if (!cursor.hasNext()) {
      return null;
    }
    return cursor.next();
  }

  /**
   * Maps this iterable from the source document type to the target document type.
   *
   * @param mapper a function that maps from the source to the target document type
   * @param <U> the target document type
   * @return an iterable which maps T to U
   */
  public <U> CoreRemoteMongoIterable<U> map(final Function<ResultT, U> mapper) {
    return new CoreRemoteMappingIterable<>(this, mapper);
  }

  /**
   * Iterates over all documents in the view, applying the given block to each.
   *
   * <p>Similar to {@code map} but the function is fully encapsulated with no returned result.</p>
   *
   * @param block the block to apply to each document of type T.
   */
  public void forEach(final Block<? super ResultT> block) {
    for (final ResultT resultT : this) {
      block.apply(resultT);
    }
  }

  /**
   * Iterates over all the documents, adding each to the given target.
   *
   * @param target the collection to insert into
   * @param <A> the collection type
   * @return the target
   */
  public <A extends Collection<? super ResultT>> A into(final A target) {
    forEach(new Block<ResultT>() {
      @Override
      public void apply(@Nonnull final ResultT t) {
        target.add(t);
      }
    });
    return target;
  }
}
