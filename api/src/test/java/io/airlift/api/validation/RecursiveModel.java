package io.airlift.api.validation;

import com.google.common.collect.ImmutableList;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

import java.util.List;

import static java.util.Objects.requireNonNull;

@ApiResource(name = "recursiveModel", description = "A model that references itself")
public record RecursiveModel(@ApiDescription("Child") List<RecursiveModel> children, @ApiDescription("sub") SubModel sub)
{
    @ApiResource(name = "recursiveSubModel", description = "A model that contains a recursive parent resource")
    public record SubModel(@ApiDescription("sub") List<RecursiveModel> recursiveModels)
    {
        public SubModel
        {
            recursiveModels = ImmutableList.copyOf(recursiveModels);
        }
    }

    public RecursiveModel
    {
        children = ImmutableList.copyOf(children);
        requireNonNull(sub, "sub is null");
    }
}
