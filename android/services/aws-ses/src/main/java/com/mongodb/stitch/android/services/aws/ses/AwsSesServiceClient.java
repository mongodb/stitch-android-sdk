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

package com.mongodb.stitch.android.services.aws.ses;

import android.support.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.core.services.StitchService;
import com.mongodb.stitch.android.core.services.internal.NamedServiceClientFactory;
import com.mongodb.stitch.android.services.aws.ses.internal.AwsSesServiceClientImpl;
import com.mongodb.stitch.core.StitchAppClientInfo;
import com.mongodb.stitch.core.services.aws.ses.AwsSesSendResult;

public interface AwsSesServiceClient {

  /**
   * Sends an email.
   *
   * @param to the email address to send the email to.
   * @param from the email address to send the email from.
   * @param subject the subject of the email.
   * @param body the body text of the email.
   * @return a task containing the result of the send that completes when the send is done.
   */
  Task<AwsSesSendResult> sendEmail(
      @NonNull final String to,
      @NonNull final String from,
      @NonNull final String subject,
      @NonNull final String body);

  NamedServiceClientFactory<AwsSesServiceClient> Factory =
      new NamedServiceClientFactory<AwsSesServiceClient>() {
        @Override
        public AwsSesServiceClientImpl getClient(
            final StitchService service,
            final StitchAppClientInfo appInfo,
            final TaskDispatcher dispatcher
        ) {
          return new AwsSesServiceClientImpl(service, dispatcher);
        }
      };
}
