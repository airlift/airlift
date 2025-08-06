package io.airlift.mcp.reference;

import com.google.inject.Inject;
import io.airlift.mcp.McpMetadata;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

public class ReferenceFilter
        extends HttpFilter
{
    private final HttpServletStatelessServerTransport transport;
    private final McpMetadata metadata;

    @Inject
    public ReferenceFilter(HttpServletStatelessServerTransport transport, McpMetadata metadata)
    {
        this.transport = requireNonNull(transport, "transport is null");
        this.metadata = requireNonNull(metadata, "metadata is null");
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException
    {
        if (isMcpRequest(request)) {
            transport.service(request, response);
        }
        else {
            chain.doFilter(request, response);
        }
    }

    private boolean isMcpRequest(HttpServletRequest request)
    {
        if (request.getMethod().equalsIgnoreCase("POST")) {
            return metadata.uriPath().equals(request.getRequestURI());
        }
        return false;
    }
}
