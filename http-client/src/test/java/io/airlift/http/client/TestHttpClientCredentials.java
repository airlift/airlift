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

import java.net.URI;

import static io.airlift.http.client.HeaderNames.AUTHORIZATION;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static org.assertj.core.api.Assertions.assertThat;

public class TestHttpClientCredentials
{
    @Test
    public void testAppliesNamedCredentialsWithoutDiagnosticDisclosure()
    {
        BearerTokenProvider tokenProvider = BearerTokenProvider.fixedToken("bearer-secret");
        HttpClientCredentials credentials = HttpClientCredentials.builder()
                .bearerToken("serviceBearer", tokenProvider)
                .basicAuth("serviceBasic", "Aladdin", "open sesame")
                .headerApiKey("serviceKey", "api-key-secret")
                .build();

        assertThat(credentials.hasBearerToken("serviceBearer")).isTrue();
        assertThat(credentials.hasBasicAuth("serviceBasic")).isTrue();
        assertThat(credentials.hasHeaderApiKey("serviceKey")).isTrue();
        assertThat(credentials.bearerTokenProvider("serviceBearer")).isSameAs(tokenProvider);

        Request.Builder requestBuilder = prepareGet().setUri(URI.create("http://example.test/"));
        credentials.applyBearerToken("serviceBearer", "bearer-secret", requestBuilder);
        assertThat(requestBuilder.build().getHeader(AUTHORIZATION)).isEqualTo("Bearer bearer-secret");

        requestBuilder = prepareGet().setUri(URI.create("http://example.test/"));
        credentials.applyBasicAuth("serviceBasic", requestBuilder);
        assertThat(requestBuilder.build().getHeader(AUTHORIZATION)).isEqualTo("Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==");

        requestBuilder = prepareGet().setUri(URI.create("http://example.test/"));
        credentials.applyHeaderApiKey("serviceKey", "X-Service-Key", requestBuilder);
        assertThat(requestBuilder.build().getHeader(HeaderName.of("X-Service-Key"))).isEqualTo("api-key-secret");

        assertThat(credentials.toString())
                .contains("serviceBearer", "serviceBasic", "serviceKey")
                .doesNotContain("bearer-secret", "open sesame", "api-key-secret");
        assertThat(new BasicAuthCredentials("Aladdin", "open sesame").toString()).doesNotContain("Aladdin", "open sesame");
        assertThat(new HeaderApiKeyCredentials("api-key-secret").toString()).doesNotContain("api-key-secret");
    }
}
