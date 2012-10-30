package com.facebook;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;

public class FacebookRequestError {

    /** Represents an invalid or unknown error code from the server. */
    public static final int INVALID_ERROR_CODE = -1;

    /**
     * Indicates that there was no valid HTTP status code returned, indicating
     * that either the error occurred locally, before the request was sent, or
     * that something went wrong with the HTTP connection. Check the exception
     * from {@link #getException()};
     */
    public static final int INVALID_HTTP_STATUS_CODE = -1;

    private static final String CODE_KEY = "code";
    private static final String BODY_KEY = "body";
    private static final String ERROR_KEY = "error";
    private static final String ERROR_TYPE_FIELD_KEY = "type";
    private static final String ERROR_CODE_FIELD_KEY = "code";
    private static final String ERROR_MESSAGE_FIELD_KEY = "message";
    private static final String ERROR_CODE_KEY = "error_code";
    private static final String ERROR_SUB_CODE_KEY = "error_subcode";
    private static final String ERROR_MSG_KEY = "error_msg";
    private static final String ERROR_REASON_KEY = "error_reason";

    private String userActionMessage;
    private Category category;
    private final int requestStatusCode;
    private final int errorCode;
    private final int subErrorCode;
    private final String errorType;
    private final String errorMessage;
    private final JSONObject requestResult;
    private final JSONObject requestResultBody;
    private final Object batchRequestResult;
    private final HttpURLConnection connection;
    private final FacebookException exception;

    private FacebookRequestError(int requestStatusCode, int errorCode,
            int subErrorCode, String errorType, String errorMessage, JSONObject requestResultBody,
            JSONObject requestResult, Object batchRequestResult, HttpURLConnection connection,
            FacebookException exception) {
        this.requestStatusCode = requestStatusCode;
        this.errorCode = errorCode;
        this.subErrorCode = subErrorCode;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.requestResultBody = requestResultBody;
        this.requestResult = requestResult;
        this.batchRequestResult = batchRequestResult;
        this.connection = connection;
        if (exception != null) {
            this.exception = exception;
        } else {
            this.exception = new FacebookServiceException(this, errorMessage);
        }

        // TODO: initialize the appAction, userAction, userActionMessage and category
    }

    private FacebookRequestError(int requestStatusCode, int errorCode,
            int subErrorCode, String errorType, String errorMessage, JSONObject requestResultBody,
            JSONObject requestResult, Object batchRequestResult, HttpURLConnection connection) {
        this(requestStatusCode, errorCode, subErrorCode, errorType, errorMessage,
                requestResultBody, requestResult, batchRequestResult, connection, null);
    }

    FacebookRequestError(HttpURLConnection connection, Exception exception) {
        this(INVALID_HTTP_STATUS_CODE, INVALID_ERROR_CODE, INVALID_ERROR_CODE,
                null, null, null, null, null, connection,
                (exception instanceof FacebookException) ?
                        (FacebookException) exception : new FacebookException(exception));
    }

    /**
     * Returns a user-friendly message for the application to present to the user.
     *
     * @return a user-friendly message to present to the user
     */
    public String getUserActionMessage() {
        return userActionMessage;
    }

    /**
     * Returns the category in which the error belongs. Applications can use the category
     * to determine how best to handle the errors (e.g. exponential backoff for retries if
     * being throttled).
     *
     * @return the category in which the error belong
     */
    public Category getCategory() {
        return category;
    }

    /**
     * Returns the HTTP status code for this particular request.
     *
     * @return the HTTP status code for the request
     */
    public int getRequestStatusCode() {
        return requestStatusCode;
    }

    /**
     * Returns the error code returned from Facebook.
     *
     * @return the error code returned from Facebook
     */
    public int getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the sub-error code returned from Facebook.
     *
     * @return the sub-error code returned from Facebook
     */
    public int getSubErrorCode() {
        return subErrorCode;
    }

    /**
     * Returns the type of error as a raw string. This is generally less useful
     * than using the {@link #getCategory()} method, but can provide further details
     * on the error.
     *
     * @return the type of error as a raw string
     */
    public String getErrorType() {
        return errorType;
    }

    /**
     * Returns the error message returned from Facebook.
     *
     * @return the error message returned from Facebook
     */
    public String getErrorMessage() {
        if (errorMessage != null) {
            return errorMessage;
        } else {
            return exception.getLocalizedMessage();
        }
    }

    /**
     * Returns the body portion of the response corresponding to the request from Facebook.
     *
     * @return the body of the response for the request
     */
    public JSONObject getRequestResultBody() {
        return requestResultBody;
    }

    /**
     * Returns the full JSON response for the corresponding request. In a non-batch request,
     * this would be the raw response in the form of a JSON object. In a batch request, this
     * result will contain the body of the response as well as the HTTP headers that pertain
     * to the specific request (in the form of a "headers" JSONArray).
     *
     * @return the full JSON response for the request
     */
    public JSONObject getRequestResult() {
        return requestResult;
    }

    /**
     * Returns the full JSON response for the batch request. If the request was not a batch
     * request, then the result from this method is the same as {@link #getRequestResult()}.
     * In case of a batch request, the result will be a JSONArray where the elements
     * correspond to the requests in the batch. Callers should check the return type against
     * either JSONObject or JSONArray and cast accordingly.
     *
     * @return the full JSON response for the batch
     */
    public Object getBatchRequestResult() {
        return batchRequestResult;
    }

    /**
     * Returns the HTTP connection that was used to make the request.
     *
     * @return the HTTP connection used to make the request
     */
    public HttpURLConnection getConnection() {
        return connection;
    }

    /**
     * Returns the exception associated with this request, if any.
     *
     * @return the exception associated with this request
     */
    public FacebookException getException() {
        return exception;
    }

    @Override
    public String toString() {
        return new StringBuilder("{HttpStatus: ")
                .append(requestStatusCode)
                .append(", errorCode: ")
                .append(errorCode)
                .append(", errorType: ")
                .append(errorType)
                .append(", errorMessage: ")
                .append(errorMessage)
                .append("}")
                .toString();
    }
    static FacebookRequestError checkResponseAndCreateError(JSONObject singleResult,
            Object batchResult, HttpURLConnection connection) {
        try {
            if (singleResult.has(CODE_KEY)) {
                int responseCode = singleResult.getInt(CODE_KEY);
                Object body = Utility.getStringPropertyAsJSON(singleResult, BODY_KEY,
                        Response.NON_JSON_RESPONSE_PROPERTY);

                if (body != null && body instanceof JSONObject) {
                    JSONObject jsonBody = (JSONObject) body;
                    // Does this response represent an error from the service? We might get either an "error"
                    // with several sub-properties, or else one or more top-level fields containing error info.
                    String errorType = null;
                    String errorMessage = null;
                    int errorCode = INVALID_ERROR_CODE;
                    int errorSubCode = INVALID_ERROR_CODE;

                    boolean hasError = false;
                    if (jsonBody.has(ERROR_KEY)) {
                        // We assume the error object is correctly formatted.
                        JSONObject error = (JSONObject) Utility.getStringPropertyAsJSON(jsonBody, ERROR_KEY, null);

                        errorType = error.optString(ERROR_TYPE_FIELD_KEY, null);
                        errorMessage = error.optString(ERROR_MESSAGE_FIELD_KEY, null);
                        errorCode = error.optInt(ERROR_CODE_FIELD_KEY, INVALID_ERROR_CODE);
                        errorSubCode = error.optInt(ERROR_SUB_CODE_KEY, INVALID_ERROR_CODE);
                        hasError = true;
                    } else if (jsonBody.has(ERROR_CODE_KEY) || jsonBody.has(ERROR_MSG_KEY)
                            || jsonBody.has(ERROR_REASON_KEY)) {
                        errorType = jsonBody.optString(ERROR_REASON_KEY, null);
                        errorMessage = jsonBody.optString(ERROR_MSG_KEY, null);
                        errorCode = jsonBody.optInt(ERROR_CODE_KEY, INVALID_ERROR_CODE);
                        errorSubCode = jsonBody.optInt(ERROR_SUB_CODE_KEY, INVALID_ERROR_CODE);
                        hasError = true;
                    }

                    if (hasError) {
                        return new FacebookRequestError(responseCode, errorCode, errorSubCode,
                                errorType, errorMessage, jsonBody, singleResult, batchResult, connection);
                    }
                }

                // If we didn't get error details, but we did get a failure response code, report it.
                if (responseCode < 200 || responseCode >= 300) {
                    return new FacebookRequestError(responseCode, INVALID_ERROR_CODE,
                            INVALID_ERROR_CODE, null, null,
                            singleResult.has(BODY_KEY) ?
                                    (JSONObject) Utility.getStringPropertyAsJSON(
                                            singleResult, BODY_KEY, Response.NON_JSON_RESPONSE_PROPERTY) : null,
                            singleResult, batchResult, connection);
                }
            }
        } catch (JSONException e) {
            // defer the throwing of a JSONException to the graph object proxy
        }
        return null;
    }

    /**
     * An enum that represents the Facebook SDK classification for the error that occurred.
     */
    public enum Category {
        /**
         * Indicates that the error category is unknown and likely represents an error
         * that is unrelated to Facebook or the Facebook SDK.
         */
        UNKNOWN,

        /**
         * Indicates that the error is authentication related, and that the app should retry
         * the request after some user action.
         */
        AUTHENTICATION_RETRY,

        /**
         * Indicates that the error is authentication related, and that the app should close
         * the session and reopen it.
         */
        AUTHENTICATION_REOPEN_SESSION,

        /** Indicates that the error is permission related. */
        PERMISSION,

        /**
         * Indicates that the error implies the server had an unexpected failure or may be
         * temporarily unavailable.
         */
        SERVER,

        /** Indicates that the error results from the server throttling the client. */
        THROTTLING,

        /**
         * Indicates that the error is Facebook-related but cannot be categorized at this time,
         * and is likely newer than the current version of the SDK.
         */
        OTHER,

        /**
         * Indicates that the error is an application error resulting in a bad or malformed
         * request to the server.
         */
        BAD_REQUEST,

        /**
         * Indicates that this is a client-side error. Examples of this can include, but are
         * not limited to, JSON parsing errors or {@link java.io.IOException}s.
         */
        CLIENT
    };

}
