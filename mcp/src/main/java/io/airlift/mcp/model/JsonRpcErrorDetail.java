package io.airlift.mcp.model;

import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record JsonRpcErrorDetail(int code, String message, Optional<Object> data)
{
    public JsonRpcErrorDetail
    {
        requireNonNull(message, "message is null");
        data = firstNonNull(data, Optional.empty());
    }

    public JsonRpcErrorDetail(JsonRpcErrorCode errorCode, String message)
    {
        this(errorCode.code(), message, Optional.empty());
    }

    public JsonRpcErrorDetail(JsonRpcErrorCode errorCode, String message, Object data)
    {
        this(errorCode.code(), message, Optional.of(data));
    }
}
