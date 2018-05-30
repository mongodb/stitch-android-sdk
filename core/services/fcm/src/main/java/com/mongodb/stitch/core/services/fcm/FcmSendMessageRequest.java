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
import org.bson.Document;

/**
 * An FcmSendMessageRequest encapsulates the details of an FCM send message request.
 */
public final class FcmSendMessageRequest {
  private final FcmSendMessagePriority priority;
  private final String collapseKey;
  private final Boolean contentAvailable;
  private final Boolean mutableContent;
  private final Long timeToLive;
  private final Document data;
  private final FcmSendMessageNotification notification;

  private FcmSendMessageRequest(
      final FcmSendMessagePriority priority,
      final String collapseKey,
      final Boolean contentAvailable,
      final Boolean mutableContent,
      final Long timeToLive,
      final Document data,
      final FcmSendMessageNotification notification
  ) {
    this.priority = priority;
    this.collapseKey = collapseKey;
    this.contentAvailable = contentAvailable;
    this.mutableContent = mutableContent;
    this.timeToLive = timeToLive;
    this.data = data;
    this.notification = notification;
  }

  /**
   * Returns the priority of the message.
   *
   * @return the priority of the message.
   */
  @Nonnull
  public FcmSendMessagePriority getPriority() {
    return priority;
  }

  /**
   * Returns the group of messages that can be collapsed.
   *
   * @return the group of messages that can be collapsed.
   */
  @Nullable
  public String getCollapseKey() {
    return collapseKey;
  }

  /**
   * Returns whether or not to indicate to the client that content is available in order
   * to wake the device. Note: iOS only.
   *
   * @return whether or not to indicate to the client that content is available in order
   *         to wake the device.
   */
  @Nullable
  public Boolean getContentAvailable() {
    return contentAvailable;
  }

  /**
   * Returns whether or not the content in the message can be mutated. Note: iOS only.
   *
   * @return whether or not the content in the message can be mutated.
   */
  @Nullable
  public Boolean getMutableContent() {
    return mutableContent;
  }

  /**
   * Returns how long (in seconds) the message should be kept in FCM storage if the device is
   * offline.
   *
   * @return how long (in seconds) the message should be kept in FCM storage if the device is
   *         offline.
   */
  @Nullable
  public Long getTimeToLive() {
    return timeToLive;
  }

  /**
   * Returns the custom data to send in the payload.
   *
   * @return the custom data to send in the payload.
   */
  @Nullable
  public Document getData() {
    return data;
  }

  /**
   * Returns the predefined, user-visible key-value pairs of the notification payload.
   *
   * @return the predefined, user-visible key-value pairs of the notification payload.
   */
  @Nullable
  public FcmSendMessageNotification getNotification() {
    return notification;
  }

  /**
   * A builder that can build {@link FcmSendMessageRequest}s.
   */
  public static class Builder {
    private FcmSendMessagePriority priority;
    private String collapseKey;
    private Boolean contentAvailable;
    private Boolean mutableContent;
    private Long timeToLive;
    private Document data;
    private FcmSendMessageNotification notification;

    /**
     * Constructs a new builder for an FCM send message request.
     */
    public Builder() {}

    /**
     * Sets the priority of the message.
     *
     * @param priority the priority of the message.
     * @return the builder.
     */
    public Builder withPriority(@Nonnull final FcmSendMessagePriority priority) {
      this.priority = priority;
      return this;
    }

    /**
     * Sets the group of messages that can be collapsed.
     *
     * @param collapseKey the group of messages that can be collapsed.
     * @return the builder.
     */
    public Builder withCollapseKey(@Nonnull final String collapseKey) {
      this.collapseKey = collapseKey;
      return this;
    }

    /**
     * Sets whether or not to indicate to the client that content is available in order
     * to wake the device. Note: iOS only.
     *
     * @param contentAvailable whether or not to indicate to the client that content is available
     *                         in order to wake the device.
     * @return the builder.
     */
    public Builder withContentAvailable(final boolean contentAvailable) {
      this.contentAvailable = contentAvailable;
      return this;
    }

    /**
     * Sets whether or not the content in the message can be mutated. Note: iOS only.
     *
     * @param mutableContent whether or not the content in the message can be mutated.
     * @return the builder.
     */
    public Builder withMutableContent(final boolean mutableContent) {
      this.mutableContent = mutableContent;
      return this;
    }

    /**
     * Sets how long (in seconds) the message should be kept in FCM storage if the device is
     * offline.
     *
     * @param timeToLive how long (in seconds) the message should be kept in FCM storage if the
     *                   device is offline.
     * @return the builder.
     */
    public Builder withTimeToLive(final long timeToLive) {
      this.timeToLive = timeToLive;
      return this;
    }

    /**
     * Sets the custom data to send in the payload.
     *
     * @param data the custom data to send in the payload.
     * @return the builder.
     */
    public Builder withData(@Nonnull final Document data) {
      this.data = data;
      return this;
    }

    /**
     * Sets the predefined, user-visible key-value pairs of the notification payload.
     *
     * @param notification the predefined, user-visible key-value pairs of the notification payload.
     * @return the builder.
     */
    public Builder withNotification(@Nonnull final FcmSendMessageNotification notification) {
      this.notification = notification;
      return this;
    }


    /**
     * Builds, validates, and returns the {@link FcmSendMessageRequest}.
     *
     * @return the built FCM send message request.
     */
    public FcmSendMessageRequest build() {
      if (priority == null) {
        priority = FcmSendMessagePriority.NORMAL;
      }

      return new FcmSendMessageRequest(
          priority,
          collapseKey,
          contentAvailable,
          mutableContent,
          timeToLive,
          data,
          notification);
    }
  }
}
