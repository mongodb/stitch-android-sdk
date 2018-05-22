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

public final class StitchDocRequest extends StitchRequest {
  private final Document document;

  private StitchDocRequest(final StitchRequest request, final Document document) {
    super(request);
    this.document = document;
  }

  public Builder builder() {
    return new Builder(this);
  }

  private Document getDocument() {
    return document;
  }

  public static class Builder extends StitchRequest.Builder {
    private Document document;

    /**
     * Constructs a new builder.
     */
    public Builder() {}

    Builder(final StitchDocRequest request) {
      super(request);
      document = request.document;
    }

    /**
     * Sets the document to send in the request.
     *
     * @param document the document to send in the request.
     * @return the builder.
     */
    public Builder withDocument(final Document document) {
      this.document = document;
      return this;
    }

    /**
     * Returns the document to send in the request.
     *
     * @return the document to send in the request.
     */
    public Document getDocument() {
      return this.document;
    }

    /**
     * Sets the HTTP method of the request.
     *
     * @param method the HTTP method of the request.
     * @return the builder.
     */
    public Builder withMethod(final Method method) {
      super.withMethod(method);
      return this;
    }

    /**
     * Sets the Stitch API path of the request.
     *
     * @param path the Stitch API path of the request.
     * @return the builder.
     */
    public Builder withPath(final String path) {
      super.withPath(path);
      return this;
    }

    /**
     * Sets the headers that will be included in the request.
     *
     * @param headers the headers that will be included in the request.
     * @return the builder.
     */
    public Builder withHeaders(final Map<String, String> headers) {
      super.withHeaders(headers);
      return this;
    }

    /**
     * Sets a copy of the body that will be sent along with the request.
     *
     * @param body a copy of the body that will be sent along with the request.
     * @return the builder.
     */
    public Builder withBody(final byte[] body) {
      super.withBody(body);
      return this;
    }

    /**
     * Builds the {@link StitchDocRequest}.
     *
     * @return the built {@link StitchDocRequest}.
     */
    public StitchDocRequest build() {
      return this.build(BsonUtils.DEFAULT_CODEC_REGISTRY);
    }

    /**
     * Builds the {@link StitchDocRequest} using the given codec registry for encoding the
     * document.
     *
     * @param codecRegistry the registry to use for encoding the document.
     * @return the built {@link StitchDocRequest}.
     */
    public StitchDocRequest build(final CodecRegistry codecRegistry) {
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
      return new StitchDocRequest(super.build(), document);
    }
  }

  @Override
  protected Document toDocument() {
    final Document doc = new Document("request", super.toDocument());
    doc.put("document", getDocument());
    return doc;
  }

  @Override
  public boolean equals(final Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof StitchDocRequest)) {
      return false;
    }
    final StitchDocRequest other = (StitchDocRequest) object;
    return super.equals(other) && getDocument().equals(other.getDocument());
  }

  @Override
  public int hashCode() {
    return super.hashCode() + getDocument().hashCode();
  }
}
