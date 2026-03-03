package io.airlift.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.base.Strings;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class TestLimits
{
    @Test
    public void testNameLimitDefaultJsonFactory()
            throws IOException
    {
        testNameLengthLimit(new JsonMapperProvider());
    }

    @Test
    public void testNameLimitCustomJsonFactory()
            throws IOException
    {
        JsonFactory myJsonFactory = new JsonFactory();
        testNameLengthLimit(new JsonMapperProvider(myJsonFactory));
    }

    private void testNameLengthLimit(JsonMapperProvider jsonMapperProvider)
            throws IOException
    {
        JsonMapper jsonMapper = jsonMapperProvider.get();
        String longName = Strings.repeat("a", 100000);

        String content = String.format("{ \"%s\" : \"value\" }", longName);
        JsonNode jsonNode = jsonMapper.readTree(content);
        assertThat(jsonNode.has(longName)).isTrue();
        assertThat(jsonNode.findValue(longName).asText()).isEqualTo("value");
    }

    @Test
    public void testStringLimitDefaultJsonFactory()
            throws IOException
    {
        testStringLengthLimit(new JsonMapperProvider());
    }

    @Test
    public void testStringLimitCustomJsonFactory()
            throws IOException
    {
        JsonFactory myJsonFactory = new JsonFactory();
        testStringLengthLimit(new JsonMapperProvider(myJsonFactory));
    }

    private void testStringLengthLimit(JsonMapperProvider jsonMapperProvider)
            throws IOException
    {
        JsonMapper jsonMapper = jsonMapperProvider.get();
        String longValue = Strings.repeat("a", 100000);

        String content = String.format("{ \"key\" : \"%s\" }", longValue);
        JsonNode jsonNode = jsonMapper.readTree(content);
        assertThat(jsonNode.findValue("key").asText()).isEqualTo(longValue);
    }
}
