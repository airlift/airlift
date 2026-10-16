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
package io.airlift.secrets.oidc;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.airlift.http.client.FormDataBodyBuilder;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.client.StringResponseHandler;
import io.airlift.json.JsonCodec;
import io.airlift.spi.secrets.HttpHeadersSecret;
import io.airlift.spi.secrets.Secret;
import io.airlift.spi.secrets.SecretProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.json.JsonCodec.jsonCodec;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.Objects.requireNonNull;

public class OidcSecretProvider
        implements SecretProvider
{
    private static final JsonCodec<TokenExchangeResponse> TOKEN_EXCHANGE_RESPONSE_JSON_CODEC = jsonCodec(TokenExchangeResponse.class);
    private final LoadingCache<String, BearerTokenCredential> cache;

    @Inject
    public OidcSecretProvider(@ForOidcHttpClient HttpClient httpClient, OidcSecretProviderConfig config)
    {
        requireNonNull(config, "config is null");
        this.cache = CacheBuilder.newBuilder().expireAfterWrite(config.getCacheExpiry().toJavaTime())
                .build(new CacheLoader<>()
                {
                    @Override
                    public BearerTokenCredential load(String subjectToken)
                    {
                        Request request = preparePost()
                                .setUri(config.getTokenUrl())
                                .setMethod("POST")
                                .setHeader(CONTENT_TYPE, "application/x-www-form-urlencoded")
                                .setBodyGenerator(new FormDataBodyBuilder()
                                        .addField("client_id", requireNonNull(config.getClientId(), "clientId is null"))
                                        .addField("client_secret", requireNonNull(config.getClientSecret(), "clientSecret is null"))
                                        .addField("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                                        .addField("subject_token", subjectToken)
                                        .addField("subject_token_type", "urn:ietf:params:oauth:token-type:access_token")
                                        .addField("scope", config.getScopes())
                                        .addField("audience", config.getAudience())
                                        .build())
                                .build();

                        StringResponseHandler.StringResponse response = httpClient.execute(request, createStringResponseHandler());
                        if (response.getStatusCode() != 200) {
                            throw new IllegalStateException(config.getTokenUrl() + " returned " + response.getStatusCode() + ": " + response.getBody());
                        }

                        TokenExchangeResponse exchangeResponse = TOKEN_EXCHANGE_RESPONSE_JSON_CODEC.fromJson(response.getBody());
                        return new BearerTokenCredential(exchangeResponse.accessToken(), Instant.now().plusSeconds(exchangeResponse.expiresIn()).minusSeconds(30));
                    }
                });
    }

    @Override
    public String resolveSecretValue(String key)
    {
        throw new IllegalStateException("Oidc secrets plugin needs context resolver");
    }

    @Override
    public <T extends Secret> Optional<T> resolveSecret(String key, Class<T> type, Function<String, Optional<Object>> contextResolver)
    {
        if (!type.isAssignableFrom(HttpHeadersSecret.class)) {
            return Optional.empty();
        }
        return contextResolver.apply("subject_token").map(subjectToken ->
                        cache.getUnchecked((String) subjectToken))
                .map(credential -> (T) new HttpHeadersSecret(ImmutableMap.of("Authorization", "Bearer %s".formatted(credential.accessToken()))));
    }

    record BearerTokenCredential(String accessToken, Instant expiresIn) {}
}
