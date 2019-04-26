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

package com.mongodb.stitch.core.internal.net;

import java.io.IOException;

public interface Transport {
  // This is how Stitch Server calculates the maximum request size
  // This number is equal to 17,825,792 or ~ 17Mb
  int MAX_REQUEST_SIZE = 17 * (1 << 20);

  Response roundTrip(Request request) throws Exception;

  EventStream stream(Request request) throws IOException;

  void close();
}
