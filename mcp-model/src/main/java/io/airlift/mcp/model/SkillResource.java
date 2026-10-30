package io.airlift.mcp.model;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public record SkillResource(String uri, String digest, long size)
{
    public SkillResource
    {
        requireNonNull(uri, "uri is null");
        requireNonNull(digest, "digest is null");
        checkArgument(size >= 0, "size is negative");
    }
}
