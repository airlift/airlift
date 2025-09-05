package io.airlift.api.servertests.unwrapped;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;
import io.airlift.api.ApiUnwrapped;

@ApiResource(name = "top", openApiAlternateName = "topResult", description = "unused")
public record TopLevelResult(
        ApiResourceVersion syncToken,
        @ApiDescription("unused") TopLevelId topId,
        @ApiDescription("unused") String name,
        @ApiDescription("unused") int age,
        @ApiUnwrapped ChildLevel childLevel)
{
}
