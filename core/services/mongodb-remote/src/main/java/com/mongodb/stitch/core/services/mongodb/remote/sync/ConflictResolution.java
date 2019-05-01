/*
 * Copyright 2018-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.stitch.core.services.mongodb.remote.sync;

/**
 * {@link ConflictResolution} represents a resolution to a MongoDB Mobile Sync conflict. Conflicts
 * occur when a local pending write conflicts with an incoming remote change. The user-defined
 * conflict handler method in a {@link ConflictHandler} returns a {@link ConflictResolution}.
 */
public abstract class ConflictResolution {

  /**
   * The different ways of resolving a conflict.
   */
  public enum ConflictResolutionType {
    WITH_DOCUMENT,
    FROM_LOCAL,
    FROM_REMOTE
  }

  /**
   * {@link WithDocument} resolves a conflict by providing a new document that should be treated as
   * the new source of truth. {@link WithDocument} is useful for conflict handlers where data from
   * both the local and remote change events are used in resolving the conflict.
   *
   * @param <T> The type of document for this resolution.
   */
  public static final class WithDocument<T> extends ConflictResolution {
    private final T fullDocumentForResolution;

    WithDocument(final T fullDocumentForResolution) {
      this.fullDocumentForResolution = fullDocumentForResolution;
    }

    @Override
    public ConflictResolutionType getType() {
      return ConflictResolutionType.WITH_DOCUMENT;
    }

    /**
     * Returns the full document that will be used to resolve the conflict.
     *
     * @return the full document that will be used to resolve the conflict
     */
    public T getFullDocumentForResolution() {
      return fullDocumentForResolution;
    }
  }

  /**
   * {@link FromRemote} resolves a conflict by accepting the current state of the document on the
   * remote MongoDB server. This may incur a remote call to the server.
   */
  static final class FromRemote extends ConflictResolution {
    static final FromRemote instance = new FromRemote();

    private FromRemote() {
    }

    @Override
    public ConflictResolutionType getType() {
      return ConflictResolutionType.FROM_REMOTE;
    }

  }

  /**
   * {@link FromLocal} resolves a conflict by accepting the current state of the document on the
   * device. This local version will eventually be propagated to the remote server.
   */
  static final class FromLocal extends ConflictResolution {
    static final FromLocal instance = new FromLocal();

    private FromLocal() {
    }

    @Override
    public ConflictResolutionType getType() {
      return ConflictResolutionType.FROM_LOCAL;
    }

  }

  /**
   * Returns a new {@link WithDocument} resolution with the provided document.
   *
   * @param fullDocumentForResolution The object representing the document that resolves the
   *                                  conflict
   * @param <T> The Java type of the full document.
   * @return the {@link WithDocument} conflict resolution with the provided full document.
   */
  public static <T> ConflictResolution withDocument(final T fullDocumentForResolution) {
    return new WithDocument<>(fullDocumentForResolution);
  }

  /**
   * Returns the {@link FromRemote} conflict resolution.
   *
   * @return the {@link FromRemote} conflict resolution.
   */
  public static ConflictResolution fromRemote() {
    return FromRemote.instance;
  }

  /**
   * Returns the {@link FromLocal} conflict resolution.
   *
   * @return the {@link FromLocal} conflict resolution.
   */
  public static ConflictResolution fromLocal() {
    return FromLocal.instance;
  }

  /**
   * Returns the enum type of this {@link ConflictResolution}. For internal use only.
   *
   * @return the type of this {@link ConflictResolution}
   */
  public abstract ConflictResolutionType getType();
}
