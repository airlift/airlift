package io.airlift.api.servertests.integration.testingserver.external;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiResource;

import java.time.Instant;

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
}
