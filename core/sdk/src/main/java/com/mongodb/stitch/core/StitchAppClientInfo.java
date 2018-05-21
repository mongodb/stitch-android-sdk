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

import org.bson.codecs.configuration.CodecRegistry;

/** A class providing basic information about a Stitch app client. */
public final class StitchAppClientInfo {
  private final String clientAppId;
  private final String dataDirectory;
  private final String localAppName;
  private final String localAppVersion;
  private final CodecRegistry codecRegistry;

  /**
   * Constructs the {@link StitchAppClientInfo}.
   */
  public StitchAppClientInfo(
      final String clientAppId,
      final String dataDirectory,
      final String localAppName,
      final String localAppVersion,
      final CodecRegistry codecRegistry) {
    this.clientAppId = clientAppId;
    this.dataDirectory = dataDirectory;
    this.localAppName = localAppName;
    this.localAppVersion = localAppVersion;
    this.codecRegistry = codecRegistry;
  }

  /**
   * Gets the client app id of the Stitch application that this client communicates with.
   */
  public String getClientAppId() {
    return clientAppId;
  }

  /**
   * Gets the local directory in which Stitch can store any data (e.g. MongoDB Mobile data
   * directory).
   */
  public String getDataDirectory() {
    return dataDirectory;
  }

  /**
   * Gets the name of the local application.
   */
  public String getLocalAppName() {
    return localAppName;
  }

  /**
   * Gets the current version of the local application.
   */
  public String getLocalAppVersion() {
    return localAppVersion;
  }

  /**
   * Returns the codec registry.
   */
  public CodecRegistry getCodecRegistry() {
    return codecRegistry;
  }
}
