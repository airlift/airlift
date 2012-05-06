package com.proofpoint.http.client;

import com.google.common.collect.ImmutableListMultimap;
import org.testng.annotations.Test;

import java.net.URI;

import static com.proofpoint.http.client.Request.Builder.fromRequest;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static org.testng.Assert.assertEquals;

public class TestRequestBuilder
{
    public static final BodyGenerator NULL_BODY_GENERATOR = createStaticBodyGenerator(new byte[0]);

    @Test
    public void testRequestBuilder()
    {
        Request request = createRequest();
        assertEquals(request.getMethod(), "GET");
        assertEquals(request.getBodyGenerator(), NULL_BODY_GENERATOR);
        assertEquals(request.getUri(), URI.create("http://example.com"));
        assertEquals(request.getHeaders(), ImmutableListMultimap.of(
                "newheader", "withvalue", "anotherheader", "anothervalue"));
    }

    @Test
    public void testBuilderFromRequest()
    {
        Request request = createRequest();
        assertEquals(fromRequest(request).build(), request);
    }

    private Request createRequest()
    {
        return prepareGet()
                    .setUri(URI.create("http://example.com"))
                    .addHeader("newheader", "withvalue")
                    .addHeader("anotherheader", "anothervalue")
                    .setBodyGenerator(NULL_BODY_GENERATOR)
                    .build();
    }
}
