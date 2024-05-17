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
package io.airlift.http.client;

import com.google.common.collect.ImmutableListMultimap;
import org.testng.annotations.Test;

import java.net.URI;

import static io.airlift.http.client.Request.Builder.fromRequest;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

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
        assertFalse(request.isFollowRedirects());
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
    public void testAddCaseInsensitive()
    {
        Request request = prepareGet()
                .setUri(URI.create("http://example.com"))
                .addHeader("my-header", "lower case")
                .addHeader("My-HeADer", "MixED CAse")
                .build();
        assertThat(request.getHeaders().keySet()).hasSize(1);
        assertThat(request.getHeaders().entries())
                .containsExactlyInAnyOrder(
                        entry("my-header", "lower case"),
                        entry("my-header", "MixED CAse"));
    }

    @Test
    public void testSetCaseInsensitive()
    {
        Request request = prepareGet()
                .setUri(URI.create("http://example.com"))
                .addHeader("my-header", "lower case")
                .addHeader("My-HeADer", "MixED CAse")
                // setHeader() should replace the existing headers
                .setHeader("My-Header", "replaced")
                .build();
        assertThat(request.getHeaders().keySet()).hasSize(1);
        assertThat(request.getHeaders().entries())
                .containsExactly(entry("My-Header", "replaced"));

        request = prepareGet()
                .setUri(URI.create("http://example.com"))
                .addHeader("my-header", "lower case")
                .addHeader("My-HeADer", "MixED CAse")
                // setHeader() should replace the existing headers
                .setHeader("My-HeADEr", "replaced-mixed")
                .build();
        assertThat(request.getHeaders().keySet()).hasSize(1);
        assertThat(request.getHeaders().entries())
                .containsExactly(entry("My-HeADEr", "replaced-mixed"));
    }

    private static Request createRequest()
    {
        return prepareGet()
                .setUri(URI.create("http://example.com"))
                .addHeader("newheader", "withvalue")
                .addHeader("anotherheader", "anothervalue")
                .setBodyGenerator(NULL_BODY_GENERATOR)
                .setFollowRedirects(false)
                .setPreserveAuthorizationOnRedirect(true)
                .build();
    }
}
