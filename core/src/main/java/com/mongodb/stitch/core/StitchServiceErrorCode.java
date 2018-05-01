package com.mongodb.stitch.core;

import java.util.HashMap;
import java.util.Map;

/**
 * StitchServiceErrorCode represents the set of errors that can come back from a Stitch request.
 */
public enum StitchServiceErrorCode {
  MISSING_AUTH_REQ("MissingAuthReq"),
  INVALID_SESSION("InvalidSession"), // Invalid session, expired, no associated user, or app domain mismatch),
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

  private static final Map<String, StitchServiceErrorCode> codeNameToError = new HashMap<>();

  static {
    codeNameToError.put(INVALID_SESSION._code, INVALID_SESSION);
    codeNameToError.put(MISSING_AUTH_REQ._code, MISSING_AUTH_REQ);
    codeNameToError.put(INVALID_SESSION._code, INVALID_SESSION);
    codeNameToError.put(USER_APP_DOMAIN_MISMATCH._code, USER_APP_DOMAIN_MISMATCH);
    codeNameToError.put(DOMAIN_NOT_ALLOWED._code, DOMAIN_NOT_ALLOWED);
    codeNameToError.put(READ_SIZE_LIMIT_EXCEEDED._code, READ_SIZE_LIMIT_EXCEEDED);
    codeNameToError.put(INVALID_PARAMETER._code, INVALID_PARAMETER);
    codeNameToError.put(MISSING_PARAMETER._code, MISSING_PARAMETER);
    codeNameToError.put(TWILIO_ERROR._code, TWILIO_ERROR);
    codeNameToError.put(GCM_ERROR._code, GCM_ERROR);
    codeNameToError.put(HTTP_ERROR._code, HTTP_ERROR);
    codeNameToError.put(AWS_ERROR._code, AWS_ERROR);
    codeNameToError.put(MONGODB_ERROR._code, MONGODB_ERROR);
    codeNameToError.put(ARGUMENTS_NOT_ALLOWED._code, ARGUMENTS_NOT_ALLOWED);
    codeNameToError.put(FUNCTION_EXECUTION_ERROR._code, FUNCTION_EXECUTION_ERROR);
    codeNameToError.put(NO_MATCHING_RULE_FOUND._code, NO_MATCHING_RULE_FOUND);
    codeNameToError.put(INTERNAL_SERVER_ERROR._code, INTERNAL_SERVER_ERROR);
    codeNameToError.put(AUTH_PROVIDER_NOT_FOUND._code, AUTH_PROVIDER_NOT_FOUND);
    codeNameToError.put(AUTH_PROVIDER_ALREADY_EXISTS._code, AUTH_PROVIDER_ALREADY_EXISTS);
    codeNameToError.put(SERVICE_NOT_FOUND._code, SERVICE_NOT_FOUND);
    codeNameToError.put(SERVICE_TYPE_NOT_FOUND._code, SERVICE_TYPE_NOT_FOUND);
    codeNameToError.put(SERVICE_ALREADY_EXISTS._code, SERVICE_ALREADY_EXISTS);
    codeNameToError.put(SERVICE_COMMAND_NOT_FOUND._code, SERVICE_COMMAND_NOT_FOUND);
    codeNameToError.put(VALUE_NOT_FOUND._code, VALUE_NOT_FOUND);
    codeNameToError.put(VALUE_ALREADY_EXISTS._code, VALUE_ALREADY_EXISTS);
    codeNameToError.put(VALUE_DUPLICATE_NAME._code, VALUE_DUPLICATE_NAME);
    codeNameToError.put(FUNCTION_NOT_FOUND._code, FUNCTION_NOT_FOUND);
    codeNameToError.put(FUNCTION_ALREADY_EXISTS._code, FUNCTION_ALREADY_EXISTS);
    codeNameToError.put(FUNCTION_DUPLICATE_NAME._code, FUNCTION_DUPLICATE_NAME);
    codeNameToError.put(FUNCTION_SYNTAX_ERROR._code, FUNCTION_SYNTAX_ERROR);
    codeNameToError.put(FUNCTION_INVALID._code, FUNCTION_INVALID);
    codeNameToError.put(INCOMING_WEBHOOK_NOT_FOUND._code, INCOMING_WEBHOOK_NOT_FOUND);
    codeNameToError.put(INCOMING_WEBHOOK_ALREADY_EXISTS._code, INCOMING_WEBHOOK_ALREADY_EXISTS);
    codeNameToError.put(INCOMING_WEBHOOK_DUPLICATE_NAME._code, INCOMING_WEBHOOK_DUPLICATE_NAME);
    codeNameToError.put(RULE_NOT_FOUND._code, RULE_NOT_FOUND);
    codeNameToError.put(API_KEY_NOT_FOUND._code, API_KEY_NOT_FOUND);
    codeNameToError.put(RULE_ALREADY_EXISTS._code, RULE_ALREADY_EXISTS);
    codeNameToError.put(RULE_DUPLICATE_NAME._code, RULE_DUPLICATE_NAME);
    codeNameToError.put(AUTH_PROVIDER_DUPLICATE_NAME._code, AUTH_PROVIDER_DUPLICATE_NAME);
    codeNameToError.put(RESTRICTED_HOST._code, RESTRICTED_HOST);
    codeNameToError.put(API_KEY_ALREADY_EXISTS._code, API_KEY_ALREADY_EXISTS);
    codeNameToError.put(INCOMING_WEBHOOK_AUTH_FAILED._code, INCOMING_WEBHOOK_AUTH_FAILED);
    codeNameToError.put(EXECUTION_TIME_LIMIT_EXCEEDED._code, EXECUTION_TIME_LIMIT_EXCEEDED);
    codeNameToError.put(NOT_CALLABLE._code, NOT_CALLABLE);
    codeNameToError.put(USER_ALREADY_CONFIRMED._code, USER_ALREADY_CONFIRMED);
    codeNameToError.put(USER_NOT_FOUND._code, USER_NOT_FOUND);
    codeNameToError.put(USER_DISABLED._code, USER_DISABLED);
  }

  private final String _code;

  public String getCodeName() {
    return _code;
  }

  StitchServiceErrorCode(final String codeName) {
    _code = codeName;
  }

  public static synchronized StitchServiceErrorCode fromCodeName(final String codeName) {
    if (!codeNameToError.containsKey(codeName)) {
      return UNKNOWN;
    }
    return codeNameToError.get(codeName);
  }
}
