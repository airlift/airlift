package io.airlift.jsonrpc;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Request;

import java.util.Optional;

public interface JsonRpcRequestFilter
{
    void filter(Request request, Optional<String> rpcMethod)
            throws WebApplicationException;
}
