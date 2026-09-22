/*
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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.ResponseHandlerUtils.propagate;
import static io.airlift.http.client.ResponseHandlerUtils.sanitizeUri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestResponseHandlerUtils
{
    @Test
    void testSanitizeUri()
    {
        assertThat(sanitizeUri(null)).isNull();
        assertThat(sanitizeUri(URI.create("https://example.test/path")))
                .isEqualTo(URI.create("https://example.test/path"));
        assertThat(sanitizeUri(URI.create("https://user:password@example.test:8443/path?token=secret#fragment")))
                .isEqualTo(URI.create("https://example.test:8443/path"));
        assertThat(sanitizeUri(URI.create("https://example.test/a%2Fb/%E2%82%AC?q=1")))
                .isEqualTo(URI.create("https://example.test/a%2Fb/%E2%82%AC"));
        assertThat(sanitizeUri(URI.create("/relative/path?token=secret")))
                .isEqualTo(URI.create("/relative/path"));
        assertThat(sanitizeUri(URI.create("https://example.test")))
                .isEqualTo(URI.create("https://example.test"));
    }

    @Test
    void testPropagateRedactsUri()
    {
        Request request = prepareGet()
                .setUri(URI.create("https://user:password@example.test/path?token=secret"))
                .build();

        assertThatThrownBy(() -> {
            throw propagate(request, new IOException("boom"));
        })
                .hasMessage("Failed communicating with server: https://example.test/path");
    }
}
