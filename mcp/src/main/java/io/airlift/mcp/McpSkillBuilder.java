package io.airlift.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.model.Constants;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceTemplate;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.airlift.mcp.model.Constants.SKILL_MIME_TYPE;

public final class McpSkillBuilder
{
    private static final List<String> HEADING_LEVELS = ImmutableList.of("#", "##", "###", "####", "#####");
    private static final Set<Character> ALLOWED_NAME_CHARS = "abcdefghijklmnopqrstuvwxyz1234567890-".chars().mapToObj(c -> (char) c).collect(toImmutableSet());
    private static final String FRONTMATTER_DELIMITER = "---";

    private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .enable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
            .build();

    private final ImmutableMap.Builder<String, Object> frontmatter = ImmutableMap.builder();
    private final ImmutableList.Builder<String> content = ImmutableList.builder();
    private boolean lastWasContent;

    private McpSkillBuilder() {}

    public static String skillUri(String basePath)
    {
        return ("skill://%s/" + Constants.SKILL_MD_FILE).formatted(basePath);
    }

    public static McpSkillBuilder mcpSkillBuilder(ResourceTemplate resourceTemplate)
    {
        Resource workResource = new Resource(resourceTemplate.name(), resourceTemplate.uriTemplate(), resourceTemplate.description(), resourceTemplate.mimeType(), OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty());
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

    public static Map<String, Object> parseFrontmatter(String skillMarkdown)
    {
        List<String> lines = skillMarkdown.lines().toList();
        checkArgument(!lines.isEmpty() && lines.getFirst().stripTrailing().equals(FRONTMATTER_DELIMITER), "%s must begin with YAML frontmatter", Constants.SKILL_MD_FILE);

        int end = IntStream.range(1, lines.size())
                .filter(index -> lines.get(index).stripTrailing().equals(FRONTMATTER_DELIMITER))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(Constants.SKILL_MD_FILE + " frontmatter is not terminated"));

        Map<String, Object> frontmatter;
        try {
            frontmatter = YAML_MAPPER.readValue(String.join("\n", lines.subList(1, end)), new TypeReference<LinkedHashMap<String, Object>>() {});
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException(Constants.SKILL_MD_FILE + " frontmatter is not valid YAML", e);
        }
        checkArgument(frontmatter != null, "%s frontmatter is empty", Constants.SKILL_MD_FILE);

        if (!(frontmatter.get("name") instanceof String name)) {
            throw new IllegalArgumentException("\"name\" is required in frontmatter and must be a string");
        }
        if (!(frontmatter.get("description") instanceof String description)) {
            throw new IllegalArgumentException("\"description\" is required in frontmatter and must be a string");
        }
        validateName(name);
        validateDescription(description);

        return Collections.unmodifiableMap(frontmatter);
    }

    public McpSkillBuilder addFrontmatter(String fieldName, String value)
    {
        if (fieldName.equals("name")) {
            validateName(value);
        }
        else if (fieldName.equals("description")) {
            validateDescription(value);
        }

        frontmatter.put(fieldName, value);
        return this;
    }

    public McpSkillBuilder addFrontmatter(String fieldName, Map<String, String> value)
    {
        frontmatter.put(fieldName, ImmutableMap.copyOf(value));
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
        String yaml;
        try {
            yaml = YAML_MAPPER.writeValueAsString(frontmatter.buildOrThrow());
        }
        catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        StringWriter skillFile = new StringWriter();
        PrintWriter writer = new PrintWriter(skillFile);

        writer.println(FRONTMATTER_DELIMITER);
        writer.print(yaml);
        writer.println(FRONTMATTER_DELIMITER);
        writer.println();
        content.build().forEach(writer::println);

        writer.flush();
        return skillFile.toString();
    }

    // see https://agentskills.io/specification#name-field
    private static void validateName(String name)
    {
        checkArgument(!name.isEmpty() && (name.length() <= 64), "\"name\" must be between 1 and 64 characters");
        checkArgument(name.chars().allMatch(c -> ALLOWED_NAME_CHARS.contains((char) c)), "\"name\" has illegal characters. Allowed characters: %s", ALLOWED_NAME_CHARS);
        checkArgument(!name.startsWith("-") && !name.endsWith("-"), "\"name\" must not start or end with \"-\"");
        checkArgument(!name.contains("--"), "\"name\" must not contain \"--\"");
    }

    // see https://agentskills.io/specification#description-field
    private static void validateDescription(String description)
    {
        checkArgument(!description.isEmpty() && (description.length() <= 1024), "\"description\" must be between 1 and 1024 characters");
    }
}
