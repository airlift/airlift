package io.airlift.json;

import com.google.common.base.Strings;
import org.junit.jupiter.api.Test;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

public class TestLimits
{
    @Test
    public void testNameLimitDefaultJsonFactory()
    {
        testNameLengthLimit(new JsonMapperProvider());
        testStringLengthLimit(new JsonMapperProvider());
    }

    @Test
    public void testNameLimitCustomJsonFactory()
    {
        JsonFactory myJsonFactory = new JsonFactory();
        testNameLengthLimit(new JsonMapperProvider(myJsonFactory));
    }

    private void testNameLengthLimit(JsonMapperProvider jsonMapperProvider)
    {
        JsonMapper jsonMapper = jsonMapperProvider.get();
        String longName = Strings.repeat("a", 100000);

        String content = String.format("{ \"%s\" : \"value\" }", longName);
        JsonNode jsonNode = jsonMapper.readTree(content);
        assertThat(jsonNode.has(longName)).isTrue();
        assertThat(jsonNode.findValue(longName).asString()).isEqualTo("value");
    }

    @Test
    public void testStringLimitDefaultJsonFactory()
    {
        testStringLengthLimit(new JsonMapperProvider());
    }

    @Test
    public void testStringLimitCustomJsonFactory()
    {
        JsonFactory myJsonFactory = new JsonFactory();
        testStringLengthLimit(new JsonMapperProvider(myJsonFactory));
    }

    private void testStringLengthLimit(JsonMapperProvider jsonMapperProvider)
    {
        JsonMapper jsonMapper = jsonMapperProvider.get();
        String longValue = Strings.repeat("a", 100000);

        String content = String.format("{ \"key\" : \"%s\" }", longValue);
        JsonNode jsonNode = jsonMapper.readTree(content);
        assertThat(jsonNode.findValue("key").stringValue()).isEqualTo(longValue);
    }
}
