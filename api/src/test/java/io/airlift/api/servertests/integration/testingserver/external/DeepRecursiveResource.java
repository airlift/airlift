package io.airlift.api.servertests.integration.testingserver.external;

import com.google.common.collect.ImmutableList;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiReadOnly;
import io.airlift.api.ApiResource;

import java.util.List;

@ApiPolyResource(key = "typeKey", name = "deepRecursiveResource", description = "A deep recursive polymorphic resource")
public sealed interface DeepRecursiveResource
{
    String name();

    @ApiResource(name = "leaf", description = "Leaf deep recursive resource")
    record Leaf(@ApiDescription("Name") String name)
            implements DeepRecursiveResource
    {
    }

    @ApiResource(name = "branch", description = "Branch deep recursive resource")
    @ApiReadOnly
    record Branch(
            @ApiDescription("Name") String name,
            @ApiReadOnly @ApiDescription("Nested resources") List<DeepRecursiveResource> nestedResources)
            implements DeepRecursiveResource
    {
        public Branch
        {
            nestedResources = ImmutableList.copyOf(nestedResources);
        }
    }
}
