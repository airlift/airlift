package io.airlift.mcp;

import static java.util.Objects.requireNonNull;

public class McpClientException
        extends RuntimeException
{
    private final McpException mcpException;

    public McpClientException(McpException mcpException)
    {
        super(mcpException.getMessage(), mcpException);

        this.mcpException = requireNonNull(mcpException, "mcpException is null");
    }

    public McpException unwrap()
    {
        return mcpException;
    }
}
