package io.airlift.mcp.internal;

import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.ErrorHandler;
import io.airlift.mcp.McpClientException;
import io.airlift.mcp.McpException;
import io.airlift.mcp.model.JsonRpcErrorCode;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.JsonRpcMessage;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.WebApplicationException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Throwables.getRootCause;
import static io.airlift.mcp.model.Constants.MESSAGE_WRITER_ATTRIBUTE;
import static io.airlift.mcp.model.Constants.RPC_MESSAGE_ATTRIBUTE;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.model.JsonRpcErrorCode.REQUEST_TIMEOUT;
import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class InternalErrorHandler
        implements ErrorHandler
{
    private static final Logger log = Logger.get(InternalErrorHandler.class);

    private final ObjectMapper objectMapper;

    @Inject
    public InternalErrorHandler(ObjectMapper objectMapper)
    {
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
    }

    @Override
    public void handleException(HttpServletRequest request, HttpServletResponse response, Throwable throwable)
    {
        logException(throwable);

        handleException(request, response, throwable, false);
    }

    public void handleException(HttpServletRequest request, HttpServletResponse response, Throwable throwable, boolean isRootCause)
    {
        switch (throwable) {
            case WebApplicationException webApplicationException -> {
                if (webApplicationException.getCause() instanceof McpException mcpException) {
                    throw mcpException;
                }
                throw webApplicationException;
            }

            case InterruptedException _ -> {
                Thread.currentThread().interrupt();
                writeErrorResponse(request, response, new JsonRpcErrorDetail(INTERNAL_ERROR, messageFromException(throwable)), false);
            }

            case TimeoutException _ -> writeErrorResponse(request, response, new JsonRpcErrorDetail(REQUEST_TIMEOUT, messageFromException(throwable)), false);

            case IllegalStateException _ -> writeErrorResponse(request, response, new JsonRpcErrorDetail(INVALID_REQUEST, messageFromException(throwable)), false);

            case McpException mcpException -> writeErrorResponse(request, response, mcpException.errorDetail(), false);

            case McpClientException mcpClientException -> writeErrorResponse(request, response, mcpClientException.unwrap().errorDetail(), true);

            default -> {
                if (isRootCause) {
                    writeErrorResponse(request, response, new JsonRpcErrorDetail(INTERNAL_ERROR, messageFromException(throwable)), false);
                }
                else {
                    handleException(request, response, getRootCause(throwable), true);
                }
            }
        }
    }

    public void writeErrorResponse(HttpServletRequest request, HttpServletResponse response, JsonRpcErrorDetail error, boolean isClientError)
    {
        JsonRpcMessage requestMessage = (JsonRpcMessage) request.getAttribute(RPC_MESSAGE_ATTRIBUTE);
        Object requestId = switch (requestMessage) {
            case JsonRpcRequest<?> rpcRequest -> rpcRequest.id();
            case JsonRpcResponse<?> rpcResponse -> rpcResponse.id();
            case null -> null;
        };

        JsonRpcResponse<?> rpcResponse = new JsonRpcResponse<>(requestId, Optional.of(error), Optional.empty());
        String rpcResponseJson;
        try {
            rpcResponseJson = objectMapper.writeValueAsString(rpcResponse);
        }
        catch (JacksonException e) {
            log.error(e, "Failed to serialize error response: %s", rpcResponse);
            throw new McpException(e, error);
        }

        InternalMessageWriter messageWriter = (InternalMessageWriter) request.getAttribute(MESSAGE_WRITER_ATTRIBUTE);
        if (messageWriter != null && messageWriter.hasBeenUpgraded()) {
            messageWriter.write(rpcResponseJson);
            messageWriter.flushMessages();
        }
        else if (!response.isCommitted()) {
            writeResponseError(response, rpcResponseJson, rpcResponse, isClientError);
        }
        else {
            log.warn("Response already committed, cannot write error response: %s", rpcResponseJson);
            throw new McpException(error);
        }
    }

    public void writeResponseError(HttpServletResponse response, String rpcResponseJson, JsonRpcResponse<?> rpcResponse, boolean isClientError)
    {
        int errorCode = rpcResponse.error().map(JsonRpcErrorDetail::code).orElse(0);
        if (isClientError || (errorCode == JsonRpcErrorCode.RESOURCE_NOT_FOUND.code())) {
            response.setStatus(SC_OK);
        }
        else {
            response.setStatus((errorCode < 0) ? SC_BAD_REQUEST : SC_INTERNAL_SERVER_ERROR);
        }

        response.setContentType(APPLICATION_JSON);
        response.setCharacterEncoding(UTF_8);

        try {
            PrintWriter printWriter = response.getWriter();
            printWriter.write(rpcResponseJson);
            printWriter.flush();
        }
        catch (IOException e) {
            log.error(e, "Failed to serialize error response: %s", rpcResponse);
            throw new McpException(e, new JsonRpcErrorDetail(INTERNAL_ERROR, "Failed to write error response"));
        }
    }

    public void logException(Throwable throwable)
    {
        log.debug(throwable, "");
    }

    public String messageFromException(Throwable throwable)
    {
        return Optional.ofNullable(throwable.getMessage())
                .orElse("Unknown error");
    }
}
