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

package com.mongodb.stitch.core.auth.internal;

import com.mongodb.stitch.core.internal.net.Response;
import com.mongodb.stitch.core.internal.net.StitchAuthRequest;
import com.mongodb.stitch.core.internal.net.Stream;

import org.bson.codecs.Decoder;
import org.bson.codecs.configuration.CodecRegistry;

public interface StitchAuthRequestClient {
  Response doAuthenticatedRequest(final StitchAuthRequest stitchReq);

  <T> T doAuthenticatedRequest(final StitchAuthRequest stitchReq, final Decoder<T> decoder);

  <T> T doAuthenticatedRequest(final StitchAuthRequest stitchReq,
                               final Class<T> resultClass,
                               final CodecRegistry codecRegistry);

  <T> Stream<T> openAuthenticatedStream(final StitchAuthRequest stitchReq,
                                        final Decoder<T> decoder) throws InterruptedException;
}
