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
import java.io.PrintWriter;
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
    public McpRequestContext get(HttpServletRequest request, HttpServletResponse response, Optional<Object> progressToken)
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
                    case Number n -> Optional.of(n.longValue());
                    default -> progressToken;
                });

                ProgressNotification notification = new ProgressNotification(appliedProgressToken, message, OptionalDouble.of(progress), OptionalDouble.of(total));
                sendNotification("notifications/progress", Optional.of(notification));
            }

            private void sendNotification(String method, Optional<Object> params)
            {
                try {
                    PrintWriter writer = response.getWriter();
                    if (!(writer instanceof SsePrintWriter ssePrintWriter)) {
                        throw exception("Response writer is not an SsePrintWriter");
                    }
                    JsonRpcRequest<?> notification = params.map(p -> JsonRpcRequest.buildNotification(method, p)).orElseGet(() -> JsonRpcRequest.buildNotification(method));
                    String json = objectMapper.writeValueAsString(notification);
                    ssePrintWriter.writeMessage(json);
                    ssePrintWriter.flush();
                }
                catch (IOException e) {
                    throw exception(e);
                }
            }
        };
    }
}
