package com.mongodb.stitch.core.services.mongodb.remote;

import com.mongodb.stitch.core.internal.net.StitchEvent;
import com.mongodb.stitch.core.internal.net.Stream;
import com.mongodb.stitch.core.services.mongodb.remote.sync.BaseChangeEventListener;
import org.bson.BsonValue;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;

public abstract class BaseChangeStream<
    DocumentT,
    NextEventT,
    ChangeEventT extends BaseChangeEvent<DocumentT>,
    ChangeEventListenerT extends BaseChangeEventListener<DocumentT, ChangeEventT>>
    implements Closeable {


  private final Stream<ChangeEventT> internalStream;
  private ExceptionListener exceptionListener = null;
  ArrayList<ChangeEventListenerT> listeners;

  protected BaseChangeStream(final Stream<ChangeEventT> stream) {
    if (stream == null) {
      throw new IllegalArgumentException("null stream passed to change stream");
    }
    this.internalStream = stream;
  }

//  abstract void addChangeEventListener(final ChangeEventListenerT changeEventListener);

  /**
   * Optionally adds a listener that is notified when an attempt to retrieve the next event
   * fails.
   *
   * @param exceptionListener The exception listener.
   */
  public void setExceptionListener(final ExceptionListener exceptionListener) {
    this.exceptionListener = exceptionListener;
  }

  /**
   * Returns the next event available from the stream.
   *
   * @return The next event.
   * @throws IOException If the underlying stream throws an {@link IOException}
   */
  public abstract NextEventT nextEvent() throws IOException;

  /**
   * Indicates whether or not the change stream is currently open.
   * @return True if the underlying change stream is open.
   */
  public boolean isOpen() {
    return internalStream.isOpen();
  }

  /**
   * Closes the underlying stream.
   * @throws IOException If the underlying stream throws an {@link IOException} when it is closed.
   */
  @Override
  public void close() throws IOException {
    internalStream.close();
  }

  protected Stream<ChangeEventT> getInternalStream() {
    return this.internalStream;
  }

  protected ExceptionListener getExceptionListener() {
    return this.exceptionListener;
  }

  protected void dispatchError(final StitchEvent<ChangeEventT> event) {
    if (exceptionListener != null) {
      BsonValue documentId = null;
      if (event.getData() != null) {
        documentId = event.getData().getDocumentKey();
      }
      exceptionListener.onError(documentId, event.getError());
    }
  }
}