package io.airlift.mcp.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public record Skill(String uri, Map<String, Object> frontmatter, SkillResources resources)
{
    public Skill
    {
        requireNonNull(uri, "uri is null");
        // YAML frontmatter can contain null values, which ImmutableMap rejects
        frontmatter = Collections.unmodifiableMap(new LinkedHashMap<>(requireNonNull(frontmatter, "frontmatter is null")));
        requireNonNull(resources, "resources is null");
    }
}
