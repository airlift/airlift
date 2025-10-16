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

import static io.airlift.http.client.Request.Builder.fromRequest;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableListMultimap;
import java.net.URI;
import org.junit.jupiter.api.Test;

public class TestRequestBuilder {
    public static final BodyGenerator NULL_BODY_GENERATOR = createStaticBodyGenerator(new byte[0]);

    @Test
    public void testRequestBuilder() {
        Request request = createRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getBodyGenerator()).isEqualTo(NULL_BODY_GENERATOR);
        assertThat(request.getUri()).isEqualTo(URI.create("http://example.com"));
        assertThat(request.getHeaders())
                .isEqualTo(ImmutableListMultimap.of("newheader", "withvalue", "anotherheader", "anothervalue"));
        assertThat(request.isFollowRedirects()).isFalse();
    }

    @Test
    public void testCannotBuildRequestToIllegalPort() {
        assertThatThrownBy(() -> prepareGet().setUri(URI.create("http://example.com:0/")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot make requests to HTTP port 0");
    }

    @Test
    public void testBuilderFromRequest() {
        Request request = createRequest();
        assertThat(fromRequest(request).build()).isEqualTo(request);
    }

    private static Request createRequest() {
        return prepareGet()
                .setUri(URI.create("http://example.com"))
                .addHeader("newheader", "withvalue")
                .addHeader("anotherheader", "anothervalue")
                .setBodyGenerator(NULL_BODY_GENERATOR)
                .setFollowRedirects(false)
                .build();
    }
}
