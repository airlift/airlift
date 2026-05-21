package io.airlift.mcp;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.model.Constants;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceTemplate;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.airlift.mcp.model.Constants.SKILL_MIME_TYPE;

public final class McpSkillBuilder
{
    private static final List<String> HEADING_LEVELS = ImmutableList.of("#", "##", "###", "####", "#####");
    private static final Set<Character> ALLOWED_NAME_CHARS = "abcdefghijklmnopqrstuvwxyz1234567890-".chars().mapToObj(c -> (char) c).collect(toImmutableSet());

    private final ImmutableMap.Builder<String, String> frontmatter = ImmutableMap.builder();
    private final ImmutableMap.Builder<String, Map<String, String>> metadata = ImmutableMap.builder();
    private final ImmutableList.Builder<String> content = ImmutableList.builder();
    private boolean lastWasContent;

    private McpSkillBuilder() {}

    public static String skillUri(String basePath)
    {
        return ("skill://%s/" + Constants.SKILL_MD_FILE).formatted(basePath);
    }

    public static McpSkillBuilder mcpSkillBuilder(ResourceTemplate resourceTemplate)
    {
        Resource workResource = new Resource(resourceTemplate.name(), resourceTemplate.uriTemplate(), resourceTemplate.description(), resourceTemplate.mimeType(), OptionalLong.empty(), Optional.empty(), Optional.empty());
        return mcpSkillBuilder(workResource);
    }

    public static McpSkillBuilder mcpSkillBuilder(Resource resource)
    {
        checkArgument(resource.mimeType().equals(SKILL_MIME_TYPE), "MIME type must be %s", SKILL_MIME_TYPE);
        String description = resource.description().orElseThrow(() -> new IllegalArgumentException("Description is required for skills"));
        validateUri(resource.uri(), resource.name());
        return mcpSkillBuilder(resource.name(), description);
    }

    // from Claude
    private static void validateUri(String uri, String name)
    {
        String pattern = "^skill://([^/]+/)*" + Pattern.quote(name) + "/" + Pattern.quote(Constants.SKILL_MD_FILE) + "$";
        if ((uri == null) || !uri.matches(pattern)) {
            throw new IllegalArgumentException("URI must match skill://[/]/SKILL.md. URI: " + uri);
        }
    }

    public static McpSkillBuilder mcpSkillBuilder(String name, String description)
    {
        return new McpSkillBuilder()
                .addFrontmatter("name", name)
                .addFrontmatter("description", description);
    }

    public McpSkillBuilder addFrontmatter(String fieldName, String value)
    {
        if (fieldName.equals("name")) {
            // see https://github.com/shalomb/agent-skills/blob/main/docs/reference/frontmatter.md#name
            checkArgument(!value.isEmpty() && (value.length() <= 64), "\"name\" must be between 1 and 64 characters");
            checkArgument(value.chars().allMatch(c -> ALLOWED_NAME_CHARS.contains((char) c)), "\"name\" has illegal characters. Allowed characters: %s", ALLOWED_NAME_CHARS);
            checkArgument(!value.contains("--"), "\"name\" must not contain \"--\"");
        }
        else if (fieldName.equals("description")) {
            // see https://github.com/shalomb/agent-skills/blob/main/docs/reference/frontmatter.md#description
            checkArgument(!value.isEmpty() && (value.length() <= 1024), "\"description\" must be between 1 and 1024 characters");
        }

        frontmatter.put(fieldName, value);
        return this;
    }

    public McpSkillBuilder addFrontmatter(String fieldName, Map<String, String> value)
    {
        metadata.put(fieldName, value);
        return this;
    }

    public McpSkillBuilder addContent(String rawMarkdown)
    {
        content.add(rawMarkdown);
        lastWasContent = true;
        return this;
    }

    public McpSkillBuilder addHeadingContent(String heading, int level)
    {
        if (lastWasContent) {
            content.add("");
        }
        lastWasContent = false;

        checkArgument((level > 0) && (level <= HEADING_LEVELS.size()), "level must be between 1 and %s", HEADING_LEVELS.size());
        content.add(HEADING_LEVELS.get(level - 1) + " " + heading);
        content.add("");
        return this;
    }

    public McpSkillBuilder addListContent(List<String> list)
    {
        list.forEach(value -> content.add("- " + value));
        lastWasContent = true;
        return this;
    }

    public String buildSkill()
    {
        StringWriter skillFile = new StringWriter();
        PrintWriter writer = new PrintWriter(skillFile);

        writer.println("---");
        addMap(writer, frontmatter.buildOrThrow(), "");
        addMetadata(writer, metadata.buildOrThrow());
        writer.println("---");
        writer.println();
        content.build().forEach(writer::println);

        writer.flush();
        return skillFile.toString();
    }

    private void addMap(PrintWriter writer, Map<String, String> map, String prefix)
    {
        map.forEach((key, value) -> writer.println(prefix + key + ": " + value));
    }

    private void addMetadata(PrintWriter writer, Map<String, Map<String, String>> metadata)
    {
        metadata.forEach((key, map) -> {
            writer.println(key + ":");
            addMap(writer, map, "  - ");
        });
    }
}
