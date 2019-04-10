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

package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.internal.common.Dispatcher;
import com.mongodb.stitch.core.services.mongodb.remote.ChangeEvent;

import java.util.Locale;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.bson.BsonDocument;
import org.bson.diagnostics.Logger;
import org.bson.diagnostics.Loggers;

public class EventDispatcher {
  private final Logger logger;
  private final Lock listenersLock;
  private final Dispatcher eventDispatcher;

  public EventDispatcher(final String instanceKey,
                         final Dispatcher eventDispatcher) {
    this.listenersLock = new ReentrantLock();
    this.eventDispatcher = eventDispatcher;
    this.logger = Loggers.getLogger(String.format("EventDispatcher-%s", instanceKey));
  }

  /**
   * Emits a change event for the given document id.
   *
   * @param nsConfig   the configuration for the namespace to which the
   *                   document referred to by the change event belongs.
   * @param event      the change event.
   */
  public void emitEvent(
      final NamespaceSynchronizationConfig nsConfig,
      final ChangeEvent<BsonDocument> event) {
    listenersLock.lock();
    try {
      if (nsConfig.getNamespaceListenerConfig() == null) {
        return;
      }
      final NamespaceListenerConfig namespaceListener =
          nsConfig.getNamespaceListenerConfig();
      eventDispatcher.dispatch(() -> {
        try {
          if (namespaceListener.getEventListener() != null) {
            namespaceListener.getEventListener().onEvent(
                BsonUtils.getDocumentId(event.getDocumentKey()),
                ChangeEvents.transformChangeEventForUser(
                    event, namespaceListener.getDocumentCodec()));
          }
        } catch (final Exception ex) {
          logger.error(String.format(
              Locale.US,
              "emitEvent ns=%s documentId=%s emit exception: %s",
              event.getNamespace(),
              BsonUtils.getDocumentId(event.getDocumentKey()),
              ex), ex);
        }
        return null;
      });
    } finally {
      listenersLock.unlock();
    }
  }
}
