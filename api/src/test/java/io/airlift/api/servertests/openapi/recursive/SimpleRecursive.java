package io.airlift.api.servertests.openapi.recursive;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiResource;

import java.time.Instant;
import java.util.List;

import static io.airlift.api.ApiOpenApiTrait.USE_ONE_OF_DISCRIMINATORS;

@ApiPolyResource(key = "typeKey", name = "simple", description = "dummy", openApiTraits = USE_ONE_OF_DISCRIMINATORS)
public sealed interface SimpleRecursive
        permits SimpleRecursive.NameAndAge, SimpleRecursive.Schedule, SimpleRecursive.RecursiveDetail
{
    @ApiResource(name = "nameAndAge", description = "dummy")
    record NameAndAge(
            @ApiDescription("dummy") String detailId,
            @ApiDescription("dummy") String syncToken,
            @ApiDescription("dummy") String name,
            @ApiDescription("dummy") int age)
            implements SimpleRecursive {}

    @ApiResource(name = "schedule", description = "dummy")
    record Schedule(
            @ApiDescription("dummy") String detailId,
            @ApiDescription("dummy") String syncToken,
            @ApiDescription("dummy") String name,
            @ApiDescription("dummy") Instant startDate,
            @ApiDescription("dummy") Instant endDate)
            implements SimpleRecursive {}

    @ApiResource(name = "recursiveDetail", description = "dummy")
    record RecursiveDetail(
            @ApiDescription("dummy") String detailId,
            @ApiDescription("dummy") String syncToken,
            @ApiDescription("dummy") String name,
            @ApiDescription("dummy") List<SimpleRecursive> nested)
            implements SimpleRecursive {}
}
