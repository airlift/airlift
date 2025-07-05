package io.airlift.mcp;

import io.airlift.mcp.model.Role;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target(METHOD)
public @interface McpResourceTemplate
{
    String name();

    String uriTemplate();

    String mimeType();

    String description() default "";

    long size() default -1;

    Role[] audience() default {};

    double priority() default Double.NaN;
}
