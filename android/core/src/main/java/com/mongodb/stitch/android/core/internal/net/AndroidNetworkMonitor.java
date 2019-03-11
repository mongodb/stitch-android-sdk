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

package com.mongodb.stitch.android.core.internal.net;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;

import com.mongodb.stitch.core.internal.net.NetworkMonitor;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

public class AndroidNetworkMonitor extends BroadcastReceiver implements NetworkMonitor {

  private final ConnectivityManager connManager;
  private final Set<StateListener> listeners;

  public AndroidNetworkMonitor(final ConnectivityManager connManager) {
    this.connManager = connManager;
    this.listeners = new HashSet<>();
  }

  @Override
  public boolean isConnected() {
    final NetworkInfo activeNetworkInfo = connManager.getActiveNetworkInfo();
    return activeNetworkInfo != null && activeNetworkInfo.isConnected();
  }

  @Override
  public synchronized void addNetworkStateListener(@Nonnull final StateListener listener) {
    listeners.add(listener);
  }

  @Override
  public synchronized void removeNetworkStateListener(@Nonnull final StateListener listener) {
    listeners.remove(listener);
  }

  @Override
  public void onReceive(final Context context, final Intent intent) {
    // Dispatch our network change callback to a background thread,
    // since our listeners may block the main thread.
    // See https://developer.android.com/guide/components/broadcasts#effects-process-state
    final PendingResult pendingResult = goAsync();
    final NetworkStateChangedTask asyncTask =
        new NetworkStateChangedTask(pendingResult, new HashSet<>(listeners));
    asyncTask.execute();
  }

  private static final class NetworkStateChangedTask extends AsyncTask<Void, Void, Void> {
    private final Set<StateListener> listeners;
    private final PendingResult pendingResult;

    private NetworkStateChangedTask(
        final PendingResult result,
        final Set<StateListener> listeners
    ) {
      this.pendingResult = result;
      this.listeners = listeners;
    }

    @Override
    protected Void doInBackground(final Void... v) {
      for (final StateListener listener: listeners) {
        listener.onNetworkStateChanged();
      }
      return null;
    }

    @Override
    protected void onPostExecute(final Void v) {
      super.onPostExecute(v);
      pendingResult.finish();
    }
  }
}
