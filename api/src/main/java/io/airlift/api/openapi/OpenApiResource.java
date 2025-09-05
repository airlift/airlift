package io.airlift.api.openapi;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.airlift.api.model.ModelServiceType;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.ext.RuntimeDelegate;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.nio.charset.StandardCharsets.UTF_8;

@OpenApiService
@Singleton
@Path("/dummy") // gets replaced by OpenApiModule
public class OpenApiResource
{
    private final OpenApiCache openApiCache;
    private final Map<ServiceTypeKey, ModelServiceType> serviceTypes;
    private final Map<String, EntityTag> entityTags = new ConcurrentHashMap<>();

    private record ServiceTypeKey(String type, String version) {}

    @Inject
    public OpenApiResource(Collection<ModelServiceType> modelServiceTypes, OpenApiProvider openApiProvider, OpenApiFilter openApiFilter, OpenApiMetadata openApiMetadata)
    {
        // pre-build services as a validation that there are no OpenApi build errors
        modelServiceTypes.forEach(serviceType -> openApiProvider.build(serviceType, ignore -> true));

        serviceTypes = modelServiceTypes
                .stream()
                .collect(toImmutableMap(type -> new ServiceTypeKey(type.id(), "v" + type.version()), type -> type));

        openApiCache = new OpenApiCache(openApiProvider, openApiFilter, openApiMetadata.cacheDuration());
    }

    @SuppressWarnings("UnresolvedRestParam")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getOpenApiFile(
            @Context ContainerRequestContext containerRequestContext,
            @HeaderParam(HttpHeaders.IF_NONE_MATCH) String ifNoneMatch,
            @PathParam("type") String type,
            @PathParam("version") String version,
            @QueryParam("includeSummary") boolean includeSummary)
    {
        ModelServiceType serviceType = serviceTypes.get(new ServiceTypeKey(type, version));
        if (serviceType != null) {
            String json = openApiCache.getOrBuild(serviceType, containerRequestContext, includeSummary);
            return withCaching(json, ifNoneMatch);
        }
        throw new WebApplicationException(Response.Status.NOT_FOUND);
    }

    @OPTIONS
    public Response getOptions()
    {
        return withCorsHeaders(Response.ok()).build();
    }

    private static ResponseBuilder withCorsHeaders(ResponseBuilder builder)
    {
        return builder.header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "OPTIONS, GET");
    }

    // copied from portal's UIContent
    private Response withCaching(String content, String ifNoneMatch)
    {
        EntityTag eTag = entityTags.computeIfAbsent(content, OpenApiResource::calculateETag);
        EntityTag requestETag = getRequestETag(ifNoneMatch);

        if (eTag.equals(requestETag)) {
            return Response.notModified(eTag)
                    .build();
        }

        CacheControl cacheControl = new CacheControl();
        return withCorsHeaders(Response.ok(content))
                .cacheControl(cacheControl)
                .tag(eTag)
                .build();
    }

    private static EntityTag calculateETag(String content)
    {
        HashCode hashCode = Hashing.sha256().hashString(content, UTF_8);
        return new EntityTag(hashCode.toString());
    }

    // copied from portal's UIContent
    private static EntityTag getRequestETag(String ifNoneMatch)
    {
        if (ifNoneMatch == null) {
            return null;
        }
        return RuntimeDelegate.getInstance().createHeaderDelegate(EntityTag.class).fromString(ifNoneMatch);
    }
}
