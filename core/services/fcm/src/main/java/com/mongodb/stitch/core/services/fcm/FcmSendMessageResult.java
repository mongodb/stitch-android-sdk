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

import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * The result of an FCM send message request.
 */
public class FcmSendMessageResult {
  private final long successes;
  private final long failures;
  private final List<FcmSendMessageResultFailureDetail> failureDetails;

  /**
   * Constructs a new result to an FCM send message request.
   *
   * @param successes the number of messages successfully sent.
   * @param failures the number of messages that failed to be sent.
   * @param failureDetails the details of each failure.
   */
  public FcmSendMessageResult(
      final long successes,
      final long failures,
      final List<FcmSendMessageResultFailureDetail> failureDetails
  ) {
    this.successes = successes;
    this.failures = failures;
    this.failureDetails = failureDetails == null
        ? Collections.<FcmSendMessageResultFailureDetail>emptyList() : failureDetails;
  }

  /**
   * Returns the number of messages successfully sent.
   *
   * @return the number of messages successfully sent.
   */
  public long getSuccesses() {
    return successes;
  }

  /**
   * Returns the number of messages that failed to be sent.
   *
   * @return the number of messages that failed to be sent.
   */
  public long getFailures() {
    return failures;
  }

  /**
   * Returns the details of each failure.
   *
   * @return the details of each failure.
   */
  @Nonnull
  public List<FcmSendMessageResultFailureDetail> getFailureDetails() {
    return failureDetails;
  }
}
