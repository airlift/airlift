package io.airlift.mcp.model;

/**
 * Declares whether a tool can be executed as an MCP task. A tool creates its own tasks; this
 * controls what the server requires of the client before calling it. Note: the Tasks extension
 * does not put the declaration on the wire.
 */
public enum TaskSupport
{
    /**
     * The tool never creates a task. This is the default.
     */
    NONE,

    /**
     * The tool may create a task. It is called whether or not the client has negotiated the
     * Tasks extension, so it must check before creating one.
     */
    OPTIONAL,

    /**
     * The tool always creates a task, so it is called only for clients that have negotiated the
     * Tasks extension. Other clients are rejected before it is called: with a
     * {@code MissingRequiredClientCapability} error, or, on a legacy protocol, which cannot carry
     * the extension, with an error result.
     */
    REQUIRED,
}
