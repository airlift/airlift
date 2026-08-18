package io.airlift.mcp;

import static java.util.Objects.requireNonNull;

public class McpClientException
        extends RuntimeException
{
    private final McpException mcpException;

    public McpClientException(McpException mcpException)
    {
        super(requireNonNull(mcpException, "mcpException is null").getMessage(), mcpException);

        this.mcpException = mcpException;
    }

    public McpException unwrap()
    {
        return mcpException;
    }
}
