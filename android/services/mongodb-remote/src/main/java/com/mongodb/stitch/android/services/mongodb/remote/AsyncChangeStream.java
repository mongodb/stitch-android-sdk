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

package com.mongodb.stitch.android.services.mongodb.remote;

import com.google.android.gms.tasks.Task;

import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.core.internal.net.StitchEvent;
import com.mongodb.stitch.core.internal.net.Stream;
import com.mongodb.stitch.core.services.mongodb.remote.BaseChangeEvent;
import com.mongodb.stitch.core.services.mongodb.remote.ChangeEvent;
import com.mongodb.stitch.core.services.mongodb.remote.ChangeStream;

import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * An implementation of {@link com.mongodb.stitch.core.services.mongodb.remote.ChangeStream} that
 * returns each event as a {@link Task}.
 *
 * @param <DocumentT> The type of the full document on the underlying change event to be returned
 *                   asynchronously.
 */
public class AsyncChangeStream<DocumentT, ChangeEventT extends BaseChangeEvent<DocumentT>> extends
    ChangeStream<Task<ChangeEventT>, ChangeEventT> {
  private final TaskDispatcher dispatcher;

  /**
   * Initializes a passthrough change stream with the provided underlying event stream.
   *
   * @param stream The event stream.
   * @param dispatcher The event dispatcher.
   */
  public AsyncChangeStream(final Stream<ChangeEventT> stream,
                           final TaskDispatcher dispatcher) {
    super(stream);
    this.dispatcher = dispatcher;
  }

  /**
   * Returns a {@link Task} whose resolution gives the next event from the underlying stream.
   * @return task providing the next event
   * @throws IOException if the underlying stream throws an {@link IOException}
   */
  @Override
  public Task<ChangeEventT> nextEvent() throws IOException {
    return dispatcher.dispatchTask(new Callable<ChangeEventT>() {
      @Override
      public ChangeEventT call() throws Exception {
        final StitchEvent<ChangeEventT> nextEvent = getStream().nextEvent();

        if (nextEvent == null) {
          return null;
        }
        if (nextEvent.getError() != null) {
          dispatchError(nextEvent);
          return null;
        }
        if (nextEvent.getData() == null) {
          return null;
        }

        return nextEvent.getData();
      }
    });
  }
}
