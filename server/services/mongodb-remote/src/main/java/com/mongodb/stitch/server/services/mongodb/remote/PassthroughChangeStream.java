/*
 * Copyright 2018-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.stitch.server.services.mongodb.remote;

import com.mongodb.stitch.core.internal.net.StitchEvent;
import com.mongodb.stitch.core.internal.net.Stream;
import com.mongodb.stitch.core.services.mongodb.remote.BaseChangeEvent;
import com.mongodb.stitch.core.services.mongodb.remote.ChangeStream;

import java.io.IOException;

/**
 * Simple {@link ChangeStream} implementation that unwraps and returns the same change event
 * provided on the internal {@link StitchEvent}.
 * @param <DocumentT> The type of full document on the change event.
 * @param <ChangeEventT> The type of MongoDB change event that this stream internally returns.
 */
public class PassthroughChangeStream<
    DocumentT, ChangeEventT extends BaseChangeEvent<DocumentT>
> extends
    ChangeStream<ChangeEventT, ChangeEventT> {
  /**
   * Initializes a passthrough change stream with the provided underlying event stream.
   * @param stream The event stream.
   */
  public PassthroughChangeStream(final Stream<ChangeEventT> stream) {
    super(stream);
  }

  @Override
  public ChangeEventT nextEvent() throws IOException {
    final StitchEvent<ChangeEventT> nextEvent = getStream().nextEvent();

    if (nextEvent == null) {
      return null;
    }

    if (nextEvent.getError() != null) {
      dispatchError(nextEvent);
      return null;
    }

    return nextEvent.getData();
  }
}
