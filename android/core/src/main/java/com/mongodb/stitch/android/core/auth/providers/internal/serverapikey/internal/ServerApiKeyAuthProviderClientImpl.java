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

package com.mongodb.stitch.android.core.auth.providers.internal.serverapikey.internal;

import com.mongodb.stitch.android.core.auth.providers.internal.serverapikey.ServerApiKeyAuthProviderClient;
import com.mongodb.stitch.core.auth.providers.serverapikey.CoreServerApiKeyAuthProviderClient;

public final class ServerApiKeyAuthProviderClientImpl extends CoreServerApiKeyAuthProviderClient
    implements ServerApiKeyAuthProviderClient {

  public ServerApiKeyAuthProviderClientImpl() {
    super(CoreServerApiKeyAuthProviderClient.DEFAULT_PROVIDER_NAME);
  }
}
