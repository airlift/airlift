package io.airlift.jsonrpc.model;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.Optional;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static java.util.Objects.requireNonNull;

public record JsonRpcErrorDetail(int code, String message, Optional<Object> data)
{
    public JsonRpcErrorDetail
    {
        requireNonNull(message, "message is null");
        requireNonNull(data, "data is null");
    }

    public JsonRpcErrorDetail(JsonRpcErrorCode errorCode, String message)
    {
        this(errorCode.code(), message, Optional.empty());
    }

    public JsonRpcErrorDetail(JsonRpcErrorCode errorCode, String message, Object data)
    {
        this(errorCode.code(), message, Optional.of(data));
    }

    public static WebApplicationException exception(JsonRpcErrorCode errorCode, String message)
    {
        JsonRpcErrorDetail detail = new JsonRpcErrorDetail(errorCode.code(), message, Optional.empty());
        return new WebApplicationException(Response.status(BAD_REQUEST).entity(detail).build());
    }

    public static WebApplicationException exception(JsonRpcErrorCode errorCode, String message, Object data)
    {
        JsonRpcErrorDetail detail = new JsonRpcErrorDetail(errorCode.code(), message, Optional.of(data));
        return new WebApplicationException(Response.status(BAD_REQUEST).entity(detail).build());
    }
}
