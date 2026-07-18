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

import com.google.common.collect.ImmutableMap;

import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.http.client.HeaderNames.AUTHORIZATION;
import static java.util.Objects.requireNonNull;

public final class HttpClientCredentials
{
    private final Map<String, Credential> credentials;

    private HttpClientCredentials(Map<String, Credential> credentials)
    {
        this.credentials = ImmutableMap.copyOf(credentials);
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static HttpClientCredentials none()
    {
        return builder().build();
    }

    public boolean hasBearerToken(String schemeName)
    {
        return credentials.get(schemeName) instanceof BearerCredential;
    }

    public boolean hasBasicAuth(String schemeName)
    {
        return credentials.get(schemeName) instanceof BasicCredential;
    }

    public boolean hasHeaderApiKey(String schemeName)
    {
        return credentials.get(schemeName) instanceof HeaderApiKeyCredential;
    }

    public BearerTokenProvider bearerTokenProvider(String schemeName)
    {
        return credential(schemeName, BearerCredential.class).tokenProvider();
    }

    public void applyBearerToken(String schemeName, String token, Request.Builder requestBuilder)
    {
        credential(schemeName, BearerCredential.class).apply(token, requestBuilder);
    }

    public void applyBasicAuth(String schemeName, Request.Builder requestBuilder)
    {
        credential(schemeName, BasicCredential.class).credentials().apply(requestBuilder);
    }

    public void applyHeaderApiKey(String schemeName, String headerName, Request.Builder requestBuilder)
    {
        credential(schemeName, HeaderApiKeyCredential.class).credentials().apply(headerName, requestBuilder);
    }

    private <T extends Credential> T credential(String schemeName, Class<T> type)
    {
        requireNonNull(schemeName, "schemeName is null");
        Credential credential = credentials.get(schemeName);
        checkArgument(type.isInstance(credential), "No %s credentials configured for scheme '%s'", typeName(type), schemeName);
        return type.cast(credential);
    }

    private static String typeName(Class<? extends Credential> type)
    {
        if (type == BearerCredential.class) {
            return "bearer token";
        }
        if (type == BasicCredential.class) {
            return "Basic authentication";
        }
        return "header API key";
    }

    @Override
    public String toString()
    {
        return "HttpClientCredentials{schemes=" + credentials.keySet() + '}';
    }

    public static final class Builder
    {
        private final ImmutableMap.Builder<String, Credential> credentials = ImmutableMap.builder();

        private Builder() {}

        public Builder bearerToken(String schemeName, BearerTokenProvider tokenProvider)
        {
            credentials.put(validateSchemeName(schemeName), new BearerCredential(requireNonNull(tokenProvider, "tokenProvider is null")));
            return this;
        }

        public Builder basicAuth(String schemeName, BasicAuthCredentials basicAuthCredentials)
        {
            credentials.put(validateSchemeName(schemeName), new BasicCredential(requireNonNull(basicAuthCredentials, "basicAuthCredentials is null")));
            return this;
        }

        public Builder basicAuth(String schemeName, String username, String password)
        {
            return basicAuth(schemeName, new BasicAuthCredentials(username, password));
        }

        public Builder headerApiKey(String schemeName, HeaderApiKeyCredentials headerApiKeyCredentials)
        {
            credentials.put(validateSchemeName(schemeName), new HeaderApiKeyCredential(requireNonNull(headerApiKeyCredentials, "headerApiKeyCredentials is null")));
            return this;
        }

        public Builder headerApiKey(String schemeName, String apiKey)
        {
            return headerApiKey(schemeName, new HeaderApiKeyCredentials(apiKey));
        }

        public HttpClientCredentials build()
        {
            return new HttpClientCredentials(credentials.buildOrThrow());
        }

        private static String validateSchemeName(String schemeName)
        {
            checkArgument(!requireNonNull(schemeName, "schemeName is null").isBlank(), "schemeName is blank");
            return schemeName;
        }
    }

    private sealed interface Credential
            permits BasicCredential,
                    BearerCredential,
                    HeaderApiKeyCredential {}

    private record BearerCredential(BearerTokenProvider tokenProvider)
            implements Credential
    {
        private BearerCredential
        {
            requireNonNull(tokenProvider, "tokenProvider is null");
        }

        private void apply(String token, Request.Builder requestBuilder)
        {
            requireNonNull(requestBuilder, "requestBuilder is null")
                    .setHeader(AUTHORIZATION, "Bearer " + requireNonNull(token, "token is null"));
        }
    }

    private record BasicCredential(BasicAuthCredentials credentials)
            implements Credential {}

    private record HeaderApiKeyCredential(HeaderApiKeyCredentials credentials)
            implements Credential {}
}
