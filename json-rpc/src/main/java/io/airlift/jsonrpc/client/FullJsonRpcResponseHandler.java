package io.airlift.jsonrpc.client;

import io.airlift.http.client.FullJsonResponseHandler;
import io.airlift.http.client.FullJsonResponseHandler.JsonResponse;
import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
import io.airlift.http.client.ResponseHandler;
import io.airlift.json.JsonCodec;
import io.airlift.jsonrpc.model.JsonRpcResponse;

import static io.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static java.util.Objects.requireNonNull;

public class FullJsonRpcResponseHandler<T>
        implements ResponseHandler<JsonResponse<JsonRpcResponse<T>>, RuntimeException>
{
    public static <T> FullJsonRpcResponseHandler<T> createFullJsonRpcResponseHandler(JsonCodec<JsonRpcResponse<T>> jsonCodec)
    {
        return new FullJsonRpcResponseHandler<>(createFullJsonResponseHandler(jsonCodec));
    }

    private final FullJsonResponseHandler<JsonRpcResponse<T>> responseHandler;

    private FullJsonRpcResponseHandler(FullJsonResponseHandler<JsonRpcResponse<T>> responseHandler)
    {
        this.responseHandler = requireNonNull(responseHandler, "responseHandler is null");
    }

    @Override
    public JsonResponse<JsonRpcResponse<T>> handleException(Request request, Exception exception)
            throws RuntimeException
    {
        return responseHandler.handleException(request, exception);
    }

    @Override
    public JsonResponse<JsonRpcResponse<T>> handle(Request request, Response response)
            throws RuntimeException
    {
        return responseHandler.handle(request, response);
    }
}
