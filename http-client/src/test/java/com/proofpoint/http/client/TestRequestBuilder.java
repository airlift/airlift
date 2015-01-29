/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.http.client;

import com.google.common.collect.ImmutableListMultimap;
import org.testng.annotations.Test;

import java.net.URI;

import static com.proofpoint.http.client.Request.Builder.fromRequest;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.Request.Builder.preparePut;
import static com.proofpoint.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestRequestBuilder
{
    public static final BodySource NULL_BODY_SOURCE = new BodySource()
    {
    };

    @Test
    public void testRequestBuilder()
    {
        Request request = createRequest();
        assertEquals(request.getMethod(), "GET");
        assertEquals(request.getBodySource(), NULL_BODY_SOURCE);
        assertEquals(request.getUri(), URI.create("http://example.com"));
        assertEquals(request.getHeaders(), ImmutableListMultimap.of(
                "newheader", "withvalue", "anotherheader", "anothervalue"));
        assertTrue(request.isFollowRedirects());
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Cannot make requests to HTTP port 0")
    public void testCannotBuildRequestToIllegalPort()
            throws Exception
    {
        prepareGet().setUri(URI.create("http://example.com:0/"));
    }

    @Test
    public void testBuilderFromRequest()
    {
        Request request = createRequest();
        assertEquals(fromRequest(request).build(), request);
    }

    @Test
    public void testDefaults()
    {
        Request request = preparePut()
                .setUri(URI.create("http://example.com"))
                .build();
        assertEquals(request.getMethod(), "PUT");
        assertNull(request.getBodySource());
        assertEquals(request.getUri(), URI.create("http://example.com"));
        assertEquals(request.getHeaders(), ImmutableListMultimap.<String, String>of());
        assertFalse(request.isFollowRedirects());
    }

    private Request createRequest()
    {
        return prepareGet()
                    .setUri(URI.create("http://example.com"))
                    .addHeader("newheader", "withvalue")
                    .addHeader("anotherheader", "anothervalue")
                    .setBodySource(NULL_BODY_SOURCE)
                    .setFollowRedirects(true)
                    .build();
    }
}
