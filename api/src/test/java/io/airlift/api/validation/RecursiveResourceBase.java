package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiReadOnly;
import io.airlift.api.ApiResource;

import java.util.List;
import java.util.Optional;

@ApiPolyResource(key = "typeKey", name = "foo", description = "foo")
public sealed interface RecursiveResourceBase
{
    @ApiResource(name = "actualResource", description = "foo")
    record ActualResource(
            @ApiDescription("foo") Optional<List<RecursiveResourceBase>> nested,
            @ApiDescription("foo") List<ActualResource> selfList,
            @ApiDescription("foo") List<InnerRecursive> innerRecursive)
            implements RecursiveResourceBase
    {
        @ApiResource(name = "innerRecursive", description = "A resource containing a list of itself")
        public record InnerRecursive(@ApiReadOnly @ApiDescription("Inner name") List<InnerRecursive> nested) {}
    }
}
