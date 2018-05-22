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

package com.mongodb.stitch.core.services.http;

import java.util.Collection;
import java.util.Map;
import org.bson.types.Binary;

/**
 * An HttpRequest encapsulates the details of an HTTP request over the HTTP service.
 */
public final class HttpRequest {
  private final String url;
  private final HttpMethod method;
  private final String authUrl;
  private final Map<String, Collection<String>> headers;
  private final Map<String, String> cookies;
  private final Object body;
  private final Boolean encodeBodyAsJson;
  private final Map<String, String> form;
  private final Boolean followRedirects;

  private HttpRequest(
      final String url,
      final HttpMethod method,
      final String authUrl,
      final Map<String, Collection<String>> headers,
      final Map<String, String> cookies,
      final Object body,
      final Boolean encodeBodyAsJson,
      final Map<String, String> form,
      final Boolean followRedirects
  ) {
    this.url = url;
    this.method = method;
    this.authUrl = authUrl;
    this.headers = headers;
    this.cookies = cookies;
    this.body = body;
    this.encodeBodyAsJson = encodeBodyAsJson;
    this.form = form;
    this.followRedirects = followRedirects;
  }

  /**
   * Returns the URL that the request will be performed against.
   *
   * @return the URL that the request will be performed against.
   */
  public String getUrl() {
    return url;
  }

  /**
   * Returns the HTTP method of the request.
   *
   * @return the HTTP method of the request.
   */
  public HttpMethod getMethod() {
    return method;
  }

  /**
   * Returns the URL that will be used to capture cookies for authentication before the
   * actual request is executed.
   *
   * @return the URL that will be used to capture cookies for authentication before the
   *         actual request is executed.
   */
  public String getAuthUrl() {
    return authUrl;
  }

  /**
   * Returns the headers that will be included in the request.
   *
   * @return the headers that will be included in the request.
   */
  public Map<String, Collection<String>> getHeaders() {
    return headers;
  }

  /**
   * Returns the cookies that will be included in the request.
   *
   * @return the cookies that will be included in the request.
   */
  public Map<String, String> getCookies() {
    return cookies;
  }

  /**
   * Returns the body that will be included in the request.
   *
   * @return the body that will be included in the request.
   */
  public Object getBody() {
    return body;
  }

  /**
   * Returns whether or not the included body should be encoded as extended JSON when sent to the
   * url in this request.
   *
   * @return whether or not the included body should be encoded as extended JSON when sent to the
   *         url in this request.
   */
  public Boolean getEncodeBodyAsJson() {
    return encodeBodyAsJson;
  }

  /**
   * Returns the form that will be included in the request.
   *
   * @return the form that will be included in the request.
   */
  public Map<String, String> getForm() {
    return form;
  }

  /**
   * Returns whether or not Stitch should follow redirects while executing the request. Defaults
   * to false.
   *
   * @return whether or not Stitch should follow redirects while executing the request. Defaults
   *          to false.
   */
  public Boolean getFollowRedirects() {
    return followRedirects;
  }

  /**
   * A builder that can build {@link HttpRequest}s.
   */
  public static class Builder {
    private String url;
    private HttpMethod method;
    private String authUrl;
    private Map<String, Collection<String>> headers;
    private Map<String, String> cookies;
    private Object body;
    private Boolean encodeBodyAsJson;
    private Map<String, String> form;
    private Boolean followRedirects;

    /**
     * Constructs a new builder for an HTTP request.
     */
    public Builder() {}

    /**
     * Sets the URL that the request will be performed against.
     *
     * @param url the URL that the request will be performed against.
     * @return the builder.
     */
    public Builder withUrl(final String url) {
      this.url = url;
      return this;
    }

    /**
     * Sets the HTTP method of the request.
     *
     * @param method the HTTP method of the request.
     * @return the builder.
     */
    public Builder withMethod(final HttpMethod method) {
      this.method = method;
      return this;
    }

    /**
     * Sets the URL that will be used to capture cookies for authentication before the
     * actual request is executed.
     *
     * @param authUrl the URL that will be used to capture cookies for authentication before the
     *                actual request is executed.
     * @return the builder.
     */
    public Builder withAuthUrl(final String authUrl) {
      this.authUrl = authUrl;
      return this;
    }

    /**
     * Sets the headers that will be included in the request.
     *
     * @param headers the headers that will be included in the request.
     * @return the builder.
     */
    public Builder withHeaders(final Map<String, Collection<String>> headers) {
      this.headers = headers;
      return this;
    }

    /**
     * Sets the cookies that will be included in the request.
     *
     * @param cookies the cookies that will be included in the request.
     * @return the builder.
     */
    public Builder withCookies(final Map<String, String> cookies) {
      this.cookies = cookies;
      return this;
    }

    /**
     * Sets the body that will be included in the request. If encodeBodyAsJson is not set
     * (or is set to false) the body must either be a {@link String} or a {@link Binary} or else
     * the request will fail when executed on Stitch.
     *
     * @param body the body that will be included in the request.
     * @return the builder.
     */
    public Builder withBody(final Object body) {
      this.body = body;
      return this;
    }

    /**
     * Sets whether or not the included body should be encoded as extended JSON when sent to the
     * url in this request. Defaults to false.
     * @see Builder#withBody withBody
     *
     * @param encodeBodyAsJson whether or not the included body should be encoded as extended JSON
     *                         when sent to the url in this request.
     * @return the builder.
     */
    public Builder withEncodeBodyAsJson(final Boolean encodeBodyAsJson) {
      this.encodeBodyAsJson = encodeBodyAsJson;
      return this;
    }

    /**
     * Sets the form that will be included in the request.
     *
     * @param form the form that will be included in the request.
     * @return the builder.
     */
    public Builder withForm(final Map<String, String> form) {
      this.form = form;
      return this;
    }

    /**
     * Sets whether or not Stitch should follow redirects while executing the request. Defaults
     * to false.
     *
     * @param followRedirects whether or not Stitch should follow redirects while executing the
     *                        request. Defaults to false.
     * @return the builder.
     */
    public Builder withFollowRedirects(final Boolean followRedirects) {
      this.followRedirects = followRedirects;
      return this;
    }

    /**
     * Builds, validates, and returns the {@link HttpRequest}.
     *
     * @return the built HTTP request.
     */
    public HttpRequest build() {
      if (url == null || url.isEmpty()) {
        throw new IllegalArgumentException("must set url");
      }

      if (method == null) {
        throw new IllegalArgumentException("must set method");
      }

      return new HttpRequest(
        url,
        method,
        authUrl,
        headers,
        cookies,
        body,
        encodeBodyAsJson,
        form,
        followRedirects);
    }
  }
}
