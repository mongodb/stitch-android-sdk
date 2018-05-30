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
 * A Notification encapsulates the details of an FCM send message request notification payload.
 */
public final class FcmSendMessageNotification {
  private final String title;
  private final String body;
  private final String sound;
  private final String clickAction;
  private final String bodyLockKey;
  private final String bodyLocArgs;
  private final String titleLocKey;
  private final String titleLocArgs;
  private final String icon;
  private final String tag;
  private final String color;
  private final String badge;

  private FcmSendMessageNotification(
      final String title,
      final String body,
      final String sound,
      final String clickAction,
      final String bodyLockKey,
      final String bodyLocArgs,
      final String titleLocKey,
      final String titleLocArgs,
      final String icon,
      final String tag,
      final String color,
      final String badge
  ) {
    this.title = title;
    this.body = body;
    this.sound = sound;
    this.clickAction = clickAction;
    this.bodyLockKey = bodyLockKey;
    this.bodyLocArgs = bodyLocArgs;
    this.titleLocKey = titleLocKey;
    this.titleLocArgs = titleLocArgs;
    this.icon = icon;
    this.tag = tag;
    this.color = color;
    this.badge = badge;
  }

  /**
   * Returns the notification's title.
   *
   * @return the notification's title.
   */
  @Nullable
  public String getTitle() {
    return title;
  }

  /**
   * Returns the notification's body text.
   *
   * @return the notification's body text.
   */
  @Nullable
  public String getBody() {
    return body;
  }

  /**
   * Returns the sound to play when the device receives the notification.
   *
   * @return the sound to play when the device receives the notification.
   */
  @Nullable
  public String getSound() {
    return sound;
  }

  /**
   * Returns the action associated with a user click on the notification.
   *
   * @return the action associated with a user click on the notification.
   */
  @Nullable
  public String getClickAction() {
    return clickAction;
  }

  /**
   * Returns the key to the body string in the app's string resources to use to localize the body
   * text to the user's current localization.
   *
   * @return the key to the body string in the app's string resources to use to localize the body
   *         text to the user's current localization.
   */
  @Nullable
  public String getBodyLockKey() {
    return bodyLockKey;
  }

  /**
   * Returns the variable string values to be used in place of the format specifiers in
   * bodyLocKey to use to localize the body text to the user's current localization.
   *
   * @return the variable string values to be used in place of the format specifiers in
   *         bodyLocKey to use to localize the body text to the user's current localization.
   */
  @Nullable
  public String getBodyLocArgs() {
    return bodyLocArgs;
  }

  /**
   * Returns the key to the title string in the app's string resources to use to localize the
   * title text to the user's current localization.
   *
   * @return the key to the title string in the app's string resources to use to localize the
   *         title text to the user's current localization.
   */
  @Nullable
  public String getTitleLocKey() {
    return titleLocKey;
  }

  /**
   * Returns the variable string values to be used in place of the format specifiers in
   * titleLocKey to use to localize the title text to the user's current localization.
   *
   * @return the variable string values to be used in place of the format specifiers in
   *         titleLocKey to use to localize the title text to the user's current localization.
   */
  @Nullable
  public String getTitleLocArgs() {
    return titleLocArgs;
  }

  /**
   * Returns the notification's icon. Note: Android only.
   *
   * @return the notification's icon.
   */
  @Nullable
  public String getIcon() {
    return icon;
  }

  /**
   * Returns the identifier used to replace existing notifications in the notification drawer.
   * Note: Android only.
   *
   * @return the identifier used to replace existing notifications in the notification drawer.
   */
  @Nullable
  public String getTag() {
    return tag;
  }

  /**
   * Returns the notification's icon color, expressed in #rrggbb format. Note: Android only.
   *
   * @return the notification's icon color, expressed in #rrggbb format.
   */
  @Nullable
  public String getColor() {
    return color;
  }

  /**
   * Returns the value of the badge on the home screen app icon. Note: iOS only.
   *
   * @return the value of the badge on the home screen app icon.
   */
  @Nullable
  public String getBadge() {
    return badge;
  }

  /**
   * A builder that can build {@link FcmSendMessageRequest}s.
   */
  public static class Builder {
    private String title;
    private String body;
    private String sound;
    private String clickAction;
    private String bodyLockKey;
    private String bodyLocArgs;
    private String titleLocKey;
    private String titleLocArgs;
    private String icon;
    private String tag;
    private String color;
    private String badge;

    /**
     * Constructs a new builder for an FCM send message request.
     */
    public Builder() {}

    /**
     * Sets the notification's title.
     *
     * @param title the notification's title.
     * @return the builder.
     */
    public Builder withTitle(@Nonnull final String title) {
      this.title = title;
      return this;
    }

    /**
     * Sets the notification's body text.
     *
     * @param body the notification's body text.
     * @return the builder.
     */
    public Builder withBody(@Nonnull final String body) {
      this.body = body;
      return this;
    }

    /**
     * Sets the sound to play when the device receives the notification.
     *
     * @param sound the sound to play when the device receives the notification.
     * @return the builder.
     */
    public Builder withSound(@Nonnull final String sound) {
      this.sound = sound;
      return this;
    }

    /**
     * Sets the action associated with a user click on the notification.
     *
     * @param clickAction the action associated with a user click on the notification.
     * @return the builder.
     */
    public Builder withClickAction(@Nonnull final String clickAction) {
      this.clickAction = clickAction;
      return this;
    }

    /**
     * Sets the key to the body string in the app's string resources to use to localize the body
     * text to the user's current localization.
     *
     * @param bodyLockKey the key to the body string in the app's string resources to use to
     *                    localize the body text to the user's current localization.
     * @return the builder.
     */
    public Builder withBodyLockKey(@Nonnull final String bodyLockKey) {
      this.bodyLockKey = bodyLockKey;
      return this;
    }

    /**
     * Sets the variable string values to be used in place of the format specifiers in
     * bodyLocKey to use to localize the body text to the user's current localization.
     *
     * @param bodyLocArgs the variable string values to be used in place of the format specifiers
     *                    in bodyLocKey to use to localize the body text to the user's current
     *                    localization.
     * @return the builder.
     */
    public Builder withBodyLocArgs(@Nonnull final String bodyLocArgs) {
      this.bodyLocArgs = bodyLocArgs;
      return this;
    }

    /**
     * Sets the key to the title string in the app's string resources to use to localize the
     * title text to the user's current localization.
     *
     * @param titleLocKey the key to the title string in the app's string resources to use to
     *                    localize the title text to the user's current localization.
     * @return the builder.
     */
    public Builder withTitleLocKey(@Nonnull final String titleLocKey) {
      this.titleLocKey = titleLocKey;
      return this;
    }

    /**
     * Sets the variable string values to be used in place of the format specifiers in
     * titleLocKey to use to localize the title text to the user's current localization.
     *
     * @param titleLocArgs the variable string values to be used in place of the format specifiers
     *                     in titleLocKey to use to localize the title text to the user's current
     *                     localization.
     * @return the builder.
     */
    public Builder withTitleLocArgs(@Nonnull final String titleLocArgs) {
      this.titleLocArgs = titleLocArgs;
      return this;
    }

    /**
     * Sets the notification's icon. Note: Android only.
     *
     * @param icon the notification's icon.
     * @return the builder.
     */
    public Builder withIcon(@Nonnull final String icon) {
      this.icon = icon;
      return this;
    }

    /**
     * Sets the identifier used to replace existing notifications in the notification drawer.
     * Note: Android only.
     *
     * @param tag the identifier used to replace existing notifications in the notification
     *            drawer.
     * @return the builder.
     */
    public Builder withTag(@Nonnull final String tag) {
      this.tag = tag;
      return this;
    }

    /**
     * Sets the notification's icon color, expressed in #rrggbb format. Note: Android only.
     *
     * @param color the notification's icon color, expressed in #rrggbb format.
     * @return the builder.
     */
    public Builder withColor(@Nonnull final String color) {
      this.color = color;
      return this;
    }

    /**
     * Sets the value of the badge on the home screen app icon. Note: iOS only.
     *
     * @param badge the value of the badge on the home screen app icon.
     * @return the builder.
     */
    public Builder withBadge(@Nonnull final String badge) {
      this.badge = badge;
      return this;
    }


    /**
     * Builds, validates, and returns the {@link FcmSendMessageNotification}.
     *
     * @return the built notification.
     */
    public FcmSendMessageNotification build() {
      return new FcmSendMessageNotification(
          title,
          body,
          sound,
          clickAction,
          bodyLockKey,
          bodyLocArgs,
          titleLocKey,
          titleLocArgs,
          icon,
          tag,
          color,
          badge);
    }
  }
}
