package io.airlift.api.servertests.patch;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiReadOnly;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;

import java.util.Optional;

@ApiResource(name = "item", description = "description")
public record Item(
        ApiResourceVersion syncToken,
        @ApiReadOnly @ApiDescription("id") ItemId itemId,
        @ApiDescription("name") String name,
        @ApiDescription("state") Optional<ItemState> state)
{
}
