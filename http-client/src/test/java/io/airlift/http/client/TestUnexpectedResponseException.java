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

import com.google.common.collect.ImmutableListMultimap;
import io.airlift.http.client.testing.TestingResponse;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static io.airlift.http.client.HeaderNames.AUTHORIZATION;
import static io.airlift.http.client.HttpStatus.INTERNAL_SERVER_ERROR;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class TestUnexpectedResponseException
{
    private static final HeaderName REQUEST_ID = HeaderName.of("X-Request-Id");
    private static final HeaderName SET_COOKIE = HeaderName.of("Set-Cookie");

    @Test
    void testDiagnosticsAreRedacted()
    {
        Request request = prepareGet()
                .setUri(URI.create("https://user:password@example.test:8443/v1/items?api_key=secret#fragment"))
                .setHeader(AUTHORIZATION, "Bearer secret-token")
                .setHeader(REQUEST_ID, "request-secret")
                .build();
        TestingResponse response = new TestingResponse(
                INTERNAL_SERVER_ERROR,
                ImmutableListMultimap.of(SET_COOKIE, "session=secret"),
                "{\"error\":\"secret-response\"}".getBytes(UTF_8));

        UnexpectedResponseException exception = new UnexpectedResponseException("Request failed", request, response);

        assertThat(request.toString())
                .contains("https://example.test:8443/v1/items")
                .contains("authorization")
                .contains("x-request-id")
                .doesNotContain("user", "password", "api_key", "secret-token", "request-secret", "fragment");
        assertThat(exception.getMessage()).isEqualTo("Request failed");
        assertThat(exception.getHeader(SET_COOKIE)).isEqualTo("session=secret");
        assertThat(exception.toString())
                .contains("GET https://example.test:8443/v1/items")
                .contains("statusCode=500")
                .doesNotContain("secret", "Set-Cookie", "headers");
    }

    @Test
    void testSanitizedUriPreservesRawPath()
    {
        Request request = prepareGet()
                .setUri(URI.create("https://user:password@example.test/a%2Fb/%E2%82%AC?token=secret#fragment"))
                .build();

        UnexpectedResponseException exception = new UnexpectedResponseException(
                "Request failed",
                request,
                INTERNAL_SERVER_ERROR.code(),
                ImmutableListMultimap.of());

        assertThat(exception.toString()).contains("GET https://example.test/a%2Fb/%E2%82%AC");
    }
}
