package io.airlift.mcp.client.internal;

import org.junit.jupiter.api.Test;

import static io.airlift.mcp.client.internal.RequestController.headerValue;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code Mcp-Name} header carries the subject of a request, and a resource URI can hold anything - so a value
 * that cannot be sent literally is base64 sentinel encoded.
 */
public class TestHeaderValue
{
    @Test
    public void testPrintableAsciiIsSentLiterally()
    {
        assertThat(headerValue("add")).isEqualTo("add");
        assertThat(headerValue("file://example.txt")).isEqualTo("file://example.txt");
        assertThat(headerValue("ui://cesium-map/mcp-app.html")).isEqualTo("ui://cesium-map/mcp-app.html");
    }

    @Test
    public void testNonAsciiIsEncoded()
    {
        // "=?base64?" + base64("file://café.txt") + "?="
        assertThat(headerValue("file://café.txt")).isEqualTo("=?base64?ZmlsZTovL2NhZsOpLnR4dA==?=");
    }

    @Test
    public void testControlCharactersAreEncoded()
    {
        // a newline would split the header, so it can never be sent literally
        assertThat(headerValue("one\ntwo")).startsWith("=?base64?").doesNotContain("\n");
    }

    @Test
    public void testAValueThatLooksLikeASentinelIsEncoded()
    {
        // sending this literally would have the far side read it back as an encoded value
        assertThat(headerValue("=?base64?bm90IGVuY29kZWQ=?=")).isEqualTo("=?base64?PT9iYXNlNjQ/Ym05MElHVnVZMjlrWldRPT89?=");
    }
}
