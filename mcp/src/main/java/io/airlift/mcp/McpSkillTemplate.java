package io.airlift.mcp;

import io.airlift.mcp.model.Role;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.function.Consumer;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target(METHOD)
public @interface McpSkillTemplate
{
    String name();

    String[] uriTemplateParts();

    String description();

    /**
     * Names of icons for this tool. Icons must be bound
     * via {@link McpModule.Builder#addIcon(String, Consumer)}.
     */
    String[] icons() default {};

    Role[] audience() default {};

    double priority() default Double.NaN;
}
