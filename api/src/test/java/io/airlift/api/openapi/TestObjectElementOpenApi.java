package io.airlift.api.openapi;

import com.google.common.collect.ImmutableList;
import io.airlift.api.model.ModelApi;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.openapi.models.OpenAPI;
import io.airlift.api.openapi.models.Schema;
import io.airlift.api.validation.ServiceWithObjectField;
import io.airlift.json.JsonCodec;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static io.airlift.api.builders.ApiBuilder.apiBuilder;
import static io.airlift.api.servertests.openapi.TestOpenApi.validateOpenApiJson;
import static io.airlift.json.JsonCodec.jsonCodec;
import static org.assertj.core.api.Assertions.assertThat;

public class TestObjectElementOpenApi
{
    private static final JsonCodec<OpenAPI> OPEN_API_CODEC = jsonCodec(OpenAPI.class);

    @Test
    public void testObjectElementSchemasAreUnconstrained()
    {
        ModelApi modelApi = apiBuilder().add(ServiceWithObjectField.class).build();
        assertThat(modelApi.modelServices().errors()).isEmpty();

        ModelServiceType serviceType = modelApi.modelServices().services().iterator().next().service().type();
        OpenAPI openAPI = OpenApiProvider.create(modelApi.modelServices(), new OpenApiMetadata(Optional.empty(), ImmutableList.of()))
                .build(serviceType, _ -> true);

        validateOpenApiJson(OPEN_API_CODEC.toJson(openAPI));

        Schema<?> objectField = openAPI.getComponents().getSchemas().get("ObjectField");
        assertThat(objectField).isNotNull();
        Map<String, Schema> properties = objectField.getProperties();

        Schema<?> payloads = properties.get("payloads");
        assertThat(payloads.getType()).isEqualTo("array");
        assertUnconstrainedSchema(payloads.getItems());

        Schema<?> rows = properties.get("rows");
        assertThat(rows.getType()).isEqualTo("array");
        Schema<?> row = rows.getItems();
        assertThat(row.getType()).isEqualTo("array");
        assertUnconstrainedSchema(row.getItems());

        Schema<?> attributes = properties.get("attributes");
        assertThat(attributes.getType()).isEqualTo("object");
        assertThat(attributes.getAdditionalProperties()).isInstanceOf(Schema.class);
        assertUnconstrainedSchema((Schema<?>) attributes.getAdditionalProperties());
    }

    private static void assertUnconstrainedSchema(Schema<?> schema)
    {
        assertThat(schema.getType()).isNull();
        assertThat(schema.get$ref()).isNull();
        assertThat(schema.getProperties()).isNull();
        assertThat(schema.getItems()).isNull();
        assertThat(schema.getAdditionalProperties()).isNull();
        assertThat(schema.getAllOf()).isNull();
        assertThat(schema.getOneOf()).isNull();
        assertThat(schema.getDescription()).isNull();
    }
}
