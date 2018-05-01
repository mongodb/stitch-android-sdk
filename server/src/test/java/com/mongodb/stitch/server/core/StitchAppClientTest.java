package com.mongodb.stitch.server.core;

import static org.junit.Assert.assertEquals;

import com.mongodb.stitch.core.StitchAppClientConfiguration;
import com.mongodb.stitch.server.core.auth.StitchUser;
import com.mongodb.stitch.server.core.auth.providers.anonymous.AnonymousAuthProvider;
import com.mongodb.stitch.server.core.auth.providers.anonymous.AnonymousAuthProviderClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.Test;

class StitchAppClientTest {

  @BeforeAll
  static void setup() {
    Stitch.initialize();
  }

  @Test
  void callFunction() {
    final StitchAppClient client =
        Stitch.initializeAppClient(
            // TODO: Make configurable...
            StitchAppClientConfiguration.Builder.forApp("test-vxfnm", "https://erd.ngrok.io"));
    final AnonymousAuthProviderClient anonClient =
        client.getAuth().getProviderClient(AnonymousAuthProvider.ClientProvider);
    final StitchUser user = client.getAuth().loginWithCredential(anonClient.getCredential());
    assertEquals(user.getUserType(), "normal");
  }
}
