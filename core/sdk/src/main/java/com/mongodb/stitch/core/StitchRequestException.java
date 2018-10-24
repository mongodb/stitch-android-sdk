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

package com.mongodb.stitch.core;

/**
 * Indicates that an error occurred while a request was being carried out. This could be due to (but
 * is not limited to) an unreachable server, a connection timeout, or an inability to decode the
 * result. An error code is included, which indicates whether the error was a transport error or
 * decoding error. The inherited getCause() method from the {@link Exception} class can be used to
 * produce that underlying error that caused a StitchRequestException. In the case of transport
 * errors, these exception are thrown by the underlying {@link
 * com.mongodb.stitch.core.internal.net.Transport} of the Stitch client. An error in decoding the
 * result from the server is typically a {@link java.io.IOException} thrown internally by the Stitch
 * SDK.
 */
public class StitchRequestException extends StitchException {
  private final StitchRequestErrorCode errorCode;

  /**
   * Constructs a request exception from the underlying exception and error code.
   *
   * @param underlyingException the underlying exception that caused this exception.
   * @param errorCode the request error code associated with this exception.
   */
  public StitchRequestException(
      final Exception underlyingException, final StitchRequestErrorCode errorCode) {
    super(underlyingException);
    this.errorCode = errorCode;
  }

  /**
   * Returns the {@link StitchRequestErrorCode} indicating the reason for this exception.
   *
   * @return the {@link StitchRequestErrorCode} indicating the reason for this exception.
   */
  public StitchRequestErrorCode getErrorCode() {
    return errorCode;
  }

  @Override
  public String toString() {
    return String.format("(%s): %s: %s", super.toString(), errorCode.name(), errorCode.toString());
  }
}
