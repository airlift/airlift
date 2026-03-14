package io.airlift.jsonrpc.server;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@FunctionalInterface
public interface JsonRpcHandler
{
    void handle(JsonRpcRequestContext requestContext, HttpServletRequest request, HttpServletResponse response);
}
