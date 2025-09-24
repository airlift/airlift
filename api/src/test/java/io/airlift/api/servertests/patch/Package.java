package io.airlift.api.servertests.patch;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiReadOnly;
import io.airlift.api.ApiResource;

import java.util.List;
import java.util.Optional;

@ApiResource(name = "package", description = "dummy")
public record Package(
        @ApiReadOnly @ApiDescription("dummy") PackageId packageId,
        @ApiDescription("dummy") Optional<Manifest> manifest,
        @ApiReadOnly @ApiDescription("dummy") Item mainItem,
        @ApiDescription("dummy") String veryImportantThing,
        @ApiDescription("dummy") List<ItemId> itemIds)
{
    public Package withVeryImportantThing(String veryImportantThing)
    {
        return new Package(packageId, manifest, mainItem, veryImportantThing, itemIds);
    }

    public Package withManifest(Optional<Manifest> manifest)
    {
        return new Package(packageId, manifest, mainItem, veryImportantThing, itemIds);
    }
}
