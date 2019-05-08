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

package com.mongodb.stitch.core.services.mongodb.remote;

import com.mongodb.stitch.core.internal.net.Stream;
import com.mongodb.stitch.core.services.mongodb.remote.sync.CompactChangeEventListener;
import java.io.IOException;
import java.util.ArrayList;

/**
 * User-level abstraction for a stream returning {@link ChangeEvent}s from the server.
 *
 * @param <DocumentT>  The type of document in the ChangeEvent
 * @param <NextEventT> The type returned to users of this API when the next event is requested. May be
 *                     the same as ChangeEventT, or may be an async wrapper around ChangeEventT.
 */
public abstract class CompactChangeStream<DocumentT, NextEventT> extends BaseChangeStream<
    DocumentT,
    NextEventT,
    CompactChangeEvent<DocumentT>,
    CompactChangeEventListener<DocumentT>> {

  protected CompactChangeStream(final Stream<CompactChangeEvent<DocumentT>> stream) {
    super(stream);
    this.listeners = new ArrayList<CompactChangeEventListener<DocumentT>>();
  }

  /**
   * Adds a ChangeEventListener to the ChangeStream.
   *
   * @param listener the ChangeEventListener
   * @return The current ChangeStream
   */
  public abstract void addChangeEventListener(CompactChangeEventListener<DocumentT> listener);

  /**
   * Remove a ChangeEventListener from the ChangeStream.
   *
   * @param listener the ChangeEventListener
   * @return The current ChangeStream
   */
  public abstract void removeChangeEventListener(CompactChangeEventListener<DocumentT> listener);

  /**
   * Returns the next event available from the stream.
   *
   * @return The next event.
   * @throws IOException If the underlying stream throws an {@link IOException}
   */
  @Override
  public abstract NextEventT nextEvent() throws IOException;
}
