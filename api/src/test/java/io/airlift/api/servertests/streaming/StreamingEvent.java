package io.airlift.api.servertests.streaming;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

@ApiResource(name = "streamingEvent", description = "An event sent through a server-sent event stream")
public record StreamingEvent(@ApiDescription("Event type") String type, @ApiDescription("Event message") String message) {}
