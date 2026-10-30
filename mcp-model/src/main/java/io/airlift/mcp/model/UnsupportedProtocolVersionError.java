package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record UnsupportedProtocolVersionError(List<String> supported, String requested)
{
    public UnsupportedProtocolVersionError
    {
        supported = ImmutableList.copyOf(supported);
        requireNonNull(requested, "requested is null");
    }
}
