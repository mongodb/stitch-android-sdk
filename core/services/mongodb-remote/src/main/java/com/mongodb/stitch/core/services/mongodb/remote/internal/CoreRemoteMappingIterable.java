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

import java.util.Collection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CoreRemoteMappingIterable<U, V> implements CoreRemoteMongoIterable<V> {

  private final CoreRemoteMongoIterable<U> iterable;
  private final Function<U, V> mapper;

  public CoreRemoteMappingIterable(
      final CoreRemoteMongoIterable<U> iterable,
      final Function<U, V> mapper
  ) {
    this.iterable = iterable;
    this.mapper = mapper;
  }

  @Override
  @Nonnull
  public CoreRemoteMongoCursor<V> iterator() {
    return new CoreRemoteMongoMappingCursor<>(iterable.iterator(), mapper);
  }

  @Nullable
  @Override
  public V first() {
    final CoreRemoteMongoCursor<V> iterator = iterator();
    if (!iterator.hasNext()) {
      return null;
    }
    return iterator.next();
  }

  @Override
  public void forEach(final Block<? super V> block) {
    iterable.forEach(new Block<U>() {
      @Override
      public void apply(@Nonnull final U document) {
        block.apply(mapper.apply(document));
      }
    });
  }

  @Override
  public <A extends Collection<? super V>> A into(final A target) {
    forEach(new Block<V>() {
      @Override
      public void apply(@Nonnull final V v) {
        target.add(v);
      }
    });
    return target;
  }

  @Override
  public <W> CoreRemoteMongoIterable<W> map(final Function<V, W> newMap) {
    return new CoreRemoteMappingIterable<>(this, newMap);
  }
}
