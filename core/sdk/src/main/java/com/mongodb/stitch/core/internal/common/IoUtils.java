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

package com.mongodb.stitch.core.internal.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class IoUtils {

  private IoUtils() {}

  /**
   * Reads the entire {@link InputStream} to EOF and returns the data as bytes.
   *
   * @param in the stream to read.
   * @return the read data as bytes.
   * @throws IOException in the event the data cannot be read.
   */
  public static byte[] readAllToBytes(final InputStream in) throws IOException {
    try {
      final ByteArrayOutputStream result = new ByteArrayOutputStream();
      final byte[] buffer = new byte[1024];
      int length;
      while ((length = in.read(buffer)) != -1) {
        result.write(buffer, 0, length);
      }
      return result.toByteArray();
    } finally {
      in.close();
    }
  }

  /**
   * Reads the entire {@link InputStream} to EOF and returns the data as a string.
   *
   * @param in the stream to read.
   * @return the read data as a string.
   * @throws IOException in the event the data cannot be read.
   */
  public static String readAllToString(final InputStream in) throws IOException {
    return new String(readAllToBytes(in), StandardCharsets.UTF_8);
  }
}
