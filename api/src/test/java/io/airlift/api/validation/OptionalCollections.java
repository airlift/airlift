package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiReadOnly;
import io.airlift.api.ApiResource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApiResource(name = "optionalCollections", description = "boxed")
@ApiReadOnly
public record OptionalCollections(
        @ApiReadOnly @ApiDescription("bool") Optional<List<Boolean>> optionalBooleanList,
        @ApiReadOnly @ApiDescription("thing") Optional<List<Thing>> optionalThingList,
        @ApiReadOnly @ApiDescription("map") Optional<Map<String, String>> optionalMap,
        @ApiReadOnly @ApiDescription("enum") Optional<List<Enum>> optionalEnumList)
{
    public enum Enum {
        ABC,
        DEF
    }
}
