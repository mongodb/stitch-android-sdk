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

import okhttp3.Call;
import okio.BufferedSource;

public class OkHttpEventStream extends EventStreamReader implements EventStream {
  private final BufferedSource source;
  private final Call call;

  OkHttpEventStream(final BufferedSource source, final Call call) {
    this.source = source;
    this.call = call;
  }

  @Override
  public Event nextEvent() throws IOException {
    return processEvent();
  }

  protected boolean isActive() {
    return !this.source.buffer().exhausted();
  }

  @Override
  protected String readLine() throws IOException {
    this.source.read()
    return this.source.readUtf8LineStrict();
  }

  @Override
  public boolean isOpen() {
    return this.source.isOpen();
  }

  @Override
  public void close() throws IOException {
    this.source.close();
    this.call.cancel();
  }

  @Override
  public void cancel() {
    this.call.cancel();
  }
}
