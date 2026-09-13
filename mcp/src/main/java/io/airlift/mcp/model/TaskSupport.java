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
     * The tool always creates a task, so clients that have not negotiated the Tasks extension
     * are rejected with a {@code MissingRequiredClientCapability} error before it is called.
     */
    REQUIRED,
}
