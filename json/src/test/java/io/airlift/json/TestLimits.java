package io.airlift.json;

import com.google.common.base.Strings;
import org.junit.jupiter.api.Test;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

public class TestLimits
{
    @Test
    public void testNameLimitDefaultJsonFactory()
    {
        testNameLengthLimit(new ObjectMapperProvider());
        testStringLengthLimit(new ObjectMapperProvider());
    }

    @Test
    public void testNameLimitCustomJsonFactory()
    {
        JsonFactory myJsonFactory = new JsonFactory();
        testNameLengthLimit(new ObjectMapperProvider(myJsonFactory));
    }

    private void testNameLengthLimit(ObjectMapperProvider objectMapperProvider)
    {
        ObjectMapper objectMapper = objectMapperProvider.get();
        String longName = Strings.repeat("a", 100000);

        String content = String.format("{ \"%s\" : \"value\" }", longName);
        JsonNode jsonNode = objectMapper.reader().readTree(content);
        assertThat(jsonNode.has(longName)).isTrue();
        assertThat(jsonNode.findValue(longName).asString()).isEqualTo("value");
    }

    @Test
    public void testStringLimitDefaultJsonFactory()
    {
        testStringLengthLimit(new ObjectMapperProvider());
    }

    @Test
    public void testStringLimitCustomJsonFactory()
    {
        JsonFactory myJsonFactory = new JsonFactory();
        testStringLengthLimit(new ObjectMapperProvider(myJsonFactory));
    }

    private void testStringLengthLimit(ObjectMapperProvider objectMapperProvider)
    {
        ObjectMapper objectMapper = objectMapperProvider.get();
        String longValue = Strings.repeat("a", 100000);

        String content = String.format("{ \"key\" : \"%s\" }", longValue);
        JsonNode jsonNode = objectMapper.reader().readTree(content);
        assertThat(jsonNode.findValue("key").asString()).isEqualTo(longValue);
    }
}
