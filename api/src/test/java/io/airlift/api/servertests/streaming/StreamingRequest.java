package io.airlift.api.servertests.streaming;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

@ApiResource(name = "streamer", openApiAlternateName = "newStreamer", description = "new streamer")
public record StreamingRequest(@ApiDescription("ok") String something) {}
