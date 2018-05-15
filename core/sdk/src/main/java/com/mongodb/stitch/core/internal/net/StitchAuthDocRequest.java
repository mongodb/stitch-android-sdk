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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mongodb.stitch.core.StitchRequestErrorCode;
import com.mongodb.stitch.core.StitchRequestException;
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;

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

    public Builder withDocument(final Document document) {
      this.document = document;
      return this;
    }

    public Document getDocument() {
      return this.document;
    }

    /**
     * Set if this request should use an access token in this request.
     */
    public Builder withAccessToken() {
      super.withAccessToken();
      return this;
    }

    /**
     * Set if this request should use a refresh token in this request.
     */
    public Builder withRefreshToken() {
      super.withRefreshToken();
      return this;
    }

    /**
     * Sets whether or not the performer of this request should attempt to refresh authentication
     * info on failure.
     */
    public Builder withShouldRefreshOnFailure(final boolean shouldRefresh) {
      super.withShouldRefreshOnFailure(shouldRefresh);
      return this;
    }

    /**
     * Sets the HTTP method of the request.
     */
    public Builder withMethod(final Method method) {
      super.withMethod(method);
      return this;
    }

    /**
     * Sets the Stitch API path of the request.
     */
    public Builder withPath(final String path) {
      super.withPath(path);
      return this;
    }

    /**
     * Sets the headers that will be included in the request.
     */
    public Builder withHeaders(final Map<String, String> headers) {
      super.withHeaders(headers);
      return this;
    }

    /**
     * Sets a copy of the body that will be sent along with the request.
     */
    public Builder withBody(final byte[] body) {
      super.withBody(body);
      return this;
    }

    /**
     * Builds the {@link StitchAuthDocRequest}.
     */
    public StitchAuthDocRequest build() {
      return this.build(BsonUtils.DEFAULT_CODEC_REGISTRY);
    }

    /**
     * Builds the {@link StitchAuthDocRequest}.
     */
    public StitchAuthDocRequest build(final CodecRegistry codecRegistry) {
      if (document == null) {
        throw new IllegalArgumentException("document must be set");
      }
      if (getHeaders() == null) {
        withHeaders(new HashMap<String, String>());
      }
      getHeaders().put(Headers.CONTENT_TYPE, ContentTypes.APPLICATION_JSON);
      try {
        withBody(StitchObjectMapper.getInstance()
            .withCodecRegistry(codecRegistry).writeValueAsBytes(document));
      } catch (final JsonProcessingException e) {
        throw new StitchRequestException(e, StitchRequestErrorCode.ENCODING_ERROR);
      }
      return new StitchAuthDocRequest(super.build(), document);
    }
  }

  @Override
  public boolean equals(final Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof StitchAuthDocRequest)) {
      return false;
    }
    final StitchAuthDocRequest other = (StitchAuthDocRequest) object;
    return super.equals(other)
        && getDocument().equals(other.getDocument());
  }

  @Override
  public int hashCode() {
    return super.hashCode()
        + getDocument().hashCode();
  }
}
