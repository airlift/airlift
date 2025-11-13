package io.airlift.mcp.reference;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * <p>
 *     Intercept output from {@link io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport}. This class
 *     has deep internal knowledge of how {@link io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport} works.
 *     {@link io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport} will think it's outputting a single
 *     JSON RPC response but this class, via {@link SsePrintWriter}, will convert that output into an SSE stream which allows
 *     us to support server-to-client notifications during request processing. NOTE: if {@link io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport}
 *     should change, then this class and {@link SsePrintWriter} will need to change.
 * </p>
 *
 * <p>
 *     Note: I've been trying to get traction in the MCP project for native support with this PR and others.
 *     <a href="https://github.com/modelcontextprotocol/java-sdk/pull/472">pull/472</a>. Additionally, I've started this discussion:
 *     <a href="https://github.com/modelcontextprotocol/java-sdk/discussions/665">discussions/665</a>
 * </p>
 */
class SseResponseWrapper
        extends HttpServletResponseWrapper
{
    private SsePrintWriter ssePrintWriter;

    SseResponseWrapper(HttpServletResponse response)
    {
        super(response);
    }

    @Override
    public PrintWriter getWriter()
            throws IOException
    {
        int status = getStatus();
        boolean useSse = (status >= SC_OK) && (status < SC_MULTIPLE_CHOICES);
        if (useSse) {
            if (ssePrintWriter == null) {
                ssePrintWriter = new SsePrintWriter(super.getWriter(), () -> super.setContentType("text/event-stream"));
            }

            return ssePrintWriter;
        }

        return super.getWriter();
    }
}
