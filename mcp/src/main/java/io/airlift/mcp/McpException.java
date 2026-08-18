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
        requireNonNull(errorDetail, "errorDetail is null");
        super(errorDetail.message());

        this.errorDetail = errorDetail;
        isSelfContained = false;
    }

    public McpException(Throwable cause, JsonRpcErrorDetail errorDetail)
    {
        requireNonNull(errorDetail, "errorDetail is null");
        super(errorDetail.message(), cause);

        this.errorDetail = errorDetail;
        isSelfContained = false;
    }

    private McpException(JsonRpcErrorDetail errorDetail, boolean isSelfContained)
    {
        requireNonNull(errorDetail, "errorDetail is null");
        super(errorDetail.message());

        this.errorDetail = errorDetail;
        this.isSelfContained = isSelfContained;
    }

    public McpClientException asClientException()
    {
        return new McpClientException(this);
    }

    public boolean isSelfContained()
    {
        return isSelfContained;
    }

    public JsonRpcErrorDetail errorDetail()
    {
        return errorDetail;
    }

    public static McpException exception(JsonRpcErrorCode errorCode, Throwable cause, String message)
    {
        JsonRpcErrorDetail detail = new JsonRpcErrorDetail(errorCode.code(), message, Optional.empty());
        return new McpException(cause, detail);
    }

    public static McpException exception(JsonRpcErrorCode errorCode, String message)
    {
        JsonRpcErrorDetail detail = new JsonRpcErrorDetail(errorCode.code(), message, Optional.empty());
        return new McpException(detail);
    }

    public static McpException exceptionWithData(JsonRpcErrorCode errorCode, String message, Object data)
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
        return exceptionWithData(code, message, Optional.empty());
    }

    public static McpException exceptionWithData(int code, String message, Optional<Object> data)
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
