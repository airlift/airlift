package io.airlift.mcp.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.handler.RequestContextProvider;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.ProgressNotification;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Optional;
import java.util.OptionalDouble;

import static io.airlift.mcp.McpException.exception;
import static java.util.Objects.requireNonNull;

public class ReferenceRequestContextProvider
        implements RequestContextProvider
{
    private final ObjectMapper objectMapper;

    @Inject
    public ReferenceRequestContextProvider(ObjectMapper objectMapper)
    {
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
    }

    @Override
    public McpRequestContext get(HttpServletRequest request, HttpServletResponse response, MessageWriter messageWriter, Optional<Object> progressToken)
    {
        return new McpRequestContext()
        {
            @Override
            public HttpServletRequest request()
            {
                return request;
            }

            @SuppressWarnings("SwitchStatementWithTooFewBranches")
            @Override
            public void sendProgress(double progress, double total, String message)
            {
                Optional<Object> appliedProgressToken = progressToken.map(token -> switch (token) {
                    case Number number -> Optional.of(number.longValue());
                    default -> progressToken;
                });

                ProgressNotification notification = new ProgressNotification(appliedProgressToken, message, OptionalDouble.of(progress), OptionalDouble.of(total));
                sendNotification("notifications/progress", Optional.of(notification));
            }

            @SuppressWarnings("SameParameterValue")
            private void sendNotification(String method, Optional<Object> params)
            {
                try {
                    JsonRpcRequest<?> notification = params.map(param -> JsonRpcRequest.buildNotification(method, param)).orElseGet(() -> JsonRpcRequest.buildNotification(method));
                    String json = objectMapper.writeValueAsString(notification);
                    messageWriter.writeMessage(json);
                    messageWriter.flushMessages();
                }
                catch (IOException e) {
                    throw exception(e);
                }
            }
        };
    }
}
