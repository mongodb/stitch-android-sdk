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

package com.mongodb.stitch.server.core.services.internal;

import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.services.internal.CoreStitchService;
import com.mongodb.stitch.core.services.internal.StitchServiceRoutes;
import com.mongodb.stitch.server.core.services.StitchService;
import java.util.List;
import org.bson.codecs.Codec;

public final class StitchServiceImpl extends CoreStitchService implements StitchService {

  public StitchServiceImpl(
      final StitchAuthRequestClient requestClient,
      final StitchServiceRoutes routes,
      final String name) {
    super(requestClient, routes, name);
  }

  @Override
  public <ResultT> ResultT callFunction(
      final String name, final List<? extends Object> args, final Class<ResultT> resultClass) {
    return callFunctionInternal(name, args, resultClass);
  }

  @Override
  public <ResultT> ResultT callFunction(
      final String name, final List<? extends Object> args, final Codec<ResultT> resultCodec) {
    return callFunctionInternal(name, args, resultCodec);
  }
}
