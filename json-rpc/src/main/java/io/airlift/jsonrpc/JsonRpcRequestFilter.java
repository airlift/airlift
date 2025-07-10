package io.airlift.jsonrpc;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Request;

public interface JsonRpcRequestFilter
{
    void filter(Request request, String rpcMethod)
            throws WebApplicationException;
}
