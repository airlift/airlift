package io.airlift.mcp.reflection;

import io.airlift.mcp.McpResource;
import io.airlift.mcp.McpResourceTemplate;
import io.airlift.mcp.McpSkill;
import io.airlift.mcp.McpSkillTemplate;
import io.airlift.mcp.model.Role;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.airlift.mcp.McpSkillBuilder.mcpSkillBuilder;
import static io.airlift.mcp.McpSkillBuilder.skillUri;
import static io.airlift.mcp.model.Constants.SKILL_MIME_TYPE;

public interface SkillsHelper
{
    static McpResource resourceFromSkill(McpSkill skill)
    {
        // this will validate the values
        mcpSkillBuilder(skill.name(), skill.description()).buildSkill();

        String basePath = buildBasePath(skill.name(), skill.parentPath());
        String uri = skillUri(basePath);

        return new McpResource()
        {
            @Override
            public String name()
            {
                return skill.name();
            }

            @Override
            public String uri()
            {
                return uri;
            }

            @Override
            public String mimeType()
            {
                return SKILL_MIME_TYPE;
            }

            @Override
            public String description()
            {
                return skill.description();
            }

            @Override
            public String[] icons()
            {
                return skill.icons();
            }

            @Override
            public long size()
            {
                return skill.size();
            }

            @Override
            public Role[] audience()
            {
                return skill.audience();
            }

            @Override
            public double priority()
            {
                return skill.priority();
            }

            @Override
            public Class<? extends Annotation> annotationType()
            {
                return McpResource.class;
            }

            @Override
            public int hashCode()
            {
                return Objects.hash(
                        name(),
                        uri,
                        mimeType(),
                        description(),
                        Arrays.hashCode(icons()),
                        size(),
                        Arrays.hashCode(audience()),
                        priority());
            }

            @Override
            public boolean equals(Object obj)
            {
                return ((this == obj) || (
                        (obj instanceof McpResource mcpResource)
                                && name().equals(mcpResource.name())
                                && mimeType().equals(mcpResource.mimeType())
                                && description().equals(mcpResource.description())
                                && Arrays.equals(icons(), mcpResource.icons())
                                && uri.equals(mcpResource.uri())
                                && size() == mcpResource.size()
                                && Arrays.equals(audience(), mcpResource.audience())
                                && Double.compare(priority(), mcpResource.priority()) == 0));
            }
        };
    }

    static McpResourceTemplate resourceTemplateFromSkillTemplate(McpSkillTemplate skillTemplate)
    {
        // this will validate the values
        mcpSkillBuilder(skillTemplate.name(), skillTemplate.description()).buildSkill();

        String templatePath = buildBasePath(skillTemplate.name(), skillTemplate.uriTemplateParts());
        String uriTemplate = skillUri(templatePath);

        return new McpResourceTemplate()
        {
            @Override
            public String name()
            {
                return skillTemplate.name();
            }

            @Override
            public String uriTemplate()
            {
                return uriTemplate;
            }

            @Override
            public String mimeType()
            {
                return SKILL_MIME_TYPE;
            }

            @Override
            public String description()
            {
                return skillTemplate.description();
            }

            @Override
            public String[] icons()
            {
                return skillTemplate.icons();
            }

            @Override
            public Role[] audience()
            {
                return skillTemplate.audience();
            }

            @Override
            public double priority()
            {
                return skillTemplate.priority();
            }

            @Override
            public Class<? extends Annotation> annotationType()
            {
                return McpResourceTemplate.class;
            }

            @Override
            public int hashCode()
            {
                return Objects.hash(
                        name(),
                        uriTemplate,
                        mimeType(),
                        description(),
                        Arrays.hashCode(icons()),
                        Arrays.hashCode(audience()),
                        priority());
            }

            @Override
            public boolean equals(Object obj)
            {
                return ((this == obj) || (
                        (obj instanceof McpResourceTemplate mcpResourceTemplate)
                                && name().equals(mcpResourceTemplate.name())
                                && mimeType().equals(mcpResourceTemplate.mimeType())
                                && description().equals(mcpResourceTemplate.description())
                                && Arrays.equals(icons(), mcpResourceTemplate.icons())
                                && uriTemplate.equals(mcpResourceTemplate.uriTemplate())
                                && Arrays.equals(audience(), mcpResourceTemplate.audience())
                                && Double.compare(priority(), mcpResourceTemplate.priority()) == 0));
            }
        };
    }

    static String buildBasePath(String name, String[] parentPath)
    {
        if (name == null || name.isBlank() || name.contains("/")) {
            throw new IllegalArgumentException("Skill name must be non-blank and must not contain '/'");
        }
        for (String part : parentPath) {
            if (part == null || part.isBlank() || part.contains("/")) {
                throw new IllegalArgumentException("Each parent path segment must be non-blank and must not contain '/'");
            }
        }
        return (parentPath.length == 0)
                ? name
                : Stream.concat(Stream.of(parentPath), Stream.of(name)).collect(Collectors.joining("/"));
    }
}
