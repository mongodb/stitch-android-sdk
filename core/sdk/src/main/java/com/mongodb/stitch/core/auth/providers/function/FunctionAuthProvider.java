package com.mongodb.stitch.core.auth.providers.function;

/**
 * Information about the custom function authentication provider.
 */
public final class FunctionAuthProvider {
  private FunctionAuthProvider() {}

  public static final String TYPE = "custom-function";
  public static final String DEFAULT_NAME = "custom-function";
}
