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

package com.mongodb.stitch.core.internal.net;

import java.io.IOException;

public interface EventStream {
  /**
   * The next event in this event stream.
   *
   * @return next event in this stream
   * @throws ServerSideEventError server sent event related errors
   * @throws IOException general i/o related errors
   */
  CoreEvent nextEvent() throws ServerSideEventError, IOException;

  /**
   * Whether or not the stream is currently open.
   * @return true if open, false if not
   */
  boolean isOpen();

  /**
   * Close the current stream.
   * @throws IOException can throw exception if internal buffer not closed properly
   */
  void close() throws IOException;
}
