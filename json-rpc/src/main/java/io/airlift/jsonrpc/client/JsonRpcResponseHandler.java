package io.airlift.jsonrpc.client;

import io.airlift.http.client.JsonResponseHandler;
import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
import io.airlift.http.client.ResponseHandler;
import io.airlift.json.JsonCodec;
import io.airlift.jsonrpc.model.JsonRpcResponse;

import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static java.util.Objects.requireNonNull;

public class JsonRpcResponseHandler<T>
        implements ResponseHandler<JsonRpcResponse<T>, RuntimeException>
{
    public static <T> JsonRpcResponseHandler<T> createJsonRpcResponseHandler(JsonCodec<JsonRpcResponse<T>> jsonCodec)
    {
        return new JsonRpcResponseHandler<>(createJsonResponseHandler(jsonCodec));
    }

    public static <T> JsonRpcResponseHandler<T> createJsonRpcResponseHandler(JsonCodec<JsonRpcResponse<T>> jsonCodec, int firstSuccessfulResponseCode, int... otherSuccessfulResponseCodes)
    {
        return new JsonRpcResponseHandler<>(createJsonResponseHandler(jsonCodec, firstSuccessfulResponseCode, otherSuccessfulResponseCodes));
    }

    private final JsonResponseHandler<JsonRpcResponse<T>> responseHandler;

    private JsonRpcResponseHandler(JsonResponseHandler<JsonRpcResponse<T>> responseHandler)
    {
        this.responseHandler = requireNonNull(responseHandler, "responseHandler is null");
    }

    @Override
    public JsonRpcResponse<T> handleException(Request request, Exception exception)
            throws RuntimeException
    {
        return responseHandler.handleException(request, exception);
    }

    @Override
    public JsonRpcResponse<T> handle(Request request, Response response)
            throws RuntimeException
    {
        return responseHandler.handle(request, response);
    }
}
