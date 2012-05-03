package com.proofpoint.http.client;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.proofpoint.testing.EquivalenceTester;
import org.testng.annotations.Test;

import java.net.URI;

public class TestRequest
{
    @Test
    public void testEquivalence()
    {
        BodyGenerator bodyGenerator = createBodyGenerator();

        EquivalenceTester.<Request>equivalenceTester()
                .addEquivalentGroup(
                        new Request(createUri1(), "GET", createHeaders1(), bodyGenerator),
                        new Request(createUri1(), "GET", createHeaders1(), bodyGenerator))
                .addEquivalentGroup(
                        new Request(createUri2(), "GET", createHeaders1(), bodyGenerator))
                .addEquivalentGroup(
                        new Request(createUri1(), "PUT", createHeaders1(), bodyGenerator),
                        new Request(createUri1(), "PUT", createHeaders1(), bodyGenerator))
                .addEquivalentGroup(
                        new Request(createUri1(), "GET", createHeaders2(), bodyGenerator))
                .addEquivalentGroup(
                        new Request(createUri1(), "GET", createHeaders1(), createBodyGenerator()))
                .check();
    }

    private URI createUri1()
    {
        return URI.create("http://example.com");
    }

    private URI createUri2()
    {
        return URI.create("http://example.net");
    }

    private ListMultimap<String, String> createHeaders1()
    {
        return ImmutableListMultimap.of("foo", "bar", "abc", "xyz");
    }

    private ListMultimap<String, String> createHeaders2()
    {
        return ImmutableListMultimap.of("foo", "bar", "abc", "xyz", "qqq", "www", "foo", "zzz");
    }

    public static BodyGenerator createBodyGenerator()
    {
        return StaticBodyGenerator.createStaticBodyGenerator(new byte[0]);
    }
}
