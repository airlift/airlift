package io.airlift.mcp;

import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static java.util.Objects.requireNonNull;

import io.airlift.mcp.model.JsonRpcErrorCode;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import java.util.Optional;

public class McpException extends RuntimeException {
    private final JsonRpcErrorDetail errorDetail;

    public McpException(JsonRpcErrorDetail errorDetail) {
        this.errorDetail = requireNonNull(errorDetail, "errorDetail is null");
    }

    public JsonRpcErrorDetail errorDetail() {
        return errorDetail;
    }

    public static McpException exception(JsonRpcErrorCode errorCode, String message) {
        JsonRpcErrorDetail detail = new JsonRpcErrorDetail(errorCode.code(), message, Optional.empty());
        return new McpException(detail);
    }

    public static McpException exception(JsonRpcErrorCode errorCode, String message, Object data) {
        JsonRpcErrorDetail detail = new JsonRpcErrorDetail(errorCode.code(), message, Optional.of(data));
        return new McpException(detail);
    }

    public static McpException exception(String message) {
        JsonRpcErrorDetail detail = new JsonRpcErrorDetail(INVALID_REQUEST, message, Optional.empty());
        return new McpException(detail);
    }

    public static McpException exception(int code, String message) {
        JsonRpcErrorDetail detail = new JsonRpcErrorDetail(code, message, Optional.empty());
        return new McpException(detail);
    }

    public static McpException exception(int code, String message, Optional<Object> data) {
        JsonRpcErrorDetail detail = new JsonRpcErrorDetail(code, message, data);
        return new McpException(detail);
    }
}
