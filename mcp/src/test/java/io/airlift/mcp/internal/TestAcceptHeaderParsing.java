package io.airlift.mcp.internal;

import org.junit.jupiter.api.Test;

import static io.airlift.mcp.internal.InternalFilter.parseAcceptHeader;
import static org.assertj.core.api.Assertions.assertThat;

public class TestAcceptHeaderParsing
{
    @Test
    public void singleType()
    {
        assertThat(parseAcceptHeader("text/html"))
                .containsExactly("text/html");
    }

    @Test
    public void multipleTypes()
    {
        assertThat(parseAcceptHeader("text/html, application/xml, application/json"))
                .containsExactly("text/html", "application/xml", "application/json");
    }

    @Test
    public void stripsParameters()
    {
        assertThat(parseAcceptHeader("text/html, application/xml;q=0.9, */*;q=0.8"))
                .containsExactly("text/html", "application/xml", "*/*");
    }

    @Test
    public void stripsMultipleParameters()
    {
        assertThat(parseAcceptHeader("text/html;level=1;q=0.9;charset=utf-8"))
                .containsExactly("text/html");
    }

    @Test
    public void preservesOrder()
    {
        assertThat(parseAcceptHeader("*/*, text/html, application/json"))
                .containsExactly("*/*", "text/html", "application/json");
    }

    @Test
    public void lowercasesTypes()
    {
        assertThat(parseAcceptHeader("Text/HTML, Application/JSON"))
                .containsExactly("text/html", "application/json");
    }

    @Test
    public void trimsWhitespace()
    {
        assertThat(parseAcceptHeader("  text/html  ,   application/json  "))
                .containsExactly("text/html", "application/json");
    }

    @Test
    public void handlesWildcards()
    {
        assertThat(parseAcceptHeader("text/*, */*"))
                .containsExactly("text/*", "*/*");
    }

    @Test
    public void nullReturnsEmptyList()
    {
        assertThat(parseAcceptHeader(null)).isEmpty();
    }

    @Test
    public void emptyStringReturnsEmptyList()
    {
        assertThat(parseAcceptHeader("")).isEmpty();
    }

    @Test
    public void blankStringReturnsEmptyList()
    {
        assertThat(parseAcceptHeader("   ")).isEmpty();
    }

    @Test
    public void skipsEmptyEntries()
    {
        assertThat(parseAcceptHeader("text/html,,application/json"))
                .containsExactly("text/html", "application/json");
    }

    @Test
    public void skipsEntryThatIsJustAParameter()
    {
        assertThat(parseAcceptHeader("text/html, ;q=0.5"))
                .containsExactly("text/html");
    }

    @Test
    public void realBrowserHeader()
    {
        assertThat(parseAcceptHeader(
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"))
                .containsExactly("text/html", "application/xhtml+xml", "application/xml", "image/webp", "*/*");
    }
}
