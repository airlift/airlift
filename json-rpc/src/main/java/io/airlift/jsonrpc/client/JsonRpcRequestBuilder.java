package io.airlift.jsonrpc.client;

import com.google.common.reflect.TypeToken;
import io.airlift.http.client.Request;
import io.airlift.json.JsonCodec;
import io.airlift.jsonrpc.model.JsonRpcRequest;

import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.json.JsonCodec.jsonCodec;

public class JsonRpcRequestBuilder
{
    private JsonRpcRequestBuilder() {}

    public static <T> Request.Builder jsonRpcRequest(JsonRpcRequest<T> jsonRpcMessage)
    {
        JsonCodec<JsonRpcRequest<T>> jsonCodec = jsonCodec(new TypeToken<>() {});

        return preparePost()
                .setHeader("Content-Type", "application/json")
                .setBodyGenerator(jsonBodyGenerator(jsonCodec, jsonRpcMessage));
    }
}
