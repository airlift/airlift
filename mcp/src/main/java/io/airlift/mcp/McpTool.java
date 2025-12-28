package io.airlift.mcp;

import io.airlift.mcp.model.OptionalBoolean;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

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

    OptionalBoolean readOnlyHint() default UNDEFINED;

    OptionalBoolean destructiveHint() default UNDEFINED;

    OptionalBoolean idempotentHint() default UNDEFINED;

    OptionalBoolean openWorldHint() default UNDEFINED;
}
