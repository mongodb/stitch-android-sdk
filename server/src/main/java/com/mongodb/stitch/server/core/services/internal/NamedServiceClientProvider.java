package com.mongodb.stitch.server.core.services.internal;

import com.mongodb.stitch.core.StitchAppClientInfo;
import com.mongodb.stitch.server.core.services.StitchService;

public interface NamedServiceClientProvider<T> {
  T getClient(final StitchService service, final StitchAppClientInfo client);
}
