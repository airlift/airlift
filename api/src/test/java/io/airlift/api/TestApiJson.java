package io.airlift.api;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

public class TestApiJson
{
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Test
    public void testApiJsonNodeSerializesWrappedJsonValue()
            throws Exception
    {
        ApiJsonNode jsonNode = new ApiJsonNode(JSON_MAPPER.readTree("{\"field\":\"value\"}"));

        assertThat(JSON_MAPPER.writeValueAsString(jsonNode)).isEqualTo("{\"field\":\"value\"}");
    }

    @Test
    public void testApiJsonNodeDeserializesArbitraryJsonValue()
            throws Exception
    {
        assertThat(JSON_MAPPER.readValue("\"value\"", ApiJsonNode.class).value().asString()).isEqualTo("value");
    }

    @Test
    public void testStructuredTypesUseTheirJsonShape()
            throws Exception
    {
        ApiJsonObject jsonObject = JSON_MAPPER.readValue("{\"field\":\"value\"}", ApiJsonObject.class);
        assertThat(jsonObject.value()).isInstanceOf(ObjectNode.class);
        assertThat(JSON_MAPPER.writeValueAsString(jsonObject)).isEqualTo("{\"field\":\"value\"}");

        ApiJsonList jsonList = JSON_MAPPER.readValue("[\"value\"]", ApiJsonList.class);
        assertThat(jsonList.value()).isInstanceOf(ArrayNode.class);
        assertThat(JSON_MAPPER.writeValueAsString(jsonList)).isEqualTo("[\"value\"]");
    }
}
