package io.airlift.api.internals;

import io.airlift.api.ApiJsonList;
import io.airlift.api.ApiJsonNode;
import io.airlift.api.ApiJsonObject;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static io.airlift.api.internals.ApiJsonTypes.apiJsonResourceDescription;
import static io.airlift.api.internals.ApiJsonTypes.apiJsonResourceName;
import static io.airlift.api.internals.ApiJsonTypes.isApiJsonType;
import static io.airlift.api.internals.ApiJsonTypes.jacksonJsonType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestApiJsonTypes
{
    @Test
    public void testApiJsonTypeDetection()
    {
        assertThat(isApiJsonType(ApiJsonNode.class)).isTrue();
        assertThat(isApiJsonType(ApiJsonObject.class)).isTrue();
        assertThat(isApiJsonType(ApiJsonList.class)).isTrue();
        assertThat(isApiJsonType(String.class)).isFalse();
    }

    @Test
    public void testApiJsonResourceNames()
    {
        assertThat(apiJsonResourceName(ApiJsonNode.class)).isEqualTo("json");
        assertThat(apiJsonResourceName(ApiJsonObject.class)).isEqualTo("jsonObject");
        assertThat(apiJsonResourceName(ApiJsonList.class)).isEqualTo("jsonList");
    }

    @Test
    public void testApiJsonResourceDescriptions()
    {
        assertThat(apiJsonResourceDescription(ApiJsonNode.class)).isEqualTo("An arbitrary JSON value.");
        assertThat(apiJsonResourceDescription(ApiJsonObject.class)).isEqualTo("A JSON object.");
        assertThat(apiJsonResourceDescription(ApiJsonList.class)).isEqualTo("A JSON array.");
    }

    @Test
    public void testJacksonJsonTypes()
    {
        assertThat(jacksonJsonType(ApiJsonNode.class)).isEqualTo(JsonNode.class);
        assertThat(jacksonJsonType(ApiJsonObject.class)).isEqualTo(ObjectNode.class);
        assertThat(jacksonJsonType(ApiJsonList.class)).isEqualTo(ArrayNode.class);
    }

    @Test
    public void testUnsupportedJsonType()
    {
        assertThatThrownBy(() -> apiJsonResourceDescription(String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported ApiJson type: java.lang.String");
        assertThatThrownBy(() -> jacksonJsonType(String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported ApiJson type: java.lang.String");
    }
}
