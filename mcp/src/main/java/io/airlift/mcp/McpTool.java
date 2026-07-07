package io.airlift.mcp;

import io.airlift.mcp.model.OptionalBoolean;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.function.Consumer;

import static io.airlift.mcp.model.OptionalBoolean.UNDEFINED;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target(METHOD)
public @interface McpTool
{
    String name();

    String description() default "";

    String title() default "";

    /**
     * Names of icons for this tool. Icons must be bound
     * via {@link McpModule.Builder#addIcon(String, Consumer)}.
     */
    String[] icons() default {};

    OptionalBoolean readOnlyHint() default UNDEFINED;

    OptionalBoolean destructiveHint() default UNDEFINED;

    OptionalBoolean idempotentHint() default UNDEFINED;

    OptionalBoolean openWorldHint() default UNDEFINED;

    McpApp app() default @McpApp(resourceUri = "", sourcePath = "");

    /**
     * If specified, overrides the input schema for the tool. Normally, the arguments
     * to the tool method are used to generate the input schema. When this annotation is
     * specified, the input schema is taken from the {@link McpSchema} annotation.
     */
    McpSchema inputSchema() default @McpSchema;

    /**
     * If specified, overrides the output schema for the tool. Normally, the return type
     * of the tool method is used to generate the output schema. When this annotation is
     * specified, the output schema is taken from the {@link McpSchema} annotation.
     */
    McpSchema outputSchema() default @McpSchema;
}
