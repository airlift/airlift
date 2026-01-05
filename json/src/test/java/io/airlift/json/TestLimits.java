package io.airlift.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        testNameLengthLimit(new ObjectMapperProvider());
    }

    @Test
    public void testNameLimitCustomJsonFactory()
            throws IOException
    {
        JsonFactory myJsonFactory = new JsonFactory();
        testNameLengthLimit(new ObjectMapperProvider(myJsonFactory));
    }

    private void testNameLengthLimit(ObjectMapperProvider objectMapperProvider)
            throws IOException
    {
        ObjectMapper objectMapper = objectMapperProvider.get();
        String longName = Strings.repeat("a", 100000);

        String content = String.format("{ \"%s\" : \"value\" }", longName);
        JsonNode jsonNode = objectMapper.reader().readTree(content);
        assertThat(jsonNode.has(longName)).isTrue();
        assertThat(jsonNode.findValue(longName).asText()).isEqualTo("value");
    }

    @Test
    public void testStringLimitDefaultJsonFactory()
            throws IOException
    {
        testStringLengthLimit(new ObjectMapperProvider());
    }

    @Test
    public void testStringLimitCustomJsonFactory()
            throws IOException
    {
        JsonFactory myJsonFactory = new JsonFactory();
        testStringLengthLimit(new ObjectMapperProvider(myJsonFactory));
    }

    private void testStringLengthLimit(ObjectMapperProvider objectMapperProvider)
            throws IOException
    {
        ObjectMapper objectMapper = objectMapperProvider.get();
        String longValue = Strings.repeat("a", 100000);

        String content = String.format("{ \"key\" : \"%s\" }", longValue);
        JsonNode jsonNode = objectMapper.reader().readTree(content);
        assertThat(jsonNode.findValue("key").asText()).isEqualTo(longValue);
    }
}
