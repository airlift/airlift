package io.airlift.mcp;

import io.airlift.mcp.model.OptionalBoolean;
import io.airlift.mcp.model.ToolExecution;

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

    ToolExecution execution() default ToolExecution.UNDEFINED;

    OptionalBoolean readOnlyHint() default UNDEFINED;

    OptionalBoolean destructiveHint() default UNDEFINED;

    OptionalBoolean idempotentHint() default UNDEFINED;

    OptionalBoolean openWorldHint() default UNDEFINED;

    McpApp app() default @McpApp(resourceUri = "", sourcePath = "");
}
