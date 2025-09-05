package io.airlift.api.servertests.standard;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

@ApiResource(name = "baggage", description = "dummy", quotas = "BAGGAGE")
public record Baggage(@ApiDescription("dummy") String dummy)
{
}
