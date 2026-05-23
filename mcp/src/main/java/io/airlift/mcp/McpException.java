package io.airlift.mcp;

import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.JsonRpcErrorCode;
import io.airlift.mcp.model.JsonRpcErrorDetail;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.model.JsonRpcErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY;
import static java.util.Objects.requireNonNull;

public class McpException
        extends RuntimeException
{
    private final JsonRpcErrorDetail errorDetail;
    private final boolean isSelfContained;

    public McpException(JsonRpcErrorDetail errorDetail)
    {
        this.errorDetail = requireNonNull(errorDetail, "errorDetail is null");
        isSelfContained = false;
    }

    public McpException(Throwable cause, JsonRpcErrorDetail errorDetail)
    {
        super(cause);

        this.errorDetail = requireNonNull(errorDetail, "errorDetail is null");
        isSelfContained = false;
    }

    private McpException(JsonRpcErrorDetail errorDetail, boolean isSelfContained)
    {
        this.errorDetail = requireNonNull(errorDetail, "errorDetail is null");
        this.isSelfContained = isSelfContained;
    }

    public boolean isSelfContained()
    {
        return isSelfContained;
    }

    public JsonRpcErrorDetail errorDetail()
    {
        return errorDetail;
    }

    public static McpException exception(JsonRpcErrorCode errorCode, String message)
    {
        JsonRpcErrorDetail detail = new JsonRpcErrorDetail(errorCode.code(), message, Optional.empty());
        return new McpException(detail);
    }

    public static McpException exception(JsonRpcErrorCode errorCode, String message, Object data)
    {
        JsonRpcErrorDetail detail = new JsonRpcErrorDetail(errorCode.code(), message, Optional.of(data));
        return new McpException(detail);
    }

    public static McpException exception(String message)
    {
        JsonRpcErrorDetail detail = new JsonRpcErrorDetail(INVALID_REQUEST, message, Optional.empty());
        return new McpException(detail);
    }

    public static McpException exception(int code, String message)
    {
        JsonRpcErrorDetail detail = new JsonRpcErrorDetail(code, message, Optional.empty());
        return new McpException(detail);
    }

    public static McpException exception(int code, String message, Optional<Object> data)
    {
        JsonRpcErrorDetail detail = new JsonRpcErrorDetail(code, message, data);
        return new McpException(detail);
    }

    public static McpException exception(Throwable cause)
    {
        return exception(INVALID_REQUEST, cause);
    }

    public static McpException exception(JsonRpcErrorCode errorCode, Throwable cause)
    {
        JsonRpcErrorDetail detail = new JsonRpcErrorDetail(errorCode, Optional.ofNullable(cause.getMessage()).orElse("Internal error"), Optional.empty());
        return new McpException(cause, detail);
    }

    public static McpException clientCapabilityError(ClientCapabilities clientCapabilities)
    {
        Map<String, Object> requiredCapabilities = ImmutableMap.of("requiredCapabilities", clientCapabilities);
        JsonRpcErrorDetail errorDetail = new JsonRpcErrorDetail(MISSING_REQUIRED_CLIENT_CAPABILITY, "Client capabilities error", requiredCapabilities);
        return new McpException(errorDetail, true);
    }
}
