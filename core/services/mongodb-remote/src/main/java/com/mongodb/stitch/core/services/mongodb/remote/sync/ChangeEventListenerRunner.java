//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.mongodb.stitch.core.services.mongodb.remote.sync;

import com.mongodb.MongoInterruptedException;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.services.mongodb.remote.ChangeStream;
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.NamespaceChangeStreamListener;

import java.io.Closeable;
import java.io.InterruptedIOException;
import java.lang.ref.WeakReference;
import java.util.Enumeration;

import org.bson.diagnostics.Logger;

class ChangeEventListenerRunner implements Runnable, Closeable {
  private final WeakReference<ChangeStream> streamRef;

  ChangeEventListenerRunner(WeakReference<ChangeStream> streamRef) {
    this.streamRef = streamRef;
  }

  public synchronized void run() {
    ChangeStream stream = (ChangeStream)this.streamRef.get();
    if (stream != null) {
      do {
        ChangeEvent event = stream.nextEvent()
        boolean hasListener = false;
        Enumeration<BaseChangeEventListener> listeners = stream.getChangeEventListeners();
        while (listeners.hasMoreElements()) {
          BaseChangeEventListener listener = listeners.nextElement();

        }
        for (BaseChangeEventListener listener : stream.getChangeEventListeners()) {
          while (e)
        }
        if (stream.list)
      } while()
    }
    (ChangeEvent)
    NamespaceChangeStreamListener listener = (NamespaceChangeStreamListener)this.listenerRef.get();
    if (listener != null) {
      do {
        boolean isOpen = listener.isOpen();
        if (!isOpen) {
          try {
            isOpen = listener.openStream();
          } catch (MongoInterruptedException var6) {
            this.logger.error("NamespaceChangeStreamRunner::run error happened while opening stream:", var6);
            this.close();
            return;
          } catch (InterruptedIOException | InterruptedException var7) {
            this.close();
            return;
          } catch (Throwable var8) {
            if (Thread.currentThread().isInterrupted()) {
              this.logger.info("NamespaceChangeStreamRunner::stream interrupted:");
              this.close();
              return;
            }

            this.logger.error("NamespaceChangeStreamRunner::run error happened while opening stream:", var8);
          }

          try {
            if (!isOpen) {
              this.wait(RETRY_SLEEP_MILLIS);
            }
          } catch (InterruptedException var5) {
            this.close();
            return;
          }
        }

        if (isOpen) {
          try {
            listener.storeNextEvent();
          } catch (IllegalStateException var4) {
            this.logger.info(String.format("NamespaceChangeStreamRunner::stream %s: ", var4.getLocalizedMessage()));
            return;
          }
        }
      } while(this.networkMonitor.isConnected() && !Thread.currentThread().isInterrupted());

    }
  }

  public void close() {
    NamespaceChangeStreamListener listener = (NamespaceChangeStreamListener)this.listenerRef.get();
    if (listener != null) {
      listener.close();
    }
  }
}
