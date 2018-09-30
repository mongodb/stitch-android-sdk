package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.internal.common.AuthMonitor;
import com.mongodb.stitch.core.internal.common.Callback;
import com.mongodb.stitch.core.internal.common.OperationResult;
import com.mongodb.stitch.core.internal.common.Stream;
import com.mongodb.stitch.core.internal.net.Event;
import com.mongodb.stitch.core.internal.net.EventType;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.diagnostics.Logger;
import org.bson.diagnostics.Loggers;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;
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
  private final Map<BsonValue, ChangeEvent<BsonDocument>> events;
  private Thread streamThread;
  private ReadWriteLock nsLock;
  private final Deque<Callback<ChangeEvent<BsonDocument>, Object>> callbackQueue;

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
    this.events = new HashMap<>();
    this.nsLock = new ReentrantReadWriteLock();
    this.logger =
        Loggers.getLogger(
            String.format("NamespaceChangeStreamListener-%s", namespace.toString()));
    this.callbackQueue = new ArrayDeque<>();
  }

  /**
   * Opens the stream in a background thread.
   */
  public void start() {
    nsLock.writeLock().lock();
    try {
      if (streamThread != null) {
        return;
      }
      streamThread =
          new Thread(new NamespaceChangeStreamRunner(
              new WeakReference<>(this), networkMonitor, logger));
      streamThread.start();
    } finally {
      nsLock.writeLock().unlock();
    }
  }

  /**
   * Stops the background stream thread.
   */
  public void stop() {
    nsLock.writeLock().lock();
    try {
      if (streamThread == null) {
        return;
      }
      System.out.println("INTERRUPTING");


      streamThread.interrupt();
      try {
        if (currentStream != null) {
          currentStream.close();
        }
        System.out.println("JOINING");
//        streamThread.join();
        System.out.println("DONE JOINING");
      } catch (final Exception e) {
        System.out.println("INTERRUPTED EXCEPTION");
        return;
      }
      streamThread = null;
    } catch(Exception e) {

    } finally {
      nsLock.writeLock().unlock();
    }
  }

  void queueWatcher(Callback<ChangeEvent<BsonDocument>, Object> callback) {
    callbackQueue.add(callback);
  }

  void clearWatchers() {
    for (int i = 0; i < callbackQueue.size(); i++) {
      callbackQueue.remove().onComplete(OperationResult.<ChangeEvent<BsonDocument>, Object>failedResultOf(null));
    }
  }

  Stream<ChangeEvent<BsonDocument>> currentStream;

  /**
   * Opens the next next.
   */
  void stream() {
    logger.info("stream START");
    if (!networkMonitor.isConnected()) {
      logger.info("stream END - Network disconnected");
      return;
    }
    if (!authMonitor.isLoggedIn()) {
      logger.info("stream END - Logged out");
      return;
    }

    if (nsConfig.getStaleDocumentIds().isEmpty()) {
      logger.info("stream END - No stale documents");
      return;
    }

    final Document args = new Document();
    args.put("database", namespace.getDatabaseName());
    args.put("collection", namespace.getCollectionName());
    args.put("ids", nsConfig.getStaleDocumentIds());

    currentStream =
        service.streamFunction(
            "watch",
            Collections.singletonList(args),
            ChangeEvent.changeEventCoder);
    try {
      while (!Thread.interrupted() && currentStream.isOpen()) {
        Event<ChangeEvent<BsonDocument>> event = currentStream.nextEvent();
        if (event.getEventType() == EventType.ERROR) {
          throw event.getError();
        }

        if (event.getEventType() == EventType.MESSAGE) {
          nsLock.writeLock().lock();
          events.put(event.getData().getDocumentKey(), event.getData());
          nsLock.writeLock().unlock();
        }

        for (int i = 0; i < callbackQueue.size(); i++) {
          logger.debug(
              String.format("dequeuing callback for event: %s", event.getData())
          );
          callbackQueue.remove().onComplete(OperationResult.successfulResultOf(event.getData()));
        }
      }
    } catch (final Exception ex) {
      ex.printStackTrace();
      logger.error(String.format(
          Locale.US,
          "NamespaceChangeStreamListener::stream ns=%s exception on listening to change next: %s",
          nsConfig.getNamespace(),
          ex));
      logger.info("poll END");
    } finally {
      try {
        currentStream.close();
        clearWatchers();
      } catch (final IOException ioex) {
        logger.error(String.format(
            Locale.US,
            "NamespaceChangeStreamListener::stream ns=%s exception on closing change next: %s",
            nsConfig.getNamespace(),
            ioex));
      } finally {
        logger.info("stream END");
      }
    }
  }

  /**
   * Returns the latest change events.
   *
   * @return the latest change events.
   */
  @SuppressWarnings("unchecked")
  public Map<BsonValue, ChangeEvent<BsonDocument>> getEvents() {
    nsLock.readLock().lock();
    Map<BsonValue, ChangeEvent<BsonDocument>> events = new HashMap<>(this.events);
    nsLock.readLock().unlock();
    nsLock.writeLock().lock();
    this.events.clear();
    nsLock.writeLock().unlock();
    return events;
  }
}
