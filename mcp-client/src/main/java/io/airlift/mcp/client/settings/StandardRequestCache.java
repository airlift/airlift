package io.airlift.mcp.client.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSet;
import io.airlift.http.client.Request;
import io.airlift.log.Logger;
import io.airlift.mcp.model.CacheableResult;
import io.airlift.mcp.model.InputRequests;
import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListResourceTemplatesResult;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListToolsResult;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.ResourcesUpdatedNotification;

import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS;
import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.http.client.HeaderNames.AUTHORIZATION;
import static io.airlift.mcp.client.McpMapper.jsonMapper;
import static io.airlift.mcp.model.CacheScope.PUBLIC;
import static io.airlift.mcp.model.Constants.METADATA_CLIENT_LOG_LEVEL;
import static io.airlift.mcp.model.Constants.METADATA_PROGRESS_TOKEN;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROMPTS_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_UPDATED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_TOOLS_LIST_CHANGED;
import static java.util.Objects.requireNonNull;

public class StandardRequestCache
        implements RequestCache
{
    private static final Logger log = Logger.get(StandardRequestCache.class);

    private static final String META_FIELD = "_meta";
    private static final Set<String> CALL_METADATA = ImmutableSet.of(METADATA_PROGRESS_TOKEN, METADATA_CLIENT_LOG_LEVEL);
    private static final ObjectWriter CANONICAL_WRITER = jsonMapper().writer().with(ORDER_MAP_ENTRIES_BY_KEYS);

    private final Cache<Key<?>, Entry<?>> cache;
    private final Function<Request, String> authMapper;
    private final URI uri;

    private StandardRequestCache(URI uri, Cache<Key<?>, Entry<?>> cache, Function<Request, String> authMapper)
    {
        this.uri = requireNonNull(uri, "uri is null");
        this.cache = requireNonNull(cache, "cache is null");
        this.authMapper = requireNonNull(authMapper, "authMapper is null");
    }

    // resourceUri is the resource a resources/read names, kept apart so that a resources/updated notification can find
    // every read of it whatever else its parameters hold
    private record Key<R>(Class<R> responseClass, String method, String params, Optional<String> resourceUri, Optional<String> auth, Optional<String> cursor)
    {
        private Key
        {
            requireNonNull(responseClass, "clazz is null");
            requireNonNull(method, "method is null");
            requireNonNull(params, "params is null");
            requireNonNull(resourceUri, "resourceUri is null");
            requireNonNull(auth, "auth is null");
            requireNonNull(cursor, "cursor is null");
        }

        Key<R> withAuth(String auth)
        {
            return new Key<>(responseClass, method, params, resourceUri, Optional.of(auth), cursor);
        }
    }

    private record Entry<R extends CacheableResult<?>>(R result, Instant cachedAt)
    {
        private Entry
        {
            requireNonNull(result, "result is null");
            requireNonNull(cachedAt, "cachedAt is null");
        }
    }

    public static Builder builder(URI uri)
    {
        return new Builder(uri);
    }

    public static class Builder
    {
        private int maxEntries = 1000;
        private final URI uri;
        private Duration maxTtl = Duration.ofMinutes(15);
        private Function<Request, String> authMapper = request -> Optional.ofNullable(request.getHeader(AUTHORIZATION)).orElse("");

        private Builder(URI uri)
        {
            this.uri = requireNonNull(uri, "uri is null");
        }

        public Builder withMaxEntries(int maxEntries)
        {
            checkArgument(maxEntries > 0, "maxEntries must be > 0");
            this.maxEntries = maxEntries;
            return this;
        }

        /**
         * The longest any result is kept, whatever TTL the server gave it.
         */
        public Builder withMaxTtl(Duration maxTtl)
        {
            this.maxTtl = requireNonNull(maxTtl, "maxTtl is null");
            return this;
        }

        public Builder withAuthMapper(Function<Request, String> authMapper)
        {
            this.authMapper = requireNonNull(authMapper, "authMapper is null");
            return this;
        }

        public StandardRequestCache build()
        {
            Cache<Key<?>, Entry<?>> cache = CacheBuilder.newBuilder()
                    .expireAfterWrite(maxTtl)
                    .maximumSize(maxEntries)
                    .build();
            return new StandardRequestCache(uri, cache, authMapper);
        }
    }

    @Override
    public <T, R extends CacheableResult<?>> R executeRequest(Request request, String mcpMethod, T mcpRequest, Class<R> resultClass, Optional<String> cursor, Supplier<R> resultSupplier)
    {
        checkArgument(uri.equals(request.getUri()), "cache is bound to %s but the request is for %s", uri, request.getUri());

        Optional<String> resourceUri = (mcpRequest instanceof ReadResourceRequest readResourceRequest) ? Optional.of(readResourceRequest.uri()) : Optional.empty();
        Key<R> keyWithoutAuth = new Key<>(resultClass, mcpMethod, cacheableParams(mcpRequest), resourceUri, Optional.empty(), cursor);

        // a blank credential means the caller could not be identified - see withAuthMapper()
        Optional<Key<R>> keyWithAuth = Optional.ofNullable(authMapper.apply(request))
                .filter(auth -> !auth.isBlank())
                .map(keyWithoutAuth::withAuth);

        // an unidentified caller may only read entries that everyone shares
        Optional<Entry<?>> maybeEntry = keyWithAuth
                .flatMap(key -> Optional.<Entry<?>>ofNullable(cache.asMap().compute(key, (_, currentValue) -> computeEntry(currentValue))))
                .or(() -> Optional.ofNullable(cache.asMap().compute(keyWithoutAuth, (_, currentValue) -> computeEntry(currentValue))));

        return maybeEntry.map(entry -> resultClass.cast(entry.result))
                .orElseGet(() -> {
                    R result;
                    try {
                        result = resultSupplier.get();
                    }
                    catch (RuntimeException e) {
                        // https://modelcontextprotocol.io/specification/2026-07-28/server/utilities/caching#interaction-with-pagination
                        // a page that cannot be fetched may mean its cursor is no longer valid, so every cached page of
                        // the listing is discarded and the next listing starts again from the first page
                        cursor.ifPresent(_ -> invalidate(resultClass, Optional.empty()));
                        throw e;
                    }
                    cacheKey(result, keyWithoutAuth, keyWithAuth)
                            .ifPresent(cacheKey -> cache.put(cacheKey, new Entry<>(result, Instant.now())));
                    return result;
                });
    }

    @Override
    public void accept(Object id, String method, Optional<Object> params)
    {
        switch (method) {
            case NOTIFICATION_TOOLS_LIST_CHANGED -> invalidate(ListToolsResult.class, Optional.empty());
            case NOTIFICATION_PROMPTS_LIST_CHANGED -> invalidate(ListPromptsResult.class, Optional.empty());
            case NOTIFICATION_RESOURCES_LIST_CHANGED -> {
                invalidate(ListResourcesResult.class, Optional.empty());
                invalidate(ListResourceTemplatesResult.class, Optional.empty());
            }
            case NOTIFICATION_RESOURCES_UPDATED -> resourcesUpdatedNotification(params)
                    .ifPresent(notification -> invalidate(ReadResourceResult.class, Optional.of(notification.uri())));
        }
    }

    private static Optional<ResourcesUpdatedNotification> resourcesUpdatedNotification(Optional<Object> params)
    {
        try {
            return params.map(value -> jsonMapper().convertValue(value, ResourcesUpdatedNotification.class));
        }
        catch (Exception e) {
            log.debug(e, "Ignoring unreadable %s notification", NOTIFICATION_RESOURCES_UPDATED);
            return Optional.empty();
        }
    }

    private void invalidate(Class<?> responseClass, Optional<String> resourceUri)
    {
        cache.asMap().entrySet()
                .stream()
                .filter(entry -> responseClass.equals(entry.getKey().responseClass()))
                .filter(entry -> resourceUri.map(uri -> entry.getKey().resourceUri().equals(Optional.of(uri))).orElse(true))
                .forEach(entry -> cache.invalidate(entry.getKey()));
    }

    private static <R extends CacheableResult<?>> Optional<Key<R>> cacheKey(R result, Key<R> keyWithoutAuth, Optional<Key<R>> keyWithAuth)
    {
        if (isCacheable(result)) {
            return result.cacheScope().orElse(PUBLIC) == PUBLIC ? Optional.of(keyWithoutAuth) : keyWithAuth;
        }
        return Optional.empty();
    }

    private static boolean isCacheable(CacheableResult<?> result)
    {
        if ((result instanceof InputRequests inputRequests) && inputRequests.inputRequests().isPresent()) {
            return false;
        }

        // https://modelcontextprotocol.io/specification/2026-07-28/server/utilities/caching#time-to-live-ttl-field
        // a ttl of zero or less means the server does not want the result reused, and a result without one is to be
        // treated the same way
        return result.ttlMs().stream().anyMatch(ttlMs -> ttlMs > 0);
    }

    private Entry<?> computeEntry(Entry<?> currentValue)
    {
        if ((currentValue != null) && currentValue.result.ttlMs().isPresent()) {
            long age = Duration.between(currentValue.cachedAt, Instant.now()).toMillis();
            if (age >= currentValue.result.ttlMs().orElseThrow()) {
                return null;
            }
        }
        return currentValue;
    }

    // https://modelcontextprotocol.io/specification/2026-07-28/server/utilities/caching#cache-key
    // a result may only be reused for a request with the same parameters. That includes _meta, where the protocol
    // version, the client capabilities and any META a connection adds can each change what the server returns. Only
    // the metadata that describes the call itself, rather than what it asks for, is left out
    private static String cacheableParams(Object mcpRequest)
    {
        JsonNode params = jsonMapper().valueToTree(mcpRequest);
        if (params.path(META_FIELD) instanceof ObjectNode meta) {
            meta.remove(CALL_METADATA);
        }

        try {
            // map entries are sorted so that the same parameters give the same key whatever order they were added in
            return CANONICAL_WRITER.writeValueAsString(jsonMapper().convertValue(params, Object.class));
        }
        catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
