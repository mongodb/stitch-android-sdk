package com.mongodb.stitch.core.internal.net;

public interface Transport {
  Response roundTrip(Request request) throws Exception;
}
