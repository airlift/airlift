package io.airlift.api.openapi;

import com.google.common.reflect.TypeToken;
import io.airlift.api.ApiBuilderConfig;
import io.airlift.api.ApiJsonList;
import io.airlift.api.ApiJsonNode;
import io.airlift.api.ApiJsonObject;
import io.airlift.api.model.ModelResource;
import io.airlift.api.openapi.models.Schema;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.airlift.api.builders.ResourceBuilder.resourceBuilder;
import static io.airlift.api.openapi.SchemaBuilder.BuildSchemaMode.STANDARD;
import static org.assertj.core.api.Assertions.assertThat;

public class TestApiJsonSchemas
{
    private static final ApiBuilderConfig CONFIG = ApiBuilderConfig.jackson();

    @Test
    public void testJsonNodeIsUnconstrained()
    {
        Schema<?> schema = buildSchema(ApiJsonNode.class);

        assertThat(schema.getType()).isNull();
        assertThat(schema.getNullable()).isTrue();
        assertThat(schema.getProperties()).isNull();
        assertThat(schema.getItems()).isNull();
        assertThat(schema.getDescription()).isEqualTo("An arbitrary JSON value.");
    }

    @Test
    public void testStructuredJsonTypesUseTheirJsonShape()
    {
        Schema<?> objectSchema = buildSchema(ApiJsonObject.class);
        assertThat(objectSchema.getType()).isEqualTo("object");
        assertThat(objectSchema.getAdditionalProperties()).isEqualTo(true);
        assertThat(objectSchema.getDescription()).isEqualTo("A JSON object.");

        Schema<?> arraySchema = buildSchema(ApiJsonList.class);
        assertThat(arraySchema.getType()).isEqualTo("array");
        assertThat(arraySchema.getItems()).isNotNull();
        assertThat(arraySchema.getItems().getType()).isNull();
        assertThat(arraySchema.getDescription()).isEqualTo("A JSON array.");
    }

    @Test
    public void testMapWithJsonNodeAllowsArbitraryJsonValues()
    {
        ModelResource modelResource = resourceBuilder(new TypeToken<Map<String, ApiJsonNode>>() {}.getType(), CONFIG.enumValueResolver()).build();

        Schema<?> schema = new SchemaBuilder(false, CONFIG.enumValueResolver()).buildSchema(modelResource, STANDARD);

        assertThat(schema.getType()).isEqualTo("object");
        assertThat(schema.getAdditionalProperties()).isInstanceOfSatisfying(Schema.class, additionalProperties -> {
            assertThat(additionalProperties.getType()).isNull();
            assertThat(additionalProperties.getNullable()).isTrue();
        });
    }

    private static Schema<?> buildSchema(Class<?> clazz)
    {
        return new SchemaBuilder(false, CONFIG.enumValueResolver()).buildSchema(resourceBuilder(clazz, CONFIG.enumValueResolver()).build(), STANDARD);
    }
}
