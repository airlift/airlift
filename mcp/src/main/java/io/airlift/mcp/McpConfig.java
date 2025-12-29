package io.airlift.mcp;

import io.airlift.configuration.Config;
import jakarta.validation.constraints.Min;

public class McpConfig
{
    private int defaultPageSize = 25;

    @Min(1)
    public int getDefaultPageSize()
    {
        return defaultPageSize;
    }

    @Config("mcp.page-size")
    public McpConfig setDefaultPageSize(int defaultPageSize)
    {
        this.defaultPageSize = defaultPageSize;
        return this;
    }
}
