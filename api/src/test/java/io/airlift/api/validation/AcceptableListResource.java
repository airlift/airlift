package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;

import java.util.List;

@ApiResource(name = "acceptablelists", description = "resource containing acceptable lists")
public record AcceptableListResource(
        ApiResourceVersion syncToken,
        @ApiDescription("id") AcceptableListResourceId acceptablelistsId,
        @ApiDescription("readonly") List<ReadOnlyResource> readOnlyResourceList,
        @ApiDescription("strings") List<String> stringList,
        @ApiDescription("enums") List<Enum> enumList)
{
    public enum Enum {
        ABC,
        DEF
    }
}
