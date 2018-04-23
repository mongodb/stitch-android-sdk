package com.mongodb.stitch.core.auth.internal;

import com.mongodb.stitch.core.internal.net.Response;
import com.mongodb.stitch.core.internal.net.StitchAuthDocRequest;
import com.mongodb.stitch.core.internal.net.StitchAuthRequest;

import org.bson.BsonValue;
import org.bson.codecs.Codec;
import org.bson.codecs.Decoder;
import org.bson.codecs.configuration.CodecRegistry;

public interface StitchAuthRequestClient {
  Response doAuthenticatedRequest(final StitchAuthRequest stitchReq);

  <T> T doAuthenticatedJSONRequest(final StitchAuthDocRequest stitchReq, Decoder<T> decoder);

  <T> T doAuthenticatedJSONRequest(final StitchAuthDocRequest stitchReq, Class<T> resultClass);

  Response doAuthenticatedJSONRequestRaw(final StitchAuthDocRequest stitchReq);
}
