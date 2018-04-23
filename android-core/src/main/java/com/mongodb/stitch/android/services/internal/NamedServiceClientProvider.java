package com.mongodb.stitch.android.services.internal;

import com.mongodb.stitch.android.services.StitchService;
import com.mongodb.stitch.core.StitchAppClientInfo;

public interface NamedServiceClientProvider<T> {
  T getClient(final StitchService service, final StitchAppClientInfo client);
}
