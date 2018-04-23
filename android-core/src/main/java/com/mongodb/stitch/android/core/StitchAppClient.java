package com.mongodb.stitch.android.core;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.auth.StitchAuth;
import com.mongodb.stitch.android.services.internal.NamedServiceClientProvider;
import com.mongodb.stitch.android.services.internal.ServiceClientProvider;
import java.util.List;
import org.bson.codecs.Codec;
import org.bson.codecs.Decoder;

public interface StitchAppClient {
  StitchAuth getAuth();

  <T> T getServiceClient(final NamedServiceClientProvider<T> provider, final String serviceName);

  <T> T getServiceClient(final ServiceClientProvider<T> provider);

  /**
   * Calls the specified Stitch function, and decodes the response into an instance of the
   * specified type. The response will be decoded using the codec registry specified when the
   * client was configured. If no codec registry was configured, a default codec registry will be
   * used. The default codec registry supports the mappings specified
   * <a href="http://mongodb.github.io/mongo-java-driver/3.1/bson/documents/#document">here</a>
   *
   * @param name The name of the Stitch function to call.
   * @param args The arguments to pass to the function.
   * @param resultClass The class that the response should be decoded as.
   * @param <TResult> The type into which the Stitch response will be decoded.
   * @return A {@link Task} containing the decoded value.
   */
  <TResult> Task<TResult> callFunction(final String name, final List<? extends Object> args, final Class<TResult> resultClass);

  /**
   * Calls the specified Stitch function, and decodes the response into a value using the provided
   * {@link Decoder} or {@link Codec}.
   *
   * @param name The name of the Stitch function to call.
   * @param args The arguments to pass to the function.
   * @param resultDecoder The {@link Decoder} or {@link Codec} to use to decode the response into a
   *                      value.
   * @param <TResult> The type into which the response will be decoded.
   * @return A {@link Task} containing the decoded value.
   */
  <TResult> Task<TResult> callFunction(final String name, final List<? extends Object> args, final Decoder<TResult> resultDecoder);
}
