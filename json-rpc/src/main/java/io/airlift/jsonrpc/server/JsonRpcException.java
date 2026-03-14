package io.airlift.jsonrpc.server;

import io.airlift.jsonrpc.model.JsonRpcErrorCode;
import io.airlift.jsonrpc.model.JsonRpcErrorDetail;

import java.util.Optional;

import static io.airlift.jsonrpc.model.JsonRpcErrorCode.INVALID_REQUEST;
import static java.util.Objects.requireNonNull;

public class JsonRpcException
        extends RuntimeException
{
    private final JsonRpcErrorDetail errorDetail;

    public JsonRpcException(JsonRpcErrorDetail errorDetail)
    {
        this.errorDetail = requireNonNull(errorDetail, "errorDetail is null");
    }

    public JsonRpcException(Throwable cause, JsonRpcErrorDetail errorDetail)
    {
        super(cause);

        this.errorDetail = requireNonNull(errorDetail, "errorDetail is null");
    }

    public JsonRpcErrorDetail errorDetail()
    {
        return errorDetail;
    }

    public static JsonRpcException exception(JsonRpcErrorCode errorCode, String message)
    {
        JsonRpcErrorDetail detail = new JsonRpcErrorDetail(errorCode.code(), message, Optional.empty());
        return new JsonRpcException(detail);
    }

    public static JsonRpcException exception(JsonRpcErrorCode errorCode, String message, Object data)
    {
        JsonRpcErrorDetail detail = new JsonRpcErrorDetail(errorCode.code(), message, Optional.of(data));
        return new JsonRpcException(detail);
    }

    public static JsonRpcException exception(String message)
    {
        JsonRpcErrorDetail detail = new JsonRpcErrorDetail(INVALID_REQUEST, message, Optional.empty());
        return new JsonRpcException(detail);
    }

    public static JsonRpcException exception(int code, String message)
    {
        JsonRpcErrorDetail detail = new JsonRpcErrorDetail(code, message, Optional.empty());
        return new JsonRpcException(detail);
    }

    public static JsonRpcException exception(int code, String message, Optional<Object> data)
    {
        JsonRpcErrorDetail detail = new JsonRpcErrorDetail(code, message, data);
        return new JsonRpcException(detail);
    }

    public static JsonRpcException exception(Throwable cause)
    {
        return exception(INVALID_REQUEST, cause);
    }

    public static JsonRpcException exception(JsonRpcErrorCode errorCode, Throwable cause)
    {
        JsonRpcErrorDetail detail = new JsonRpcErrorDetail(errorCode, Optional.ofNullable(cause.getMessage()).orElse("Internal error"), Optional.empty());
        return new JsonRpcException(cause, detail);
    }
}
