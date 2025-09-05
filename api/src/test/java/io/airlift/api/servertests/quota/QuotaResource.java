package io.airlift.api.servertests.quota;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

@ApiResource(name = "kwota", description = "a description", quotas = "DUMMY")
public record QuotaResource(@ApiDescription("thing") String name)
{
}
