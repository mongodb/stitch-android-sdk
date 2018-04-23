package com.mongodb.stitch.core.internal.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class IOUtils {
  private static byte[] readAllToBytes(final InputStream in) throws IOException {
    try {
      final ByteArrayOutputStream result = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int length;
      while ((length = in.read(buffer)) != -1) {
        result.write(buffer, 0, length);
      }
      return result.toByteArray();
    } finally {
      in.close();
    }
  }

  public static String readAllToString(final InputStream in) throws IOException {
    return new String(readAllToBytes(in), StandardCharsets.UTF_8);
  }
}
