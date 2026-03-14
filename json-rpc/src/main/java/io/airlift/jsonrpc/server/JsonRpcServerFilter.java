package io.airlift.jsonrpc.server;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.base.Suppliers;
import com.google.inject.Inject;
import io.airlift.jsonrpc.model.JsonRpcMessage;
import io.airlift.jsonrpc.model.JsonRpcResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.base.Throwables.getRootCause;
import static io.airlift.jsonrpc.model.JsonRpcMessage.readRequestMessage;
import static io.airlift.jsonrpc.model.JsonRpcMessage.writeJsonRpcResponse;
import static java.util.Objects.requireNonNull;

public class JsonRpcServerFilter
        extends HttpFilter
{
    private final JsonRpcRequestHandler requestHandler;
    private final JsonMapper jsonMapper;
    private final String uriPath;

    @Inject
    public JsonRpcServerFilter(
            JsonRpcMetadata metadata,
            JsonRpcRequestHandler requestHandler,
            JsonMapper jsonMapper)
    {
        uriPath = metadata.uriPath();
        this.requestHandler = requireNonNull(requestHandler, "requestHandler is null");
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException
    {
        if (isOurRequest(request)) {
            InternalRequestContext requestContext = new InternalRequestContext(request, response, jsonMapper);

            try {
                requestHandler.handle(requestContext, request, response);
            }
            catch (Throwable throwable) {
                if (getRootCause(throwable) instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }

                if (!response.isCommitted() && (throwable instanceof JsonRpcException jsonRpcException) && requestContext.requestRead) {
                    JsonRpcResponse<Object> jsonRpcResponse = new JsonRpcResponse<>(requestContext.jsonRpcMessage().id(), Optional.of(jsonRpcException.errorDetail()), Optional.empty());
                    writeJsonRpcResponse(requestContext, jsonRpcResponse);
                }
                else if (throwable instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                else {
                    throw new RuntimeException(throwable);
                }
            }
        }
        else {
            chain.doFilter(request, response);
        }
    }

    private boolean isOurRequest(HttpServletRequest request)
    {
        return uriPath.equals(request.getRequestURI());
    }

    private static class InternalRequestContext
            implements JsonRpcRequestContext
    {
        private final Supplier<JsonRpcMessage> jsonRpcMessageSupplier;
        private final JsonMapper jsonMapper;
        private final SseMessageWriter sseMessageWriter;
        private volatile boolean requestRead;

        private InternalRequestContext(HttpServletRequest request, HttpServletResponse response, JsonMapper jsonMapper)
        {
            this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
            this.sseMessageWriter = new SseMessageWriter(response);

            jsonRpcMessageSupplier = Suppliers.memoize(() -> {
                requestRead = true;
                return readRequestMessage(this, request);
            });
        }

        @Override
        public JsonRpcMessage jsonRpcMessage()
        {
            return jsonRpcMessageSupplier.get();
        }

        @Override
        public JsonMapper jsonMapper()
        {
            return jsonMapper;
        }

        @Override
        public SseMessageWriter sseMessageWriter()
        {
            return sseMessageWriter;
        }
    }
}
