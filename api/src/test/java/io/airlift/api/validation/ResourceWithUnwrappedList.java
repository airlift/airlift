package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiUnwrapped;

import java.util.List;

@ApiResource(name = "unwrappedList", description = "Resource with @ApiUnwrapped List", quotas = "TEST")
public record ResourceWithUnwrappedList(
        @ApiDescription("A name") String name,
        @ApiDescription("Entries") @ApiUnwrapped List<UnwrappedEntry> entries) {}
