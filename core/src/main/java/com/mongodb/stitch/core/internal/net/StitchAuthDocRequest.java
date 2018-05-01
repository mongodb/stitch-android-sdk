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

import org.bson.Document;

public final class StitchAuthDocRequest extends StitchAuthRequest {
  private final Document document;

  public StitchAuthDocRequest(final StitchAuthRequest request, final Document document) {
    super(request);
    this.document = document;
  }

  public StitchAuthDocRequest(final StitchRequest request, final Document document) {
    super(request, false);
    this.document = document;
  }

  public Builder builder() {
    return new Builder(this);
  }

  public Document getDocument() {
    return document;
  }

  public static class Builder extends StitchAuthRequest.Builder {
    private Document document;

    public Builder() {}

    Builder(final StitchAuthDocRequest request) {
      super(request);
      document = request.document;
    }

    @SuppressWarnings("UnusedReturnValue")
    public Builder withDocument(final Document document) {
      this.document = document;
      return this;
    }

    public Document getDocument() {
      return this.document;
    }

    public StitchAuthDocRequest build() {
      return new StitchAuthDocRequest(super.build(), document);
    }
  }
}
