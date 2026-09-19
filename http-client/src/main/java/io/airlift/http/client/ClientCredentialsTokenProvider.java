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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.airlift.json.JsonCodec;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.net.UrlEscapers.urlFormParameterEscaper;
import static io.airlift.http.client.HeaderNames.ACCEPT;
import static io.airlift.http.client.HeaderNames.AUTHORIZATION;
import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.ResponseHandlerUtils.captureUnexpectedResponse;
import static io.airlift.http.client.ResponseHandlerUtils.getResponseBytes;
import static io.airlift.http.client.ResponseHandlerUtils.isJsonUtf8Content;
import static io.airlift.http.client.ResponseHandlerUtils.propagate;
import static io.airlift.json.JsonCodec.mapJsonCodec;
import static io.airlift.units.DataSize.Unit.MEGABYTE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public final class ClientCredentialsTokenProvider
        implements BearerTokenProvider
{
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    // far above any real token document; the transport rejects a larger response before it is buffered
    private static final DataSize MAX_TOKEN_RESPONSE_SIZE = DataSize.of(1, MEGABYTE);
    private static final int MAX_ATTEMPTS = 10;
    private static final Duration DEFAULT_EXPIRY_SKEW = new Duration(30, TimeUnit.SECONDS);
    private static final Duration DEFAULT_RETRY_INITIAL_DELAY = new Duration(100, TimeUnit.MILLISECONDS);
    private static final Duration DEFAULT_RETRY_MAX_DELAY = new Duration(1, TimeUnit.SECONDS);
    private static final JsonCodec<Map<String, Object>> JSON_DOCUMENT_CODEC = mapJsonCodec(String.class, Object.class);

    private final HttpClient httpClient;
    private final URI tokenUri;
    private final String authorizationHeader;
    private final List<String> scopes;
    private final Optional<String> resource;
    private final long expirySkewMillis;
    private final int maxAttempts;
    private final long retryInitialDelayMillis;
    private final long retryMaxDelayMillis;
    private final Clock clock;
    private final Object refreshLock = new Object();

    private volatile CachedToken cachedToken;

    private ClientCredentialsTokenProvider(Builder builder)
    {
        httpClient = builder.httpClient;
        tokenUri = builder.tokenUri;
        authorizationHeader = clientSecretBasicAuthorization(builder.clientId, builder.clientSecret);
        scopes = ImmutableList.copyOf(builder.scopes);
        resource = builder.resource;
        expirySkewMillis = builder.expirySkew.toMillis();
        maxAttempts = builder.maxAttempts;
        retryInitialDelayMillis = builder.retryInitialDelay.toMillis();
        retryMaxDelayMillis = builder.retryMaxDelay.toMillis();
        clock = builder.clock;
    }

    public static Builder builder(HttpClient httpClient, URI tokenUri, String clientId, String clientSecret)
    {
        return new Builder(httpClient, tokenUri, clientId, clientSecret);
    }

    @Override
    public String getToken()
    {
        Instant now = clock.instant();
        CachedToken token = cachedToken;
        if (isFresh(token, now)) {
            return token.accessToken();
        }

        synchronized (refreshLock) {
            now = clock.instant();
            token = cachedToken;
            if (isFresh(token, now)) {
                return token.accessToken();
            }

            try {
                CachedToken refreshed = acquireToken();
                cachedToken = refreshed;
                return refreshed.accessToken();
            }
            catch (RuntimeException failure) {
                if (isUnexpired(token, clock.instant())) {
                    return token.accessToken();
                }
                throw failure;
            }
        }
    }

    @Override
    public boolean refreshToken(String rejectedToken)
    {
        requireNonNull(rejectedToken, "rejectedToken is null");
        synchronized (refreshLock) {
            CachedToken current = cachedToken;
            if (isUnexpired(current, clock.instant()) && !current.accessToken().equals(rejectedToken)) {
                return true;
            }
            if (current != null && current.accessToken().equals(rejectedToken)) {
                cachedToken = null;
            }

            CachedToken refreshed = acquireToken();
            if (refreshed.accessToken().equals(rejectedToken)) {
                return false;
            }
            cachedToken = refreshed;
            return true;
        }
    }

    public Set<String> getGrantedScopes()
    {
        CachedToken token = cachedToken;
        return token == null ? Set.of() : token.grantedScopes();
    }

    @Override
    public String toString()
    {
        return "ClientCredentialsTokenProvider{REDACTED}";
    }

    private CachedToken acquireToken()
    {
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                TokenResponse response = httpClient.execute(buildTokenRequest(), new TokenResponseHandler(scopes));
                return toCachedToken(response, scopes, clock.instant());
            }
            catch (RuntimeException failure) {
                lastFailure = failure;
                if (attempt + 1 >= maxAttempts || !isRetryable(failure)) {
                    throw failure;
                }
                sleep(getRetryDelayMillis(failure, attempt));
            }
        }
        throw requireNonNull(lastFailure, "lastFailure is null");
    }

    private Request buildTokenRequest()
    {
        FormDataBodyBuilder form = new FormDataBodyBuilder()
                .addField("grant_type", "client_credentials");
        if (!scopes.isEmpty()) {
            form.addField("scope", String.join(" ", scopes));
        }
        resource.ifPresent(value -> form.addField("resource", value));

        return preparePost()
                .setUri(tokenUri)
                .setHeader(AUTHORIZATION, authorizationHeader)
                .setHeader(ACCEPT, "application/json")
                .setHeader(CONTENT_TYPE, "application/x-www-form-urlencoded")
                .setMaxResponseContentLength(MAX_TOKEN_RESPONSE_SIZE)
                .setBodyGenerator(form.build())
                .build();
    }

    private static CachedToken toCachedToken(TokenResponse response, List<String> requestedScopes, Instant acquiredAt)
    {
        Optional<Instant> expiresAt = Optional.ofNullable(response.expiresIn())
                .map(seconds -> expiration(acquiredAt, seconds));
        return new CachedToken(response.accessToken(), expiresAt, grantedScopes(response, requestedScopes));
    }

    private static Set<String> grantedScopes(TokenResponse response, Collection<String> requestedScopes)
    {
        if (response.scope() == null) {
            return ImmutableSet.copyOf(requestedScopes);
        }
        return response.scope().isBlank() ? Set.of() : ImmutableSet.copyOf(response.scope().trim().split("\\s+"));
    }

    private static Instant expiration(Instant acquiredAt, long expiresInSeconds)
    {
        if (expiresInSeconds <= 0) {
            return acquiredAt;
        }
        try {
            return acquiredAt.plusSeconds(expiresInSeconds);
        }
        catch (ArithmeticException | DateTimeException _) {
            // beyond Instant.MAX (DateTimeException) or beyond a long of seconds (ArithmeticException)
            return Instant.MAX;
        }
    }

    private boolean isFresh(@Nullable CachedToken token, Instant now)
    {
        if (token == null || !isUnexpired(token, now)) {
            return false;
        }
        return token.expiresAt()
                .map(expiresAt -> now.plusMillis(expirySkewMillis).isBefore(expiresAt))
                .orElse(true);
    }

    private static boolean isUnexpired(@Nullable CachedToken token, Instant now)
    {
        return token != null && token.expiresAt().map(now::isBefore).orElse(true);
    }

    private long getRetryDelayMillis(RuntimeException failure, int attempt)
    {
        if (failure instanceof UnexpectedResponseException responseException) {
            Optional<Long> retryAfterMillis = responseException.getRetryAfter(clock.instant()).map(java.time.Duration::toMillis).filter(millis -> millis > 0);
            if (retryAfterMillis.isPresent()) {
                return Math.min(retryAfterMillis.orElseThrow(), retryMaxDelayMillis);
            }
        }

        double calculatedDelay = retryInitialDelayMillis * Math.pow(2, attempt);
        return Math.min(calculatedDelay >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) calculatedDelay, retryMaxDelayMillis);
    }

    private static boolean isRetryable(Throwable failure)
    {
        if (failure instanceof UnexpectedResponseException responseException) {
            return responseException.getStatusCode() == 429 ||
                    (responseException.getStatusCode() >= 500 && responseException.getStatusCode() < 600);
        }
        if (failure instanceof IOException || failure instanceof UncheckedIOException) {
            return true;
        }
        return failure.getCause() != null && failure.getCause() != failure && isRetryable(failure.getCause());
    }

    private static void sleep(long delayMillis)
    {
        try {
            Thread.sleep(delayMillis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("OAuth2 token retry interrupted", e);
        }
    }

    private static String clientSecretBasicAuthorization(String clientId, String clientSecret)
    {
        String encodedClientId = urlFormParameterEscaper().escape(requireNonNull(clientId, "clientId is null"));
        String encodedClientSecret = urlFormParameterEscaper().escape(requireNonNull(clientSecret, "clientSecret is null"));
        String credentials = encodedClientId + ":" + encodedClientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(UTF_8));
    }

    private record CachedToken(String accessToken, Optional<Instant> expiresAt, Set<String> grantedScopes)
    {
        private CachedToken
        {
            requireNonNull(accessToken, "accessToken is null");
            requireNonNull(expiresAt, "expiresAt is null");
            grantedScopes = ImmutableSet.copyOf(grantedScopes);
        }
    }

    private record TokenResponse(String accessToken, String tokenType, Long expiresIn, String scope) {}

    private record OAuthErrorDocument(String error, String errorDescription, String errorUri) {}

    private static final class TokenResponseHandler
            implements ResponseHandler<TokenResponse, RuntimeException>
    {
        private final List<String> requestedScopes;

        private TokenResponseHandler(List<String> requestedScopes)
        {
            this.requestedScopes = ImmutableList.copyOf(requestedScopes);
        }

        @Override
        public TokenResponse handleException(Request request, Exception exception)
        {
            throw propagate(request, exception);
        }

        @Override
        public TokenResponse handle(Request request, Response response)
        {
            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                // parse the whole token document; diagnostics, built only on failure, keep a bounded copy of it
                byte[] responseBody = getResponseBytes(request, response);
                Supplier<UnexpectedResponseException> captured = () -> {
                    int diagnosticLength = Math.min(responseBody.length, UnexpectedResponseException.DEFAULT_MAX_RESPONSE_BODY_BYTES);
                    return new UnexpectedResponseException(
                            "OAuth2 token request returned status " + response.getStatusCode(),
                            request,
                            response.getStatusCode(),
                            response.getHeaders(),
                            Arrays.copyOf(responseBody, diagnosticLength),
                            diagnosticLength < responseBody.length);
                };
                if (!isJsonContent(response)) {
                    throw invalidResponse(request, captured.get());
                }
                try {
                    Map<String, Object> document = JSON_DOCUMENT_CODEC.fromJson(responseBody);
                    TokenResponse tokenResponse = new TokenResponse(
                            stringValue(document, "access_token"),
                            stringValue(document, "token_type"),
                            longValue(document, "expires_in"),
                            stringValue(document, "scope"));
                    checkArgument(tokenResponse.accessToken() != null && !tokenResponse.accessToken().isBlank(), "OAuth2 token response has no access_token");
                    checkArgument(tokenResponse.tokenType() != null && tokenResponse.tokenType().equalsIgnoreCase("bearer"), "OAuth2 token response has unsupported token_type");
                    Set<String> granted = grantedScopes(tokenResponse, requestedScopes);
                    checkArgument(requestedScopes.stream().allMatch(granted::contains), "OAuth2 token response scope does not include requested scopes");
                    return tokenResponse;
                }
                catch (IllegalArgumentException _) {
                    // the parser's cause quotes the token document, so it is not attached
                    throw invalidResponse(request, captured.get());
                }
            }

            UnexpectedResponseException captured = captureUnexpectedResponse(
                    "OAuth2 token request returned status " + response.getStatusCode(),
                    request,
                    response);
            byte[] responseBody = captured.getResponseBodyBytes();
            OAuthErrorDocument error = null;
            if (isJsonContent(response)) {
                try {
                    Map<String, Object> document = JSON_DOCUMENT_CODEC.fromJson(responseBody);
                    error = new OAuthErrorDocument(
                            stringValue(document, "error"),
                            stringValue(document, "error_description"),
                            stringValue(document, "error_uri"));
                }
                catch (IllegalArgumentException _) {
                    // Preserve the bounded raw response without exposing it in the exception message.
                }
            }
            throw new OAuth2TokenException(
                    "OAuth2 token request failed with status " + response.getStatusCode(),
                    request,
                    captured,
                    error == null ? null : error.error(),
                    error == null ? null : error.errorDescription(),
                    error == null ? null : parseUri(error.errorUri()),
                    captured.getCause());
        }

        @Nullable
        private static String stringValue(Map<String, Object> document, String name)
        {
            Object value = document.get(name);
            checkArgument(value == null || value instanceof String, "OAuth2 token response field '%s' is not a string", name);
            return (String) value;
        }

        @Nullable
        private static Long longValue(Map<String, Object> document, String name)
        {
            Object value = document.get(name);
            checkArgument(value == null || value instanceof Number, "OAuth2 token response field '%s' is not a number", name);
            return value == null ? null : ((Number) value).longValue();
        }

        private static boolean isJsonContent(Response response)
        {
            try {
                return isJsonUtf8Content(response);
            }
            catch (IllegalArgumentException _) {
                // a malformed Content-Type is not JSON; the bounded raw body is still captured for diagnostics
                return false;
            }
        }

        private static OAuth2TokenException invalidResponse(Request request, UnexpectedResponseException captured)
        {
            return new OAuth2TokenException(
                    "OAuth2 token endpoint returned an invalid successful response",
                    request,
                    captured,
                    null,
                    null,
                    null,
                    null);
        }

        @Nullable
        private static URI parseUri(@Nullable String value)
        {
            if (value == null) {
                return null;
            }
            try {
                return URI.create(value);
            }
            catch (IllegalArgumentException _) {
                return null;
            }
        }
    }

    public static final class Builder
    {
        private final HttpClient httpClient;
        private final URI tokenUri;
        private final String clientId;
        private final String clientSecret;
        private List<String> scopes = List.of();
        private Optional<String> resource = Optional.empty();
        private Duration expirySkew = DEFAULT_EXPIRY_SKEW;
        private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
        private Duration retryInitialDelay = DEFAULT_RETRY_INITIAL_DELAY;
        private Duration retryMaxDelay = DEFAULT_RETRY_MAX_DELAY;
        private Clock clock = Clock.systemUTC();

        private Builder(HttpClient httpClient, URI tokenUri, String clientId, String clientSecret)
        {
            this.httpClient = requireNonNull(httpClient, "httpClient is null");
            this.tokenUri = requireNonNull(tokenUri, "tokenUri is null");
            checkArgument(tokenUri.isAbsolute(), "tokenUri is not absolute");
            checkArgument("http".equalsIgnoreCase(tokenUri.getScheme()) || "https".equalsIgnoreCase(tokenUri.getScheme()), "tokenUri scheme must be HTTP or HTTPS");
            this.clientId = requireNonNull(clientId, "clientId is null");
            this.clientSecret = requireNonNull(clientSecret, "clientSecret is null");
        }

        public Builder scopes(List<String> scopes)
        {
            ImmutableList.Builder<String> builder = ImmutableList.builder();
            requireNonNull(scopes, "scopes is null").forEach(scope -> {
                checkArgument(!requireNonNull(scope, "scope is null").isBlank(), "scope is blank");
                builder.add(scope);
            });
            this.scopes = builder.build();
            return this;
        }

        public Builder scope(String scope)
        {
            checkArgument(!requireNonNull(scope, "scope is null").isBlank(), "scope is blank");
            this.scopes = ImmutableList.<String>builder()
                    .addAll(scopes)
                    .add(scope)
                    .build();
            return this;
        }

        public Builder resource(String resource)
        {
            checkArgument(!requireNonNull(resource, "resource is null").isBlank(), "resource is blank");
            this.resource = Optional.of(resource);
            return this;
        }

        public Builder expirySkew(Duration expirySkew)
        {
            checkArgument(requireNonNull(expirySkew, "expirySkew is null").toMillis() >= 0, "expirySkew is negative");
            this.expirySkew = expirySkew;
            return this;
        }

        public Builder maxAttempts(int maxAttempts)
        {
            checkArgument(maxAttempts >= 1 && maxAttempts <= MAX_ATTEMPTS, "maxAttempts must be between 1 and %s", MAX_ATTEMPTS);
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder retryInitialDelay(Duration retryInitialDelay)
        {
            checkArgument(requireNonNull(retryInitialDelay, "retryInitialDelay is null").toMillis() >= 0, "retryInitialDelay is negative");
            this.retryInitialDelay = retryInitialDelay;
            return this;
        }

        public Builder retryMaxDelay(Duration retryMaxDelay)
        {
            checkArgument(requireNonNull(retryMaxDelay, "retryMaxDelay is null").toMillis() >= 0, "retryMaxDelay is negative");
            this.retryMaxDelay = retryMaxDelay;
            return this;
        }

        Builder clock(Clock clock)
        {
            this.clock = requireNonNull(clock, "clock is null");
            return this;
        }

        public ClientCredentialsTokenProvider build()
        {
            checkArgument(retryInitialDelay.toMillis() <= retryMaxDelay.toMillis(), "retryInitialDelay is greater than retryMaxDelay");
            return new ClientCredentialsTokenProvider(this);
        }
    }
}
