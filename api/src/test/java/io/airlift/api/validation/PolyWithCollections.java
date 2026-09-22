package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiReadOnly;
import io.airlift.api.ApiResource;

import java.util.List;
import java.util.Optional;

@ApiPolyResource(key = "kind", name = "polyWithCollections", description = "Poly resource whose sub-resources hold collections")
public sealed interface PolyWithCollections
{
    enum Flavor
    {
        SWEET,
        SOUR,
    }

    @ApiResource(name = "flavored", description = "Sub-resource with an enum collection")
    record Flavored(
            @ApiDescription("Flavors") List<Flavor> flavors,
            @ApiDescription("Preferred") Optional<Flavor> preferred)
            implements PolyWithCollections {}

    @ApiResource(name = "counted", description = "Sub-resource with a read-only collection")
    record Counted(
            @ApiDescription("Name") String name,
            @ApiReadOnly @ApiDescription("Counts") List<Integer> counts)
            implements PolyWithCollections {}
}
