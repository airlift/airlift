package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;
import java.util.OptionalInt;

import static io.airlift.mcp.model.CacheScope.PRIVATE;

@SuppressWarnings("rawtypes")
public interface CacheableResult<T extends CacheableResult<T>>
{
    CacheableResult<?> DEFAULT = new CacheableResult()
    {
        @Override
        public OptionalInt ttlMs()
        {
            return OptionalInt.of(0);
        }

        @Override
        public Optional<CacheScope> cacheScope()
        {
            return Optional.of(PRIVATE);
        }

        @Override
        public CacheableResult withCacheableResult(int ttlMs, CacheScope cacheScope)
        {
            throw new UnsupportedOperationException();
        }
    };

    @JsonProperty
    default Optional<ResultType> resultType()
    {
        return Optional.empty();
    }

    @JsonProperty
    default OptionalInt ttlMs()
    {
        return OptionalInt.empty();
    }

    @JsonProperty
    default Optional<CacheScope> cacheScope()
    {
        return Optional.empty();
    }

    T withCacheableResult(int ttlMs, CacheScope cacheScope);
}
