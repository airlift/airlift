package io.airlift.mcp;

import io.airlift.mcp.model.CacheableResult;
import io.airlift.mcp.model.Implementation;

import java.util.Optional;

import static io.airlift.mcp.model.Constants.SKILL_MD_FILE;
import static java.util.Objects.requireNonNull;

public record McpMetadata(String uriPath, Implementation implementation, Optional<String> instructions, CacheableResult<?> cacheableResultValues, boolean autoAddSkillInstructions)
{
    public static final McpMetadata DEFAULT = new McpMetadata("/mcp");

    public static final String SKILLS_INSTRUCTIONS =
            """
            This server provides skills: resources whose URIs end in "SKILL_MD_FILE". For **any** skill relevant to \
            the user's task, **read its "SKILL_MD_FILE"** resource to load the skill into context, then follow its \
            instructions when formulating your response. Relative paths in a skill resolve against the directory \
            containing its "SKILL_MD_FILE".\
            """.replace("SKILL_MD_FILE", SKILL_MD_FILE);

    public McpMetadata
    {
        requireNonNull(uriPath, "uriPath is null");
        requireNonNull(implementation, "implementation is null");
        requireNonNull(instructions, "instructions is null");
        requireNonNull(cacheableResultValues, "cacheableResultValues is null");
    }

    public McpMetadata(String uriPath, Implementation implementation, Optional<String> instructions)
    {
        this(uriPath, implementation, instructions, CacheableResult.DEFAULT, true);
    }

    public McpMetadata(String uriPath)
    {
        this(uriPath, new Implementation("mcp", "1.0.0"), Optional.empty(), CacheableResult.DEFAULT, true);
    }

    public McpMetadata withImplementation(Implementation implementation)
    {
        return new McpMetadata(uriPath, implementation, instructions, cacheableResultValues, autoAddSkillInstructions);
    }

    public McpMetadata withInstructions(String instructions)
    {
        return new McpMetadata(uriPath, implementation, Optional.ofNullable(instructions), cacheableResultValues, autoAddSkillInstructions);
    }

    public Optional<String> adjustedInstructions(boolean serverHasSkills)
    {
        if (!serverHasSkills || !autoAddSkillInstructions) {
            return instructions;
        }
        return instructions.map(value -> value + "\n\n" + SKILLS_INSTRUCTIONS)
                .or(() -> Optional.of(SKILLS_INSTRUCTIONS));
    }

    public McpMetadata withCacheableResultValues(CacheableResult<?> cacheableResultValues)
    {
        return new McpMetadata(uriPath, implementation, instructions, cacheableResultValues, autoAddSkillInstructions);
    }
}
