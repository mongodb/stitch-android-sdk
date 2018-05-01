package com.mongodb.stitch.core.internal.net;

import org.bson.Document;

public final class StitchAuthDocRequest extends StitchAuthRequest {
  public final Document document;

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
