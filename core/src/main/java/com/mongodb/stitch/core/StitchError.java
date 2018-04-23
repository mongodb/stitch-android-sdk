package com.mongodb.stitch.core;

import static com.mongodb.stitch.core.internal.common.IOUtils.readAllToString;

import com.mongodb.stitch.core.internal.common.BSONUtils;
import com.mongodb.stitch.core.internal.net.ContentTypes;
import com.mongodb.stitch.core.internal.net.Headers;
import com.mongodb.stitch.core.internal.net.Response;
import java.io.IOException;
import java.util.Locale;
import org.bson.Document;

public final class StitchError {

  /**
   * Static utility method that accepts an HTTP response object, and throws the
   * {@link StitchServiceException} representing the the error in the response. If the error cannot
   * be recognized, this will throw a {@link StitchServiceException} with the "UNKNOWN" error code.
   *
   * @param response The network response.
   */

  public static void handleRequestError(final Response response) throws StitchRequestException {
    if (response.body == null) {
      throw new StitchServiceException(
          String.format(
                  Locale.ENGLISH, "received unexpected status code %d", response.statusCode
          ), StitchServiceErrorCode.UNKNOWN
      );
    }

    final String body;
    try {
      body = readAllToString(response.body);
    } catch (final Exception e) {
      throw new StitchServiceException(String.format(
              Locale.ENGLISH, "received unexpected status code %d", response.statusCode
      ), StitchServiceErrorCode.UNKNOWN);
    }

    final String errorMsg = handleRichError(response, body);
    throw new StitchServiceException(errorMsg, StitchServiceErrorCode.UNKNOWN);
  }

  /**
   * Private helper method which decodes the Stitch error from the body of an HTTP `Response`
   * object. If the error is successfully decoded, this function will throw the error for the end
   * user to eventually consume. If the error cannot be decoded, this is likely not an error from
   * the Stitch server, and this function will return an error message that the calling function
   * should use as the message of a StitchServiceException with an unknown code.
   */
  private static String handleRichError(final Response response, final String body) {
    if (!response.headers.containsKey(Headers.CONTENT_TYPE)
        || !response.headers.get(Headers.CONTENT_TYPE).equals(ContentTypes.APPLICATION_JSON)) {
      return body;
    }

    final Document doc;
    try {
      doc = BSONUtils.parseValue(body, Document.class);
    } catch (Exception e) {
      return body;
    }

    if (!doc.containsKey(Fields.ERROR)) {
      return body;
    }
    final String errorMsg = doc.getString(Fields.ERROR);
    if (!doc.containsKey(Fields.ERROR_CODE)) {
      return errorMsg;
    }

    final String errorCode = doc.getString(Fields.ERROR_CODE);
    throw new StitchServiceException(errorMsg, StitchServiceErrorCode.fromCodeName(errorCode));
  }

  private static class Fields {
    private static final String ERROR = "error";
    private static final String ERROR_CODE = "error_code";
  }
}
