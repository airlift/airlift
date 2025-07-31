package io.airlift.mcp.handler;

import io.airlift.mcp.session.SessionId;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.ext.Providers;
import org.glassfish.jersey.spi.ContextResolvers;

import static java.util.Objects.requireNonNull;

public record RequestContext(Request request, SessionId sessionId, Providers providers, ContextResolvers contextResolvers)
{
    public RequestContext
    {
        requireNonNull(request, "request is null");
        requireNonNull(sessionId, "sessionId is null");
        requireNonNull(providers, "providers is null");
        requireNonNull(contextResolvers, "contextResolvers is null");
    }
}
