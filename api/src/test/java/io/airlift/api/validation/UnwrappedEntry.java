package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

import java.util.List;
import java.util.Optional;

@ApiResource(name = "entry", description = "Unwrapped entry")
public record UnwrappedEntry(
        @ApiDescription("Entry names") List<String> names,
        @ApiDescription("Entry priority") int priority,
        @ApiDescription("Optional description") Optional<String> description) {}
