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

package com.mongodb.stitch.core.services.fcm;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The details of an individual message failure inside an FCM send message request.
 */
public class FcmSendMessageResultFailureDetail {
  private final long index;
  private final String error;
  private final String userId;

  /**
   * Constructs a new FCM send message result failure detail.
   *
   * @param index the index corresponding to the target.
   * @param error the error that occurred.
   * @param userId the user id that could not be sent a message to, if applicable.
   */
  public FcmSendMessageResultFailureDetail(
      final long index,
      final String error,
      final String userId
  ) {
    this.index = index;
    this.error = error;
    this.userId = userId;
  }

  /**
   * Returns the index corresponding to the target.
   *
   * @return the index corresponding to the target.
   */
  public long getIndex() {
    return index;
  }

  /**
   * Returns the error that occurred.
   *
   * @return the error that occurred.
   */
  @Nonnull
  public String getError() {
    return error;
  }

  /**
   * Returns the user id that could not be sent a message to, if applicable.
   *
   * @return the user id that could not be sent a message to, if applicable.
   */
  @Nullable
  public String getUserId() {
    return userId;
  }
}
