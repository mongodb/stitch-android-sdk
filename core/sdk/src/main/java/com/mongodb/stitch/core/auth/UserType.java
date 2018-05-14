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

package com.mongodb.stitch.core.auth;

public enum UserType {
  NORMAL("normal"),
  SERVER("server"),
  UNKNOWN("unknown");

  private final String typeName;

  UserType(final String name) {
    this.typeName = name;
  }

  public String getTypeName() {
    return typeName;
  }

  @Override
  public String toString() {
    return typeName;
  }

  /**
   * Gets a UserType from a typeName; UNKNOWN if none is found.
   */
  public static UserType fromName(final String name) {
    if (name.equals(NORMAL.typeName)) {
      return NORMAL;
    } else if (name.equals(SERVER.typeName)) {
      return SERVER;
    }
    return UNKNOWN;
  }
}
