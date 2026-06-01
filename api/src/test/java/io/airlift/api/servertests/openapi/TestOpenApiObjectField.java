package io.airlift.api.servertests.openapi;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import io.airlift.api.builders.ApiBuilder;
import io.airlift.api.model.ModelApi;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.model.ModelServices;
import io.airlift.api.openapi.OpenApiMetadata;
import io.airlift.api.openapi.OpenApiProvider;
import io.airlift.api.openapi.models.OpenAPI;
import io.airlift.api.validation.ServiceWithObjectField;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.airlift.api.servertests.openapi.TestOpenApi.validateOpenApiJson;
import static io.airlift.json.JsonCodec.jsonCodec;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class TestOpenApiObjectField
{
    @Test
    public void testOpenApiObjectFieldSchema()
            throws Exception
    {
        ModelApi modelApi = ApiBuilder.apiBuilder().add(ServiceWithObjectField.class).build();
        ModelServices modelServices = modelApi.modelServices();
        ModelServiceType serviceType = modelServices.services().stream()
                .map(service -> service.service().type())
                .findFirst()
                .orElseThrow();

        OpenApiProvider provider = OpenApiProvider.create(modelServices, new OpenApiMetadata(Optional.empty(), ImmutableList.of()));
        OpenAPI openAPI = provider.build(serviceType, _ -> true);
        String actual = jsonCodec(OpenAPI.class).toJson(openAPI);

        validateOpenApiJson(actual);

        String expected = Resources.toString(Resources.getResource("openapi/object-field.json"), UTF_8);
        assertThat(actual.strip()).isEqualTo(expected.strip());
    }
}
