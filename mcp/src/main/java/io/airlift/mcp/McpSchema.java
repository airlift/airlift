package io.airlift.mcp;

import io.airlift.mcp.model.JsonSchemaBuilder;

/**
 * IMPORTANT: all attributes of this annotation are mutually
 * exclusive. Only one attribute can be specified at a time.
 * It is an error to specify more than one, and an exception
 * will be thrown if it is.
 */
public @interface McpSchema
{
    /**
     * The class to use to generate the schema. The class will
     * be passed to the bound {@link JsonSchemaBuilder.SchemaBuilder}.
     */
    Class<?> schemaClass() default void.class;

    /**
     * The raw schema to use to generate the schema. The schema will
     * be parsed by a {@code JsonMapper} but is otherwise unchanged.
     */
    String rawSchema() default "";

    /**
     * The fully qualified name of the class to use to generate the schema.
     * The class will be passed to the bound {@link JsonSchemaBuilder.SchemaBuilder}.
     */
    String schemaClassName() default "";
}
