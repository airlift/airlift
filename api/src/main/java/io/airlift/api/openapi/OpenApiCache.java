package io.airlift.api.openapi;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.openapi.models.OpenAPI;
import io.airlift.json.JsonCodec;
import jakarta.ws.rs.container.ContainerRequestContext;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import static io.airlift.json.JsonCodec.jsonCodec;
import static java.util.Objects.requireNonNull;

class OpenApiCache
{
    private static final JsonCodec<OpenAPI> OPEN_API_CODEC = jsonCodec(OpenAPI.class);

    private final OpenApiProvider openApiProvider;
    private final OpenApiFilter openApiFilter;
    private final Cache<CachedFilteredServiceTypeKey, String> cache;

    private record CachedFilteredServiceTypeKey(ModelServiceType serviceType, String path, boolean includeSummary) {}

    OpenApiCache(OpenApiProvider openApiProvider, OpenApiFilter openApiFilter, Duration cacheDuration)
    {
        this.openApiProvider = requireNonNull(openApiProvider, "openApiFilteredBuilder is null");
        this.openApiFilter = requireNonNull(openApiFilter, "openApiFilterProvider is null");

        cache = CacheBuilder.newBuilder().expireAfterWrite(cacheDuration).build();
    }

    String getOrBuild(ModelServiceType serviceType, ContainerRequestContext containerRequestContext, boolean includeSummary)
    {
        CachedFilteredServiceTypeKey key = new CachedFilteredServiceTypeKey(serviceType, containerRequestContext.getUriInfo().getAbsolutePath().toString(), includeSummary);
        try {
            return cache.get(key, () -> {
                Predicate<Method> methodFilter = openApiFilter.filterForRequest(containerRequestContext);
                OpenAPI openAPI = openApiProvider.build(serviceType, methodFilter);
                return OPEN_API_CODEC.toJson(openAPI);
            });
        }
        catch (ExecutionException e) {
            // should never get here
            throw new UncheckedExecutionException(e);
        }
    }
}
