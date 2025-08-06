package io.airlift.mcp;

import io.airlift.mcp.model.Role;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target(METHOD)
public @interface McpPrompt
{
    String name();

    Role role() default Role.USER;

    String description() default "";
}
