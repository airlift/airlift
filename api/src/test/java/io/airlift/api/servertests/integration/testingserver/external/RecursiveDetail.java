package io.airlift.api.servertests.integration.testingserver.external;

import com.google.common.collect.ImmutableList;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiReadOnly;
import io.airlift.api.ApiResource;

import java.util.List;

import static java.util.Objects.requireNonNull;

@ApiResource(name = "recursive", description = "Recursive resource test")
public record RecursiveDetail(@ApiDescription("name") String name, @ApiReadOnly @ApiDescription("items") List<Item> items)
{
    @ApiResource(name = "recursiveItem", description = "Recursive resource test")
    public record Item(@ApiDescription("qty") int qty, @ApiDescription("recursive") List<RecursiveDetail> recursiveDetails)
    {
        public Item
        {
            recursiveDetails = ImmutableList.copyOf(recursiveDetails);
        }
    }

    public RecursiveDetail
    {
        requireNonNull(name, "name is null");
        items = ImmutableList.copyOf(items);
    }
}
