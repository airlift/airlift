package io.airlift.api.validation;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.PUBLIC_ONLY;

@JsonAutoDetect(getterVisibility = PUBLIC_ONLY)
@ApiResource(name = "thing", description = "thingy")
public record DuplicateThing(
        ApiResourceVersion version,
        @ApiDescription("id") ThingId thingId,
        @ApiDescription("original name") String name,
        @ApiDescription("original description") String description)
{
}
