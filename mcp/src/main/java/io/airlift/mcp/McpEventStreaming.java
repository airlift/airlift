package io.airlift.mcp;

import java.time.Duration;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public record McpEventStreaming(Duration pingThreshold, Duration timeout)
{
    public static final McpEventStreaming DEFAULT = new McpEventStreaming(Duration.ofSeconds(15), Duration.ofMinutes(5));

    public McpEventStreaming
    {
        requireNonNull(pingThreshold, "pingThreshold is null");
        requireNonNull(timeout, "timeout is null");

        checkArgument(pingThreshold.isPositive(), "pingThreshold must be positive");
        checkArgument(timeout.isPositive(), "timeout must be positive");
    }
}
