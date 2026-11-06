package io.airlift.mcp.model;

import io.airlift.jackson.JacksonSubType;

public final class McpJacksonSubTypes
{
    private McpJacksonSubTypes() {}

    public static JacksonSubType buildJacksonSubType()
    {
        return JacksonSubType.builder()
                .forBase(Content.class, "type")
                .add(Content.TextContent.class, "text")
                .add(Content.ImageContent.class, "image")
                .add(Content.AudioContent.class, "audio")
                .add(Content.EmbeddedResource.class, "resource")
                .add(Content.ResourceLink.class, "resource_link")
                .forBase(CompleteReference.class, "type")
                .add(CompleteReference.PromptReference.class, "ref/prompt")
                .add(CompleteReference.ResourceReference.class, "ref/resource")
                .build();
    }
}
