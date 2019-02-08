package com.mongodb.stitch.core.services.internal;

import com.mongodb.stitch.core.auth.internal.CoreStitchUser;

import javax.annotation.Nullable;

public abstract class AuthEvent extends RebindEvent {
  public enum Type {
    USER_LOGGED_IN,
    USER_LOGGED_OUT,
    USER_SWITCHED,
    USER_REMOVED
  }

  public static class UserLoggedIn<StitchUserT extends CoreStitchUser> extends AuthEvent {
    private final StitchUserT loggedInUser;
    @Nullable
    private final StitchUserT previousActiveUser;

    public UserLoggedIn(final StitchUserT loggedInUser,
                        @Nullable final StitchUserT previousActiveUser) {
      this.loggedInUser = loggedInUser;
      this.previousActiveUser = previousActiveUser;
    }

    public StitchUserT getLoggedInUser() {
      return loggedInUser;
    }

    @Nullable
    public StitchUserT getPreviousActiveUser() {
      return previousActiveUser;
    }

    @Override
    public Type getAuthEventType() {
      return Type.USER_LOGGED_IN;
    }
  }

  public static class UserLoggedOut<StitchUserT extends CoreStitchUser> extends AuthEvent {
    private final StitchUserT loggedOutUser;

    public UserLoggedOut(final StitchUserT loggedOutUser) {
      this.loggedOutUser = loggedOutUser;
    }

    public StitchUserT getLoggedOutUser() {
      return loggedOutUser;
    }

    @Override
    public Type getAuthEventType() {
      return Type.USER_LOGGED_OUT;
    }
  }

  public static class UserSwitched<StitchUserT extends CoreStitchUser> extends AuthEvent {
    private final StitchUserT currentActiveUser;
    @Nullable
    private final StitchUserT previousActiveUser;

    public UserSwitched(final StitchUserT currentActiveUser,
                        @Nullable final StitchUserT previousActiveUser) {
      this.currentActiveUser = currentActiveUser;
      this.previousActiveUser = previousActiveUser;
    }

    public StitchUserT getCurrentActiveUser() {
      return currentActiveUser;
    }

    @Nullable
    public StitchUserT getPreviousActiveUser() {
      return previousActiveUser;
    }

    @Override
    public Type getAuthEventType() {
      return Type.USER_SWITCHED;
    }
  }

  public static class UserRemoved<StitchUserT extends CoreStitchUser> extends AuthEvent {
    private final StitchUserT removedUser;

    public UserRemoved(final StitchUserT removedUser) {
      this.removedUser = removedUser;
    }

    public StitchUserT getRemovedUser() {
      return removedUser;
    }

    @Override
    public Type getAuthEventType() {
      return Type.USER_REMOVED;
    }
  }

  @Override
  public final RebindEventType getType() {
    return RebindEventType.AUTH_EVENT;
  }

  abstract public Type getAuthEventType();
}
