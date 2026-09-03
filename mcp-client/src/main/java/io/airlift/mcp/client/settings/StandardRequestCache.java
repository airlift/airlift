package io.airlift.mcp.client.settings;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.airlift.http.client.Request;
import io.airlift.log.Logger;
import io.airlift.mcp.model.CacheableResult;
import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListResourceTemplatesResult;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListToolsResult;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.ResourcesUpdatedNotification;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.http.client.HeaderNames.AUTHORIZATION;
import static io.airlift.mcp.client.McpMapper.jsonMapper;
import static io.airlift.mcp.model.CacheScope.PUBLIC;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_READ;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROMPTS_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_UPDATED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_TOOLS_LIST_CHANGED;
import static io.airlift.mcp.model.ResultType.COMPLETE;
import static io.airlift.mcp.model.ResultType.INPUT_REQUIRED;
import static java.util.Objects.requireNonNull;

public class StandardRequestCache
        implements RequestCache
{
    private static final Logger log = Logger.get(StandardRequestCache.class);

    private final Cache<Key<?>, Entry<?>> cache;
    private final Function<Request, String> authMapper;
    private final URI uri;

    private StandardRequestCache(URI uri, Cache<Key<?>, Entry<?>> cache, Function<Request, String> authMapper)
    {
        this.uri = requireNonNull(uri, "uri is null");
        this.cache = requireNonNull(cache, "cache is null");
        this.authMapper = requireNonNull(authMapper, "authMapper is null");
    }

    private record Key<R>(Class<R> responseClass, String key, Optional<String> auth, Optional<String> cursor)
    {
        private Key
        {
            requireNonNull(responseClass, "clazz is null");
            requireNonNull(key, "key is null");
            requireNonNull(auth, "auth is null");
            requireNonNull(cursor, "cursor is null");
        }

        Key<R> withAuth(String auth)
        {
            return new Key<>(responseClass, key, Optional.of(auth), cursor);
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
        private Duration defaultExpiration = Duration.ofMinutes(15);
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

        public Builder withDefaultTtl(Duration defaultExpiration)
        {
            this.defaultExpiration = requireNonNull(defaultExpiration, "defaultExpiration is null");
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
                    .expireAfterWrite(defaultExpiration)
                    .maximumSize(maxEntries)
                    .build();
            return new StandardRequestCache(uri, cache, authMapper);
        }
    }

    @Override
    public <T, R extends CacheableResult<?>> R executeRequest(Request request, String mcpMethod, T mcpRequest, Class<R> resultClass, Optional<String> cursor, Supplier<R> resultSupplier)
    {
        checkArgument(uri.equals(request.getUri()), "cache is bound to %s but the request is for %s", uri, request.getUri());

        Key<R> keyWithoutAuth = new Key<>(resultClass, buildCacheKey(mcpMethod, mcpRequest), Optional.empty(), cursor);

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
                    R result = resultSupplier.get();
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
                    .ifPresent(notification -> {
                        String key = buildCacheKey(METHOD_RESOURCES_READ, new ReadResourceRequest(notification.uri()));
                        invalidate(ReadResourceResult.class, Optional.of(key));
                    });
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

    private void invalidate(Class<?> responseClass, Optional<String> matchKey)
    {
        cache.asMap().entrySet()
                .stream()
                .filter(entry -> responseClass.equals(entry.getKey().responseClass()))
                .filter(entry -> matchKey.map(key -> key.equals(entry.getKey().key())).orElse(true))
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
        if (result.resultType().orElse(COMPLETE) == INPUT_REQUIRED) {
            return false;
        }

        // a ttl of zero or less means the server does not want the result reused
        return result.ttlMs().stream().allMatch(ttlMs -> ttlMs > 0);
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

    private static String buildCacheKey(String mcpMethod, Object mcpRequest)
    {
        return switch (mcpRequest) {
            case ReadResourceRequest(var uri, _, _, _) -> String.join("|", mcpMethod, uri);
            default -> mcpMethod;
        };
    }
}
