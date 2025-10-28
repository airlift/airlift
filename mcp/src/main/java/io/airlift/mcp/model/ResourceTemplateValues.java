package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public record ResourceTemplateValues(Map<String, String> templateValues)
{
    public ResourceTemplateValues
    {
        templateValues = ImmutableMap.copyOf(templateValues);
    }
}
