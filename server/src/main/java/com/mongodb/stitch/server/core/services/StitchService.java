package com.mongodb.stitch.server.core.services;

import org.bson.codecs.Codec;

import java.util.List;

public interface StitchService {
  /**
   * Calls the specified Stitch service function, and decodes the response into an instance of the
   * specified type. The response will be decoded using the codec registry specified when the app
   * client was configured. If no codec registry was configured, a default codec registry will be
   * used. The default codec registry supports the mappings specified
   * <a href="http://mongodb.github.io/mongo-java-driver/3.1/bson/documents/#document">here</a>
   *
   * @param name The name of the Stitch service function to call.
   * @param args The arguments to pass to the function.
   * @param resultClass The class that the response should be decoded as.
   * @param <TResult> The type into which the response will be decoded.
   * @return The decoded value.
   */
  <TResult> TResult callFunction(final String name, final List<? extends Object> args, final Class<TResult> resultClass);

  /**
   * Calls the specified Stitch service function, and decodes the response into a value using the
   * provided {@link Codec}.
   *
   * @param name The name of the Stitch service function to call.
   * @param args The arguments to pass to the function.
   * @param resultCodec The {@link Codec} to use to decode the response into a value.
   * @param <TResult> The type into which the response will be decoded.
   * @return The decoded value.
   */
  <TResult> TResult callFunction(final String name, final List<? extends Object> args, final Codec<TResult> resultCodec);
}
