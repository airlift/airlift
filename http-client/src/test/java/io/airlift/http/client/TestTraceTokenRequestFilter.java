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

import com.google.common.collect.ImmutableList;
import io.airlift.tracetoken.TraceTokenManager;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.TraceTokenRequestFilter.TRACETOKEN_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

public class TestTraceTokenRequestFilter
{
    @Test
    public void testBasic()
    {
        TraceTokenManager manager = new TraceTokenManager();
        manager.registerRequestToken("testBasic");
        TraceTokenRequestFilter filter = new TraceTokenRequestFilter(manager);
        Request original = prepareGet().setUri(URI.create("http://example.com")).build();

        Request filtered = filter.filterRequest(original);

        assertThat(filtered).isNotSameAs(original);
        assertThat(filtered.getUri()).isEqualTo(original.getUri());
        assertThat(original.getHeaders().size()).isEqualTo(0);
        assertThat(filtered.getHeaders().size()).isEqualTo(1);
        assertThat(filtered.getHeaders().get(TRACETOKEN_HEADER)).isEqualTo(ImmutableList.of("testBasic"));
    }

    @Test
    public void testSameRequestReturnedWhenTraceTokenNotSet()
    {
        TraceTokenManager manager = new TraceTokenManager();
        TraceTokenRequestFilter filter = new TraceTokenRequestFilter(manager);
        Request original = prepareGet().setUri(URI.create("http://example.com")).build();

        Request request = filter.filterRequest(original);

        assertThat(request).isSameAs(original);
    }
}
