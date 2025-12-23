package io.airlift.api.servertests.integration.testingserver.external;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiReadOnly;
import io.airlift.api.ApiResource;

import java.time.Instant;
import java.util.List;

@ApiPolyResource(key = "typeKey", name = "detail", description = "A polymorphic resource example", quotas = "DETAIL")
public sealed interface Detail
{
    String name();

    @ApiResource(name = "nameAndAge", description = "A name and an age")
    record NameAndAge(@ApiDescription("A name") String name, @ApiDescription("An age") int age)
            implements Detail
    {
    }

    @ApiResource(name = "schedule", description = "A schedule")
    record Schedule(@ApiDescription("A schedule name") String name, @ApiDescription("Start date") Instant startDate, @ApiDescription("End date") Instant endDate)
            implements Detail
    {
    }

    @ApiResource(name = "recursiveDetail", description = "A resource containing a list of itself")
    @ApiReadOnly
    record Recursive(
            @ApiDescription("Name") String name,
            @ApiReadOnly @ApiDescription("Nested resource") List<Recursive> nested)
            implements Detail
    {
    }
}
