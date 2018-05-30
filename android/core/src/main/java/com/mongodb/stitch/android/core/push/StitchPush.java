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

package com.mongodb.stitch.android.core.push;

import com.mongodb.stitch.android.core.push.internal.NamedPushClientFactory;

/**
 * StitchPush can be used to get clients that can register for push notifications via Stitch.
 */
public interface StitchPush {

  /**
  * Gets a push client for the given named push service.
  *
  * @param factory the provider that will create a client for the authentication provider.
  * @param serviceName the name of the push service.
  * @param <T> the type of client to be returned by the provider.
  * @return a client to interact with the push service.
  */
  <T> T getClient(final NamedPushClientFactory<T> factory, final String serviceName);
}
