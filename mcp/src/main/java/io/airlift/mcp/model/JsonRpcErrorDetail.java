package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

import java.util.Optional;

public record JsonRpcErrorDetail(int code, String message, Optional<Object> data) {
    public JsonRpcErrorDetail {
        requireNonNull(message, "message is null");
        requireNonNull(data, "data is null");
    }

    public JsonRpcErrorDetail(JsonRpcErrorCode errorCode, String message) {
        this(errorCode.code(), message, Optional.empty());
    }

    public JsonRpcErrorDetail(JsonRpcErrorCode errorCode, String message, Object data) {
        this(errorCode.code(), message, Optional.of(data));
    }
}
