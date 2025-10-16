package io.airlift.mcp;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.airlift.mcp.model.Role;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(METHOD)
public @interface McpResource {
    String name();

    String uri();

    String mimeType();

    String description() default "";

    long size() default -1;

    Role[] audience() default {};

    double priority() default Double.NaN;
}
