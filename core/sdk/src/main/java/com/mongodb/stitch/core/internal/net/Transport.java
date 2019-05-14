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

import com.mongodb.stitch.core.StitchServiceException;

import java.io.IOException;

public interface Transport {
  // This is how Stitch Server calculates the maximum request size
  // This number is equal to 17,825,792 or ~ 17Mb
  int MAX_REQUEST_SIZE = 17 * (1 << 20);

  /**
   * Performs an HTTP request using the given request object. If the request results in a Stitch
   * service error, the caller is responsible for calling
   * {@link com.mongodb.stitch.core.internal.common.StitchError#handleRequestError(Response)} to
   * decode the exception, as this method will not throw the service exception.
   *
   * @param request The HTTP request to perform.
   * @return The response to the request.
   * @throws Exception if the request fails in transport for any reason. This will not be a
   *         {@link StitchServiceException}, since those must be decoded by the caller.
   */
  Response roundTrip(Request request) throws Exception;

  /**
   * Opens a Server-Sent Event (SSE) stream.
   * See https://www.w3.org/TR/2009/WD-eventsource-20090421/ for specification details.
   * If the underlying request to the Stitch server results in a service exception, this function
   * will detect it and throw it, unlike the {@link Transport#roundTrip(Request)} method.
   *
   * @param request The request to open the stream.
   * @return A raw {@link EventStream} representing the opened change stream.
   * @throws IOException if the request fails to due to an I/O error
   * @throws StitchServiceException if the request to the Stitch server was completed, but the
   *         stream could not be opened due to a Stitch error (such as "InvalidSession").
   */
  EventStream stream(Request request) throws IOException, StitchServiceException;

  void close();
}
