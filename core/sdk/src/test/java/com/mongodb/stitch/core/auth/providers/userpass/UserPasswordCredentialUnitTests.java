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

package com.mongodb.stitch.core.auth.providers.userpass;

import static com.mongodb.stitch.core.auth.providers.userpass.UserPasswordAuthProvider.DEFAULT_NAME;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UserPasswordCredentialUnitTests {

  @Test
  public void testCredential() {
    final String username = "username@10gen.com";
    final String password = "password";

    final UserPasswordCredential credential = new UserPasswordCredential(username, password);

    assertEquals(DEFAULT_NAME, credential.getProviderName());
    assertEquals(username, credential.getMaterial().get("username"));
    assertEquals(password, credential.getMaterial().get("password"));
    assertEquals(false, credential.getProviderCapabilities().getReusesExistingSession());

    final UserPasswordCredential credentialWithProv =
        new UserPasswordCredential("hello", username, password);

    assertEquals("hello", credentialWithProv.getProviderName());
    assertEquals(username, credentialWithProv.getMaterial().get("username"));
    assertEquals(password, credentialWithProv.getMaterial().get("password"));
    assertEquals(false, credentialWithProv.getProviderCapabilities().getReusesExistingSession());
  }
}
