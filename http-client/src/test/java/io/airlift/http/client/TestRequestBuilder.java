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
import org.junit.jupiter.api.Test;

import java.net.URI;

import static io.airlift.http.client.Request.Builder.fromRequest;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestRequestBuilder
{
    public static final BodyGenerator NULL_BODY_GENERATOR = createStaticBodyGenerator(new byte[0]);

    @Test
    public void testRequestBuilder()
    {
        Request request = createRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getBodyGenerator()).isEqualTo(NULL_BODY_GENERATOR);
        assertThat(request.getUri()).isEqualTo(URI.create("http://example.com"));
        assertThat(request.getHeaders()).isEqualTo(ImmutableListMultimap.of(
                "newheader", "withvalue", "anotherheader", "anothervalue"));
        assertThat(request.isFollowRedirects()).isFalse();
    }

    @Test
    public void testCannotBuildRequestToIllegalPort()
    {
        assertThatThrownBy(() -> prepareGet().setUri(URI.create("http://example.com:0/")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot make requests to HTTP port 0");
    }

    @Test
    public void testBuilderFromRequest()
    {
        Request request = createRequest();
        assertThat(fromRequest(request).build()).isEqualTo(request);
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
                .build();
    }
}
