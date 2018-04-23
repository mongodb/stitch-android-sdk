package com.mongodb.stitch.core.mock;

import com.mongodb.stitch.core.auth.providers.anonymous.CoreAnonymousAuthProviderClient;

public class MockCoreAnonymousAuthProviderClient extends CoreAnonymousAuthProviderClient {
    public MockCoreAnonymousAuthProviderClient() {
        super(CoreAnonymousAuthProviderClient.DEFAULT_PROVIDER_NAME);
    }
}
