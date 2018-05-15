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

import static com.mongodb.stitch.core.internal.common.Assertions.notNull;

import com.mongodb.Function;
import java.io.IOException;

class CoreRemoteMongoMappingCursor<T, U> implements CoreRemoteMongoCursor<U> {
  private final CoreRemoteMongoCursor<T> proxied;
  private final Function<T, U> mapper;

  CoreRemoteMongoMappingCursor(
      final CoreRemoteMongoCursor<T> proxied,
      final Function<T, U> mapper
  ) {
    notNull("proxied", proxied);
    notNull("mapper", mapper);
    this.proxied = proxied;
    this.mapper = mapper;
  }

  @Override
  public boolean hasNext() {
    return proxied.hasNext();
  }

  @Override
  public U next() {
    return mapper.apply(proxied.next());
  }

  @Override
  public void remove() {
    proxied.remove();
  }

  @Override
  public void close() throws IOException {
    proxied.close();
  }
}
