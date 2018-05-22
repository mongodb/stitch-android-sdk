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

package com.mongodb.stitch.core;

import java.util.HashMap;
import java.util.Map;

/** StitchServiceErrorCode represents the set of errors that can come back from a Stitch request. */
public enum StitchServiceErrorCode {
  MISSING_AUTH_REQ("MissingAuthReq"),
  INVALID_SESSION(
      "InvalidSession"), // Invalid session, expired, no associated user, or app domain mismatch),
  USER_APP_DOMAIN_MISMATCH("UserAppDomainMismatch"),
  DOMAIN_NOT_ALLOWED("DomainNotAllowed"),
  READ_SIZE_LIMIT_EXCEEDED("ReadSizeLimitExceeded"),
  INVALID_PARAMETER("InvalidParameter"),
  MISSING_PARAMETER("MissingParameter"),
  TWILIO_ERROR("TwilioError"),
  GCM_ERROR("GCMError"),
  HTTP_ERROR("HTTPError"),
  AWS_ERROR("AWSError"),
  MONGODB_ERROR("MongoDBError"),
  ARGUMENTS_NOT_ALLOWED("ArgumentsNotAllowed"),
  FUNCTION_EXECUTION_ERROR("FunctionExecutionError"),
  NO_MATCHING_RULE_FOUND("NoMatchingRuleFound"),
  INTERNAL_SERVER_ERROR("InternalServerError"),
  AUTH_PROVIDER_NOT_FOUND("AuthProviderNotFound"),
  AUTH_PROVIDER_ALREADY_EXISTS("AuthProviderAlreadyExists"),
  SERVICE_NOT_FOUND("ServiceNotFound"),
  SERVICE_TYPE_NOT_FOUND("ServiceTypeNotFound"),
  SERVICE_ALREADY_EXISTS("ServiceAlreadyExists"),
  SERVICE_COMMAND_NOT_FOUND("ServiceCommandNotFound"),
  VALUE_NOT_FOUND("ValueNotFound"),
  VALUE_ALREADY_EXISTS("ValueAlreadyExists"),
  VALUE_DUPLICATE_NAME("ValueDuplicateName"),
  FUNCTION_NOT_FOUND("FunctionNotFound"),
  FUNCTION_ALREADY_EXISTS("FunctionAlreadyExists"),
  FUNCTION_DUPLICATE_NAME("FunctionDuplicateName"),
  FUNCTION_SYNTAX_ERROR("FunctionSyntaxError"),
  FUNCTION_INVALID("FunctionInvalid"),
  INCOMING_WEBHOOK_NOT_FOUND("IncomingWebhookNotFound"),
  INCOMING_WEBHOOK_ALREADY_EXISTS("IncomingWebhookAlreadyExists"),
  INCOMING_WEBHOOK_DUPLICATE_NAME("IncomingWebhookDuplicateName"),
  RULE_NOT_FOUND("RuleNotFound"),
  API_KEY_NOT_FOUND("APIKeyNotFound"),
  RULE_ALREADY_EXISTS("RuleAlreadyExists"),
  RULE_DUPLICATE_NAME("RuleDuplicateName"),
  AUTH_PROVIDER_DUPLICATE_NAME("AuthProviderDuplicateName"),
  RESTRICTED_HOST("RestrictedHost"),
  API_KEY_ALREADY_EXISTS("APIKeyAlreadyExists"),
  INCOMING_WEBHOOK_AUTH_FAILED("IncomingWebhookAuthFailed"),
  EXECUTION_TIME_LIMIT_EXCEEDED("ExecutionTimeLimitExceeded"),
  NOT_CALLABLE("FunctionNotCallable"),
  USER_ALREADY_CONFIRMED("UserAlreadyConfirmed"),
  USER_NOT_FOUND("UserNotFound"),
  USER_DISABLED("UserDisabled"),
  UNKNOWN("Unknown");

  private static final Map<String, StitchServiceErrorCode> CODE_NAME_TO_ERROR = new HashMap<>();

  static {
    CODE_NAME_TO_ERROR.put(INVALID_SESSION.code, INVALID_SESSION);
    CODE_NAME_TO_ERROR.put(MISSING_AUTH_REQ.code, MISSING_AUTH_REQ);
    CODE_NAME_TO_ERROR.put(INVALID_SESSION.code, INVALID_SESSION);
    CODE_NAME_TO_ERROR.put(USER_APP_DOMAIN_MISMATCH.code, USER_APP_DOMAIN_MISMATCH);
    CODE_NAME_TO_ERROR.put(DOMAIN_NOT_ALLOWED.code, DOMAIN_NOT_ALLOWED);
    CODE_NAME_TO_ERROR.put(READ_SIZE_LIMIT_EXCEEDED.code, READ_SIZE_LIMIT_EXCEEDED);
    CODE_NAME_TO_ERROR.put(INVALID_PARAMETER.code, INVALID_PARAMETER);
    CODE_NAME_TO_ERROR.put(MISSING_PARAMETER.code, MISSING_PARAMETER);
    CODE_NAME_TO_ERROR.put(TWILIO_ERROR.code, TWILIO_ERROR);
    CODE_NAME_TO_ERROR.put(GCM_ERROR.code, GCM_ERROR);
    CODE_NAME_TO_ERROR.put(HTTP_ERROR.code, HTTP_ERROR);
    CODE_NAME_TO_ERROR.put(AWS_ERROR.code, AWS_ERROR);
    CODE_NAME_TO_ERROR.put(MONGODB_ERROR.code, MONGODB_ERROR);
    CODE_NAME_TO_ERROR.put(ARGUMENTS_NOT_ALLOWED.code, ARGUMENTS_NOT_ALLOWED);
    CODE_NAME_TO_ERROR.put(FUNCTION_EXECUTION_ERROR.code, FUNCTION_EXECUTION_ERROR);
    CODE_NAME_TO_ERROR.put(NO_MATCHING_RULE_FOUND.code, NO_MATCHING_RULE_FOUND);
    CODE_NAME_TO_ERROR.put(INTERNAL_SERVER_ERROR.code, INTERNAL_SERVER_ERROR);
    CODE_NAME_TO_ERROR.put(AUTH_PROVIDER_NOT_FOUND.code, AUTH_PROVIDER_NOT_FOUND);
    CODE_NAME_TO_ERROR.put(AUTH_PROVIDER_ALREADY_EXISTS.code, AUTH_PROVIDER_ALREADY_EXISTS);
    CODE_NAME_TO_ERROR.put(SERVICE_NOT_FOUND.code, SERVICE_NOT_FOUND);
    CODE_NAME_TO_ERROR.put(SERVICE_TYPE_NOT_FOUND.code, SERVICE_TYPE_NOT_FOUND);
    CODE_NAME_TO_ERROR.put(SERVICE_ALREADY_EXISTS.code, SERVICE_ALREADY_EXISTS);
    CODE_NAME_TO_ERROR.put(SERVICE_COMMAND_NOT_FOUND.code, SERVICE_COMMAND_NOT_FOUND);
    CODE_NAME_TO_ERROR.put(VALUE_NOT_FOUND.code, VALUE_NOT_FOUND);
    CODE_NAME_TO_ERROR.put(VALUE_ALREADY_EXISTS.code, VALUE_ALREADY_EXISTS);
    CODE_NAME_TO_ERROR.put(VALUE_DUPLICATE_NAME.code, VALUE_DUPLICATE_NAME);
    CODE_NAME_TO_ERROR.put(FUNCTION_NOT_FOUND.code, FUNCTION_NOT_FOUND);
    CODE_NAME_TO_ERROR.put(FUNCTION_ALREADY_EXISTS.code, FUNCTION_ALREADY_EXISTS);
    CODE_NAME_TO_ERROR.put(FUNCTION_DUPLICATE_NAME.code, FUNCTION_DUPLICATE_NAME);
    CODE_NAME_TO_ERROR.put(FUNCTION_SYNTAX_ERROR.code, FUNCTION_SYNTAX_ERROR);
    CODE_NAME_TO_ERROR.put(FUNCTION_INVALID.code, FUNCTION_INVALID);
    CODE_NAME_TO_ERROR.put(INCOMING_WEBHOOK_NOT_FOUND.code, INCOMING_WEBHOOK_NOT_FOUND);
    CODE_NAME_TO_ERROR.put(INCOMING_WEBHOOK_ALREADY_EXISTS.code, INCOMING_WEBHOOK_ALREADY_EXISTS);
    CODE_NAME_TO_ERROR.put(INCOMING_WEBHOOK_DUPLICATE_NAME.code, INCOMING_WEBHOOK_DUPLICATE_NAME);
    CODE_NAME_TO_ERROR.put(RULE_NOT_FOUND.code, RULE_NOT_FOUND);
    CODE_NAME_TO_ERROR.put(API_KEY_NOT_FOUND.code, API_KEY_NOT_FOUND);
    CODE_NAME_TO_ERROR.put(RULE_ALREADY_EXISTS.code, RULE_ALREADY_EXISTS);
    CODE_NAME_TO_ERROR.put(RULE_DUPLICATE_NAME.code, RULE_DUPLICATE_NAME);
    CODE_NAME_TO_ERROR.put(AUTH_PROVIDER_DUPLICATE_NAME.code, AUTH_PROVIDER_DUPLICATE_NAME);
    CODE_NAME_TO_ERROR.put(RESTRICTED_HOST.code, RESTRICTED_HOST);
    CODE_NAME_TO_ERROR.put(API_KEY_ALREADY_EXISTS.code, API_KEY_ALREADY_EXISTS);
    CODE_NAME_TO_ERROR.put(INCOMING_WEBHOOK_AUTH_FAILED.code, INCOMING_WEBHOOK_AUTH_FAILED);
    CODE_NAME_TO_ERROR.put(EXECUTION_TIME_LIMIT_EXCEEDED.code, EXECUTION_TIME_LIMIT_EXCEEDED);
    CODE_NAME_TO_ERROR.put(NOT_CALLABLE.code, NOT_CALLABLE);
    CODE_NAME_TO_ERROR.put(USER_ALREADY_CONFIRMED.code, USER_ALREADY_CONFIRMED);
    CODE_NAME_TO_ERROR.put(USER_NOT_FOUND.code, USER_NOT_FOUND);
    CODE_NAME_TO_ERROR.put(USER_DISABLED.code, USER_DISABLED);
  }

  private final String code;

  StitchServiceErrorCode(final String codeName) {
    code = codeName;
  }

  /**
   * Creates an error code from the given name.
   *
   * @param codeName the API code name to look up.
   * @return the error that maps to the code name.
   */
  public static synchronized StitchServiceErrorCode fromCodeName(final String codeName) {
    if (!CODE_NAME_TO_ERROR.containsKey(codeName)) {
      return UNKNOWN;
    }
    return CODE_NAME_TO_ERROR.get(codeName);
  }

  /**
   * Gets the name of this error as the API refers to it.
   *
   * @return the name of this error as the API refers to it.
   */
  public String getCodeName() {
    return code;
  }
}
