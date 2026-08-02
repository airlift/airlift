package io.airlift.mcp.client.settings;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.airlift.http.client.Request;
import io.airlift.mcp.model.CacheableResult;
import io.airlift.mcp.model.ReadResourceRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.http.client.HeaderNames.AUTHORIZATION;
import static io.airlift.mcp.model.CacheScope.PUBLIC;
import static io.airlift.mcp.model.ResultType.COMPLETE;
import static io.airlift.mcp.model.ResultType.INPUT_REQUIRED;
import static java.util.Objects.requireNonNull;

public class StandardRequestCache
        implements RequestCache
{
    private final Cache<Key<?>, Entry<?>> cache;
    private final Function<Request, String> authMapper;

    private StandardRequestCache(Cache<Key<?>, Entry<?>> cache, Function<Request, String> authMapper)
    {
        this.cache = requireNonNull(cache, "cache is null");
        this.authMapper = requireNonNull(authMapper, "authMapper is null");
    }

    private record Key<R>(URI uri, Class<R> responseClass, String key, Optional<String> auth)
    {
        private Key
        {
            requireNonNull(uri, "uri is null");
            requireNonNull(responseClass, "clazz is null");
            requireNonNull(key, "key is null");
            requireNonNull(auth, "auth is null");
        }

        Key<R> withAuth(String auth)
        {
            return new Key<>(uri, responseClass, key, Optional.of(auth));
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

    public static Builder builder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private int maxEntries = 1000;
        private Duration defaultExpiration = Duration.ofMinutes(15);
        private Function<Request, String> authMapper = request -> Optional.ofNullable(request.getHeader(AUTHORIZATION)).orElse("");

        private Builder() {}

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
            return new StandardRequestCache(cache, authMapper);
        }
    }

    @Override
    public <T, R extends CacheableResult<?>> R executeRequest(Request request, String mcpMethod, T mcpRequest, Class<R> resultClass, Optional<String> cursor, Supplier<R> resultSupplier)
    {
        Key<R> keyWithoutAuth = new Key<>(request.getUri(), resultClass, buildCacheKey(mcpMethod, mcpRequest, cursor), Optional.empty());
        Key<R> keyWithAuth = keyWithoutAuth.withAuth(authMapper.apply(request));

        Optional<Entry<?>> maybeEntry = Optional.<Entry<?>>ofNullable(cache.asMap().compute(keyWithAuth, (_, currentValue) -> computeEntry(currentValue)))
                .or(() -> Optional.ofNullable(cache.asMap().compute(keyWithoutAuth, (_, currentValue) -> computeEntry(currentValue))));

        return maybeEntry.map(entry -> resultClass.cast(entry.result))
                .orElseGet(() -> {
                    R result = resultSupplier.get();
                    if (isCacheable(result)) {
                        Key<R> cacheKey = (result.cacheScope().orElse(PUBLIC) == PUBLIC) ? keyWithoutAuth : keyWithAuth;
                        cache.put(cacheKey, new Entry<>(result, Instant.now()));
                    }
                    return result;
                });
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

    private String buildCacheKey(String mcpMethod, Object mcpRequest, Optional<String> cursor)
    {
        String key = switch (mcpRequest) {
            case ReadResourceRequest(var uri, _, _, _) -> String.join("|", mcpMethod, uri);
            default -> mcpMethod;
        };
        return cursor.map(value -> String.join("|", key, value)).orElse(key);
    }
}
