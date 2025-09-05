package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

import java.util.Optional;

@ApiResource(name = "boxed", description = "boxed")
public record BoxedOptionals(
        @ApiDescription("bool") Optional<Boolean> boxedBoolean,
        @ApiDescription("int") Optional<Integer> boxedInt,
        @ApiDescription("long") Optional<Long> boxedLong,
        @ApiDescription("double") Optional<Double> boxedDouble)
{
}
