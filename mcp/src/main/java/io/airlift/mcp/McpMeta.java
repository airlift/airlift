package io.airlift.mcp;

public @interface McpMeta
{
    String name();

    /**
     * Single string value or multiple string values. You must provide either {@code value} or {@link #jsonValue}.
     */
    String[] value() default {};

    /**
     * Any valid JSON value. E.g. {@code {"x": 20.20}}, {@code 1.2}, {@code ["foo", "bar"]}, etc.
     *  You must provide either {@link #value} or {@code jsonValue}.
     */
    String jsonValue() default "";
}
