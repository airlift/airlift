package io.airlift.mcp;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestMcpSkillBuilder
{
    @Test
    public void testBasicBuilder()
    {
        McpSkillBuilder builder = McpSkillBuilder.mcpSkillBuilder("my-skill", "A test skill");
        assertThat(builder).isNotNull();
        assertThat(builder.buildSkill()).isNotNull();
    }

    @Test
    public void testNameMinLength()
    {
        McpSkillBuilder builder = McpSkillBuilder.mcpSkillBuilder("a", "description");
        assertThat(builder).isNotNull();
    }

    @Test
    public void testNameMaxLength()
    {
        String maxName = "a".repeat(64);
        McpSkillBuilder builder = McpSkillBuilder.mcpSkillBuilder(maxName, "description");
        assertThat(builder).isNotNull();
    }

    @Test
    public void testNameEmpty()
    {
        assertThatThrownBy(() -> McpSkillBuilder.mcpSkillBuilder("", "description"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be between 1 and 64 characters");
    }

    @Test
    public void testNameTooLong()
    {
        String longName = "a".repeat(65);
        assertThatThrownBy(() -> McpSkillBuilder.mcpSkillBuilder(longName, "description"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be between 1 and 64 characters");
    }

    @Test
    public void testNameIllegalCharacters()
    {
        assertThatThrownBy(() -> McpSkillBuilder.mcpSkillBuilder("My Skill", "description"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("illegal characters");

        assertThatThrownBy(() -> McpSkillBuilder.mcpSkillBuilder("my_skill", "description"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("illegal characters");

        assertThatThrownBy(() -> McpSkillBuilder.mcpSkillBuilder("UPPERCASE", "description"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("illegal characters");
    }

    @Test
    public void testNameDoubleDash()
    {
        assertThatThrownBy(() -> McpSkillBuilder.mcpSkillBuilder("my--skill", "description"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain \"--\"");
    }

    @Test
    public void testNameAllowedCharacters()
    {
        McpSkillBuilder builder = McpSkillBuilder.mcpSkillBuilder("abc-xyz-123", "description");
        assertThat(builder).isNotNull();
    }

    @Test
    public void testDescriptionMinLength()
    {
        McpSkillBuilder builder = McpSkillBuilder.mcpSkillBuilder("skill", "a");
        assertThat(builder).isNotNull();
    }

    @Test
    public void testDescriptionMaxLength()
    {
        String maxDescription = "a".repeat(1024);
        McpSkillBuilder builder = McpSkillBuilder.mcpSkillBuilder("skill", maxDescription);
        assertThat(builder).isNotNull();
    }

    @Test
    public void testDescriptionEmpty()
    {
        assertThatThrownBy(() -> McpSkillBuilder.mcpSkillBuilder("skill", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be between 1 and 1024 characters");
    }

    @Test
    public void testDescriptionTooLong()
    {
        String longDescription = "a".repeat(1025);
        assertThatThrownBy(() -> McpSkillBuilder.mcpSkillBuilder("skill", longDescription))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be between 1 and 1024 characters");
    }

    @Test
    public void testAddFrontmatterString()
    {
        McpSkillBuilder builder = McpSkillBuilder.mcpSkillBuilder("skill", "description")
                .addFrontmatter("custom-field", "custom-value");
        assertThat(builder).isNotNull();
    }

    @Test
    public void testAddFrontmatterMap()
    {
        Map<String, String> metadata = ImmutableMap.of("key1", "value1", "key2", "value2");
        McpSkillBuilder builder = McpSkillBuilder.mcpSkillBuilder("skill", "description")
                .addFrontmatter("metadata", metadata);
        assertThat(builder).isNotNull();
    }

    @Test
    public void testAddContent()
    {
        McpSkillBuilder builder = McpSkillBuilder.mcpSkillBuilder("skill", "description")
                .addContent("Some raw markdown content");
        assertThat(builder).isNotNull();
    }

    @Test
    public void testAddHeadingContentValidLevels()
    {
        McpSkillBuilder builder = McpSkillBuilder.mcpSkillBuilder("skill", "description");
        builder.addHeadingContent("Level 1", 1);
        builder.addHeadingContent("Level 2", 2);
        builder.addHeadingContent("Level 3", 3);
        builder.addHeadingContent("Level 4", 4);
        assertThat(builder).isNotNull();
    }

    @Test
    public void testAddHeadingContentLevelZero()
    {
        McpSkillBuilder builder = McpSkillBuilder.mcpSkillBuilder("skill", "description");
        assertThatThrownBy(() -> builder.addHeadingContent("Bad", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testAddHeadingContentLevelTooHigh()
    {
        McpSkillBuilder builder = McpSkillBuilder.mcpSkillBuilder("skill", "description");
        assertThatThrownBy(() -> builder.addHeadingContent("Bad", 6))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testAddHeadingContentNegativeLevel()
    {
        McpSkillBuilder builder = McpSkillBuilder.mcpSkillBuilder("skill", "description");
        assertThatThrownBy(() -> builder.addHeadingContent("Bad", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testAddListContent()
    {
        McpSkillBuilder builder = McpSkillBuilder.mcpSkillBuilder("skill", "description")
                .addListContent(ImmutableList.of("item1", "item2", "item3"));
        assertThat(builder).isNotNull();
    }

    @Test
    public void testAddListContentEmpty()
    {
        McpSkillBuilder builder = McpSkillBuilder.mcpSkillBuilder("skill", "description")
                .addListContent(ImmutableList.of());
        assertThat(builder).isNotNull();
    }

    @Test
    public void testBuilderChaining()
    {
        String result = McpSkillBuilder.mcpSkillBuilder("my-skill", "A test skill")
                .addFrontmatter("version", "1.0")
                .addFrontmatter("metadata", ImmutableMap.of("author", "test"))
                .addContent("Introduction paragraph")
                .addHeadingContent("Section 1", 1)
                .addContent("Section 1 content")
                .addHeadingContent("Subsection", 2)
                .addListContent(ImmutableList.of("item-a", "item-b"))
                .buildSkill();
        assertThat(result).isNotNull();
    }

    @Test
    public void testBuildSimpleSkillMatchesResource()
            throws IOException
    {
        String expected = loadResource("skills/simple-skill.md");
        String actual = McpSkillBuilder.mcpSkillBuilder("simple-skill", "A simple test skill")
                .buildSkill();
        assertThat(actual.strip()).isEqualTo(expected.strip());
    }

    @Test
    public void testBuildContentSkillMatchesResource()
            throws IOException
    {
        String expected = loadResource("skills/content-skill.md");
        String actual = McpSkillBuilder.mcpSkillBuilder("content-skill", "A skill with content")
                .addContent("Introduction paragraph.")
                .addHeadingContent("Details", 1)
                .addContent("Some details here.")
                .buildSkill();
        assertThat(actual.strip()).isEqualTo(expected.strip());
    }

    @Test
    public void testBuildMetadataSkillMatchesResource()
            throws IOException
    {
        String expected = loadResource("skills/metadata-skill.md");
        String actual = McpSkillBuilder.mcpSkillBuilder("metadata-skill", "A skill with metadata")
                .addFrontmatter("version", "1.0")
                .addFrontmatter("config", ImmutableMap.of("timeout", "30"))
                .addContent("Metadata skill content.")
                .buildSkill();
        assertThat(actual.strip()).isEqualTo(expected.strip());
    }

    @Test
    public void testBuildFullSkillMatchesResource()
            throws IOException
    {
        String expected = loadResource("skills/full-skill.md");
        String actual = McpSkillBuilder.mcpSkillBuilder("full-skill", "A comprehensive skill")
                .addFrontmatter("version", "2.0")
                .addFrontmatter("icons", ImmutableMap.of("default", "star", "main", "starburst"))
                .addFrontmatter("metadata", ImmutableMap.of("version", "\"1.0.0\"", "author", "me"))
                .addHeadingContent("Overview", 1)
                .addContent("This skill demonstrates all builder features.")
                .addContent("")
                .addListContent(ImmutableList.of("a", "b", "c", "d"))
                .addHeadingContent("Usage", 2)
                .addContent("Follow these steps to use this skill.")
                .buildSkill();
        assertThat(actual.strip()).isEqualTo(expected.strip());
    }

    @Test
    public void testSimpleSkillResourceStructure()
            throws IOException
    {
        String content = loadResource("skills/simple-skill.md");
        assertThat(content).startsWith("---");
        assertThat(content).contains("name: simple-skill");
        assertThat(content).contains("description: A simple test skill");
    }

    @Test
    public void testContentSkillResourceStructure()
            throws IOException
    {
        String content = loadResource("skills/content-skill.md");
        assertThat(content).startsWith("---");
        assertThat(content).contains("name: content-skill");
        assertThat(content).contains("# Details");
        assertThat(content).contains("Introduction paragraph.");
        assertThat(content).contains("Some details here.");
    }

    @Test
    public void testMetadataSkillResourceStructure()
            throws IOException
    {
        String content = loadResource("skills/metadata-skill.md");
        assertThat(content).startsWith("---");
        assertThat(content).contains("name: metadata-skill");
        assertThat(content).contains("version: 1.0");
        assertThat(content).contains("config:");
        assertThat(content).contains("timeout: 30");
    }

    @Test
    public void testFullSkillResourceStructure()
            throws IOException
    {
        String content = loadResource("skills/full-skill.md");
        assertThat(content).startsWith("---");
        assertThat(content).contains("name: full-skill");
        assertThat(content).contains("version: 2.0");
        assertThat(content).contains("icons:");
        assertThat(content).contains("default: star");
        assertThat(content).contains("# Overview");
        assertThat(content).contains("## Usage");
    }

    private static String loadResource(String path)
            throws IOException
    {
        return Resources.toString(Resources.getResource(path), UTF_8);
    }
}
