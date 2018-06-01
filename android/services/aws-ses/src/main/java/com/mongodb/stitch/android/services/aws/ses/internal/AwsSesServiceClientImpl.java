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

package com.mongodb.stitch.android.services.aws.ses.internal;

import android.support.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.services.aws.ses.AwsSesServiceClient;
import com.mongodb.stitch.core.services.aws.ses.AwsSesSendResult;
import com.mongodb.stitch.core.services.aws.ses.internal.CoreAwsSesServiceClient;

import java.util.concurrent.Callable;

public final class AwsSesServiceClientImpl implements AwsSesServiceClient {

  private final CoreAwsSesServiceClient proxy;
  private final TaskDispatcher dispatcher;

  public AwsSesServiceClientImpl(
      final CoreAwsSesServiceClient client,
      final TaskDispatcher dispatcher
  ) {
    this.proxy = client;
    this.dispatcher = dispatcher;
  }

  /**
   * Sends an email.
   *
   * @param to the email address to send the email to.
   * @param from the email address to send the email from.
   * @param subject the subject of the email.
   * @return a task containing the result of the send that completes when the send is done.
   */
  public Task<AwsSesSendResult> sendEmail(
      @NonNull final String to,
      @NonNull final String from,
      @NonNull final String subject,
      @NonNull final String body) {
    return dispatcher.dispatchTask(new Callable<AwsSesSendResult>() {
      @Override
      public AwsSesSendResult call() {
        return proxy.sendEmail(to, from, subject, body);
      }
    });
  }
}
