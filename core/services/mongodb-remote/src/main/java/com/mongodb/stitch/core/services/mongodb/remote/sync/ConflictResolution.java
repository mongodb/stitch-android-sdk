package com.mongodb.stitch.core.services.mongodb.remote.sync;

public abstract class ConflictResolution {

  public enum ConflictResolutionType {
    WITH_DOCUMENT,
    FROM_LOCAL,
    FROM_REMOTE
  }

  public static final class WithDocument<T> extends ConflictResolution {
    private final T fullDocumentForResolution;

    WithDocument(final T fullDocumentForResolution) {
      this.fullDocumentForResolution = fullDocumentForResolution;
    }

    @Override
    public ConflictResolutionType getType() {
      return ConflictResolutionType.WITH_DOCUMENT;
    }

    public T getFullDocumentForResolution() {
      return fullDocumentForResolution;
    }
  }

  public static final class FromRemote extends ConflictResolution {

    static final FromRemote instance = new FromRemote();

    private FromRemote() {
    }

    @Override
    public ConflictResolutionType getType() {
      return ConflictResolutionType.FROM_REMOTE;
    }

  }

  public static final class FromLocal extends ConflictResolution {

    static final FromLocal instance = new FromLocal();

    private FromLocal() {
    }

    @Override
    public ConflictResolutionType getType() {
      return ConflictResolutionType.FROM_LOCAL;
    }

  }

  public static <T> ConflictResolution withDocument(T fullDocumentForResolution) {
    return new WithDocument<>(fullDocumentForResolution);
  }

  public static ConflictResolution fromRemote() {
    return FromRemote.instance;
  }

  public static ConflictResolution fromLocal() {
    return FromLocal.instance;
  }

  public abstract ConflictResolutionType getType();
}
