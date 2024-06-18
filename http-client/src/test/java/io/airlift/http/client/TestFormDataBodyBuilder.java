package io.airlift.http.client;

import org.testng.annotations.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class TestFormDataBodyBuilder
{
    @Test
    public void test()
    {
        byte[] body = new FormDataBodyBuilder()
                .addField("a", "apple")
                .addField("b", "banana split")
                .addField("c", "com,ma")
                .addField(":", "colon")
                .build()
                .getBody();
        assertThat(new String(body, UTF_8)).isEqualTo("a=apple&b=banana+split&c=com%2Cma&%3A=colon");
    }
}
