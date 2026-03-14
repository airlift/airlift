package io.airlift.jsonrpc.server;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.airlift.jsonrpc.model.JsonRpcMessage;

public interface JsonRpcRequestContext
{
    JsonMapper jsonMapper();

    SseMessageWriter sseMessageWriter();

    JsonRpcMessage jsonRpcMessage();
}
