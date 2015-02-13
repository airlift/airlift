package com.proofpoint.http.client;

import org.testng.annotations.Test;

import java.net.URI;

import static com.proofpoint.http.client.RelativeUriBuilder.relativeUriBuilder;
import static com.proofpoint.http.client.RelativeUriBuilder.relativeUriBuilderFrom;
import static org.testng.Assert.assertEquals;

public class TestRelativeUriBuilder
{
    @Test
    public void testCreateFromUri()
    {
        URI original = URI.create("http://www.example.com:8081/a%20/%C3%A5?k=1&k=2&%C3%A5=3");
        assertEquals(relativeUriBuilderFrom(original).build(), URI.create("a%20/%C3%A5?k=1&k=2&%C3%A5=3"));
    }

    @Test
    public void testToString()
    {
        URI original = URI.create("http://www.example.com:8081/a%20/%C3%A5?k=1&k=2&%C3%A5=3");
        assertEquals(relativeUriBuilderFrom(original).toString(), "a%20/%C3%A5?k=1&k=2&%C3%A5=3");
        assertEquals(relativeUriBuilder().toString(), "");
    }

    @Test
    public void testBasic()
    {
        URI uri = relativeUriBuilder()
                .build();

        assertEquals(uri.toASCIIString(), "");
    }


    @Test
    public void testWithPath()
    {
        URI uri = relativeUriBuilder()
                .replacePath("/a/b/c")
                .build();

        assertEquals(uri.toASCIIString(), "a/b/c");
    }

    @Test
    public void testReplacePathWithRelative()
    {
        URI uri = relativeUriBuilder()
                .replacePath("a/b/c")
                .build();

        assertEquals(uri.toASCIIString(), "a/b/c");
    }

    @Test
    public void testAppendToDefaultPath()
    {
        URI uri = relativeUriBuilder()
                .appendPath("/a/b/c")
                .build();

        assertEquals(uri.toASCIIString(), "a/b/c");
    }

    @Test
    public void testAppendRelativePathToDefault()
    {
        URI uri = relativeUriBuilder()
                .appendPath("a/b/c")
                .build();

        assertEquals(uri.toASCIIString(), "a/b/c");
    }

    @Test
    public void testAppendAbsolutePath()
    {
        URI uri = relativeUriBuilder()
                .appendPath("/a/b/c")
                .appendPath("/x/y/z")
                .build();

        assertEquals(uri.toASCIIString(), "a/b/c/x/y/z");
    }

    @Test
    public void testAppendRelativePath()
    {
        URI uri = relativeUriBuilder()
                .appendPath("/a/b/c")
                .appendPath("x/y/z")
                .build();

        assertEquals(uri.toASCIIString(), "a/b/c/x/y/z");
    }

    @Test
    public void testAppendPathElidesSlashes()
    {
        URI uri = relativeUriBuilder()
                .appendPath("/a/b/c/")
                .appendPath("/x/y/z")
                .build();

        assertEquals(uri.toASCIIString(), "a/b/c/x/y/z");
    }

    @Test
    public void testDoesNotStripTrailingSlash()
    {
        URI uri = relativeUriBuilder()
                .appendPath("/a/b/c/")
                .build();

        assertEquals(uri.toASCIIString(), "a/b/c/");
    }

    @Test
    public void testFull()
    {
        URI uri = relativeUriBuilder()
                .replacePath("/a/b/c")
                .replaceParameter("k", "1")
                .build();
        
        assertEquals(uri.toASCIIString(), "a/b/c?k=1");
    }

    @Test
    public void testAddParameter()
    {
        URI uri = relativeUriBuilder()
                .replacePath("/")
                .addParameter("k1", "1")
                .addParameter("k1", "2")
                .addParameter("k1", "0")
                .addParameter("k2", "3")
                .build();

        assertEquals(uri.toASCIIString(), "?k1=1&k1=2&k1=0&k2=3");
    }

    @Test
    public void testAddParameterMultivalued()
    {
        URI uri = relativeUriBuilder()
                .replacePath("/")
                .addParameter("k1", "1", "2", "0")
                .build();

        assertEquals(uri.toASCIIString(), "?k1=1&k1=2&k1=0");
    }

    @Test
    public void testAddEmptyParameter()
    {
        URI uri = relativeUriBuilder()
                .addParameter("pretty")
                .build();

        assertEquals(uri.toASCIIString(), "?pretty");
    }

    @Test
    public void testAddMultipleEmptyParameters()
    {
        URI uri = relativeUriBuilder()
                .addParameter("pretty")
                .addParameter("pretty")
                .build();

        assertEquals(uri.toASCIIString(), "?pretty&pretty");
    }

    @Test
    public void testAddMixedEmptyAndNonEmptyParameters()
    {
        URI uri = relativeUriBuilder()
                .addParameter("pretty")
                .addParameter("pretty", "true")
                .addParameter("pretty")
                .build();

        assertEquals(uri.toASCIIString(), "?pretty&pretty=true&pretty");
    }

    @Test
    public void testReplaceParameters()
    {
        URI uri = relativeUriBuilderFrom(URI.create("http://www.example.com:8081/?k1=1&k1=2&k2=3"))
                .replaceParameter("k1", "4")
                .build();

        assertEquals(uri.toASCIIString(), "?k2=3&k1=4");
    }

    @Test
    public void testReplaceParameterMultivalued()
    {
        URI uri = relativeUriBuilderFrom(URI.create("http://www.example.com/?k1=1&k1=2&k2=3"))
                .replaceParameter("k1", "a", "b", "c")
                .build();

        assertEquals(uri.toASCIIString(), "?k2=3&k1=a&k1=b&k1=c");
    }

    @Test
    public void testReplaceRawQuery()
    {
        URI uri = relativeUriBuilderFrom(URI.create("http://www.example.com:8081/a%20/%C3%A5?k1=1&k1=2&k2=3"))
                .replaceRawQuery("k=1&k=2&%C3%A5=3")
                .build();

        assertEquals(uri.toASCIIString(), "a%20/%C3%A5?k=1&k=2&%C3%A5=3");
    }

    @Test
    public void testEncodesPath()
    {
        URI uri = relativeUriBuilder()
                .replacePath("/`#%^{}|[]<>?áéíóú+?")
                .build();

        assertEquals(uri.toASCIIString(), "%60%23%25%5E%7B%7D%7C%5B%5D%3C%3E%3F%C3%A1%C3%A9%C3%AD%C3%B3%C3%BA%2B%3F");
    }

    @Test
    public void testEncodesQueryParameters()
    {
        URI uri = relativeUriBuilder()
            .replaceParameter("a+&", "&+")
            .build();

        assertEquals(uri.toASCIIString(), "?a%2B%26=%26%2B");
    }
}
