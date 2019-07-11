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

package com.mongodb.stitch.core.services.mongodb.remote.internal;

import com.mongodb.stitch.core.StitchAppClientInfo;
import com.mongodb.stitch.core.services.internal.AuthEvent;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.internal.RebindEvent;
import com.mongodb.stitch.core.services.internal.StitchServiceBinder;
import com.mongodb.stitch.core.services.mongodb.local.internal.EmbeddedMongoClientFactory;
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.DataSynchronizer;
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.SyncMongoClientFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CoreRemoteMongoClientImpl implements CoreRemoteMongoClient, StitchServiceBinder {

  private final CoreStitchServiceClient service;
  private final StitchAppClientInfo appInfo;

  // Sync related fields
  private boolean hasSync;
  @Nullable private final EmbeddedMongoClientFactory clientFactory;
  @Nullable private DataSynchronizer dataSynchronizer;
  @Nonnull private String lastActiveUserId;

  public CoreRemoteMongoClientImpl(final CoreStitchServiceClient service,
                                   final String instanceKey,
                                   final StitchAppClientInfo appInfo,
                                   final @Nullable EmbeddedMongoClientFactory clientFactory) {
    this.service = service;
    this.appInfo = appInfo;
    this.clientFactory = clientFactory;

    if (clientFactory != null) {
      this.hasSync = true;
      this.dataSynchronizer = new DataSynchronizer(
          instanceKey,
          service,
          SyncMongoClientFactory.getClient(
              appInfo,
              service.getName(),
              clientFactory
          ),
          this,
          appInfo.getNetworkMonitor(),
          appInfo.getAuthMonitor(),
          appInfo.getEventDispatcher()
      );
      // set this to an empty string to avoid cumbersome null
      // checks when comparing to the next activeUser
      this.lastActiveUserId = appInfo.getAuthMonitor().getActiveUserId() != null
          ? appInfo.getAuthMonitor().getActiveUserId() : "";
    } else {
      this.hasSync = false;
      this.lastActiveUserId = "";
    }

    this.service.bind(this);

  }

  private void onAuthEvent(final AuthEvent authEvent) {
    // Only perform user rebinding if this client has mobile sync functionality.
    if (hasSync) {
      switch (authEvent.getAuthEventType()) {
        case USER_REMOVED:
          final String userId = ((AuthEvent.UserRemoved)authEvent).getRemovedUser().getId();
          if (!SyncMongoClientFactory.deleteDatabase(
              appInfo,
              service.getName(),
              clientFactory,
              userId
          )) {
            System.err.println("Could not delete database for user id " + userId);
          }
          break;
        case ACTIVE_USER_CHANGED:
          if (!lastActiveUserId.equals(appInfo.getAuthMonitor().getActiveUserId())) {
            this.lastActiveUserId = appInfo.getAuthMonitor().getActiveUserId() != null
                ? appInfo.getAuthMonitor().getActiveUserId() : "";
            if (authEvent instanceof AuthEvent.ActiveUserChanged
                && ((AuthEvent.ActiveUserChanged)authEvent).getCurrentActiveUser() != null) {
              // reinitialize the DataSynchronizer entirely.
              // any auth event will trigger this.
              this.dataSynchronizer.reinitialize(
                  SyncMongoClientFactory.getClient(
                      appInfo,
                      service.getName(),
                      clientFactory
                  )
              );
            } else {
              this.dataSynchronizer.stop();
            }
          }
          break;
        default:
          // no-op
          break;
      }
    }
  }

  @Override
  public void onRebindEvent(final RebindEvent rebindEvent) {
    switch (rebindEvent.getType()) {
      case AUTH_EVENT:
        this.onAuthEvent((AuthEvent)rebindEvent);
        break;
      default:
        break;
    }
  }

  /**
   * Gets a {@link CoreRemoteMongoDatabaseImpl} instance for the given database name.
   *
   * @param databaseName the name of the database to retrieve
   * @return a {@code CoreRemoteMongoDatabaseImpl} representing the specified database
   */
  public CoreRemoteMongoDatabaseImpl getDatabase(final String databaseName) {
    return new CoreRemoteMongoDatabaseImpl(
      databaseName,
      service,
      (hasSync) ? dataSynchronizer : null,
      appInfo.getNetworkMonitor()
    );
  }

  public DataSynchronizer getDataSynchronizer() {
    return dataSynchronizer;
  }

  @Override
  public void close() {
    if (hasSync) {
      this.dataSynchronizer.stop();
      this.dataSynchronizer.close();
    }
  }
}
