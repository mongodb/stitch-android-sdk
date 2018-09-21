package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.internal.common.AuthMonitor;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.internal.net.Transport;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.diagnostics.Logger;
import org.bson.diagnostics.Loggers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class NamespaceChangeStreamListener {
  private final MongoNamespace namespace;
  private final InstanceSynchronizationConfig instanceConfig;
  private final NamespaceSynchronizationConfig nsConfig;
  private final CoreStitchServiceClient service;
  private final NetworkMonitor networkMonitor;
  private final AuthMonitor authMonitor;
  private final Logger logger;
  private final List<Map.Entry<BsonValue, ChangeEvent<BsonDocument>>> events;
  private final ReadWriteLock nsLock;

  NamespaceChangeStreamListener(
      final MongoNamespace namespace,
      final InstanceSynchronizationConfig instanceConfig,
      final NamespaceSynchronizationConfig nsConfig,
      final CoreStitchServiceClient service,
      final NetworkMonitor networkMonitor,
      final AuthMonitor authMonitor
  ) {
    this.namespace = namespace;
    this.instanceConfig = instanceConfig;
    this.nsConfig = nsConfig;
    this.service = service;
    this.networkMonitor = networkMonitor;
    this.authMonitor = authMonitor;
    this.events = new ArrayList<>();
    this.nsLock = new ReentrantReadWriteLock();
    this.logger =
        Loggers.getLogger(
            String.format("NamespaceChangeStreamShortPoller-%s", namespace.toString()));
  }


}
