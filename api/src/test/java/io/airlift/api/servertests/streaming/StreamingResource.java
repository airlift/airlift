package io.airlift.api.servertests.streaming;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;

@ApiResource(name = "streamer", description = "something")
public record StreamingResource(@ApiDescription("dummy") StreamingResourceId streamerId, ApiResourceVersion syncToken, @ApiDescription("ok") String something)
{
}
