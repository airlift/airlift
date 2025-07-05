package io.airlift.mcp;

import com.google.common.collect.ImmutableList;

import java.util.List;

public record SimpleThing(@McpDescription("Some values") List<String> values, @McpDescription("The average value") double averageValue)
{
    public SimpleThing
    {
        values = ImmutableList.copyOf(values);
    }
}
