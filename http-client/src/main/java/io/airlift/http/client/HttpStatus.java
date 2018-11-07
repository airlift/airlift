package io.airlift.http.client;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

@Beta
public enum HttpStatus
{
    CONTINUE(100, "Continue"),
    SWITCHING_PROTOCOLS(101, "Switching Protocols"),
    PROCESSING(102, "Processing"),

    OK(200, "OK"),
    CREATED(201, "Created"),
    ACCEPTED(202, "Accepted"),
    NON_AUTHORITATIVE_INFORMATION(203, "Non-Authoritative Information"),
    NO_CONTENT(204, "No Content"),
    RESET_CONTENT(205, "Reset Content"),
    PARTIAL_CONTENT(206, "Partial Content"),
    MULTI_STATUS(207, "Multi-Status"),
    ALREADY_REPORTED(208, "Already Reported"),

    MULTIPLE_CHOICES(300, "Multiple Choices"),
    MOVED_PERMANENTLY(301, "Moved Permanently"),
    FOUND(302, "Found"),
    SEE_OTHER(303, "See Other"),
    NOT_MODIFIED(304, "Not Modified"),
    USE_PROXY(305, "Use Proxy"),
    TEMPORARY_REDIRECT(307, "Temporary Redirect"),
    PERMANENT_REDIRECT(308, "Permanent Redirect"),

    BAD_REQUEST(400, "Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    PAYMENT_REQUIRED(402, "Payment Required"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not Found"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
    NOT_ACCEPTABLE(406, "Not Acceptable"),
    PROXY_AUTHENTIATION_REQUIRED(407, "Proxy Authentication Required"),
    REQUEST_TIMEOUT(408, "Request Timeout"),
    CONFLICT(409, "Conflict"),
    GONE(410, "Gone"),
    LENGTH_REQUIRED(411, "Length Required"),
    PRECONDITION_FAILED(412, "Precondition Failed"),
    REQUEST_ENTITY_TOO_LARGE(413, "Request Entity Too Large"),
    REQUEST_URI_TOO_LONG(414, "Request-URI Too Long"),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type"),
    REQUEST_RANGE_NOT_SATISFIABLE(416, "Request Range Not Satisfiable"),
    EXPECTATION_FAILED(417, "Expectation Failed"),
    IM_A_TEAPOT(418, "I'm a teapot"),
    ENHANCE_YOUR_CALM(420, "Enhance Your Calm"),
    UNPROCESSABLE_ENTITY(422, "Unprocessable Entity"),
    LOCKED(423, "Locked"),
    FAILED_DEPENDENCY(424, "Failed Dependency"),
    UPGRADE_REQUIRED(426, "Upgrade Required"),
    PRECONDITION_REQUIRED(428, "Precondition Required"),
    TOO_MANY_REQUESTS(429, "Too Many Requests"),
    REQUEST_HEADER_FIELDS_TOO_LARGE(431, "Request Header Fields Too Large"),
    UNAVAILABLE_FOR_LEGAL_REASONS(451, "Unavailable For Legal Reasons"),

    INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
    NOT_IMPLEMENTED(501, "Not Implemented"),
    BAD_GATEWAY(502, "Bad Gateway"),
    SERVICE_UNAVAILABLE(503, "Service Unavailable"),
    GATEWAY_TIMEOUT(504, "Gateway Timeout"),
    HTTP_VERSION_NOT_SUPPORTED(505, "HTTP Version Not Supported"),
    INSUFFICIENT_STORAGE(507, "Insufficient Storage"),
    LOOP_DETECTED(508, "Loop Detected"),
    BANDWIDTH_LIMIT_EXCEEDED(509, "Bandwidth Limit Exceeded"),
    NOT_EXTENDED(510, "Not Extended"),
    NETWORK_AUTHENTICATION_REQUIRED(511, "Network Authentication Required");

    public enum Family
    {
        INFORMATIONAL, SUCCESSFUL, REDIRECTION, CLIENT_ERROR, SERVER_ERROR, OTHER
    }

    private final int code;
    private final String reason;
    private final Family family;

    HttpStatus(int code, String reason)
    {
        this.code = code;
        this.reason = reason;
        this.family = familyForStatusCode(code);
    }

    public int code()
    {
        return code;
    }

    public String reason()
    {
        return toString();
    }

    public Family family()
    {
        return family;
    }

    @Override
    public String toString()
    {
        return reason;
    }

    private static final Map<Integer, HttpStatus> httpStatusCodes = buildStatusCodeMap();

    private static Map<Integer, HttpStatus> buildStatusCodeMap()
    {
        ImmutableMap.Builder<Integer, HttpStatus> map = ImmutableMap.builder();
        for (HttpStatus status : values()) {
            map.put(status.code(), status);
        }
        return map.build();
    }

    public static HttpStatus fromStatusCode(int statusCode)
    {
        return httpStatusCodes.get(statusCode);
    }

    public static Family familyForStatusCode(int code)
    {
        switch (code / 100) {
            case 1:
                return Family.INFORMATIONAL;
            case 2:
                return Family.SUCCESSFUL;
            case 3:
                return Family.REDIRECTION;
            case 4:
                return Family.CLIENT_ERROR;
            case 5:
                return Family.SERVER_ERROR;
            default:
                return Family.OTHER;
        }
    }
}
